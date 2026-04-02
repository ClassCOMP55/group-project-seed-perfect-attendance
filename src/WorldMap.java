/*
Person 2: WorldMap — the entire game world: 9 overworld rooms + 3 dungeon rooms
Who RIGs it: MainApplication (or the top-level gameplay orchestrator that replaces P1GameplayPane) —
               creates one WorldMap instance, calls update(dt, player) and draw(canvas) each tick,
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
- WorldMap does NOT draw itself — it delegates draw() to the active Room (and to RoomTransition
  during a transition animation).
- WorldMap does NOT own the Player — the top-level orchestrator passes Player into update().

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
  C1 → C2  (C1 NORTH — BLOCKED until FixedLever used on DrawbridgeLever)
  C2 ↔ C3  (C2 NORTH / C3 SOUTH)
  C3 → D1  (C3 dungeon entrance — leads into Dungeon Room 1)
  D1 → D2  (D1 NORTH — locked until all enemies dead — RoomLock)
  D2 → D3  (D2 NORTH)

  NO CONNECTIONS:
  B2 ↛ C2  (Forest only reachable via C1 bridge)
  B3 ↛ C3  (Dungeon only reachable via C2)

- TRANSITION FLOW
  1. Room.update() detects player walks off an exit edge, fires exitCallback(Direction).
  2. WorldMap.triggerTransition(Direction d) is called.
  3. WorldMap finds the neighboring room in that direction.
  4. Checks: is the exit open? (e.g. bridge not yet fixed → C1 NORTH is closed → block)
  5. If open: creates a RoomTransition, sets GamePlayState = TRANSITIONING.
  6. RoomTransition.start(fromRoom, toRoom, direction, canvas) begins the sliding pan.
  7. When RoomTransition.isComplete(): WorldMap swaps activeRoom, calls reset() on the new room,
     repositions Player at the opposite edge, restores GamePlayState = PLAYING.

- ACTIVE ROOM
- WorldMap.activeRoom is the room currently being played.
- During a transition, both fromRoom and toRoom are "active" for drawing purposes only.
  After transition, only toRoom is active.

- DUMMY ROOMS (layout testing)
- On construction, ALL 12 rooms are initialized as dummy rooms (floor+walls + label).
- This means the game is immediately walkable through all 12 rooms for layout testing.
- Real room content (enemies, objects, tile details) is added in buildXxx() methods
  called from initRooms() during the implementation sprint.

- SPECIAL ROOM BEHAVIORS TO WIRE
- A1: PathBlocker placed on south exit after opening cutscene fires (Person 1's territory).
     WorldMap.closeExit("A1", SOUTH) is called by the opening sequence when ready.
- C1: NORTH exit starts CLOSED. DrawbridgeLever.onInteract() calls WorldMap.openExit("C1", NORTH).
- D1: Has a RoomLock. RoomLock.onUnlock() calls WorldMap.openExit("D1", NORTH).

- WHAT WORLDMAP DOES NOT DO
- Does not own the Player — passed in each tick.
- Does not handle HUD, pause, or dialogue — those are separate layers.
- Does not write SaveData directly — SaveManager reads activeRoom.getRoomId() when saving.
*/

import acm.graphics.GCanvas;

/**
 * The entire game world: 9 overworld rooms (3×3 grid) + 3 dungeon rooms.
 * Creates all rooms at startup as navigable dummy rooms for layout testing.
 * See PLAN OF ACTION above before implementing.
 */
public class WorldMap {

    // =========================================================
    // CONSTANTS
    // =========================================================

    /** Number of overworld columns (A, B, C). */
    public static final int COLS = 3;

    /** Number of overworld rows (1, 2, 3 from bottom). */
    public static final int ROWS = 3;

    /** Number of linear dungeon rooms. */
    public static final int DUNGEON_ROOMS = 3;

    // =========================================================
    // FIELDS
    // =========================================================

    /**
     * The 3×3 overworld grid.
     * Access: overworldGrid[col][row] where col=0→A, col=1→B, col=2→C; row=0→row1 (bottom), row=2→row3 (top).
     */
    private final Room[][] overworldGrid = new Room[COLS][ROWS];

    /**
     * The 3 linear dungeon rooms.
     * dungeonRooms[0] = D1 (combat), dungeonRooms[1] = D2 (puzzle+save), dungeonRooms[2] = D3 (boss).
     */
    private final Room[] dungeonRooms = new Room[DUNGEON_ROOMS];

    /** The room the player is currently in. */
    private Room activeRoom;

    /** True when the player is inside the dungeon (using dungeonRooms), false for overworld. */
    private boolean inDungeon;

    /** Handles the sliding pan animation between rooms. Null when no transition is in progress. */
    private RoomTransition activeTransition;

    /** The canvas — needed to add/remove room graphics during transitions. */
    private GCanvas canvas;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    /**
     * Creates the world map and initializes all 12 rooms as navigable dummy rooms.
     * Wires all exit connections from the design doc.
     * Starting room is A1 (Market).
     *
     * @param canvas the game canvas (needed for room add/remove during transitions)
     */
    public WorldMap(GCanvas canvas) {
        this.canvas = canvas;
        initRooms();
        wireExits();
        // Starting room: A1
        activeRoom = overworldGrid[0][0];
        inDungeon  = false;
    }

    // =========================================================
    // ROOM INITIALIZATION
    // =========================================================

    /**
     * Creates all 12 rooms and calls buildDummy() on each.
     * During the implementation sprint, replace each buildDummy() call here with
     * the room's real buildXxx() method once that content is ready.
     */
    private void initRooms() {
        // Overworld rooms
        overworldGrid[0][0] = new Room("A1"); overworldGrid[0][0].buildDummy(); // Market (start)
        overworldGrid[1][0] = new Room("B1"); overworldGrid[1][0].buildDummy(); // Inn
        overworldGrid[2][0] = new Room("C1"); overworldGrid[2][0].buildDummy(); // Bridge
        overworldGrid[0][1] = new Room("A2"); overworldGrid[0][1].buildDummy(); // Push Block puzzle
        overworldGrid[1][1] = new Room("B2"); overworldGrid[1][1].buildDummy(); // Ore Location
        overworldGrid[2][1] = new Room("C2"); overworldGrid[2][1].buildDummy(); // Forest
        overworldGrid[0][2] = new Room("A3"); overworldGrid[0][2].buildDummy(); // Timed Gauntlet
        overworldGrid[1][2] = new Room("B3"); overworldGrid[1][2].buildDummy(); // Riddle puzzle
        overworldGrid[2][2] = new Room("C3"); overworldGrid[2][2].buildDummy(); // Dungeon Entrance

        // Dungeon rooms
        dungeonRooms[0] = new Room("D1"); dungeonRooms[0].buildDummy(); // Combat + RoomLock
        dungeonRooms[1] = new Room("D2"); dungeonRooms[1].buildDummy(); // Puzzle + SaveCrystal
        dungeonRooms[2] = new Room("D3"); dungeonRooms[2].buildDummy(); // Boss fight

        // Wire exit callbacks so each room notifies WorldMap when the player reaches an edge
        // TODO: for each room, call room.setExitCallback(d -> this.triggerTransition(d))
    }

    /**
     * Opens all valid exits between rooms, exactly as defined in the design doc.
     * C1 NORTH starts CLOSED (bridge broken). All other valid exits start OPEN.
     */
    private void wireExits() {
        // A1 ↔ B1
        overworldGrid[0][0].setExit(Direction.RIGHT, true);
        overworldGrid[1][0].setExit(Direction.LEFT,  true);

        // A1 ↔ A2
        overworldGrid[0][0].setExit(Direction.UP,   true);
        overworldGrid[0][1].setExit(Direction.DOWN, true);

        // B1 ↔ B2
        overworldGrid[1][0].setExit(Direction.UP,   true);
        overworldGrid[1][1].setExit(Direction.DOWN, true);

        // B1 ↔ C1
        overworldGrid[1][0].setExit(Direction.RIGHT, true);
        overworldGrid[2][0].setExit(Direction.LEFT,  true);

        // A2 ↔ A3
        overworldGrid[0][1].setExit(Direction.UP,   true);
        overworldGrid[0][2].setExit(Direction.DOWN, true);

        // A2 ↔ B2
        overworldGrid[0][1].setExit(Direction.RIGHT, true);
        overworldGrid[1][1].setExit(Direction.LEFT,  true);

        // A3 ↔ B3
        overworldGrid[0][2].setExit(Direction.RIGHT, true);
        overworldGrid[1][2].setExit(Direction.LEFT,  true);

        // B2 ↔ B3
        overworldGrid[1][1].setExit(Direction.UP,   true);
        overworldGrid[1][2].setExit(Direction.DOWN, true);

        // C1 → C2: CLOSED at start (bridge broken). DrawbridgeLever calls openExit("C1", UP).
        overworldGrid[2][0].setExit(Direction.UP,   false); // CLOSED — bridge broken
        overworldGrid[2][1].setExit(Direction.DOWN, true);  // C2 south is open once C1 is fixed

        // C2 ↔ C3
        overworldGrid[2][1].setExit(Direction.UP,   true);
        overworldGrid[2][2].setExit(Direction.DOWN, true);

        // C3 → D1 (dungeon entrance — treated as a special transition, not a normal exit direction)
        // TODO: handled by a WorldObject/trigger in C3 that calls enterDungeon()

        // D1 → D2: CLOSED at start (RoomLock). RoomLock calls openExit("D1", UP) when cleared.
        dungeonRooms[0].setExit(Direction.UP, false); // CLOSED until all enemies dead
        dungeonRooms[1].setExit(Direction.DOWN, true);

        // D2 → D3
        dungeonRooms[1].setExit(Direction.UP,  true);
        dungeonRooms[2].setExit(Direction.DOWN, true);
    }

    // =========================================================
    // UPDATE / DRAW — called each tick by the top-level orchestrator
    // =========================================================

    /**
     * Per-tick update. Delegates to active room (or RoomTransition if animating).
     *
     * @param dt     delta-time in seconds
     * @param player the active Player
     */
    public void update(double dt, Player player) {
        if (activeTransition != null) {
            // TODO: activeTransition.update(dt)
            // TODO: if activeTransition.isComplete() → finishTransition()
        } else {
            // TODO: activeRoom.update(dt, player)
        }
    }

    /**
     * Draws the active room (or both rooms during a transition).
     * Called each tick after update().
     *
     * @param canvas the game canvas
     */
    public void draw(GCanvas canvas) {
        // TODO: if transition active, draw both rooms at their offset positions
        // TODO: else, activeRoom is already drawn (addTo was called when it became active)
    }

    // =========================================================
    // TRANSITION
    // =========================================================

    /**
     * Called by the active Room when the player walks off an exit edge.
     * Finds the neighboring room, validates the exit is open, starts the transition.
     *
     * @param d the direction the player exited
     */
    public void triggerTransition(Direction d) {
        // TODO: find neighborRoom based on activeRoom + direction d
        // TODO: if exit is closed, block player movement and return
        // TODO: activeTransition = new RoomTransition(); activeTransition.start(activeRoom, neighborRoom, d, canvas)
        // TODO: GamePlayState.setCurrent(GamePlayState.TRANSITIONING)
    }

    /**
     * Called when the transition animation completes.
     * Swaps the active room, resets the new room, repositions the player.
     */
    private void finishTransition() {
        // TODO: Room newRoom = activeTransition.getToRoom()
        // TODO: activeRoom.removeFrom(canvas)
        // TODO: newRoom.addTo(canvas) then newRoom.reset()
        // TODO: reposition player at the opposite edge of newRoom
        // TODO: activeRoom = newRoom
        // TODO: activeTransition = null
        // TODO: GamePlayState.setCurrent(GamePlayState.PLAYING)
    }

    // =========================================================
    // EXIT CONTROL — called by scripted events
    // =========================================================

    /**
     * Opens an exit in a specific room by room ID.
     * Used by DrawbridgeLever (opens C1 NORTH) and RoomLock (opens D1 NORTH).
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
    // ROOM LOOKUP
    // =========================================================

    /**
     * Returns the overworld room at grid position (col, row).
     * col: 0=A, 1=B, 2=C. row: 0=row1 (bottom), 1=row2, 2=row3 (top).
     */
    public Room getOverworldRoom(int col, int row) {
        if (col < 0 || col >= COLS || row < 0 || row >= ROWS) return null;
        return overworldGrid[col][row];
    }

    /**
     * Returns the dungeon room at index (0=D1, 1=D2, 2=D3).
     */
    public Room getDungeonRoom(int index) {
        if (index < 0 || index >= DUNGEON_ROOMS) return null;
        return dungeonRooms[index];
    }

    /**
     * Finds a room by its string ID. Searches overworld grid then dungeon array.
     * Returns null if no match found.
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

    /** Returns the room the player is currently in. */
    public Room getActiveRoom() { return activeRoom; }

    /** Returns true if the player is currently inside the dungeon. */
    public boolean isInDungeon() { return inDungeon; }
}
