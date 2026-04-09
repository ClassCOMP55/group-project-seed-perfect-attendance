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
import acm.graphics.GRect;
import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
     * The marker is centered near the north wall of C3.
     * // TECH DEMO: remove these constants when the real door WorldObject replaces the marker.
     * =====================
     */

    /** Left edge of the dungeon entrance trigger zone, in screen pixels. */
    private static final double DUNGEON_ENTRANCE_X = TileMap.MAP_OFFSET_X + 11 * 48; // col 11 = 544

    /** Top edge of the dungeon entrance trigger zone, in screen pixels. */
    private static final double DUNGEON_ENTRANCE_Y = 1 * 48; // row 1 = 48

    /** Width of the trigger zone in pixels (4 tiles). */
    private static final double DUNGEON_ENTRANCE_W = 4 * 48; // = 192

    /** Height of the trigger zone in pixels (2 tiles). */
    private static final double DUNGEON_ENTRANCE_H = 2 * 48; // = 96

    /** X position where the player spawns when entering D1 (center of room). */
    private static final double DUNGEON_SPAWN_X    = TileMap.MAP_OFFSET_X + ROOM_WIDTH_PX / 2.0;

    /** Y position where the player spawns when entering D1 (one tile above south edge). */
    private static final double DUNGEON_SPAWN_Y    = ROOM_HEIGHT_PX - 48;

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

    /** Left edge of the dungeon exit trigger zone (col 1, just inside the west wall of D1). */
    private static final double DUNGEON_EXIT_X = TileMap.MAP_OFFSET_X + 1 * 48; // col 1 = 64

    /** Top edge of the dungeon exit trigger zone (vertically centered in the room). */
    private static final double DUNGEON_EXIT_Y = 6 * 48; // row 6 = 288

    /** Width of the exit trigger zone (2 tiles). */
    private static final double DUNGEON_EXIT_W = 2 * 48; // = 96

    /** Height of the exit trigger zone (3 tiles). */
    private static final double DUNGEON_EXIT_H = 3 * 48; // = 144

    /** X position where the player spawns on return to C3 — offset right of the entrance marker. */
    private static final double OVERWORLD_RETURN_X = TileMap.MAP_OFFSET_X + 14 * 48; // col 14 = 688

    /** Y position where the player spawns on return to C3 — below the entrance marker. */
    private static final double OVERWORLD_RETURN_Y = 4 * 48; // row 4 = 192

    /*
     * =====================
     * End of dungeon exit constants.
     * =====================
     */

    /** Starter save crystal so save/load can be tested from the opening room immediately. */
    private static final double START_SAVE_POINT_X = TileMap.MAP_OFFSET_X + 5 * 48;
    private static final double START_SAVE_POINT_Y = 7 * 48;

    /** Debug grass patch in A1: placed in the upper-right test area near the marked sketch. */
    private static final int STARTER_GRASS_PATCH_START_COL = 15;
    private static final int STARTER_GRASS_PATCH_START_ROW = 2;
    private static final int STARTER_GRASS_PATCH_COLS = 6;
    private static final int STARTER_GRASS_PATCH_ROWS = 2;
    private static final float STARTER_GRASS_PATCH_COIN_DROP_CHANCE = 0.5f;

    /** Starter pit in A1 so live hole-fall death/respawn can be tested without leaving spawn island. */
    private static final int STARTER_HOLE_START_COL = 4;
    private static final int STARTER_HOLE_START_ROW = 3;
    private static final int STARTER_HOLE_WIDTH_TILES = 2;
    private static final int STARTER_HOLE_HEIGHT_TILES = 2;
    private static final double STARTER_HOLE_LABEL_Y_OFFSET = 18.0;

    /** Starter sign + NPC positions in A1 so room dialogue can be tested immediately from spawn. */
    private static final double START_SIGN_X = TileMap.MAP_OFFSET_X + 15 * 48;
    private static final double START_SIGN_Y = 7 * 48;
    private static final double START_NPC_X  = TileMap.MAP_OFFSET_X + 10 * 48;
    private static final double START_NPC_Y  = 7 * 48;
    private static final double START_BREAD_MERCHANT_X = TileMap.MAP_OFFSET_X + 20 * 48;
    private static final double START_BREAD_MERCHANT_Y = 7 * 48;

    /** Pickaxe chest in A1 — gives the player the tool needed for OreNode in B2. */
    private static final double PICKAXE_CHEST_X = TileMap.MAP_OFFSET_X + 8 * 48;
    private static final double PICKAXE_CHEST_Y = 5 * 48;

    /** Blacksmith NPC position in B1 (Inn). */
    private static final double BLACKSMITH_X = TileMap.MAP_OFFSET_X + 12 * 48;
    private static final double BLACKSMITH_Y = 7 * 48;

    /** OreNode position in B2 (Ore Location). */
    private static final double ORE_NODE_X = TileMap.MAP_OFFSET_X + 13 * 48;
    private static final double ORE_NODE_Y = 5 * 48;

    /** DrawbridgeLever position in C1 (Bridge room). */
    private static final double DRAWBRIDGE_LEVER_X = TileMap.MAP_OFFSET_X + 13 * 48;
    private static final double DRAWBRIDGE_LEVER_Y = 3 * 48;

    private static final String[] START_SIGN_LINES = {
        "Spawn Island Test Sign",
        "Press E near a sign or NPC to interact.",
        "Press J or Space to advance dialogue.",
        "This room is now wired through the live world map."
    };

    private static final String[] START_NPC_LINES = {
        "Welcome to the spawn island.",
        "I am a placeholder NPC living in A1 so we can test room dialogue in real rooms.",
        "If you can talk to me and read the sign, the live interaction path is working."
    };

    /** Reserved story-flag prefix used to encode exact exit states in the save file. */
    private static final String EXIT_FLAG_PREFIX = "wm_exit:";

    /**
     * Red rectangle placed near the west wall of D1 as a visible stand-in for the dungeon exit door.
     * Added to canvas when the player enters the dungeon; removed when they step on it to leave.
     *
     * // TECH DEMO: This GRect is a placeholder. Replace with a real WorldObject exit door in D1.
     * // RIG POINT: Remove dungeonExitMarker and DUNGEON_EXIT_* constants once a real door is built.
     */
    private final GRect dungeonExitMarker;

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
        overworldGrid[0][0] = new Room("A1"); overworldGrid[0][0].buildDummy(); // Market (start)
        overworldGrid[1][0] = new Room("B1"); overworldGrid[1][0].buildDummy(); // Inn
        overworldGrid[2][0] = new Room("C1"); overworldGrid[2][0].buildDummy(); // Bridge
        overworldGrid[0][1] = new Room("A2"); overworldGrid[0][1].buildDummy(); // Push Block puzzle
        overworldGrid[1][1] = new Room("B2"); overworldGrid[1][1].buildDummy(); // Ore Location
        overworldGrid[2][1] = new Room("C2"); overworldGrid[2][1].buildDummy(); // Forest
        overworldGrid[0][2] = new Room("A3"); overworldGrid[0][2].buildDummy(); // Timed Gauntlet
        overworldGrid[1][2] = new Room("B3"); overworldGrid[1][2].buildDummy(); // Riddle puzzle
        overworldGrid[2][2] = new Room("C3"); overworldGrid[2][2].buildDummy(); // Dungeon Entrance

        // --- dungeon rooms ---
        dungeonRooms[0] = new Room("D1"); dungeonRooms[0].buildDummy(); // Combat + RoomLock
        dungeonRooms[1] = new Room("D2"); dungeonRooms[1].buildDummy(); // Puzzle + SaveCrystal
        dungeonRooms[2] = new Room("D3"); dungeonRooms[2].buildDummy(); // Boss fight

        // --- convenience reference to C3 for dungeon entrance checks ---
        roomC3 = overworldGrid[2][2];

        reserveTeleportTriggerTilesForPlayers();

        populateOverworldEnemyRooms();
        populateD1();
        dungeonRooms[0].setRoomLock(
            new RoomLock(() -> openExit("D1", Direction.RIGHT)),
            Direction.RIGHT
        );

        installStarterSavePoint();
        installStarterGrassPatch();
        installStarterHolePit();
        installSpawnIslandDialogueTestObjects();
        installStarterBreadMerchant();
        installPickaxeChest();
        installBlacksmith();
        installOreNode();
        installDrawbridgeLever();

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

    /** Places an always-available save crystal in A1 for early save/load testing. */
    private void installStarterSavePoint() {
        Room startRoom = overworldGrid[0][0];
        startRoom.setSavePoint(new SavePoint(
            START_SAVE_POINT_X,
            START_SAVE_POINT_Y,
            startRoom.getRoomId(),
            SavePoint.SavePointType.SAVE_CRYSTAL,
            START_SAVE_POINT_X,
            START_SAVE_POINT_Y
        ));
    }

    /** Seeds a small 2×6 debug grass patch into A1 near the marked test area. */
    private void installStarterGrassPatch() {
        Room startRoom = overworldGrid[0][0];
        if (startRoom == null) {
            return;
        }

        int tileSize = startRoom.getTileMap().getTileSize();
        for (int row = 0; row < STARTER_GRASS_PATCH_ROWS; row++) {
            for (int col = 0; col < STARTER_GRASS_PATCH_COLS; col++) {
                double worldX = TileMap.MAP_OFFSET_X + (STARTER_GRASS_PATCH_START_COL + col) * tileSize;
                double worldY = (STARTER_GRASS_PATCH_START_ROW + row) * tileSize;

                // Let the patch regrow for QA, but cap each tile at one successful coin drop per room visit
                // so the opening room does not become a guaranteed infinite-money farm.
                startRoom.addObject(new Grass(
                    worldX,
                    worldY,
                    STARTER_GRASS_PATCH_COIN_DROP_CHANCE,
                    startRoom::addDroppedItem,
                    false
                ));
            }
        }
    }

    /** Carves a small live hole pit into A1 so hole death/respawn can be tested from the opening room. */
    private void installStarterHolePit() {
        Room startRoom = overworldGrid[0][0];
        if (startRoom == null || startRoom.getTileMap() == null) {
            return;
        }

        TileMap tileMap = startRoom.getTileMap();
        int tileSize = tileMap.getTileSize();
        for (int row = 0; row < STARTER_HOLE_HEIGHT_TILES; row++) {
            for (int col = 0; col < STARTER_HOLE_WIDTH_TILES; col++) {
                tileMap.setTileType(
                    STARTER_HOLE_START_COL + col,
                    STARTER_HOLE_START_ROW + row,
                    Tile.TileType.HOLE,
                    "assets/tile_hole.png"
                );
            }
        }

        double holeCenterX = TileMap.MAP_OFFSET_X
            + (STARTER_HOLE_START_COL + STARTER_HOLE_WIDTH_TILES / 2.0) * tileSize;
        double holeTopY = STARTER_HOLE_START_ROW * tileSize;
        startRoom.addObject(new WorldLabel(holeCenterX, holeTopY - STARTER_HOLE_LABEL_Y_OFFSET, "Hole"));
    }

    /** Seeds a simple sign + NPC into A1 so room dialogue can be exercised from spawn. */
    private void installSpawnIslandDialogueTestObjects() {
        Room startRoom = overworldGrid[0][0];
        if (startRoom == null) {
            return;
        }

        startRoom.addObject(new Sign(START_SIGN_X, START_SIGN_Y, START_SIGN_LINES, dialogue));
        startRoom.addObject(new WorldNpc(
            START_NPC_X,
            START_NPC_Y,
            "Spawn Island Villager",
            START_NPC_LINES,
            dialogue
        ));
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
            "Bread Merchant",
            shopMenu
        ));
    }

    /** Places a chest in A1 containing the Pickaxe needed to mine OreNode in B2. */
    private void installPickaxeChest() {
        Room a1 = overworldGrid[0][0];
        if (a1 == null) return;
        Chest pickaxeChest = new Chest(
            PICKAXE_CHEST_X, PICKAXE_CHEST_Y,
            "chest_pickaxe_a1", OreNode.PICKAXE_ID, false
        );
        pickaxeChest.setDialogue(dialogue);
        a1.addObject(pickaxeChest);
    }

    /** Places the Blacksmith NPC in B1 who crafts FixedLever from Ore + BrokenLever. */
    private void installBlacksmith() {
        Room b1 = overworldGrid[1][0];
        if (b1 == null) return;
        b1.addObject(new Blacksmith(BLACKSMITH_X, BLACKSMITH_Y, dialogue));
    }

    /** Places the OreNode in B2 that gives Ore + BrokenLever when mined with the Pickaxe. */
    private void installOreNode() {
        Room b2 = overworldGrid[1][1];
        if (b2 == null) return;
        OreNode oreNode = new OreNode(ORE_NODE_X, ORE_NODE_Y);
        oreNode.setDialogue(dialogue);
        if (hasCollectedItem(OreNode.SAVE_FLAG_ID)) {
            oreNode.forceMined();
        }
        b2.addObject(oreNode);
    }

    /** Places the DrawbridgeLever in C1 that lowers the bridge when the player has FixedLever. */
    private void installDrawbridgeLever() {
        Room c1 = overworldGrid[2][0];
        if (c1 == null) return;
        DrawbridgeLever lever = new DrawbridgeLever(
            DRAWBRIDGE_LEVER_X, DRAWBRIDGE_LEVER_Y,
            c1.getTileMap(), this
        );
        lever.setDialogue(dialogue);
        c1.addObject(lever);
    }

    /** Seeds live overworld enemy encounters into the currently-walkable dummy rooms. */
    private void populateOverworldEnemyRooms() {
        populateB2();
        populateC2();
    }

    /** Places an armored bruiser in the ore route so that room now has a live encounter. */
    private void populateB2() {
        Room b2 = overworldGrid[1][1];
        b2.addRespawningEntity(() -> new ArmorEnemy(420, 300, b2.getTileMap()));
    }

    /** Gives the forest a mixed encounter: one armored chaser and one ranged shooter. */
    private void populateC2() {
        Room c2 = overworldGrid[2][1];
        c2.addRespawningEntity(() -> new ArmorEnemy(360, 252, c2.getTileMap()));
        c2.addRespawningEntity(() -> new RangedEnemy(920, 432, c2.getTileMap()));
    }

    /**
     * Spawns a mixed starter wave in Dungeon Room 1.
     * Called from initRooms() after D1 is created.
     */
    private void populateD1() {
        Room d1 = dungeonRooms[0];

        // Register D1's enemy wave so Room.reset() can rebuild it on every re-entry.
        d1.addRespawningEntity(() -> new MeleeEnemy(300, 250, d1.getTileMap()));
        d1.addRespawningEntity(() -> new RangedEnemy(940, 260, d1.getTileMap()));
        d1.addRespawningEntity(() -> new MeleeEnemy(640, 500, d1.getTileMap()));
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

        // --- D2 ↔ D3 ---
        dungeonRooms[1].setExit(Direction.RIGHT, true);
        dungeonRooms[2].setExit(Direction.LEFT,  true);
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
    public void hideSpecialMarkers() {
        if (canvas == null) return;
        canvas.remove(dungeonEntranceMarker);
        canvas.remove(dungeonExitMarker);
    }

    /** Rebuilds marker visibility after room swaps, loads, and screen show/hide. */
    private void syncSpecialMarkersToActiveRoom() {
        hideSpecialMarkers();
        if (canvas == null) return;
        if (activeRoom == roomC3) {
            canvas.add(dungeonEntranceMarker);
        }
        if (activeRoom == dungeonRooms[0]) {
            canvas.add(dungeonExitMarker);
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
                return;
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
