/*
Person 2: WorldMap — the entire game world: 9 overworld rooms + 3 dungeon rooms
Who RIGs it: GameplayPane — creates one WorldMap instance, calls update(dt, player) each tick,
               and passes the canvas so rooms can add/remove their graphics on transition.
             SaveManager / SaveData — reads roomId from WorldMap.getActiveRoom() on save;
               on load, calls WorldMap.setActiveRoomById(roomId) to restore the player's location.

Extends: nothing
Owns: Room[][] overworldGrid (3×3), Room[] dungeonRooms (3), RoomTransition

===============
PLAN OF ACTION
===============

- CLASS ROLE
- WorldMap is the single source of truth for which room is active and how all rooms connect.
- WorldMap creates all 12 rooms at startup, wires their exits, and manages room transitions.
- WorldMap does NOT draw itself — it delegates draw to the active Room (and to RoomTransition
  during a transition animation).
- WorldMap does NOT own the Player — GameplayPane passes Player into update() each tick.

- COORDINATE SYSTEM (overworld grid)
    Column:   A=0   B=1   C=2
    Row:      1=0   2=1   3=2   (row 0 is the BOTTOM row on the map, row 2 is the TOP)

  Grid layout (visual, top=row2, bottom=row0):
    [A3][B3][C3]    row 2 (top)
    [A2][B2][C2]    row 1 (middle)
    [A1][B1][C1]    row 0 (bottom)

  overworldGrid[col][row]:
    overworldGrid[0][0] = A1 (Market — starting room)
    overworldGrid[1][0] = B1 (Inn)
    overworldGrid[2][0] = C1 (Bridge)
    overworldGrid[0][1] = A2 (Push Block puzzle)
    overworldGrid[1][1] = B2 (Ore Location)
    overworldGrid[2][1] = C2 (Forest)
    overworldGrid[0][2] = A3 (Timed Gauntlet puzzle)
    overworldGrid[1][2] = B3 (Riddle puzzle)
    overworldGrid[2][2] = C3 (Dungeon Entrance area)

  dungeonRooms[0] = D1 (Combat room — RoomLock)
  dungeonRooms[1] = D2 (Push Block puzzle + SaveCrystal)
  dungeonRooms[2] = D3 (Boss fight)

- ROOM CONNECTIONS (from design doc — these are the ONLY valid exits)
  A1 ↔ B1  (A1 EAST / B1 WEST)
  A1 ↔ A2  (A1 NORTH / A2 SOUTH)
  B1 ↔ B2  (B1 NORTH / B2 SOUTH)
  B1 ↔ C1  (B1 EAST / C1 WEST)
  A2 ↔ A3  (A2 NORTH / A3 SOUTH)
  A2 ↔ B2  (A2 EAST / B2 WEST)
  A3 ↔ B3  (A3 EAST / B3 WEST)
  B2 ↔ B3  (B2 NORTH / B3 SOUTH)
  C1 → C2  (C1 NORTH — BLOCKED until DrawbridgeLever is used)
  C2 ↔ C3  (C2 NORTH / C3 SOUTH)
  C3 → D1  (dungeon entrance — triggered by standing on the red GRect marker in C3)
  D1 → D2  (D1 NORTH — locked until all enemies dead — RoomLock)
  D2 → D3  (D2 NORTH)

  NO CONNECTIONS:
  B2 ↛ C2  (Forest only reachable via C1 bridge)
  B3 ↛ C3  (Dungeon only reachable via C2)

- TRANSITION FLOW (directional exits — overworld and dungeon)
  1. Room.update() detects player walks off an exit edge, fires exitCallback(Direction).
  2. WorldMap.triggerTransition(Direction d) is called.
  3. WorldMap finds the neighboring room in that direction.
  4. Creates a RoomTransition, sets GamePlayState = TRANSITIONING.
  5. RoomTransition.start() begins the sliding pan.
  6. When RoomTransition.isAnimationComplete(): WorldMap.finishTransition() swaps activeRoom,
     syncs player coordinates to sprite position, restores GamePlayState = PLAYING.

- DUNGEON ENTRANCE (C3 → D1, special non-directional trigger)
  The dungeon entrance in C3 is NOT a normal exit — it is a red GRect marker on the floor.
  When the player's center overlaps the marker, enterDungeon() is called directly (no pan).
  // TECH DEMO: The red GRect is a placeholder. Replace with a real door WorldObject later.
  // RIG POINT: Replace enterDungeon() trigger with a WorldObject.onContact() callback
  //            once the dungeon door is properly designed and added to C3's content.

- PLAYER REFERENCE DURING TRANSITIONS
  WorldMap stores a reference to the Player each tick (lastTickPlayer) so that
  triggerTransition() — which is called via Room's exit callback with no player argument —
  can still pass the player to RoomTransition.start() and to finishTransition().
  This reference is only valid during the update() call; it is not a permanent ownership.

- WHAT WORLDMAP DOES NOT DO
- Does not own the Player — passed in each tick.
- Does not handle HUD, pause, or dialogue — those are separate layers.
- Does not write SaveData directly — SaveManager reads activeRoom.getRoomId() when saving.
*/

import acm.graphics.GCanvas;
import acm.graphics.GImage;
import acm.graphics.GRect;
import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The entire game world: 9 overworld rooms (3×3 grid) + 3 dungeon rooms.
 * Creates all rooms at startup as navigable dummy rooms for layout testing.
 * Manages room transitions and the dungeon entrance trigger.
 * See PLAN OF ACTION above for full implementation details.
 */
public class WorldMap {

    // =========================================================
    // CONSTANTS — room grid dimensions
    // =========================================================

    /** Number of overworld columns (A, B, C). */
    public static final int COLS = 3;

    /** Number of overworld rows (1, 2, 3 from bottom). */
    public static final int ROWS = 3;

    /** Number of linear dungeon rooms. */
    public static final int DUNGEON_ROOMS = 3;

    // =========================================================
    // CONSTANTS — room pixel dimensions (for transition math)
    // =========================================================

    /*
     * =====================
     * Room pixel dimensions — must match TileMap constants.
     * If tile size or room dimensions change, update TileMap first.
     * =====================
     */

    /** Full room width in pixels (26 columns × 48px). Used to sync player coords after transition. */
    private static final double ROOM_WIDTH_PX  = 26 * 48; // = 1248

    /** Full room height in pixels (15 rows × 48px). Used to sync player coords after transition. */
    private static final double ROOM_HEIGHT_PX = 15 * 48; // = 720

    /*
     * =====================
     * End of room pixel dimension constants.
     * =====================
     */

    // =========================================================
    // CONSTANTS — dungeon entrance trigger (C3 red GRect marker)
    // =========================================================

    /*
     * =====================
     * Dungeon entrance trigger bounds — adjust position/size here if the marker needs to move.
     * The marker sits on the C3 castle doorway.
     * // TECH DEMO: remove these constants when the real door WorldObject replaces the marker.
     * =====================
     */

    /** Left edge of the dungeon entrance trigger zone, in screen pixels. */
    private static final double DUNGEON_ENTRANCE_X = TileMap.MAP_OFFSET_X + 20 * 48; // col 20 = 976

    /** Top edge of the dungeon entrance trigger zone, in screen pixels. */
    private static final double DUNGEON_ENTRANCE_Y = 3 * 48; // row 3 = 144

    /** Width of the trigger zone in pixels (2 tiles). */
    private static final double DUNGEON_ENTRANCE_W = 2 * 48; // = 96

    /** Height of the trigger zone in pixels (2 tiles). */
    private static final double DUNGEON_ENTRANCE_H = 2 * 48; // = 96

    /** X position where the player spawns when entering D1 (col 2). */
    private static final double DUNGEON_SPAWN_X    = TileMap.MAP_OFFSET_X + 2 * 48;

    /** Y position where the player spawns when entering D1 (row 7). */
    private static final double DUNGEON_SPAWN_Y    = 7 * 48;

    /*
     * =====================
     * End of dungeon entrance constants.
     * =====================
     */

    // =========================================================
    // FIELDS
    // =========================================================

    /**
     * The 3×3 overworld grid.
     * Access: overworldGrid[col][row] where col=0→A, col=1→B, col=2→C;
     *         row=0→row1 (bottom/south), row=2→row3 (top/north).
     */
    private final Room[][] overworldGrid = new Room[COLS][ROWS];

    /**
     * The 3 linear dungeon rooms.
     * dungeonRooms[0]=D1 (combat), dungeonRooms[1]=D2 (puzzle+save), dungeonRooms[2]=D3 (boss).
     */
    private final Room[] dungeonRooms = new Room[DUNGEON_ROOMS];

    /** The room the player is currently in. */
    private Room activeRoom;

    /** True when the player is inside the dungeon; false for the overworld. */
    private boolean inDungeon;

    /** Active sliding-pan animation. Null when no transition is in progress. */
    private RoomTransition activeTransition;

    /** The canvas — needed to add/remove room graphics during transitions. */
    private GCanvas canvas;

    /** Shared dialogue overlay used by starter-room signs and NPCs. */
    private final Dialogue dialogue;
    /** Shared shop overlay used by merchant NPCs. */
    private final ShopMenu shopMenu;

    /**
     * Holds the Player reference for the current update tick.
     * Set at the start of update() so triggerTransition() (called via Room's exit callback,
     * which has no Player parameter) can still pass the player to RoomTransition.start().
     * This is NOT permanent ownership — it is only valid during one update() call.
     */
    private Player lastTickPlayer;

    /** Persistent one-time object / pickup IDs already collected in this save file. */
    private final Set<String> collectedItemIds = new LinkedHashSet<>();

    /** Persistent story / world progression flags unrelated to the player snapshot. */
    private final Set<String> storyFlags = new LinkedHashSet<>();

    /** Persistent one-time interactables that need save-state reapplication. */
    private WorldProp pickaxeProp;
    private WorldProp minersHatProp;
    private OreNode oreNode;
    private DrawbridgeLever drawbridgeLever;
    private final List<HeroThicket> heroThickets = new ArrayList<>();
    private Chest courageChest;
    private Chest strengthChest;
    private WisdomTrialChest wisdomChest;

    // =========================================================
    // DUNGEON ENTRANCE MARKER (tech-demo placeholder)
    // =========================================================

    /**
     * Red rectangle placed near the north wall of C3 as a visible stand-in for the dungeon door.
     * Added to canvas when C3 becomes active; removed when the player enters the dungeon or leaves C3.
     *
     * // TECH DEMO: This GRect is a placeholder marker. Search "TECH DEMO" to find it.
     * // RIG POINT: Replace with a real WorldObject door in C3's buildC3() method once designed.
     *              At that point, remove dungeonEntranceMarker, DUNGEON_ENTRANCE_* constants,
     *              and the marker management code in finishTransition() and enterDungeon().
     */
    private final GRect dungeonEntranceMarker;

    /** Convenience reference to C3 — used to check when to show/hide the dungeon marker. */
    private Room roomC3;

    // =========================================================
    // DUNGEON EXIT MARKER (tech-demo placeholder)
    // =========================================================

    /*
     * =====================
     * Dungeon exit trigger bounds — placed near the west wall of D1 so the player
     * can step left to leave the dungeon and return to C3.
     * Adjust position/size here if the marker needs to move.
     * // TECH DEMO: remove these constants when a real dungeon-exit door WorldObject is built.
     * =====================
     */

    /** Left edge of the dungeon exit trigger zone (col 0). */
    private static final double DUNGEON_EXIT_X = TileMap.MAP_OFFSET_X + 0 * 48; // col 0 = 16

    /** Top edge of the dungeon exit trigger zone (row 7). */
    private static final double DUNGEON_EXIT_Y = 7 * 48; // row 7 = 336

    /** Width of the exit trigger zone (1 tile). */
    private static final double DUNGEON_EXIT_W = 1 * 48; // = 48

    /** Height of the exit trigger zone (1 tile). */
    private static final double DUNGEON_EXIT_H = 1 * 48; // = 48

    /**
     * X position where the player spawns on return to C3.
     * Centered between cols 20 and 21 so the landing point lines up with the teleport pad.
     */
    private static final double OVERWORLD_RETURN_X = DUNGEON_ENTRANCE_X + DUNGEON_ENTRANCE_W / 2.0;

    /**
     * Y position where the player spawns on return to C3.
     * Offset by half a tile so the player's center is safely below the entrance trigger.
     */
    private static final double OVERWORLD_RETURN_Y = DUNGEON_ENTRANCE_Y + DUNGEON_ENTRANCE_H + 24;

    /*
     * =====================
     * End of dungeon exit constants.
     * =====================
     */

    /** Starter save crystal placed in B1 across tiles 19,6 | 20,6 | 19,7 | 20,7. */
    private static final double START_SAVE_POINT_X = TileMap.MAP_OFFSET_X + 20 * 48;
    private static final double START_SAVE_POINT_Y = 7 * 48;

    /** Starter sign + NPC positions in A1 so room dialogue can be tested immediately from spawn. */
    private static final double START_SIGN_X = TileMap.MAP_OFFSET_X + 15 * 48;
    private static final double START_SIGN_Y = 7 * 48;
    private static final double START_NPC_X  = TileMap.MAP_OFFSET_X + 7 * 48;
    private static final double START_NPC_Y  = 10 * 48;
    private static final double START_BREAD_MERCHANT_X = TileMap.MAP_OFFSET_X + 11 * 48;
    private static final double START_BREAD_MERCHANT_Y = 4 * 48;

    /** Blacksmith NPC position in B1 (Inn). */
    private static final double BLACKSMITH_X = TileMap.MAP_OFFSET_X + 6 * 48;
    private static final double BLACKSMITH_Y = 5 * 48;

    /** Little girl NPC position in B1, just outside the inn save crystal (grid col 18, row 8). */
    private static final double LITTLE_GIRL_X = TileMap.MAP_OFFSET_X + 18 * 48;
    private static final double LITTLE_GIRL_Y = 8 * 48;

    /** Calumund Vaen Solmare (wizard-goat NPC) position in A1. */
    private static final double CALUMUND_X = TileMap.MAP_OFFSET_X + 18 * 48;
    private static final double CALUMUND_Y = 7 * 48;

    private static final String CALUMUND_2ND_TALK_FLAG  = "calumund_second_talk_seen";
    private static final String CALUMUND_REPEAT_FLAG    = "calumund_repeating_talk";

    private static final String[] CALUMUND_1ST_TALK_LINES = {
        "Ah, greetings, brave warrior. I am Calumund Vaen Solmare, once a mighty wizard of considerable renown.",
        "That scoundrel Bastian Myrwick, my traitorous apprentice, stole my polymorph wand and transformed me into... this. A goat. The indignity!",
        "But worse, he's been using my wand to corrupt the creatures of the forest, creating monsters that now plague this town.",
        "I need you to venture forth and stop him. You are my only hope."
    };

    private static final String[] CALUMUND_2ND_TALK_LINES = {
        "You may wonder why I'm entrusting this task to you specifically. Well, the answer is simple:",
        "You're the only person in this entire town who knows how to swing a sword without hurling yourself into a tree.",
        "Everyone else fled or can't fight. So by process of elimination, you're my hero."
    };

    private static final String[] CALUMUND_3RD_TALK_LINES = {
        "Oh! Before you go, I nearly forgot something crucial.",
        "There are legends of powerful relics hidden throughout these lands. Artifacts meant for heroes such as yourself.",
        "Here, take this Mark of the Hero. It will grant you access to trials where you can claim these relics.",
        "You'll need all the help you can get."
    };

    private static final String[] CALUMUND_4TH_TALK_LINES = {
        "Off you go. Defeat Bastian, restore peace to the town. Do hero things."
    };

    /** Relic chest positions for trial rooms (center of each room, approximately). */
    private static final double TRIAL_CHEST_A3_X = TileMap.MAP_OFFSET_X + 7 * 48;
    private static final double TRIAL_CHEST_A3_Y = 1 * 48;
    private static final double TRIAL_CHEST_A2_X = TileMap.MAP_OFFSET_X + 5 * 48;
    private static final double TRIAL_CHEST_A2_Y = 1 * 48;
    private static final double TRIAL_CHEST_B3_X = TileMap.MAP_OFFSET_X + 17 * 48;
    private static final double TRIAL_CHEST_B3_Y = 1 * 48;

    /** B2 ore-vein footprint (col,row) tiles: 9,4 10,4 11,4 9,5 10,5 11,5 9,6 10,6 11,6 */
    private static final int[][] ORE_VEIN_TILES_B2 = {
        {9, 4}, {10, 4}, {11, 4},
        {9, 5}, {10, 5}, {11, 5},
        {9, 6}, {10, 6}, {11, 6}
    };

    /** DrawbridgeLever position in C1 (Bridge room). */
    private static final double DRAWBRIDGE_LEVER_X = TileMap.MAP_OFFSET_X + 11 * 48;
    private static final double DRAWBRIDGE_LEVER_Y = 6 * 48;

    private static final String[] START_SIGN_LINES = {
        "Spawn Island Test Sign",
        "Press E near a sign or NPC to interact.",
        "Press J or Space to advance dialogue.",
        "This room is now wired through the live world map."
    };

    private static final String START_NPC_FIRST_TALK_FLAG = "npc_drunk_intro_seen";
    private static final String START_NPC_REWARD_FLAG = "npc_drunk_pickaxe_given";
    private static final String MINERS_HAT_B2_FLAG   = "miners_hat_b2_collected";
    private static final String PICKAXE_TAKEN_FLAG   = "pickaxe_prop_taken";

    /** Position of the decorative pickaxe prop placed next to the drunk NPC in A1. */
    private static final double PICKAXE_PROP_X = TileMap.MAP_OFFSET_X + 7 * 48;
    private static final double PICKAXE_PROP_Y = 11 * 48;

    /** Position of the miner's hat ground pickup in B2 (just left of the ore node). */
    private static final double MINERS_HAT_X = TileMap.MAP_OFFSET_X + 12 * 48;
    private static final double MINERS_HAT_Y = 9 * 48;

    private static final String[] START_NPC_LINES = {
        "I barely made it out alive when those monsters attacked. Lost my hat in the chaos, dropped it right by the ore deposits when I was running.",
        "A miner never swings a pickaxe without proper head protection, you understand? That hat's essential.",
        "If I ever find it... well, at least this drink will help comfort me."
    };

    private static final String[] START_NPC_REWARD_LINES = {
        "Well, well, well... *hic* ...look who decided to show up for the shift! Put on your hat, did ya? Good, good.",
        "Can't be swingin' a pickaxe without... without the proper protection, y'know? Here, take this. *hic*",
        "You're gonna need it more than I do anyway. Go on, get outta here before the boss gets mad."
    };

    private static final String[] START_NPC_POST_REWARD_LINES = {
        "Where'd that other miner go? *hic* Was just here a second ago... or was that yesterday?",
        "Hard to keep track these days. Anyway, good riddance. More drink for me. *hic*"
    };

    private static final String A2_HERO_THICKET_FLAG = "hero_thicket_a2_cleared";
    private static final String A3_HERO_THICKET_FLAG = "hero_thicket_a3_cleared";
    private static final String B3_HERO_THICKET_FLAG = "hero_thicket_b3_cleared";
    private static final String A3_TRIAL_SOLVED_FLAG = "trial_of_courage_cleared";
    private static final String DRAWBRIDGE_REPAIRED_FLAG = "drawbridge_c1_repaired";

    private static final double A3_TRIAL_TRIGGER_RADIUS_PX = 96.0;
    private static final double A3_TRIAL_DURATION_SECONDS = 30.0;
    private static final double A3_TRIAL_INITIAL_SPAWN_INTERVAL = 0.85;
    private static final double A3_TRIAL_MIN_SPAWN_INTERVAL = 0.22;

    private static final int[][] A2_HERO_THICKET_TILES = {
        {11, 9}, {12, 9}, {13, 9},
        {11, 10}, {12, 10}, {13, 10},
        {11, 11}, {12, 11}, {13, 11}
    };

    private static final int[][] A3_HERO_THICKET_TILES = {
        {10, 9}, {11, 9}, {12, 9},
        {10, 10}, {11, 10}, {12, 10},
        {10, 11}, {11, 11}, {12, 11}
    };

    /** Temporary retreat blocker used only while the A3 courage storm is active. */
    private static final int[][] A3_TRIAL_ACTIVE_BLOCK_TILES = {
        {12, 9}, {12, 10}, {12, 11}
    };

    /**
     * Fixed Courage-trial projectile emitters in A3, expressed as tile col,row pairs.
     * Built from design notes:
     * 1,1 -> 16,1
     * 1,1 -> 1,13
     * 1,13 -> 9,13
     * 9,7 -> 9,8
     * 10,5 -> 10,6
     * 11,5
     * 12,4
     * 13,4
     * 14,3
     * 15,3
     * 16,3
     * 17,1
     * 17,2
     */
    private static final int[][] A3_TRIAL_PROJECTILE_SPAWN_TILES = {
        {1, 1}, {2, 1}, {3, 1}, {4, 1}, {5, 1}, {6, 1}, {7, 1}, {8, 1},
        {9, 1}, {10, 1}, {11, 1}, {12, 1}, {13, 1}, {14, 1}, {15, 1}, {16, 1},
        {1, 2}, {1, 3}, {1, 4}, {1, 5}, {1, 6}, {1, 7}, {1, 8}, {1, 9},
        {1, 10}, {1, 11}, {1, 12}, {1, 13},
        {2, 13}, {3, 13}, {4, 13}, {5, 13}, {6, 13}, {7, 13}, {8, 13}, {9, 13},
        {9, 7}, {9, 8},
        {10, 5}, {10, 6},
        {11, 5},
        {12, 4},
        {13, 4},
        {14, 3},
        {15, 3},
        {16, 3},
        {17, 1},
        {17, 2}
    };

    private static final int[][] B3_HERO_THICKET_TILES = {
        {13, 5}, {14, 5},
        {13, 6}, {14, 6},
        {13, 7}, {14, 7}
    };

    /** Reserved story-flag prefix used to encode exact exit states in the save file. */
    private static final String EXIT_FLAG_PREFIX = "wm_exit:";

    /** Fired once when the Boss in D3 is defeated. Wired by GameplayPane for the ending cutscene. */
    private Runnable bossDefeatedCallback;

    /**
     * Red rectangle placed near the west wall of D1 as a visible stand-in for the dungeon exit door.
     * Added to canvas when the player enters the dungeon; removed when they step on it to leave.
     *
     * // TECH DEMO: This GRect is a placeholder. Replace with a real WorldObject exit door in D1.
     * // RIG POINT: Remove dungeonExitMarker and DUNGEON_EXIT_* constants once a real door is built.
     */
    private final GRect dungeonExitMarker;
    private GImage d1BlockerImage;
    private GImage d2BlockerImage;
    private GImage d3BlockerImage;
    private boolean d1Cleared = false;
    private boolean d2Solved  = false;
    private boolean a2Solved  = false;
    private boolean a3Solved  = false;
    private boolean a3TrialWarned = false;
    private boolean a3WaitingForRetreat = false;
    private boolean a3TrialActive = false;
    private double a3TrialTimeRemaining = 0.0;
    private double a3TrialSpawnTimer = 0.0;
    private int a3TrialLastSpawnIndex = -1;
    private boolean a3RetreatWarningLatched = false;
    private final Random a3TrialRandom = new Random();

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    /**
     * Creates the world map: initialises all 12 rooms as dummy rooms, wires exit connections,
     * and sets the starting room to A1 (Market).
     *
     * @param canvas the game canvas (needed for room add/remove during transitions)
     */
    public WorldMap(GCanvas canvas, Dialogue dialogue, ShopMenu shopMenu) {
        this.canvas = canvas;
        this.dialogue = dialogue;
        this.shopMenu = shopMenu;

        // --- build the dungeon entrance marker (C3) ---
        // TECH DEMO: red GRect standing in for the dungeon door in C3.
        dungeonEntranceMarker = new GRect(
            DUNGEON_ENTRANCE_X, DUNGEON_ENTRANCE_Y,
            DUNGEON_ENTRANCE_W, DUNGEON_ENTRANCE_H);
        dungeonEntranceMarker.setFilled(true);
        dungeonEntranceMarker.setFillColor(Color.RED);
        dungeonEntranceMarker.setColor(new Color(180, 0, 0)); // darker red outline

        // --- build the dungeon exit marker (D1 west wall) ---
        // TECH DEMO: red GRect standing in for the dungeon exit door in D1.
        // Stepping on it returns the player to C3 near the entrance marker.
        dungeonExitMarker = new GRect(
            DUNGEON_EXIT_X, DUNGEON_EXIT_Y,
            DUNGEON_EXIT_W, DUNGEON_EXIT_H);
        dungeonExitMarker.setFilled(true);
        dungeonExitMarker.setFillColor(Color.RED);
        dungeonExitMarker.setColor(new Color(180, 0, 0)); // darker red outline

        initRooms();
        wireExits();

        // Starting room: A1 (Market)
        activeRoom = overworldGrid[0][0];
        inDungeon  = false;
    }

    // =========================================================
    // ROOM INITIALISATION
    // =========================================================

    /**
     * Creates all 12 rooms, calls buildDummy() on each, and wires each room's
     * exit callback back to this WorldMap so transitions fire automatically.
     *
     * // RIG POINT: Replace each buildDummy() call here with the room's real buildXxx() method
     *              once that room's content is designed and implemented.
     */
    private void initRooms() {
        // --- overworld rooms ---
        overworldGrid[0][0] = new Room("A1"); overworldGrid[0][0].buildA1(); // Market (start)
        overworldGrid[1][0] = new Room("B1"); overworldGrid[1][0].buildB1(); // Inn
        overworldGrid[2][0] = new Room("C1"); overworldGrid[2][0].buildC1(); // Bridge
        overworldGrid[0][1] = new Room("A2"); overworldGrid[0][1].buildA2(); // Push Block puzzle
        overworldGrid[1][1] = new Room("B2"); overworldGrid[1][1].buildB2(); // Ore Location
        overworldGrid[2][1] = new Room("C2"); overworldGrid[2][1].buildC2(); // Forest
        overworldGrid[0][2] = new Room("A3"); overworldGrid[0][2].buildA3(); // Timed Gauntlet
        overworldGrid[1][2] = new Room("B3"); overworldGrid[1][2].buildB3(); // Riddle puzzle
        overworldGrid[2][2] = new Room("C3"); overworldGrid[2][2].buildC3(); // Dungeon Entrance

        // --- dungeon rooms ---
        dungeonRooms[0] = new Room("D1"); dungeonRooms[0].buildD1(); // Combat + RoomLock
        dungeonRooms[1] = new Room("D2"); dungeonRooms[1].buildD2(); // Puzzle + SaveCrystal
        dungeonRooms[2] = new Room("D3"); dungeonRooms[2].buildD3(); // Boss fight

        // --- convenience reference to C3 for dungeon entrance checks ---
        roomC3 = overworldGrid[2][2];

        reserveTeleportTriggerTilesForPlayers();

        populateOverworldEnemyRooms();
        populateD1();
        dungeonRooms[0].setRoomLock(
            new RoomLock(() -> {
                d1Cleared = true;
                openExit("D1", Direction.RIGHT);
                if (canvas != null && d1BlockerImage != null) canvas.remove(d1BlockerImage);
            }),
            Direction.RIGHT
        );

        populateD2();
        populateD3();
        installD2SavePoint();
        installD2Puzzle();

        installStarterSavePoint();
        installSpawnIslandDialogueTestObjects();
        installStarterBreadMerchant();
        installPickaxeProp();
        installMinersHat();
        installBlacksmith();
        installLittleGirlNpc();
        installCalumundNpc();
        installOreNode();
        installHeroThickets();
        installDrawbridgeLever();
        installTrialChests();
        installA3Trial();
        installA2Puzzle();
        installSparseOverworldGrass();
        installA2DebugBlockers();
        syncPersistentWorldObjects();

        // --- wire exit callbacks: each room calls triggerTransition() when the player exits ---
        for (int col = 0; col < COLS; col++) {
            for (int row = 0; row < ROWS; row++) {
                final Room room = overworldGrid[col][row];
                room.setExitCallback(direction -> triggerTransition(direction));
            }
        }
        for (Room dungeonRoom : dungeonRooms) {
            dungeonRoom.setExitCallback(direction -> triggerTransition(direction));
        }
    }

    /**
     * Keeps the dungeon entrance/exit trigger strips walkable for the player while blocking
     * enemy movement and patrol generation across those tile bands.
     */
    private void reserveTeleportTriggerTilesForPlayers() {
        int tileSize = roomC3.getTileMap().getTileSize();

        roomC3.getTileMap().addEnemyBlockedZoneByTiles(
            (int) ((DUNGEON_ENTRANCE_X - TileMap.MAP_OFFSET_X) / tileSize),
            (int) (DUNGEON_ENTRANCE_Y / tileSize),
            (int) (DUNGEON_ENTRANCE_W / tileSize),
            (int) (DUNGEON_ENTRANCE_H / tileSize)
        );

        dungeonRooms[0].getTileMap().addEnemyBlockedZoneByTiles(
            (int) ((DUNGEON_EXIT_X - TileMap.MAP_OFFSET_X) / tileSize),
            (int) (DUNGEON_EXIT_Y / tileSize),
            (int) (DUNGEON_EXIT_W / tileSize),
            (int) (DUNGEON_EXIT_H / tileSize)
        );
    }

    /** Places an always-available save crystal in B1 for early save/load testing. */
    private void installStarterSavePoint() {
        Room startRoom = overworldGrid[1][0];
        startRoom.setSavePoint(new SavePoint(
            START_SAVE_POINT_X,
            START_SAVE_POINT_Y,
            startRoom.getRoomId(),
            SavePoint.SavePointType.SAVE_CRYSTAL,
            START_SAVE_POINT_X,
            START_SAVE_POINT_Y
        ));
    }

    /** Seeds a simple sign + NPC into A1 so room dialogue can be exercised from spawn. */
    private void installSpawnIslandDialogueTestObjects() {
        Room startRoom = overworldGrid[0][0];
        if (startRoom == null) {
            return;
        }

        //startRoom.addObject(new Sign(START_SIGN_X, START_SIGN_Y, START_SIGN_LINES, dialogue));
        WorldNpc drunkNpc = new WorldNpc(
            START_NPC_X,
            START_NPC_Y,
            "Waba",
            START_NPC_LINES,
            dialogue
        );
        drunkNpc.setStoryFlagHooks(this::hasStoryFlag, this::addStoryFlag);
        drunkNpc.configureTwoStepReward(
            START_NPC_FIRST_TALK_FLAG,
            START_NPC_REWARD_FLAG,
            START_NPC_REWARD_LINES,
            START_NPC_POST_REWARD_LINES,
            p -> p.findInventoryItem(OreNode.PICKAXE_ID) != null,
            p -> {
                Item hat = p.findInventoryItem(MinersHat.ITEM_ID);
                if (hat != null) p.consumeInventoryItem(hat);
                p.collectItem(new Pickaxe());
                addStoryFlag(PICKAXE_TAKEN_FLAG);
                if (pickaxeProp != null) pickaxeProp.consume();
            }
        );
        drunkNpc.setRewardUnlockCondition(
            p -> p.findInventoryItem(MinersHat.ITEM_ID) != null
        );
        drunkNpc.setVoiceSound(GameSFX.SFX.MALE_SPEAK);
        startRoom.addObject(drunkNpc);
    }

    /** Adds an interactable bread merchant in A1 that opens the shop overlay. */
    private void installStarterBreadMerchant() {
        Room startRoom = overworldGrid[0][0];
        if (startRoom == null || shopMenu == null) {
            return;
        }
        startRoom.addObject(new BreadMerchant(
            START_BREAD_MERCHANT_X,
            START_BREAD_MERCHANT_Y,
            "Ramona",
            shopMenu,
            dialogue
        ));
    }

    /** Places the pickaxe prop in A1 next to the drunk. Consumed when the drunk gives the pickaxe. */
    private void installPickaxeProp() {
        Room a1 = overworldGrid[0][0];
        if (a1 == null) return;
        pickaxeProp = new WorldProp(
            PICKAXE_PROP_X, PICKAXE_PROP_Y,
            "assets/visuals/png's/pickaxe.png",
            "e to interact",
            p -> {
                if (dialogue != null && !dialogue.isOpen()) {
                    GamePlayState.setCurrent(GamePlayState.DIALOGUE);
                    dialogue.open(
                        new String[]{"The drunk has a firm grip on this. Maybe talk to him?"},
                        "Pickaxe",
                        false,
                        () -> GamePlayState.setCurrent(GamePlayState.PLAYING)
                    );
                }
            }
        );
        a1.addObject(pickaxeProp);
    }

    /** Places the miner's hat as a ground pickup in B2 near the ore node. */
    private void installMinersHat() {
        Room b2 = overworldGrid[1][1];
        if (b2 == null) return;
        minersHatProp = new WorldProp(
            MINERS_HAT_X, MINERS_HAT_Y,
            "assets/visuals/png's/miners_hat.png",
            "e to pick up",
            p -> {
                p.collectItem(new MinersHat());
                addStoryFlag(MINERS_HAT_B2_FLAG);
                if (dialogue != null && !dialogue.isOpen()) {
                    GamePlayState.setCurrent(GamePlayState.DIALOGUE);
                    dialogue.open(
                        new String[]{
                            "You picked up the Miner's Hat!",
                            "Someone in town might want this back..."
                        },
                        "Miner's Hat",
                        false,
                        () -> GamePlayState.setCurrent(GamePlayState.PLAYING)
                    );
                }
            },
            1.2
        );
        minersHatProp.setConsumeOnInteract(true);
        b2.addObject(minersHatProp);
    }

    /** Places the Blacksmith NPC in B1 who crafts FixedLever from Ore when lever flag is known. */
    private void installBlacksmith() {
        Room b1 = overworldGrid[1][0];
        if (b1 == null) return;
        Blacksmith blacksmith = new Blacksmith(BLACKSMITH_X, BLACKSMITH_Y, dialogue);
        blacksmith.setStoryFlagHooks(this::hasStoryFlag, this::addStoryFlag);
        b1.addObject(blacksmith);
    }

    private static final String[] LITTLE_GIRL_LINES = {
        "My mom told me that when you can do really cool things, you have to be really careful about how you use them.",
        "She said it's not about how much power you have, it's about being responsible with that power."
    };

    private static final String[] LITTLE_GIRL_HINT2_LINES = {
        "My mom says some doors only open when you speak with kindness in your heart.",
        "She says real friends can unlock things that nobody else can."
    };

    private static final String[] LITTLE_GIRL_HINT3_LINES = {
        "One time I met this guy with really crazy yellow hair. He told me that real heroes don't give up.",
        "They go beyond even when things seem impossible. He kept shouting, \"Plus Ultra!\" It sounded important."
    };

    private static final String LITTLE_GIRL_HINT1_FLAG = "little_girl_hint1_seen";
    private static final String LITTLE_GIRL_HINT2_FLAG = "little_girl_hint2_seen";

    /** Places a little girl NPC in B1 just outside the inn save crystal. */
    private void installLittleGirlNpc() {
        Room b1 = overworldGrid[1][0];
        if (b1 == null) return;
        WorldNpc littleGirl = new WorldNpc(
            LITTLE_GIRL_X,
            LITTLE_GIRL_Y,
            "Little Girl",
            LITTLE_GIRL_LINES,
            dialogue,
            "assets/visuals/png's/little girl (puzzle helper).png",
            32.0
        );
        littleGirl.setStoryFlagHooks(this::hasStoryFlag, this::addStoryFlag);
        littleGirl.configureTwoStepReward(
            LITTLE_GIRL_HINT1_FLAG,
            LITTLE_GIRL_HINT2_FLAG,
            LITTLE_GIRL_HINT2_LINES,
            LITTLE_GIRL_HINT3_LINES,
            null,
            null
        );
        littleGirl.setVoiceSound(GameSFX.SFX.FEMALE_SPEAK);
        b1.addObject(littleGirl);
    }

    /** Exposes the player reference stored each tick for use by world objects that need it. */
    public Player getLastTickPlayer() { return lastTickPlayer; }

    /** Places Calumund Vaen Solmare as a directional animated NPC in A1. */
    private void installCalumundNpc() {
        Room a1 = overworldGrid[0][0];
        if (a1 == null) return;
        CalumundNpc calumund = new CalumundNpc(
            CALUMUND_X,
            CALUMUND_Y,
            CALUMUND_1ST_TALK_LINES,
            dialogue,
            this::getLastTickPlayer
        );
        calumund.setStoryFlagHooks(this::hasStoryFlag, this::addStoryFlag);
        // Combine "why you" (2nd) + "here's the mark" (3rd) into one reward conversation.
        // rewardUnlockCondition = null so it triggers naturally after introCompleteFlag is set.
        String[] combinedMarkLines = new String[CALUMUND_2ND_TALK_LINES.length + CALUMUND_3RD_TALK_LINES.length];
        System.arraycopy(CALUMUND_2ND_TALK_LINES, 0, combinedMarkLines, 0, CALUMUND_2ND_TALK_LINES.length);
        System.arraycopy(CALUMUND_3RD_TALK_LINES, 0, combinedMarkLines, CALUMUND_2ND_TALK_LINES.length, CALUMUND_3RD_TALK_LINES.length);
        calumund.configureTwoStepReward(
            CALUMUND_2ND_TALK_FLAG,
            CALUMUND_REPEAT_FLAG,
            combinedMarkLines,
            CALUMUND_4TH_TALK_LINES,
            p -> p.hasMarkOfHero(),
            p -> { p.collectItem(new MarkOfHeroItem()); p.setHasMarkOfHero(true); }
        );
        a1.addObject(calumund);
    }

    /** Places relic chests in the three trial rooms (A3, A2, B3). */
    private void installTrialChests() {
        Room a3 = overworldGrid[0][2];
        if (a3 != null) {
            courageChest = new Chest(
                TRIAL_CHEST_A3_X, TRIAL_CHEST_A3_Y,
                "chest_a3_courage", Chest.RELIC_INTANGIBLE, true
            );
            courageChest.setDialogue(dialogue);
            courageChest.setCollectedItemRecorder(this::markCollectedItem);
            a3.addObject(courageChest);
        }

        Room a2 = overworldGrid[0][1];
        if (a2 != null) {
            strengthChest = new Chest(
                TRIAL_CHEST_A2_X, TRIAL_CHEST_A2_Y,
                "chest_a2_strength", Chest.RELIC_HALF_DAMAGE, true
            );
            strengthChest.setDialogue(dialogue);
            strengthChest.setCollectedItemRecorder(this::markCollectedItem);
            a2.addObject(strengthChest);
        }

        Room b3 = overworldGrid[1][2];
        if (b3 != null) {
            wisdomChest = new WisdomTrialChest(
                TRIAL_CHEST_B3_X, TRIAL_CHEST_B3_Y,
                "chest_b3_wisdom"
            );
            wisdomChest.setDialogue(dialogue);
            wisdomChest.setCollectedItemRecorder(this::markCollectedItem);
            b3.addObject(wisdomChest);
        }
    }

    /** Locks the A3 courage chest until the player survives the dodge gauntlet. */
    private void installA3Trial() {
        if (courageChest != null) {
            courageChest.setLocked(true);
            courageChest.setLockedMessage(
                "A storm of magic coils around the relic. Step closer to begin the Trial of Courage."
            );
        }
        resetA3TrialState(false);
    }

    /** Seeds sparse cuttable Grass across overworld rooms (excluding A2). */
    private void installSparseOverworldGrass() {
        // Keep coverage light and deterministic so the world feels greener without clutter.
        addGrassTiles(overworldGrid[2][0], new int[][] { // C1
            {3, 4}, {6, 8}, {9, 10}
        });

        // A2 intentionally excluded per design requirement.

        addGrassTiles(overworldGrid[1][1], new int[][] { // B2
            {2, 6}, {8, 6}, {5, 10}, {9, 3}
        });
        addGrassTiles(overworldGrid[2][1], new int[][] { // C2
            {2, 4}, {6, 5}, {4, 9}, {8, 10}
        });

        addGrassTiles(overworldGrid[0][2], new int[][] { // A3
            {2, 5}, {6, 3}, {8, 9}
        });
        addGrassTiles(overworldGrid[1][2], new int[][] { // B3
            {3, 4}, {5, 8}, {9, 6}
        });
        addGrassTiles(overworldGrid[2][2], new int[][] { // C3
            {2, 10}, {4, 6}, {8, 4}, {9, 9}
        });
    }

    /** Adds grass at tile coordinates for the supplied room. */
    private void addGrassTiles(Room room, int[][] tiles) {
        if (room == null || tiles == null || tiles.length == 0) return;
        int tileSize = room.getTileMap().getTileSize();
        for (int[] tile : tiles) {
            if (tile == null || tile.length < 2) continue;
            double worldX = TileMap.MAP_OFFSET_X + tile[0] * tileSize;
            double worldY = tile[1] * tileSize;
            // Only place grass on walkable floor tiles (skip walls/non-walkable areas).
            double centerX = worldX + tileSize / 2.0;
            double centerY = worldY + tileSize / 2.0;
            if (!room.getTileMap().isPassable(centerX, centerY)) continue;
            room.addObject(new Grass(worldX, worldY, 0.5f, room::addDroppedItem, false));
        }
    }

    /** Places the OreNode in B2 that gives Ore + BrokenLever when mined with the Pickaxe. */
    private void installOreNode() {
        Room b2 = overworldGrid[1][1];
        if (b2 == null) return;
        oreNode = new OreNode(b2.getTileMap(), ORE_VEIN_TILES_B2);
        oreNode.setDialogue(dialogue);
        oreNode.setCollectedItemRecorder(this::markCollectedItem);
        b2.addObject(oreNode);
    }

    /** Adds visible purple debug markers on the A2 tiles that push blocks are not allowed to enter. */
    private void installA2DebugBlockers() {
        Room a2 = overworldGrid[0][1];
        if (a2 == null) return;

        int tileSize = a2.getTileMap().getTileSize();
        Color purpleBarrier = new Color(132, 70, 196, 190);
        Color redBarrier = new Color(205, 62, 62, 210);

        addDebugBarrierRange(a2, tileSize, purpleBarrier, 1, 9, 1, 1);
        addDebugBarrierRange(a2, tileSize, purpleBarrier, 1, 1, 2, 12);
        addDebugBarrierRange(a2, tileSize, purpleBarrier, 2, 9, 12, 12);
        addDebugBarrierRange(a2, tileSize, purpleBarrier, 8, 8, 2, 7);
        addDebugBarrierRange(a2, tileSize, purpleBarrier, 9, 9, 8, 8);
        addDebugBarrierRange(a2, tileSize, purpleBarrier, 10, 10, 9, 11);

        addDebugBarrierRange(a2, tileSize, redBarrier, 5, 6, 1, 1);
    }

    private void addDebugBarrierRange(Room room, int tileSize, Color fillColor,
                                      int startCol, int endCol, int startRow, int endRow) {
        if (room == null) return;

        int minCol = Math.min(startCol, endCol);
        int maxCol = Math.max(startCol, endCol);
        int minRow = Math.min(startRow, endRow);
        int maxRow = Math.max(startRow, endRow);

        for (int col = minCol; col <= maxCol; col++) {
            for (int row = minRow; row <= maxRow; row++) {
                double worldX = TileMap.MAP_OFFSET_X + col * tileSize;
                double worldY = row * tileSize;
                room.addObject(new DebugTileMarker(worldX, worldY, fillColor));
            }
        }
    }

    /** Places the Mark-of-the-Hero tree barriers in A2, A3, and B3. */
    private void installHeroThickets() {
        heroThickets.clear();
        installHeroThicket(
            overworldGrid[0][1], A2_HERO_THICKET_FLAG, A2_HERO_THICKET_TILES,
            new String[]{"Welcome, hero. This is the Trial of Strength. Move the blocks."},
            "assets/visuals/overworld rooms/a2_open.png"
        );
        installHeroThicket(
            overworldGrid[0][2], A3_HERO_THICKET_FLAG, A3_HERO_THICKET_TILES,
            new String[]{"Welcome, hero. This is the Trial of Courage. Survive the onslaught."},
            "assets/visuals/overworld rooms/a3_open.png"
        );
        installHeroThicket(
            overworldGrid[1][2], B3_HERO_THICKET_FLAG, B3_HERO_THICKET_TILES,
            new String[]{"Welcome, hero. This is the Trial of Wisdom. Answer true."},
            "assets/visuals/overworld rooms/b3_open.png"
        );
    }

    private void installHeroThicket(Room room, String storyFlag, int[][] tiles,
                                     String[] unlockDialogue, String... openBackgroundPaths) {
        if (room == null) return;

        HeroThicket thicket = new HeroThicket(
            computeTileWorldX(tiles),
            computeTileWorldY(tiles),
            computeTileWorldWidth(tiles),
            computeTileWorldHeight(tiles),
            "Thicket",
            storyFlag,
            tiles,
            room.getTileMap(),
            room,
            dialogue,
            openBackgroundPaths
        );
        thicket.setStoryFlagHooks(this::hasStoryFlag, this::addStoryFlag);
        thicket.setUnlockDialogue(unlockDialogue);
        room.addObject(thicket);
        heroThickets.add(thicket);
    }

    /** Places the DrawbridgeLever in C1 that lowers the bridge when the player has FixedLever. */
    private void installDrawbridgeLever() {
        Room c1 = overworldGrid[2][0];
        if (c1 == null) return;
        drawbridgeLever = new DrawbridgeLever(
            DRAWBRIDGE_LEVER_X, DRAWBRIDGE_LEVER_Y,
            c1.getTileMap(), this
        );
        drawbridgeLever.setDialogue(dialogue);
        drawbridgeLever.setStoryFlagHooks(this::hasStoryFlag, this::addStoryFlag);
        c1.addObject(drawbridgeLever);
    }

    /** Seeds live overworld enemy encounters into all non-town rooms. */
    private void populateOverworldEnemyRooms() {
        populateA2();
        populateA3();
        populateB1();
        populateB2();
        populateB3();
        populateC1();
        populateC2();
        populateC3();
    }

    /** Push Block puzzle room — light mixed patrol. */
    private void populateA2() {
        Room a2 = overworldGrid[0][1];
        // a2.addRespawningEntity(() -> new LizardEnemy(350, 250, a2.getTileMap()));
        a2.addRespawningEntity(() -> new RangedEnemy(800, 400, a2.getTileMap()));
    }

    /** Timed Gauntlet room — projectile survival trial, so no roaming enemies. */
    private void populateA3() {
    }

    /** Inn (B1) — single lizard. */
    private void populateB1() {
        Room b1 = overworldGrid[1][0];
        b1.addRespawningEntity(() -> new LizardEnemy(640, 360, b1.getTileMap()));
    }

    /** Ore node area (B2) — 4 armored guards protecting the mine. */
    private void populateB2() {
        Room b2 = overworldGrid[1][1];
        b2.addRespawningEntity(() -> new ArmorEnemy(420, 300, b2.getTileMap()));
        b2.addRespawningEntity(() -> new ArmorEnemy(700, 250, b2.getTileMap()));
        b2.addRespawningEntity(() -> new ArmorEnemy(550, 500, b2.getTileMap()));
        b2.addRespawningEntity(() -> new ArmorEnemy(800, 450, b2.getTileMap()));
    }

    /** Riddle puzzle room (B3) — mixed patrol. */
    private void populateB3() {
        Room b3 = overworldGrid[1][2];
        b3.addRespawningEntity(() -> new LizardEnemy(500, 300, b3.getTileMap()));
        b3.addRespawningEntity(() -> new RangedEnemy(300, 330, b3.getTileMap()));
    }

    /** Bridge room (C1) — light mixed patrol. */
    private void populateC1() {
        Room c1 = overworldGrid[2][0];
        c1.addRespawningEntity(() -> new LizardEnemy(400, 300, c1.getTileMap()));
        c1.addRespawningEntity(() -> new RangedEnemy(1000, 200, c1.getTileMap()));
    }

    /** Dense forest (C2) — high danger: 5 enemies with mixed types. */
    private void populateC2() {
        Room c2 = overworldGrid[2][1];
        c2.addRespawningEntity(() -> new ArmorEnemy(360, 252, c2.getTileMap()));
        c2.addRespawningEntity(() -> new RangedEnemy(920, 432, c2.getTileMap()));
        c2.addRespawningEntity(() -> new ArmorEnemy(640, 180, c2.getTileMap()));
        c2.addRespawningEntity(() -> new RangedEnemy(500, 500, c2.getTileMap()));
        c2.addRespawningEntity(() -> new LizardEnemy(800, 280, c2.getTileMap()));
    }

    /** Dungeon entrance area (C3) — mixed patrol. */
    private void populateC3() {
        Room c3 = overworldGrid[2][2];
        c3.addRespawningEntity(() -> new LizardEnemy(500, 280, c3.getTileMap()));
        c3.addRespawningEntity(() -> new RangedEnemy(800, 400, c3.getTileMap()));
    }

    /**
     * Spawns a mixed starter wave in Dungeon Room 1.
     * Called from initRooms() after D1 is created.
     */
    private void populateD1() {
        Room d1 = dungeonRooms[0];

        // Register D1's enemy wave so Room.reset() can rebuild it on every re-entry.
        d1.addRespawningEntity(() -> new LizardEnemy(300, 250, d1.getTileMap()));
        d1.addRespawningEntity(() -> new RangedEnemy(940, 260, d1.getTileMap()));
        d1.addRespawningEntity(() -> new LizardEnemy(640, 500, d1.getTileMap()));
    }

    /** Sets the callback fired when the Boss in D3 is defeated. */
    public void setBossDefeatedCallback(Runnable r) { this.bossDefeatedCallback = r; }

    /** Spawns the Boss in Dungeon Room 3 and wires its defeat callback. */
    private void populateD3() {
        Room d3 = dungeonRooms[2];
        double bossX = TileMap.MAP_OFFSET_X + 640;
        double bossY = 360;
        Boss boss = new Boss(bossX, bossY, d3.getTileMap());
        boss.setProjectileList(d3.getProjectiles());
        boss.setOnDefeated(() -> {
            if (bossDefeatedCallback != null) bossDefeatedCallback.run();
        });
        d3.addRespawningEntity(() -> {
            Boss b = new Boss(bossX, bossY, d3.getTileMap());
            b.setProjectileList(d3.getProjectiles());
            b.setOnDefeated(() -> {
                if (bossDefeatedCallback != null) bossDefeatedCallback.run();
            });
            return b;
        });
    }

    /** Places a save crystal in D2 at col 24, row 5 (past the puzzle chokepoint). */
    private void installD2SavePoint() {
        Room d2 = dungeonRooms[1];
        if (d2 == null) return;
        double saveX = TileMap.MAP_OFFSET_X + 24 * 48;
        double saveY = 5 * 48;
        SavePoint sp = new SavePoint(
            saveX, saveY,
            d2.getRoomId(),
            SavePoint.SavePointType.SAVE_CRYSTAL,
            saveX, saveY
        );
        sp.enableSprite();
        d2.setSavePoint(sp);
    }

    /** Places the 3-button push-block puzzle in D2. All 3 buttons pressed simultaneously opens the chokepoint. */
    private void installD2Puzzle() {
        Room d2 = dungeonRooms[1];
        if (d2 == null) return;
        // Buttons added first (drawn behind); blocks added last (drawn on top)
        // so the rock visually sits on top of the button when occupying the same tile
        d2.addObject(new PressureButton(17, 2, false));
        d2.addObject(new PressureButton(3, 10, false));
        d2.addObject(new PressureButton(20, 8, false));
        d2.addObject(new PushBlock(17, 10));
        d2.addObject(new PushBlock(7, 4));
        d2.setPuzzleSolvedCallback(() -> {
            d2Solved = true;
            openExit("D2", Direction.RIGHT);
            // Unblock the chokepoint tiles and remove the visual blocker at the same moment
            TileMap d2Map = dungeonRooms[1].getTileMap();
            d2Map.setTileType(21, 7, Tile.TileType.FLOOR, "assets/tile_floor.png");
            d2Map.setTileType(22, 7, Tile.TileType.FLOOR, "assets/tile_floor.png");
            if (canvas != null && d2BlockerImage != null) canvas.remove(d2BlockerImage);
        });
    }

    /**
     * A2 — Trial of Strength push-block puzzle.
     *
     * Layout (tile coords):
     *   Button 1 at (3, 8)  — block-only
     *   Button 2 at (7, 11) — block-only
     *   Button 3 at (5, 6)  — requires player to stand on it
     *   Block A starts at   (3, 11)
     *   Block B starts at   (7, 7)
     *
     * Solution: push Block A left onto Button 1, push Block B down onto Button 2,
     * then the player stands on Button 3. All three pressed → chest unlocks.
     *
     * The strengthChest starts locked and is unlocked by the solved callback.
     */
    private void installA2Puzzle() {
        Room a2 = overworldGrid[0][1];
        if (a2 == null) return;

        if (strengthChest != null) {
            strengthChest.setLocked(true);
            strengthChest.setLockedMessage("The chest holds its secret still. Move the blocks to prove your strength.");
        }

        a2.addObject(new PressureButton(3, 8,  false));
        a2.addObject(new PressureButton(7, 11, false));
        a2.addObject(new PressureButton(5, 6,  false));
        a2.addObject(new PushBlock(3, 11));
        a2.addObject(new PushBlock(7, 7));

        a2.setPuzzleSolvedCallback(() -> {
            a2Solved = true;
            if (strengthChest != null) strengthChest.setLocked(false);
            debugTrialLog("A2 solved: all pressure buttons are pressed; strength chest unlocked.");
            if (dialogue != null && !dialogue.isOpen()) {
                GamePlayState.setCurrent(GamePlayState.DIALOGUE);
                dialogue.open(
                    new String[]{
                        "You hear a rumble in the distance.",
                        "Somewhere nearby, a chest has opened."
                    },
                    "Trial of Strength",
                    false,
                    () -> GamePlayState.setCurrent(GamePlayState.PLAYING)
                );
            }
        });
    }

    /** Drives the A3 dodge gauntlet: warning, retreat check, projectile storm, then chest unlock. */
    private void updateA3Trial(double dt, Player player) {
        if (player == null || courageChest == null || a3Solved) {
            return;
        }
        if (!GamePlayState.PLAYING.is()) {
            return;
        }

        boolean nearChest = isPlayerNearA3Chest(player);

        if (!a3TrialWarned && !a3TrialActive && nearChest) {
            a3TrialWarned = true;
            a3WaitingForRetreat = true;
            debugTrialLog("A3 warned: player entered the courage chest trigger radius.");
            if (dialogue != null && !dialogue.isOpen()) {
                GamePlayState.setCurrent(GamePlayState.DIALOGUE);
                dialogue.open(
                    new String[]{
                        "A chill runs through the air around the relic.",
                        "Back up. Survive the storm for thirty seconds, and the chest will open."
                    },
                    "Trial of Courage",
                    false,
                    () -> GamePlayState.setCurrent(GamePlayState.PLAYING)
                );
            }
            return;
        }

        if (a3WaitingForRetreat && !nearChest) {
            startA3Trial();
        }

        if (!a3TrialActive) {
            return;
        }

        a3TrialTimeRemaining -= dt;
        a3TrialSpawnTimer -= dt;
        while (a3TrialSpawnTimer <= 0.0 && a3TrialActive) {
            spawnA3GauntletVolley(player);
            a3TrialSpawnTimer += computeA3SpawnInterval();
        }
        maybeWarnA3RetreatAttempt(player);

        if (a3TrialTimeRemaining <= 0.0) {
            completeA3Trial();
        }
    }

    private void startA3Trial() {
        Room a3 = overworldGrid[0][2];
        if (a3 == null) return;
        a3WaitingForRetreat = false;
        a3TrialActive = true;
        a3TrialTimeRemaining = A3_TRIAL_DURATION_SECONDS;
        a3TrialSpawnTimer = 0.15;
        a3TrialLastSpawnIndex = -1;
        a3RetreatWarningLatched = false;
        setA3TrialRetreatBarrier(true);
        clearRoomProjectiles(a3);
        debugTrialLog("A3 started: courage storm active for 30.0 seconds.");
    }

    private void completeA3Trial() {
        a3TrialActive = false;
        a3TrialTimeRemaining = 0.0;
        a3TrialSpawnTimer = 0.0;
        a3RetreatWarningLatched = false;
        setA3TrialRetreatBarrier(false);
        a3Solved = true;
        addStoryFlag(A3_TRIAL_SOLVED_FLAG);
        if (courageChest != null) {
            courageChest.setLocked(false);
        }
        debugTrialLog("A3 cleared: courage chest unlocked.");
        Room a3 = overworldGrid[0][2];
        if (a3 != null) {
            clearRoomProjectiles(a3);
        }
        if (dialogue != null && !dialogue.isOpen()) {
            GamePlayState.setCurrent(GamePlayState.DIALOGUE);
            dialogue.open(
                new String[]{
                    "The storm breaks.",
                    "The chest unlocks."
                },
                "Trial of Courage",
                false,
                () -> GamePlayState.setCurrent(GamePlayState.PLAYING)
            );
        }
    }

    private void resetA3TrialState(boolean clearProjectiles) {
        a3TrialWarned = false;
        a3WaitingForRetreat = false;
        a3TrialActive = false;
        a3TrialTimeRemaining = 0.0;
        a3TrialSpawnTimer = 0.0;
        a3TrialLastSpawnIndex = -1;
        a3RetreatWarningLatched = false;
        setA3TrialRetreatBarrier(false);
        if (clearProjectiles) {
            Room a3 = overworldGrid[0][2];
            if (a3 != null) {
                clearRoomProjectiles(a3);
            }
        }
    }

    private boolean isPlayerNearA3Chest(Player player) {
        if (player == null || courageChest == null) {
            return false;
        }
        double chestCenterX = courageChest.getX() + 24.0;
        double chestCenterY = courageChest.getY() + 24.0;
        double dx = player.getX() - chestCenterX;
        double dy = player.getY() - chestCenterY;
        return dx * dx + dy * dy <= A3_TRIAL_TRIGGER_RADIUS_PX * A3_TRIAL_TRIGGER_RADIUS_PX;
    }

    private void spawnA3GauntletVolley(Player player) {
        Room a3 = overworldGrid[0][2];
        if (a3 == null || player == null) return;

        int volleyCount = 1;
        double elapsed = A3_TRIAL_DURATION_SECONDS - a3TrialTimeRemaining;
        if (elapsed >= 10.0) volleyCount++;
        if (elapsed >= 20.0) volleyCount++;

        for (int i = 0; i < volleyCount; i++) {
            double[] spawn = nextA3TrialSpawn(a3.getTileMap());
            double targetX = player.getX() + (a3TrialRandom.nextDouble() - 0.5) * 80.0;
            double targetY = player.getY() + (a3TrialRandom.nextDouble() - 0.5) * 80.0;
            a3.getProjectiles().add(new Projectile(
                spawn[0], spawn[1],
                targetX, targetY,
                a3.getTileMap(),
                null
            ));
        }
    }

    private double[] nextA3TrialSpawn(TileMap tileMap) {
        if (tileMap == null) {
            return new double[]{ TileMap.MAP_OFFSET_X + 48, 48 };
        }

        for (int attempts = 0; attempts < A3_TRIAL_PROJECTILE_SPAWN_TILES.length; attempts++) {
            int randomIndex = a3TrialRandom.nextInt(A3_TRIAL_PROJECTILE_SPAWN_TILES.length);
            int[] tile = A3_TRIAL_PROJECTILE_SPAWN_TILES[randomIndex];
            int col = tile[0];
            int row = tile[1];
            Tile spawnTile = tileMap.getTileAt(col, row);
            if (spawnTile != null && spawnTile.isPassable()) {
                a3TrialLastSpawnIndex = randomIndex;
                return new double[]{
                    TileMap.MAP_OFFSET_X + col * 48 + 24.0,
                    row * 48 + 24.0
                };
            }
        }

        return new double[]{ TileMap.MAP_OFFSET_X + 24 * 48, 24.0 + 13 * 48 };
    }

    private double computeA3SpawnInterval() {
        double progress = 1.0 - Math.max(0.0, a3TrialTimeRemaining) / A3_TRIAL_DURATION_SECONDS;
        return A3_TRIAL_INITIAL_SPAWN_INTERVAL
            - (A3_TRIAL_INITIAL_SPAWN_INTERVAL - A3_TRIAL_MIN_SPAWN_INTERVAL) * progress;
    }

    private void setA3TrialRetreatBarrier(boolean blocked) {
        Room a3 = overworldGrid[0][2];
        if (a3 == null) {
            return;
        }
        TileMap tileMap = a3.getTileMap();
        if (tileMap == null) {
            return;
        }
        Tile.TileType type = blocked ? Tile.TileType.WALL : Tile.TileType.FLOOR;
        String spritePath = blocked ? "assets/tile_wall.png" : "assets/tile_floor.png";
        for (int[] tile : A3_TRIAL_ACTIVE_BLOCK_TILES) {
            tileMap.setTileType(tile[0], tile[1], type, spritePath);
        }
    }

    private void maybeWarnA3RetreatAttempt(Player player) {
        if (!a3TrialActive || player == null) {
            return;
        }

        boolean inWarningLane = isPlayerInA3RetreatWarningLane(player);
        if (!inWarningLane) {
            a3RetreatWarningLatched = false;
            return;
        }
        if (a3RetreatWarningLatched) {
            return;
        }

        a3RetreatWarningLatched = true;
        debugTrialLog("A3 retreat blocked: player tried to leave during the active storm.");
        if (dialogue != null && !dialogue.isOpen()) {
            GamePlayState.setCurrent(GamePlayState.DIALOGUE);
            dialogue.open(
                new String[]{
                    "The trial is still active.",
                    "Push forward!"
                },
                "Trial of Courage",
                false,
                () -> GamePlayState.setCurrent(GamePlayState.PLAYING)
            );
        }
    }

    /**
     * Detects the exact A3 retreat warning strip requested by design:
     * trigger on 11,9 | 11,10 | 11,11 while the temporary barrier sits on 12,9 | 12,10 | 12,11.
     */
    private boolean isPlayerInA3RetreatWarningLane(Player player) {
        if (player == null) {
            return false;
        }

        Direction facing = player.getFacing();
        if (facing != Direction.LEFT && facing != Direction.RIGHT) {
            return false;
        }

        double zoneX = TileMap.MAP_OFFSET_X + 11 * 48;
        double zoneY = 9 * 48;
        Hitbox retreatZone = new Hitbox(zoneX, zoneY, 48, 3 * 48);
        return retreatZone.overlaps(player.getHitbox());
    }

    /** Returns room-specific debug lines for the trial chambers when F6 overlay is enabled. */
    public List<String> getTrialDebugLines(Room room, Player player) {
        ArrayList<String> lines = new ArrayList<>();
        if (room == null) {
            return lines;
        }

        String roomId = room.getRoomId();
        if ("A2".equals(roomId)) {
            appendA2DebugLines(lines, room);
        } else if ("A3".equals(roomId)) {
            appendA3DebugLines(lines, room, player);
        } else if ("B3".equals(roomId)) {
            appendB3DebugLines(lines);
        }
        return lines;
    }

    private void appendA2DebugLines(List<String> lines, Room room) {
        int totalButtons = 0;
        int pressedButtons = 0;
        ArrayList<String> buttonStates = new ArrayList<>();
        ArrayList<String> blockStates = new ArrayList<>();

        for (WorldObject object : room.getObjects()) {
            if (object instanceof PressureButton) {
                PressureButton button = (PressureButton) object;
                totalButtons++;
                if (button.isPressed()) {
                    pressedButtons++;
                }
                buttonStates.add(String.format(
                    "%d,%d=%s%s",
                    button.getTileCol(),
                    button.getTileRow(),
                    button.isPressed() ? "ON" : "off",
                    button.requiresPlayer() ? " (P)" : ""
                ));
            } else if (object instanceof PushBlock) {
                PushBlock block = (PushBlock) object;
                blockStates.add(String.format(
                    "%d,%d%s",
                    block.getTileCol(),
                    block.getTileRow(),
                    block.isFallen() ? " FALLEN" : ""
                ));
            }
        }

        lines.add(String.format(
            "A2 Strength | solved=%s | heroGate=%s",
            a2Solved,
            hasStoryFlag(A2_HERO_THICKET_FLAG)
        ));
        if (strengthChest != null) {
            lines.add(String.format(
                "Chest | locked=%s | open=%s",
                strengthChest.isLocked(),
                strengthChest.isOpen()
            ));
        }
        lines.add(String.format("Buttons | %d/%d pressed", pressedButtons, totalButtons));
        if (!buttonStates.isEmpty()) {
            lines.add("Button tiles | " + String.join("  ", buttonStates));
        }
        if (!blockStates.isEmpty()) {
            lines.add("Blocks | " + String.join("  ", blockStates));
        }
    }

    private void appendA3DebugLines(List<String> lines, Room room, Player player) {
        lines.add(String.format(
            "A3 Courage | solved=%s | heroGate=%s",
            a3Solved,
            hasStoryFlag(A3_HERO_THICKET_FLAG)
        ));
        if (courageChest != null) {
            lines.add(String.format(
                "Chest | locked=%s | open=%s | near=%s",
                courageChest.isLocked(),
                courageChest.isOpen(),
                isPlayerNearA3Chest(player)
            ));
        }
        lines.add(String.format(
            "State | warned=%s retreat=%s active=%s",
            a3TrialWarned,
            a3WaitingForRetreat,
            a3TrialActive
        ));

        double nextInterval = computeA3SpawnInterval();
        int volleyCount = 1;
        double elapsed = A3_TRIAL_DURATION_SECONDS - a3TrialTimeRemaining;
        if (elapsed >= 10.0) volleyCount++;
        if (elapsed >= 20.0) volleyCount++;
        lines.add(String.format(
            "Storm | time=%.2fs spawnIn=%.2fs interval=%.2fs volley=%d projectiles=%d",
            Math.max(0.0, a3TrialTimeRemaining),
            Math.max(0.0, a3TrialSpawnTimer),
            nextInterval,
            volleyCount,
            room.getProjectiles().size()
        ));

        if (a3TrialLastSpawnIndex >= 0 && a3TrialLastSpawnIndex < A3_TRIAL_PROJECTILE_SPAWN_TILES.length) {
            int[] lastEmitter = A3_TRIAL_PROJECTILE_SPAWN_TILES[a3TrialLastSpawnIndex];
            lines.add(String.format(
                "Emitters | last=%d,%d randomPool=%d",
                lastEmitter[0],
                lastEmitter[1],
                A3_TRIAL_PROJECTILE_SPAWN_TILES.length
            ));
        } else {
            lines.add(String.format(
                "Emitters | last=none randomPool=%d",
                A3_TRIAL_PROJECTILE_SPAWN_TILES.length
            ));
        }
    }

    private void appendB3DebugLines(List<String> lines) {
        lines.add(String.format(
            "B3 Wisdom | heroGate=%s",
            hasStoryFlag(B3_HERO_THICKET_FLAG)
        ));
        if (wisdomChest != null) {
            lines.add(String.format(
                "Chest | locked=%s | open=%s",
                wisdomChest.isLocked(),
                wisdomChest.isOpen()
            ));
        }
    }

    private void debugTrialLog(String message) {
        if (!GameplayPane.areWorldDebugMarkersVisible()) {
            return;
        }
        System.out.println("[TRIAL DEBUG] " + message);
    }

    private void clearRoomProjectiles(Room room) {
        if (room == null) return;
        if (canvas != null) {
            for (Projectile projectile : room.getProjectiles()) {
                projectile.removeSpriteFromCanvas(canvas);
            }
        }
        room.getProjectiles().clear();
    }

    /** D2 is a puzzle-only room — no enemies. */
    private void populateD2() {
    }

    /**
     * Opens all valid exits between rooms, exactly as defined in the design doc.
     * C1 NORTH starts CLOSED (bridge broken). D1 NORTH starts CLOSED (RoomLock).
     * All other connected exits start OPEN.
     */
    private void wireExits() {
        // --- A1 ↔ B1 ---
        overworldGrid[0][0].setExit(Direction.RIGHT, true);
        overworldGrid[1][0].setExit(Direction.LEFT,  true);

        // --- A1 ↔ A2 ---
        overworldGrid[0][0].setExit(Direction.UP,   true);
        overworldGrid[0][1].setExit(Direction.DOWN, true);

        // --- B1 ↔ B2 ---
        overworldGrid[1][0].setExit(Direction.UP,   true);
        overworldGrid[1][1].setExit(Direction.DOWN, true);

        // --- B1 ↔ C1 ---
        overworldGrid[1][0].setExit(Direction.RIGHT, true);
        overworldGrid[2][0].setExit(Direction.LEFT,  true);

        // --- A2 ↔ A3 ---
        overworldGrid[0][1].setExit(Direction.UP,   true);
        overworldGrid[0][2].setExit(Direction.DOWN, true);

        // --- A2 ↔ B2 ---
        overworldGrid[0][1].setExit(Direction.RIGHT, true);
        overworldGrid[1][1].setExit(Direction.LEFT,  true);

        // --- A3 ↔ B3 ---
        overworldGrid[0][2].setExit(Direction.RIGHT, true);
        overworldGrid[1][2].setExit(Direction.LEFT,  true);

        // --- B2 ↔ B3 ---
        overworldGrid[1][1].setExit(Direction.UP,   true);
        overworldGrid[1][2].setExit(Direction.DOWN, true);

        // --- C1 → C2: CLOSED at start (bridge broken) ---
        // DrawbridgeLever.onInteract() calls openExit("C1", Direction.UP) when repaired.
        overworldGrid[2][0].setExit(Direction.UP,   false);
        overworldGrid[2][1].setExit(Direction.DOWN, true);  // C2 south is open from the other side

        // --- C2 ↔ C3 ---
        overworldGrid[2][1].setExit(Direction.UP,   true);
        overworldGrid[2][2].setExit(Direction.DOWN, true);

        // --- C3 → D1: handled by the dungeon entrance marker, NOT a directional exit ---
        // No exit flag is set here; enterDungeon() handles the switch directly.

        // --- D1 ↔ D2 ---
        // Dungeon progression stays locked until D1's RoomLock sees the encounter cleared.
        dungeonRooms[0].setExit(Direction.RIGHT, false);
        dungeonRooms[1].setExit(Direction.LEFT,  true);

        // --- D2 ↔ D3 — D2 east starts CLOSED (locked by push-block puzzle) ---
        dungeonRooms[1].setExit(Direction.RIGHT, false);
        dungeonRooms[2].setExit(Direction.LEFT,  false);
    }

    /**
     * Sets the active room directly by ID without running a transition animation.
     * Used when starting or loading a fresh gameplay session.
     *
     * @return true if the room ID was found
     */
    public boolean setActiveRoomById(String roomId) {
        Room target = getRoomById(roomId);
        if (target == null) {
            return false;
        }
        activeTransition = null;
        activeRoom = target;
        inDungeon = isDungeonRoom(target);
        return true;
    }

    /** Shows only the markers that belong to the current active room. */
    public void showSpecialMarkersForActiveRoom() {
        syncSpecialMarkersToActiveRoom();
    }

    /** Hides all tech-demo room markers from the canvas. */
    /** Hides all tech-demo room markers from the canvas. */
    public void hideSpecialMarkers() {
        if (canvas == null) return;
        canvas.remove(dungeonEntranceMarker);
        canvas.remove(dungeonExitMarker);
        if (d1BlockerImage != null) canvas.remove(d1BlockerImage);
        if (d2BlockerImage != null) canvas.remove(d2BlockerImage);
        if (d3BlockerImage != null) canvas.remove(d3BlockerImage);
    }

    /** Rebuilds marker visibility after room swaps, loads, and screen show/hide. */
    private void syncSpecialMarkersToActiveRoom() {
        hideSpecialMarkers();
        if (canvas == null) return;
        if (activeRoom == roomC3) {
            //canvas.add(dungeonEntranceMarker);
        }
        // --- dungeon blockers: add on top when entering relevant room ---
        if (activeRoom == dungeonRooms[0] && !d1Cleared) {
            if (d1BlockerImage == null) {
                d1BlockerImage = new GImage("assets/visuals/overworld rooms/purpleblocker.png");
                d1BlockerImage.setSize(288, 288);
                d1BlockerImage.setLocation(TileMap.MAP_OFFSET_X + 23 * 48, 5 * 48 - 24);
            }
            canvas.add(d1BlockerImage);
        }
        if (activeRoom == dungeonRooms[1] && !d2Solved) {
            if (d2BlockerImage == null) {
                d2BlockerImage = new GImage("assets/visuals/overworld rooms/purpleblocker.png");
                d2BlockerImage.setLocation(TileMap.MAP_OFFSET_X + 19 * 48, 4.5 * 48);
            }
            canvas.add(d2BlockerImage);
        }
        if (activeRoom == dungeonRooms[2]) {
            if (d3BlockerImage == null) {
                d3BlockerImage = new GImage("assets/visuals/overworld rooms/purpleblocker.png");
                d3BlockerImage.setLocation(TileMap.MAP_OFFSET_X - 2 * 48, 6 * 48 - (int)(1.5 * 48));
            }
            canvas.add(d3BlockerImage);
        }
    }

    // =========================================================
    // UPDATE / DRAW — called each tick by GameplayPane
    // =========================================================

    /**
     * Per-tick update. Routes to the active RoomTransition if one is running,
     * or to the active room's normal update otherwise.
     * Also checks for the dungeon entrance trigger when the player is in C3.
     *
     * @param dt     delta-time in seconds (e.g. 0.016 for ~60fps)
     * @param player the active Player (position already updated for this tick)
     */
    public void update(double dt, Player player) {
        // Store player reference so triggerTransition() (called via exit callback, no player arg)
        // can pass it to RoomTransition.start() and finishTransition().
        this.lastTickPlayer = player;

        if (activeTransition != null) {
            // --- transition in progress: advance the pan animation ---
            activeTransition.update(dt);
            if (activeTransition.isAnimationComplete()) {
                finishTransition(player);
            }
        } else {
            // --- normal gameplay: update active room content ---
            activeRoom.update(dt, player);

            if (!player.isAlive()) {
                handlePlayerDeath(player);
                return;
            }

            if (activeRoom == overworldGrid[0][2]) {
                updateA3Trial(dt, player);
            }

            // --- dungeon entrance check (C3 only) ---
            // TECH DEMO: checks if player overlaps the red GRect marker in C3.
            // RIG POINT: replace this check with WorldObject.onContact() once the real door is in C3.
            if (activeRoom == roomC3) {
                checkDungeonEntranceTrigger(player);
            }

            // --- dungeon exit check (D1 only) ---
            // TECH DEMO: checks if player overlaps the red exit marker in D1.
            // RIG POINT: replace this check with WorldObject.onContact() once a real exit door is in D1.
            if (inDungeon && activeRoom == dungeonRooms[0]) {
                checkDungeonExitTrigger(player);
            }
        }
    }

    /**
     * Resets trial-room transient state on player death.
     * For A3, death is a failure: clear the storm and make the chest approach restart cleanly.
     */
    public void handlePlayerDeath(Player player) {
        if (player == null || a3Solved || activeRoom != overworldGrid[0][2]) {
            return;
        }

        boolean failedActiveTrial = a3TrialActive;
        boolean shouldResetA3 = a3TrialWarned
            || a3WaitingForRetreat
            || a3TrialActive
            || !activeRoom.getProjectiles().isEmpty();
        if (!shouldResetA3) {
            return;
        }

        resetA3TrialState(true);
        debugTrialLog("A3 failed: player died, storm cleared, trial reset.");
        if (failedActiveTrial && dialogue != null && !dialogue.isOpen()) {
            GamePlayState.setCurrent(GamePlayState.DIALOGUE);
            dialogue.open(
                new String[]{
                    "You failed the Trial of Courage.",
                    "Gather yourself and try again."
                },
                "Trial of Courage",
                false,
                () -> GamePlayState.setCurrent(GamePlayState.PLAYING)
            );
        }
    }

    // =========================================================
    // TRANSITION — trigger and finish
    // =========================================================

    /**
     * Called by a Room's exit callback when the player walks off an open exit edge.
     * Finds the neighboring room, creates a RoomTransition, and starts the pan animation.
     * Does nothing if a transition is already running (prevents double-triggering).
     *
     * @param direction the direction the player exited
     */
    public void triggerTransition(Direction direction) {
        if (activeTransition != null) return; // already transitioning; ignore

        // --- find the room in that direction ---
        Room neighbor = findNeighborRoom(direction);
        if (neighbor == null) return; // no room exists in that direction

        // --- prepare the destination room before it is drawn into the pan ---
        prepareRoomForEntry(neighbor);

        // --- start the pan animation ---
        activeTransition = new RoomTransition();
        activeTransition.start(activeRoom, neighbor, direction, canvas, lastTickPlayer);
    }

    /** Resets transient room state before the room becomes visible on screen. */
    private void prepareRoomForEntry(Room room) {
        if (room != null) {
            room.reset();
            // If re-entering D1, reset the cleared flag so the blocker reappears
            if (room == dungeonRooms[0]) {
                d1Cleared = false;
            }
            if (room == overworldGrid[0][2] && !a3Solved) {
                resetA3TrialState(false);
            }
        }
    }

    /**
     * Called when the transition animation completes.
     * Swaps the active room, syncs the player's internal coordinates to their sprite position,
     * and restores GamePlayState to PLAYING.
     *
     * Player coordinate correction after pan (verification table):
     *   Exited RIGHT → player.x = left  edge of new room, player.y unchanged
     *   Exited LEFT  → player.x = right edge of new room, player.y unchanged
     *   Exited UP    → player.y = bottom edge of new room, player.x unchanged
     *   Exited DOWN  → player.y = top   edge of new room, player.x unchanged
     *
     * @param player the active Player
     */
    private void finishTransition(Player player) {
        Room fromRoom  = activeTransition.getFromRoom();
        Room toRoom    = activeTransition.getToRoom();
        Direction exitDir = activeTransition.getDirection();

        // --- remove old room from canvas ---
        fromRoom.removeFrom(canvas);

        // --- swap active room ---
        activeRoom = toRoom;
        inDungeon  = isDungeonRoom(toRoom);

        // --- update player's tile map so collision uses the new room's layout ---
        // RIG POINT: player.setTileMap() is called here on every room transition.
        //            When real room tile layouts replace the all-floor dummy, collision
        //            will automatically use the new layout.
        player.setTileMap(activeRoom.getTileMap());

        // --- sync player's internal coordinates to where their sprite landed after the pan ---
        // The sprite was moved by ROOM_WIDTH_PX or ROOM_HEIGHT_PX total during the animation.
        // The internal x/y haven't changed yet — we correct them here.
        double newX = player.getX();
        double newY = player.getY();
        switch (exitDir) {
            case RIGHT: newX = player.getX() - ROOM_WIDTH_PX;  break;
            case LEFT:  newX = player.getX() + ROOM_WIDTH_PX;  break;
            case UP:    newY = player.getY() + ROOM_HEIGHT_PX; break;
            case DOWN:  newY = player.getY() - ROOM_HEIGHT_PX; break;
        }
        if (toRoom == dungeonRooms[2] && fromRoom == dungeonRooms[1]) {
            newX = TileMap.MAP_OFFSET_X + 2 * 48;
            newY = 7 * 48;
        }
        player.setPosition(newX, newY);
        player.setSpawnPosition(newX, newY);

        syncSpecialMarkersToActiveRoom();

        // --- clean up transition and resume gameplay ---
        activeTransition = null;
        GamePlayState.setCurrent(GamePlayState.PLAYING);
    }

    // =========================================================
    // DUNGEON ENTRANCE — C3 special trigger
    // =========================================================

    /**
     * Checks whether the player's center overlaps the red dungeon entrance marker in C3.
     * If so, calls enterDungeon() to teleport them into D1 (no sliding pan).
     *
     * @param player the active Player
     */
    private void checkDungeonEntranceTrigger(Player player) {
        double px = player.getX();
        double py = player.getY();

        boolean insideTrigger =
            px >= DUNGEON_ENTRANCE_X &&
            px <= DUNGEON_ENTRANCE_X + DUNGEON_ENTRANCE_W &&
            py >= DUNGEON_ENTRANCE_Y &&
            py <= DUNGEON_ENTRANCE_Y + DUNGEON_ENTRANCE_H;

        if (insideTrigger) {
            enterDungeon(player);
        }
    }

    /**
     * Instantly transitions the player from C3 into D1 (the dungeon entrance).
     * No sliding animation — this is a door-style transition, not a directional exit.
     * Player is placed at the south edge of D1, facing north.
     *
     * // TECH DEMO: called when player touches the red GRect in C3.
     * // RIG POINT: replace this with a proper WorldObject door interaction in C3's buildC3().
     *
     * @param player the active Player
     */
    public void enterDungeon(Player player) {
        // --- remove C3 and the dungeon entrance marker from canvas ---
        activeRoom.removeFrom(canvas);
        hideSpecialMarkers();

        // --- switch to D1 ---
        activeRoom = dungeonRooms[0];
        inDungeon  = true;

        // --- reset D1 before drawing so entry never flashes stale room state ---
        prepareRoomForEntry(activeRoom);

        // --- put D1 on canvas, including the exit marker ---
        activeRoom.addTo(canvas);
        syncSpecialMarkersToActiveRoom();

        // --- place player in center of D1, clear of the exit marker ---
        // RIG POINT: adjust DUNGEON_SPAWN_X/Y if the real dungeon entrance position changes.
        player.setTileMap(activeRoom.getTileMap());
        player.setPosition(DUNGEON_SPAWN_X, DUNGEON_SPAWN_Y);
        player.setSpawnPosition(DUNGEON_SPAWN_X, DUNGEON_SPAWN_Y);

        GamePlayState.setCurrent(GamePlayState.PLAYING);
    }

    // =========================================================
    // DUNGEON EXIT — D1 special trigger (mirrors dungeon entrance)
    // =========================================================

    /**
     * Checks whether the player's center overlaps the red dungeon exit marker in D1.
     * If so, calls exitDungeon() to return them to C3 (no sliding pan).
     *
     * // TECH DEMO: mirrors checkDungeonEntranceTrigger() — same overlap logic, opposite direction.
     * // RIG POINT: Replace with WorldObject.onContact() once a real exit door is built in D1.
     *
     * @param player the active Player
     */
    private void checkDungeonExitTrigger(Player player) {
        double px = player.getX();
        double py = player.getY();

        boolean insideTrigger =
            px >= DUNGEON_EXIT_X &&
            px <= DUNGEON_EXIT_X + DUNGEON_EXIT_W &&
            py >= DUNGEON_EXIT_Y &&
            py <= DUNGEON_EXIT_Y + DUNGEON_EXIT_H;

        if (insideTrigger) {
            exitDungeon(player);
        }
    }

    /**
     * Instantly transitions the player from D1 back to C3 (the dungeon entrance area).
     * No sliding animation — mirrors the door-style entrance from enterDungeon().
     * Player is placed just below and right of the entrance marker so they don't
     * immediately re-trigger the dungeon entrance on the next tick.
     *
     * // TECH DEMO: called when player touches the red exit marker in D1.
     * // RIG POINT: Replace with a proper WorldObject exit door interaction in D1's buildD1().
     *
     * @param player the active Player
     */
    public void exitDungeon(Player player) {
        // --- remove D1 and the exit marker from canvas ---
        activeRoom.removeFrom(canvas);
        hideSpecialMarkers();

        // --- switch back to C3 ---
        activeRoom = roomC3;
        inDungeon  = false;

        // --- reset C3 before drawing so room-local temporary state is ready immediately ---
        prepareRoomForEntry(activeRoom);

        // --- put C3 on canvas, including the entrance marker ---
        activeRoom.addTo(canvas);
        syncSpecialMarkersToActiveRoom();

        // --- place player offset from the entrance marker so they don't re-enter ---
        // OVERWORLD_RETURN_X/Y is just south of the marker; player faces south (away from dungeon).
        // RIG POINT: adjust OVERWORLD_RETURN_X/Y if the real dungeon door position changes.
        player.setTileMap(activeRoom.getTileMap());
        player.setPosition(OVERWORLD_RETURN_X, OVERWORLD_RETURN_Y);
        player.setSpawnPosition(OVERWORLD_RETURN_X, OVERWORLD_RETURN_Y);

        GamePlayState.setCurrent(GamePlayState.PLAYING);
    }

    // =========================================================
    // EXIT CONTROL — called by scripted events
    // =========================================================

    /**
     * Opens an exit in a specific room by room ID.
     * Used by DrawbridgeLever (opens C1 UP) and RoomLock (opens D1 UP).
     *
     * @param roomId    the room to modify (e.g. "C1")
     * @param direction the exit direction to open
     */
    public void openExit(String roomId, Direction direction) {
        Room r = getRoomById(roomId);
        if (r != null) r.setExit(direction, true);
    }

    /**
     * Closes an exit in a specific room by room ID.
     * Used by the opening sequence to seal A1's south exit after the PathBlocker is placed.
     *
     * @param roomId    the room to modify (e.g. "A1")
     * @param direction the exit direction to close
     */
    public void closeExit(String roomId, Direction direction) {
        Room r = getRoomById(roomId);
        if (r != null) r.setExit(direction, false);
    }

    // =========================================================
    // SAVE / LOAD SNAPSHOT HELPERS
    // =========================================================

    /** Returns a copy of the persistent one-time object IDs already collected in this save. */
    public List<String> getCollectedItemIdsSnapshot() {
        return new ArrayList<>(collectedItemIds);
    }

    /**
     * Returns persistent story flags plus the exact current exit topology for every room.
     * Exit states are encoded into the same string list so a freshly rebuilt WorldMap
     * can be restored to the saved traversal state on load.
     */
    public List<String> getStoryFlagsSnapshot() {
        LinkedHashSet<String> snapshot = new LinkedHashSet<>(storyFlags);
        addExitFlags(snapshot, overworldGrid);
        addExitFlags(snapshot, dungeonRooms);
        return new ArrayList<>(snapshot);
    }

    /** Reapplies persistent collected IDs and story flags to a freshly rebuilt WorldMap. */
    public void applyPersistentState(List<String> savedCollectedItemIds, List<String> savedStoryFlags) {
        collectedItemIds.clear();
        storyFlags.clear();

        if (savedCollectedItemIds != null) {
            for (String itemId : savedCollectedItemIds) {
                addNormalized(collectedItemIds, itemId);
            }
        }

        if (savedStoryFlags != null) {
            for (String flag : savedStoryFlags) {
                if (!applyExitFlag(flag)) {
                    addNormalized(storyFlags, flag);
                }
            }
        }

        syncPersistentWorldObjects();
    }

    /** Registers a one-time world object / pickup ID as collected for future saves. */
    public void markCollectedItem(String itemId) {
        addNormalized(collectedItemIds, itemId);
    }

    /** Returns true if the given one-time world object / pickup ID was already collected. */
    public boolean hasCollectedItem(String itemId) {
        return itemId != null && collectedItemIds.contains(itemId.trim());
    }

    /** Adds a non-exit story flag for future saves. */
    public void addStoryFlag(String flag) {
        addNormalized(storyFlags, flag);
    }

    /** Returns true if the given non-exit story flag is currently set. */
    public boolean hasStoryFlag(String flag) {
        return flag != null && storyFlags.contains(flag.trim());
    }

    /** Marks the C1 drawbridge as repaired and swaps the room to its completed art/state. */
    public void markDrawbridgeRepaired() {
        addStoryFlag(DRAWBRIDGE_REPAIRED_FLAG);
        openExit("C1", Direction.UP);
        Room c1 = overworldGrid[2][0];
        if (c1 != null) {
            c1.replaceBackgroundImage(
                "assets/visuals/overworld rooms/c1.png",
                "assets/visuals/overworld rooms/C1.png"
            );
        }
    }

    private void syncPersistentWorldObjects() {
        if (pickaxeProp != null && hasStoryFlag(PICKAXE_TAKEN_FLAG)) {
            pickaxeProp.hide();
        }
        if (minersHatProp != null && hasStoryFlag(MINERS_HAT_B2_FLAG)) {
            minersHatProp.hide();
        }
        if (oreNode != null && hasCollectedItem(OreNode.SAVE_FLAG_ID)) {
            oreNode.forceMined();
        }
        for (HeroThicket thicket : heroThickets) {
            if (thicket != null) {
                thicket.syncPersistentState();
            }
        }
        if (hasStoryFlag(A3_TRIAL_SOLVED_FLAG)) {
            a3Solved = true;
            if (courageChest != null) {
                courageChest.setLocked(false);
            }
        }
        if (courageChest != null && hasCollectedItem(courageChest.getChestId())) {
            courageChest.setLocked(false);
            courageChest.forceOpen();
            a3Solved = true;
        }
        if (strengthChest != null && hasCollectedItem(strengthChest.getChestId())) {
            strengthChest.setLocked(false);
            strengthChest.forceOpen();
            a2Solved = true;
        }
        if (wisdomChest   != null && hasCollectedItem(wisdomChest.getChestId()))   wisdomChest.forceOpen();
        Room c1 = overworldGrid[2][0];
        boolean bridgeRepaired = hasStoryFlag(DRAWBRIDGE_REPAIRED_FLAG)
            || (c1 != null && c1.getExitAt(Direction.UP));
        if (bridgeRepaired) {
            addStoryFlag(DRAWBRIDGE_REPAIRED_FLAG);
            if (drawbridgeLever != null) {
                drawbridgeLever.forceFixed();
            }
            markDrawbridgeRepaired();
        }
        if (dungeonRooms[1] != null && dungeonRooms[1].getExitAt(Direction.RIGHT)) {
            d2Solved = true;
            TileMap d2Map = dungeonRooms[1].getTileMap();
            if (d2Map != null) {
                d2Map.setTileType(21, 7, Tile.TileType.FLOOR, "assets/tile_floor.png");
                d2Map.setTileType(22, 7, Tile.TileType.FLOOR, "assets/tile_floor.png");
            }
        }
    }

    private static double computeTileWorldX(int[][] tiles) {
        int minCol = Integer.MAX_VALUE;
        for (int[] tile : tiles) {
            if (tile[0] < minCol) minCol = tile[0];
        }
        return TileMap.MAP_OFFSET_X + minCol * 48.0;
    }

    private static double computeTileWorldY(int[][] tiles) {
        int minRow = Integer.MAX_VALUE;
        for (int[] tile : tiles) {
            if (tile[1] < minRow) minRow = tile[1];
        }
        return minRow * 48.0;
    }

    private static double computeTileWorldWidth(int[][] tiles) {
        int minCol = Integer.MAX_VALUE;
        int maxCol = Integer.MIN_VALUE;
        for (int[] tile : tiles) {
            if (tile[0] < minCol) minCol = tile[0];
            if (tile[0] > maxCol) maxCol = tile[0];
        }
        return (maxCol - minCol + 1) * 48.0;
    }

    private static double computeTileWorldHeight(int[][] tiles) {
        int minRow = Integer.MAX_VALUE;
        int maxRow = Integer.MIN_VALUE;
        for (int[] tile : tiles) {
            if (tile[1] < minRow) minRow = tile[1];
            if (tile[1] > maxRow) maxRow = tile[1];
        }
        return (maxRow - minRow + 1) * 48.0;
    }

    private void addExitFlags(Set<String> snapshot, Room[][] rooms) {
        for (int col = 0; col < COLS; col++) {
            for (int row = 0; row < ROWS; row++) {
                addExitFlags(snapshot, rooms[col][row]);
            }
        }
    }

    private void addExitFlags(Set<String> snapshot, Room[] rooms) {
        for (Room room : rooms) {
            addExitFlags(snapshot, room);
        }
    }

    private void addExitFlags(Set<String> snapshot, Room room) {
        if (room == null) return;
        for (Direction direction : Direction.values()) {
            snapshot.add(encodeExitFlag(room.getRoomId(), direction, room.getExitAt(direction)));
        }
    }

    private String encodeExitFlag(String roomId, Direction direction, boolean open) {
        return EXIT_FLAG_PREFIX + roomId + ":" + direction.name() + ":" + (open ? "open" : "closed");
    }

    private boolean applyExitFlag(String flag) {
        if (flag == null || !flag.startsWith(EXIT_FLAG_PREFIX)) {
            return false;
        }

        String[] parts = flag.split(":");
        if (parts.length != 4) {
            return false;
        }

        Room room = getRoomById(parts[1]);
        if (room == null) {
            return false;
        }

        Direction direction;
        try {
            direction = Direction.valueOf(parts[2]);
        } catch (IllegalArgumentException ex) {
            return false;
        }

        if ("open".equals(parts[3])) {
            room.setExit(direction, true);
            return true;
        }
        if ("closed".equals(parts[3])) {
            room.setExit(direction, false);
            return true;
        }
        return false;
    }

    private void addNormalized(Set<String> target, String value) {
        if (target == null || value == null) return;
        String trimmed = value.trim();
        if (!trimmed.isEmpty()) {
            target.add(trimmed);
        }
    }

    // =========================================================
    // HELPER — neighbor room lookup
    // =========================================================

    /**
     * Finds the room adjacent to the current active room in the given direction.
     * Handles both the overworld grid and the linear dungeon chain.
     * Returns null if no room exists in that direction (edge of the map, or disconnected).
     *
     * Overworld grid direction → grid offset:
     *   RIGHT → col+1   LEFT → col-1
     *   UP    → row+1   DOWN → row-1   (row 0 = south, row 2 = north)
     *
     * @param direction the direction to look in
     * @return the neighboring Room, or null if none exists
     */
    private Room findNeighborRoom(Direction direction) {
        // --- search overworld grid ---
        for (int col = 0; col < COLS; col++) {
            for (int row = 0; row < ROWS; row++) {
                if (overworldGrid[col][row] == activeRoom) {
                    int neighborCol = col;
                    int neighborRow = row;
                    switch (direction) {
                        case RIGHT: neighborCol = col + 1; break;
                        case LEFT:  neighborCol = col - 1; break;
                        case UP:    neighborRow = row + 1; break; // row 0 = south, so UP = row+1
                        case DOWN:  neighborRow = row - 1; break;
                    }
                    return getOverworldRoom(neighborCol, neighborRow); // returns null if out of bounds
                }
            }
        }

        // --- search dungeon chain ---
        // Dungeon rooms run east-to-west: D1 (index 0) → D2 (index 1) → D3 (index 2).
        // RIGHT advances deeper into the dungeon; LEFT goes back toward the entrance.
        for (int i = 0; i < DUNGEON_ROOMS; i++) {
            if (dungeonRooms[i] == activeRoom) {
                if (direction == Direction.RIGHT && i + 1 < DUNGEON_ROOMS) return dungeonRooms[i + 1];
                if (direction == Direction.LEFT  && i - 1 >= 0)            return dungeonRooms[i - 1];
                return null; // no dungeon room in that direction
            }
        }

        return null; // active room not found in either grid (should not happen)
    }

    /**
     * Returns true if the given room is one of the three dungeon rooms.
     *
     * @param room the room to check
     * @return true if room is D1, D2, or D3
     */
    private boolean isDungeonRoom(Room room) {
        for (Room dr : dungeonRooms) {
            if (dr == room) return true;
        }
        return false;
    }

    // =========================================================
    // ROOM LOOKUP
    // =========================================================

    /**
     * Returns the overworld room at grid position (col, row).
     * col: 0=A, 1=B, 2=C. row: 0=row1 (south/bottom), 1=row2, 2=row3 (north/top).
     * Returns null if the coordinates are out of bounds.
     *
     * @param col column index (0–2)
     * @param row row index (0–2)
     * @return the Room at that grid position, or null if out of bounds
     */
    public Room getOverworldRoom(int col, int row) {
        if (col < 0 || col >= COLS || row < 0 || row >= ROWS) return null;
        return overworldGrid[col][row];
    }

    /**
     * Returns the dungeon room at the given index.
     * 0 = D1 (combat), 1 = D2 (puzzle+save), 2 = D3 (boss).
     * Returns null if the index is out of bounds.
     *
     * @param index dungeon room index (0–2)
     * @return the dungeon Room, or null if out of bounds
     */
    public Room getDungeonRoom(int index) {
        if (index < 0 || index >= DUNGEON_ROOMS) return null;
        return dungeonRooms[index];
    }

    /**
     * Finds a room by its string ID. Searches overworld grid first, then dungeon rooms.
     * Returns null if no room with that ID exists.
     *
     * @param roomId the room ID to search for (e.g. "A1", "D2")
     * @return the matching Room, or null if not found
     */
    public Room getRoomById(String roomId) {
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS; r++) {
                if (overworldGrid[c][r].getRoomId().equals(roomId)) return overworldGrid[c][r];
            }
        }
        for (Room dr : dungeonRooms) {
            if (dr.getRoomId().equals(roomId)) return dr;
        }
        return null;
    }

    /** @return the active room's save point, or null if this room has no save object */
    public SavePoint getActiveSavePoint() {
        return activeRoom != null ? activeRoom.getSavePoint() : null;
    }

    /** @return the room the player is currently in */
    public Room getActiveRoom() { return activeRoom; }

    /** @return true if the player is currently inside the dungeon */
    public boolean isInDungeon() { return inDungeon; }
}
