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
    // CONSTRUCTOR
    // =========================================================

    /**
     * Creates a Room with the given ID and a default empty TileMap.
     * WorldMap calls buildDummy() immediately after construction for layout testing.
     *
     * @param roomId unique string ID (e.g. "A1", "D2")
     */
    public Room(String roomId) {
        this.roomId = roomId;
        this.tileMap = new TileMap(); // default 26×15 floor+border-wall layout

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
     * Adds a centered label showing the room ID so the team knows where they are.
     * Call from WorldMap during initialization. Replace with buildXxx() during content sprint.
     */
    public void buildDummy() {
        // TODO: create a GLabel at the center of the room (x=640, y=360) with roomId text
        // TODO: set label font and color so it is visible against the tile floor
    }

    // =========================================================
    // LIFECYCLE — called by WorldMap / RoomTransition
    // =========================================================

    /**
     * Adds all room graphics to the canvas.
     * Call once when this room becomes the active room (after transition completes).
     *
     * @param canvas the game canvas
     */
    public void addTo(GCanvas canvas) {
        // TODO: tileMap.draw(canvas)
        // TODO: draw droppedItems, objects, entities (in draw-order: drops → objects → entities)
        // TODO: add dummyLabel to canvas if not null
        initialized = true;
    }

    /**
     * Removes all room graphics from the canvas.
     * Call when this room is leaving (transition starts) or the game exits.
     *
     * @param canvas the game canvas
     */
    public void removeFrom(GCanvas canvas) {
        // TODO: tileMap.removeFrom(canvas)
        // TODO: remove droppedItems, objects, entities from canvas
        // TODO: remove dummyLabel from canvas if not null
        initialized = false;
    }

    // =========================================================
    // UPDATE — called by WorldMap each tick via GameLoop
    // =========================================================

    /**
     * Per-tick update for all room content.
     * FREEZE CONTRACT: returns immediately unless GamePlayState == PLAYING.
     *
     * @param dt    delta-time in seconds
     * @param player the active Player (for enemy AI, contact checks, exit detection)
     */
    public void update(double dt, Player player) {
        // FREEZE CHECK — this must be the first thing in this method, no exceptions.
        if (!GamePlayState.PLAYING.is()) return;

        // TODO: update all entities (enemy AI, projectile movement)
        // TODO: check player contact with each WorldObject (obj.onContact(player))
        // TODO: check dropped item overlap with player (item.onCollect(player) if overlapping)
        // TODO: check SwordSwing overlap with Grass and TrainingDummy objects (obj.onHit())
        // TODO: check player walking into PushBlock (tryPush)
        // TODO: update WorldObjects that need per-tick logic (PressureButtons, etc.)
        // TODO: if roomLock != null, call roomLock.update(entities)
        // TODO: check exit detection (player walked off an edge)
        // TODO: remove dead enemies from entities list; drop coins if applicable
    }

    // =========================================================
    // RESET — called on re-entry
    // =========================================================

    /**
     * Resets this room to its initial state for re-entry.
     * Enemies re-spawn. Items that reset on re-entry (e.g. puzzle push blocks) are restored.
     * Called by WorldMap when the player enters this room from a transition.
     *
     * NOTE: Chests that have been opened do NOT reset — their open state is tracked by SaveData.
     * NOTE: ThicketGates that have been opened do NOT reset — their open state is saved.
     * NOTE: OreNode that has been mined does NOT reset.
     */
    public void reset() {
        // TODO: clear entities list and re-add all original enemies for this room
        // TODO: reset PushBlocks to their starting positions (puzzle rooms only)
        // TODO: restore PressureButtons to unpressed state (puzzle rooms only)
        // TODO: keep Chests, ThicketGates, OreNode in their current saved state
    }

    // =========================================================
    // ROOM LOCK
    // =========================================================

    /** Attaches a RoomLock to this room. Call from WorldMap during dungeon setup. */
    public void setRoomLock(RoomLock lock) {
        this.roomLock = lock;
    }

    /** Returns true when all enemies in the entities list are dead (or list is empty). */
    public boolean isCleared() {
        // TODO: return entities.stream().allMatch(e -> !e.isAlive())  — or equivalent loop
        return entities.isEmpty();
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
