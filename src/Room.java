/*
Person 2: Room — one full screen of the game world
Who RIGs it: WorldMap — creates all Room instances, calls setActiveRoom(), and routes update()/draw() to
               the active room each tick via GameLoop's Updatable.
             RoomTransition — reads fromRoom and toRoom during a sliding pan; calls draw() on both.
             SaveManager/SavePoint — reads roomId to write into SaveData on save;
               on load, WorldMap uses roomId to find which room to restore.

Extends: nothing
Contains: TileMap, List<Entity>, List<WorldObject>, List<Item> (drops), Map<Direction,Boolean> exits,
          optional RoomLock, optional ThicketGate(s)

===============
PLAN OF ACTION
===============

- CLASS ROLE
- Room is one full static screen — 26 cols × 15 rows of tiles (1248×720px, centered in 1280px window).
- Room owns everything visible and interactive on that screen: tiles, enemies, world objects, dropped items.
- Room does NOT own the Player — Player is passed in from WorldMap / the top-level orchestrator.
- Room does NOT trigger transitions itself — it signals that an exit was reached; WorldMap handles transition.

- THE FREEZE CONTRACT (GamePlayState)
- update(dt) checks GamePlayState.getCurrent() at the very top:
    - PLAYING      → run all updates (entities, objects, items, roomLock check)
    - PAUSED       → skip everything, return immediately
    - DIALOGUE     → skip everything, return immediately
    - TRANSITIONING → skip everything, return immediately (RoomTransition handles its own animation)
    - CUTSCENE     → skip everything, return immediately
- This is the single enforcement point for the game freeze. Nothing below update() needs to check state.

- FIELDS
- String roomId                         — unique ID (e.g. "A1", "B2", "D1" for dungeon rooms)
- TileMap tileMap                       — tile grid for collision and drawing
- List<Entity> entities                 — enemies (and any other moving things) in this room
- List<WorldObject> objects             — all static interactables (Grass, Signs, Chests, etc.)
- List<Item> droppedItems               — coins/items lying on the ground waiting for pickup
- Map<Direction, Boolean> exits         — which edges have open exits (true = passable)
- RoomLock roomLock                     — null unless this room needs an enemy-clear lock (Dungeon Room 1 only)
- List<ThicketGate> gates               — puzzle-area gates in this room (A2, A3, B3)
- boolean initialized                   — true once addTo(canvas) has been called for this room

- ROOM CONNECTIONS (exits map)
- Exits are set by WorldMap when the room is constructed. Room does not decide its own connections.
- An exit being "open" (true) means the player can walk off that edge to trigger a transition.
- Example: A1's north exit is open (leads to A2); A1's south exit starts open but is closed by
  PathBlocker after the opening cutscene fires.
- Room.setExit(Direction, boolean) is called by WorldMap or scripted events (e.g. bridge fixed).

- ENTITY MANAGEMENT
- entities holds Enemy (and subclass) instances. Player is NOT in this list.
- On room entry (WorldMap calls reset() when re-entering a room): all enemies are re-added to the list.
- On room exit: entities list is cleared for re-entry later.
- isCleared() returns true when all entities in the list are dead — used by RoomLock.

- PLAYER INTERACTION ROUTING (called each tick during PLAYING)
- Contact checks: for each WorldObject in objects, call obj.onContact(player) each tick.
    ThicketGate auto-opens on contact. Coin drops auto-collect on contact.
- Interact key: Room registers the J key via InputHandler.onPress(). When J is pressed,
    Room finds the nearest WorldObject whose hitbox overlaps the player and calls onInteract(player).
- Sword hits: Room checks SwordSwing hitbox against all Grass and TrainingDummy objects each tick.
    Calls obj.onHit() when overlap is detected.
- Push: Room detects player walking into PushBlock (player hitbox + direction) and calls tryPush().

- EXIT DETECTION
- Each tick, Room checks if the player has walked past an exit edge:
    player.x < MAP_OFFSET_X                  → exiting WEST
    player.x > MAP_OFFSET_X + 26 * 48        → exiting EAST
    player.y < 0                              → exiting NORTH
    player.y > 15 * 48                        → exiting SOUTH
- If an exit in that direction is open, Room calls exitCallback (set by WorldMap) with the direction.
- If the exit is closed (or does not exist), player movement is blocked at the edge wall.

- DRAW ORDER (back to front)
- 1. tileMap.draw(canvas)       — floor, walls, holes, bridges
- 2. droppedItems drawn         — coins and drops on the ground
- 3. objects drawn              — Grass, Chests, Signs, etc. (WorldObject.draw)
- 4. entities drawn             — enemies, projectiles (Entity.draw)
- 5. Player drawn               — on top of all world content
- (HUD and PauseModal are drawn above everything by their own layer — Room does not touch them)

- DUMMY ROOM (for layout testing before real content is built)
- When WorldMap initializes rooms, each room is a "dummy": just fillBorderWalls TileMap + a GLabel
  in the center showing the roomId. This lets the team walk through all 12 rooms immediately.
- Real content (enemies, objects, specific tile layouts) is added to each room's private
  buildXxx() method during the implementation sprint.
*/

import acm.graphics.GCanvas;
import acm.graphics.GLabel;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

// =========================================================
// EXIT DETECTION — pixel thresholds (used in update)
// =========================================================
// These values are derived from the tile grid constants in TileMap.
// If tile size or room dimensions ever change, update them there — not here.
//
// Room pixel span:
//   X: MAP_OFFSET_X (16) to MAP_OFFSET_X + 26 * 48 = 1264
//   Y: 0 to 15 * 48 = 720
//
// A player (center point) is considered to have exited when their center
// crosses the boundary. Clamp target pushes them just inside the edge
// so they are not re-triggered on the next tick.


/**
 * One full screen of the game world (26×15 tiles, 1248×720px).
 * Holds a TileMap, enemies, WorldObjects, dropped items, and exit flags.
 * See PLAN OF ACTION above before implementing.
 */
public class Room {

    // =========================================================
    // FIELDS
    // =========================================================

    /** Unique identifier for this room (e.g. "A1", "B2", "D1"). */
    private final String roomId;

    /** Tile grid for collision queries and drawing the floor/walls. */
    private TileMap tileMap;

    /** Enemies and other moving entities in this room. Player is NOT in this list. */
    private final List<Entity> entities = new ArrayList<>();

    /** Static interactive objects in this room (Grass, Signs, Chests, etc.). */
    private final List<WorldObject> objects = new ArrayList<>();

    /** Items lying on the ground waiting for the player to walk over them. */
    private final List<Item> droppedItems = new ArrayList<>();

    /**
     * Which edges of this room have open exits.
     * Set by WorldMap when the room is constructed. Room does not decide its own connections.
     */
    private final Map<Direction, Boolean> exits = new EnumMap<>(Direction.class);

    /**
     * Optional enemy-clear lock. Null for most rooms.
     * Only Dungeon Room 1 has one — prevents the north exit until all enemies are dead.
     */
    private RoomLock roomLock;

    /**
     * Puzzle-area gates in this room (used in A2, A3, B3).
     * ThicketGate will extend WorldObject after the refactor, but stored separately for now
     * so the contact-check loop can be explicit.
     */
    private final List<ThicketGate> gates = new ArrayList<>();

    /**
     * Called by Room when the player walks off an open exit edge.
     * WorldMap sets this callback; Room fires it with the exit Direction.
     * This keeps Room decoupled from WorldMap.
     */
    private Consumer<Direction> exitCallback;

    /** Center label shown in dummy rooms for layout testing. Null in fully-built rooms. */
    private GLabel dummyLabel;

    /** True once addTo(canvas) has been called. Prevents double-adding sprites. */
    private boolean initialized;

    // =========================================================
    // EXIT BOUNDARY CONSTANTS
    // =========================================================

    /*
     * =====================
     * Exit trigger thresholds — adjust these if tile size or room dimensions change.
     * =====================
     */

    /** Left edge of the room in pixels. Player exits LEFT when their center crosses below this. */
    private static final double EXIT_LEFT_EDGE  = TileMap.MAP_OFFSET_X;

    /** Right edge of the room in pixels. Player exits RIGHT when their center crosses above this. */
    private static final double EXIT_RIGHT_EDGE = TileMap.MAP_OFFSET_X + 26 * 48; // = 1264

    /** Top edge of the room in pixels. Player exits UP when their center crosses above this. */
    private static final double EXIT_TOP_EDGE   = 0;

    /** Bottom edge of the room in pixels. Player exits DOWN when their center crosses below this. */
    private static final double EXIT_BOTTOM_EDGE = 15 * 48; // = 720

    /*
     * =====================
     * End of adjustable exit values.
     * =====================
     */

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    /**
     * Creates a Room with the given ID and an empty placeholder TileMap.
     * WorldMap calls buildDummy() immediately after construction for layout testing.
     *
     * @param roomId unique string ID (e.g. "A1", "D2")
     */
    public Room(String roomId) {
        this.roomId = roomId;
        this.tileMap = new TileMap(); // temporary market layout; replaced by buildDummy() or buildXxx()

        // All exits closed by default — WorldMap opens the correct ones after construction.
        for (Direction d : Direction.values()) {
            exits.put(d, false);
        }
    }

    // =========================================================
    // DUMMY ROOM SETUP — for layout testing
    // =========================================================

    /**
     * Sets up this room as a navigable placeholder for layout testing.
     * Uses an all-floor tile layout (no border walls) so exit detection fires correctly.
     * Adds a centered label with the room ID for navigation reference.
     *
     * // TECH DEMO: This is a placeholder. Replace this call in WorldMap.initRooms() with
     * //            a room-specific buildXxx() method when real content is ready.
     * // RIG POINT: Replace all-floor TileMap with new TileMap(roomId) for real tile layouts.
     */
    public void buildDummy() {
        // --- all-floor tile map (no border walls) ---
        // TECH DEMO: border walls are intentionally absent so the player can reach exit edges.
        // RIG POINT: swap to new TileMap(roomId) once real room layouts are designed.
        this.tileMap = TileMap.createDummyAllFloor();

        // --- room ID label centered in the room ---
        // TECH DEMO: visible debug label; remove when real room art is in place.
        dummyLabel = new GLabel(roomId);
        dummyLabel.setFont("SansSerif-BOLD-36");
        dummyLabel.setColor(Color.WHITE);
        // Approximate center — GLabel width is not known until rendered on canvas,
        // so we offset from the room's pixel center using a rough character-width estimate.
        double approxLabelWidth = roomId.length() * 22.0;
        double centerX = EXIT_LEFT_EDGE + (26 * 48) / 2.0 - approxLabelWidth / 2.0;
        double centerY = EXIT_BOTTOM_EDGE / 2.0 + 12; // +12 to account for label ascent
        dummyLabel.setLocation(centerX, centerY);
    }

    // =========================================================
    // LIFECYCLE — called by WorldMap / RoomTransition
    // =========================================================

    /**
     * Adds all room graphics to the canvas in back-to-front draw order.
     * Safe to call repeatedly — returns immediately if already initialized.
     * Called by WorldMap / RoomTransition when this room becomes visible.
     *
     * Draw order: tiles → dropped items → world objects → entities → room label
     *
     * @param canvas the game canvas
     */
    public void addTo(GCanvas canvas) {
        if (initialized) return; // already on canvas; prevent double-adding

        // --- tiles (drawn first, at the back) ---
        tileMap.draw(canvas);

        // --- dropped items (coins, loot) ---
        // RIG POINT: Item.draw() must be implemented before dropped items appear.
        for (Item item : droppedItems) {
            item.draw(canvas);
        }

        // --- world objects (signs, chests, pushblocks, etc.) ---
        for (WorldObject obj : objects) {
            obj.draw(canvas);
        }

        // --- entities (enemies, projectiles) ---
        // RIG POINT: Entity.draw() is already implemented; add enemies to the entities list
        //            in each room's buildXxx() method.
        for (Entity entity : entities) {
            entity.draw(canvas);
        }

        // --- room ID label (tech-demo only) ---
        // TECH DEMO: visible room ID label; remove when real room art replaces buildDummy().
        if (dummyLabel != null) {
            canvas.add(dummyLabel);
        }

        initialized = true;
    }

    /**
     * Removes all room graphics from the canvas.
     * Called when this room is transitioning out or the game is exiting.
     * Sets initialized = false so addTo() can be called again on re-entry.
     *
     * @param canvas the game canvas
     */
    public void removeFrom(GCanvas canvas) {
        if (!initialized) return; // nothing to remove

        // --- tiles ---
        tileMap.removeFrom(canvas);

        // --- dropped items ---
        for (Item item : droppedItems) {
            item.removeFrom(canvas);
        }

        // --- world objects ---
        for (WorldObject obj : objects) {
            obj.removeFrom(canvas);
        }

        // --- entities ---
        for (Entity entity : entities) {
            entity.removeSpriteFromCanvas(canvas);
        }

        // --- room ID label ---
        if (dummyLabel != null) {
            canvas.remove(dummyLabel);
        }

        initialized = false;
    }

    // =========================================================
    // UPDATE — called by WorldMap each tick via GameLoop
    // =========================================================

    /**
     * Per-tick update for all room content: enemy AI, player contacts, and exit detection.
     * FREEZE CONTRACT: returns immediately unless GamePlayState == PLAYING.
     * Player movement/input is handled by GameplayPane before this is called.
     *
     * @param dt     delta-time in seconds (e.g. 0.016 for ~60fps)
     * @param player the active Player (position already updated for this tick)
     */
    public void update(double dt, Player player) {
        // --- freeze check ---
        // This must be the very first line — no game logic runs unless the state is PLAYING.
        // PAUSED, DIALOGUE, CUTSCENE, and TRANSITIONING all return here immediately.
        if (!GamePlayState.PLAYING.is()) return;

        // --- entity updates (enemy AI, projectile movement) ---
        // RIG POINT: call entity.update(dt) for each enemy once enemy AI is implemented.
        for (Entity entity : entities) {
            entity.update(dt);
        }

        // --- world object per-tick logic (pressure buttons, animated objects) ---
        // RIG POINT: PressureButton and similar objects need update(dt) called here.
        for (WorldObject obj : objects) {
            obj.update(dt);
        }

        // --- player contact with world objects (e.g. ThicketGate auto-opens on touch) ---
        // RIG POINT: onContact() for ThicketGate, Coin drops, and similar touch-triggered objects.
        for (WorldObject obj : objects) {
            if (player.getHitbox().overlaps(obj.getHitbox())) {
                obj.onContact(player);
            }
        }

        // --- dropped item pickup (player walks over a ground drop) ---
        // RIG POINT: implement Item.onCollect() and Hitbox overlap for item pickup.
        // Skipped for tech demo — no items are in any room yet.

        // --- sword hit detection (Grass cut, TrainingDummy react) ---
        // RIG POINT: check player.getActiveSwing() hitbox against Grass and TrainingDummy objects.
        // Skipped for tech demo — no sword-reactive objects in dummy rooms.

        // --- push block detection ---
        // RIG POINT: detect player walking into a PushBlock and call tryPush(direction).
        // Skipped for tech demo — no puzzle rooms have content yet.

        // --- room lock check ---
        // RIG POINT: roomLock.update(entities) checks if all enemies are dead → opens exit.
        if (roomLock != null) {
            roomLock.update(entities);
        }

        // --- remove dead enemies, trigger coin drops ---
        // RIG POINT: iterate entities with an Iterator, remove dead ones, call dropCoins().
        // Skipped for tech demo — no enemies exist in dummy rooms.

        // --- exit detection ---
        // Uses if / else if to guarantee only ONE exit fires per tick, even if the player
        // is at a corner moving diagonally. The second direction is naturally caught on the
        // next tick after the first transition completes (TRANSITIONING freeze blocks re-entry).
        detectAndFireExit(player);
    }

    /**
     * Checks if the player's center has crossed any exit edge.
     * If the exit in that direction is OPEN, fires exitCallback with the direction.
     * If the exit is CLOSED, clamps the player back inside the room boundary.
     *
     * Uses if / else if — only one exit can fire per tick (diagonal corner safety).
     *
     * @param player the active Player
     */
    private void detectAndFireExit(Player player) {
        double px = player.getX();
        double py = player.getY();

        if (px < EXIT_LEFT_EDGE) {
            // --- left (west) edge ---
            if (exits.getOrDefault(Direction.LEFT, false)) {
                if (exitCallback != null) exitCallback.accept(Direction.LEFT);
            } else {
                player.setPosition(EXIT_LEFT_EDGE + 1, py); // clamp back inside
            }
        } else if (px > EXIT_RIGHT_EDGE) {
            // --- right (east) edge ---
            if (exits.getOrDefault(Direction.RIGHT, false)) {
                if (exitCallback != null) exitCallback.accept(Direction.RIGHT);
            } else {
                player.setPosition(EXIT_RIGHT_EDGE - 1, py);
            }
        } else if (py < EXIT_TOP_EDGE) {
            // --- top (north) edge ---
            if (exits.getOrDefault(Direction.UP, false)) {
                if (exitCallback != null) exitCallback.accept(Direction.UP);
            } else {
                player.setPosition(px, EXIT_TOP_EDGE + 1);
            }
        } else if (py > EXIT_BOTTOM_EDGE) {
            // --- bottom (south) edge ---
            if (exits.getOrDefault(Direction.DOWN, false)) {
                if (exitCallback != null) exitCallback.accept(Direction.DOWN);
            } else {
                player.setPosition(px, EXIT_BOTTOM_EDGE - 1);
            }
        }
    }

    // =========================================================
    // RESET — called on re-entry
    // =========================================================

    /**
     * Resets this room to its initial state when the player enters or re-enters it.
     * Called by WorldMap after a transition completes (both first entry and re-entry).
     *
     * Rules:
     *   - Enemies re-spawn (cleared and re-added from the room's original enemy list).
     *   - Puzzle objects (PushBlocks, PressureButtons) restore to starting positions.
     *   - Persistent objects (Chests, ThicketGates, OreNode) keep their saved state.
     *
     * NOTE: Chests that have been opened do NOT reset — state is tracked by SaveData.
     * NOTE: ThicketGates that have been opened do NOT reset — state is tracked by SaveData.
     * NOTE: OreNode that has been mined does NOT reset — state is tracked by SaveData.
     */
    public void reset() {
        // --- enemies ---
        // RIG POINT: clear entities list and re-add all original enemies for this room.
        //            Each room's buildXxx() method will keep an "original enemy list" to copy from.
        //            Example: entities.clear(); entities.addAll(originalEnemies);
        // Skipped for tech demo — no enemies exist in dummy rooms.

        // --- push blocks (puzzle rooms only) ---
        // RIG POINT: iterate objects, find PushBlock instances, call pushBlock.resetPosition().
        // Skipped for tech demo — no puzzle rooms have content yet.

        // --- pressure buttons (puzzle rooms only) ---
        // RIG POINT: iterate objects, find PressureButton instances, call pressureButton.reset().
        // Skipped for tech demo — no pressure buttons exist yet.
    }

    // =========================================================
    // ROOM LOCK
    // =========================================================

    /** Attaches a RoomLock to this room. Call from WorldMap during dungeon setup. */
    public void setRoomLock(RoomLock lock) {
        this.roomLock = lock;
    }

    /**
     * Returns true when all enemies in this room are dead (or when there are no enemies).
     * Used by RoomLock to determine when the north exit should unlock.
     *
     * @return true if every entity in the list is dead, or if the list is empty
     */
    public boolean isCleared() {
        for (Entity entity : entities) {
            if (entity.isAlive()) return false;
        }
        return true; // empty list also returns true (no enemies = already cleared)
    }

    // =========================================================
    // EXIT MANAGEMENT
    // =========================================================

    /**
     * Opens or closes an exit in a given direction.
     * Called by WorldMap during construction and by scripted events (e.g. bridge fixed).
     *
     * @param d    the direction of the exit
     * @param open true to open, false to close
     */
    public void setExit(Direction d, boolean open) {
        exits.put(d, open);
    }

    /** Returns true if the exit in the given direction is open. */
    public boolean getExitAt(Direction d) {
        return exits.getOrDefault(d, false);
    }

    /**
     * Sets the callback WorldMap uses to hear when the player reaches an exit.
     * WorldMap sets this once; Room fires it when exit detection triggers.
     *
     * @param callback receives the Direction the player exited toward
     */
    public void setExitCallback(Consumer<Direction> callback) {
        this.exitCallback = callback;
    }

    // =========================================================
    // ROOM TRANSITION — pan support
    // =========================================================

    /**
     * Shifts every visual element in this room by (panX, panY) pixels on the canvas.
     * Internal positions (tile grid, entity coordinates, object positions) are NOT changed.
     * Called by RoomTransition each animation tick to slide both rooms across the screen.
     *
     * Elements panned: tiles, world objects, entities, the room ID label (if present).
     *
     * @param panX horizontal pixels to shift (negative = left, positive = right)
     * @param panY vertical pixels to shift (negative = up, positive = down)
     */
    public void panAll(double panX, double panY) {
        // --- tiles ---
        tileMap.panAll(panX, panY);

        // --- world objects ---
        for (WorldObject obj : objects) {
            obj.panVisual(panX, panY);
        }

        // --- entities (enemies, projectiles) ---
        for (Entity entity : entities) {
            entity.panVisual(panX, panY);
        }

        // --- dropped items ---
        // RIG POINT: call item.panVisual(panX, panY) once Item.panVisual() is implemented.
        // Skipped for tech demo — no items are in any room yet.

        // --- tech-demo room ID label ---
        if (dummyLabel != null) {
            dummyLabel.move(panX, panY); // GLabel inherits move() from GObject
        }

        // --- ThicketGates ---
        // RIG POINT: call gate.panVisual(panX, panY) for each gate once ThicketGate
        //            extends WorldObject and panVisual() is available on it.
        // Skipped for tech demo — no rooms have ThicketGates in the dummy layout.
    }

    // =========================================================
    // CONTENT ADDERS — called during room construction
    // =========================================================

    /** Adds an entity (enemy) to this room's entity list. */
    public void addEntity(Entity e)       { entities.add(e); }

    /** Adds a WorldObject (interactable) to this room. */
    public void addObject(WorldObject o)  { objects.add(o); }

    /** Adds a ThicketGate to this room's gate list. */
    public void addGate(ThicketGate gate) { gates.add(gate); }

    /** Adds a dropped Item (coin, etc.) to this room's ground-item list. */
    public void addDroppedItem(Item item) { droppedItems.add(item); }

    // =========================================================
    // GETTERS
    // =========================================================

    public String           getRoomId()      { return roomId; }
    public TileMap          getTileMap()     { return tileMap; }
    public List<Entity>     getEntities()    { return entities; }
    public List<WorldObject> getObjects()    { return objects; }
    public List<Item>       getDroppedItems(){ return droppedItems; }
    public RoomLock         getRoomLock()    { return roomLock; }
    public boolean          isInitialized()  { return initialized; }
}
