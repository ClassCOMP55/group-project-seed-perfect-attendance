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
- Interact key: GameplayPane routes the E key to Room.tryInteract(player).
    Room picks the nearest interactable WorldObject that is overlapping or in front of the player
    and calls onInteract(player).
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
import acm.graphics.GImage;
import acm.graphics.GLabel;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

    /** Factories that recreate this room's resettable entity population on re-entry. */
    private final List<Supplier<Entity>> respawnEntitySuppliers = new ArrayList<>();

    /** Static interactive objects in this room (Grass, Signs, Chests, etc.). */
    private final List<WorldObject> objects = new ArrayList<>();

    /** Items lying on the ground waiting for the player to walk over them. */
    private final List<Item> droppedItems = new ArrayList<>();

    /** Active projectiles fired inside this room. Cleared on room reset / exit. */
    private final List<Projectile> projectiles = new ArrayList<>();

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
    /** Exit direction temporarily blocked while {@link #roomLock} remains locked. */
    private Direction roomLockDirection;

    /** Called once when all PressureButtons in this room become simultaneously pressed. */
    private Runnable puzzleSolvedCallback;
    /** True after puzzleSolvedCallback has fired so it only fires once per session entry. */
    private boolean puzzleSolved = false;

    /**
     * Puzzle-area gates in this room (used in A2, A3, B3).
     * ThicketGate will extend WorldObject after the refactor, but stored separately for now
     * so the contact-check loop can be explicit.
     */
    private final List<ThicketGate> gates = new ArrayList<>();

    /** Optional room-level save point. Null for rooms without a save object. */
    private SavePoint savePoint;

    /**
     * Called by Room when the player walks off an open exit edge.
     * WorldMap sets this callback; Room fires it with the exit Direction.
     * This keeps Room decoupled from WorldMap.
     */
    private Consumer<Direction> exitCallback;

    /** Center label shown in dummy rooms for layout testing. Null in fully-built rooms. */
    private GLabel dummyLabel;

    /** Background image drawn behind all tiles. Null for dummy rooms. */
    private GImage backgroundImage;
    /** Optional room foreground image drawn above world actors (e.g. canopy shadows). */
    private GImage foregroundImage;
    /** Whether the TileMap visuals should be drawn for this room. */
    private boolean drawTileMap = true;

    /** True once addTo(canvas) has been called. Prevents double-adding sprites. */
    private boolean initialized;

    /** Canvas reference stored by addTo() — needed to remove dead-entity sprites mid-tick. */
    private GCanvas canvas;

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

    /** Max center-to-center distance allowed for generic sign / NPC interaction. */
    private static final double INTERACT_RADIUS_PX = 96.0;

    /** Minimum forward-facing alignment required when not already overlapping the object. */
    private static final double INTERACT_ALIGNMENT_MIN = 0.35;

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
        this.drawTileMap = true;
        this.backgroundImage = null;

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

    /** Builds the A1 (Market) room with collision map matching the background art. */
    public void buildA1() {
        this.tileMap = TileMap.createA1();
        this.drawTileMap = false;
        setBackgroundImage(
            "assets/visuals/overworld rooms/a1_closed.png",
            "assets/visuals/overworld rooms/A1.png",
            "assets/visuals/overworld rooms/a1.png"
        );
        dummyLabel = null;
    }

    /** Builds the B1 (Inn) room with collision map matching the background art. */
    public void buildB1() {
        this.tileMap = TileMap.createB1();
        this.drawTileMap = false;
        setBackgroundImage(
            "assets/visuals/overworld rooms/B1.png",
            "assets/visuals/overworld rooms/b1.png"
        );
        dummyLabel = null;
    }

    public void buildA2() {
        this.tileMap = TileMap.createA2();
        this.drawTileMap = false;
        setBackgroundImage(
            "assets/visuals/overworld rooms/a2_blocked.png",
            "assets/visuals/overworld rooms/A2.png",
            "assets/visuals/overworld rooms/a2_open.png"
        );
        setForegroundImage("assets/visuals/overworld rooms/ow_shadows.png");
        dummyLabel = null;
    }

    public void buildA3() {
        this.tileMap = TileMap.createA3();
        this.drawTileMap = false;
        setBackgroundImage(
            "assets/visuals/overworld rooms/a3_blocked.png",
            "assets/visuals/overworld rooms/A3.png",
            "assets/visuals/overworld rooms/a3_open.png"
        );
        setForegroundImage("assets/visuals/overworld rooms/ow_shadows.png");
        dummyLabel = null;
    }

    public void buildB2() {
        this.tileMap = TileMap.createB2();
        this.drawTileMap = false;
        setBackgroundImage(
            "assets/visuals/overworld rooms/B2.png",
            "assets/visuals/overworld rooms/b2.png"
        );
        setForegroundImage("assets/visuals/overworld rooms/ow_shadows.png");
        dummyLabel = null;
    }

    public void buildB3() {
        this.tileMap = TileMap.createB3();
        this.drawTileMap = false;
        setBackgroundImage(
            "assets/visuals/overworld rooms/b3_blocked.png",
            "assets/visuals/overworld rooms/B3.png",
            "assets/visuals/overworld rooms/b3_open.png"
        );
        setForegroundImage("assets/visuals/overworld rooms/ow_shadows.png");
        dummyLabel = null;
    }

    public void buildC1() {
        this.tileMap = TileMap.createC1();
        this.drawTileMap = false;
        setBackgroundImage(
            "assets/visuals/overworld rooms/c1_bridge_down.png",
            "assets/visuals/overworld rooms/C1.png",
            "assets/visuals/overworld rooms/c1.png"
        );
        setForegroundImage("assets/visuals/overworld rooms/ow_shadows.png");
        dummyLabel = null;
    }

    public void buildC2() {
        this.tileMap = TileMap.createC2();
        this.drawTileMap = false;
        setBackgroundImage(
            "assets/visuals/overworld rooms/C2.png",
            "assets/visuals/overworld rooms/c2.png"
        );
        setForegroundImage("assets/visuals/overworld rooms/ow_shadows.png");
        dummyLabel = null;
    }

    public void buildC3() {
        this.tileMap = TileMap.createC3();
        this.drawTileMap = false;
        setBackgroundImage(
            "assets/visuals/overworld rooms/C3.png",
            "assets/visuals/overworld rooms/c3.png"
        );
        setForegroundImage("assets/visuals/overworld rooms/ow_shadows.png");
        dummyLabel = null;
    }

    public void buildD1() {
        this.tileMap = TileMap.createD1();
        this.drawTileMap = false;
        setBackgroundImage("assets/visuals/overworld rooms/d1.png");
        dummyLabel = null;
    }

    public void buildD2() {
        this.tileMap = TileMap.createD2();
        this.drawTileMap = false;
        setBackgroundImage("assets/visuals/overworld rooms/d2.png");
        dummyLabel = null;
    }

    public void buildD3() {
        this.tileMap = TileMap.createD3();
        this.drawTileMap = false;
        setBackgroundImage("assets/visuals/overworld rooms/d3.png");
        dummyLabel = null;
    }

    /**
     * Attempts to load the first existing room background from a candidate list.
     * If none exist, keeps gameplay running by falling back to TileMap rendering.
     */
    private void setBackgroundImage(String... candidatePaths) {
        backgroundImage = null;
        for (String path : candidatePaths) {
            try {
                GImage candidate = new GImage(path);
                candidate.setSize(1280, 720);
                candidate.setLocation(0, 0);
                backgroundImage = candidate;
                return;
            } catch (RuntimeException ignored) {
                // Keep trying candidate paths until one loads.
            }
        }

        // Avoid hard-crashing if assets are missing in this environment.
        drawTileMap = true;
    }

    /** Attempts to load a full-screen foreground overlay for canopy/shadow coverage. */
    private void setForegroundImage(String... candidatePaths) {
        foregroundImage = null;
        for (String path : candidatePaths) {
            try {
                GImage candidate = new GImage(path);
                candidate.setSize(1280, 720);
                candidate.setLocation(0, 0);
                foregroundImage = candidate;
                return;
            } catch (RuntimeException ignored) {
                // Keep trying candidate paths until one loads.
            }
        }
    }

    /**
     * Swaps the room background to a new asset set.
     * Used by one-time world progression such as clearing a thicket.
     */
    public void replaceBackgroundImage(String... candidatePaths) {
        GImage previous = backgroundImage;
        setBackgroundImage(candidatePaths);

        if (!initialized || canvas == null) {
            return;
        }

        if (previous != null) {
            canvas.remove(previous);
        }
        if (backgroundImage != null) {
            backgroundImage.setLocation(0, 0);
            canvas.add(backgroundImage);
            backgroundImage.sendToBack();
        }
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
        this.canvas = canvas;

        // --- reset tile positions before drawing ---
        // After a room-transition animation, every tile sprite may have been panned off-screen
        // by RoomTransition.panAll(). Calling resetAllPositions() snaps each sprite back to its
        // canonical grid coordinate before it is re-added to the canvas.
        // Without this step, re-entering a room shows a blank white screen because all tiles
        // are drawn outside the visible area.
        //
        // RIG POINT: When real room layouts replace buildDummy(), this reset is still required.
        //            Entity.panVisual() and WorldObject.panVisual() accumulate the same kind of
        //            offset — add parallel reset calls here once those lists are populated.
        // --- background image (drawn first, behind tiles) ---
        if (backgroundImage != null) {
            backgroundImage.setLocation(0, 0); // reset after any panning
            canvas.add(backgroundImage);
        }

        tileMap.resetAllPositions();

        // --- tiles (drawn above background when enabled) ---
        if (drawTileMap) {
            tileMap.draw(canvas);
        }

        // --- dropped items (coins, loot) ---
        // RIG POINT: Item.draw() must be implemented before dropped items appear.
        for (Item item : droppedItems) {
            item.resetVisualPosition();
            item.draw(canvas);
        }

        // --- world objects (signs, chests, pushblocks, etc.) ---
        for (WorldObject obj : objects) {
            obj.resetVisualPosition();
            obj.draw(canvas);
        }

        // --- save point (if this room has one) ---
        if (savePoint != null) {
            savePoint.addTo(canvas);
        }

        // --- entities (enemies, projectiles) ---
        // RIG POINT: Entity.draw() is already implemented; add enemies to the entities list
        //            in each room's buildXxx() method.
        for (Entity entity : entities) {
            entity.draw(canvas);
        }

        // --- projectiles (draw above items/objects, below debug/UI) ---
        for (Projectile projectile : projectiles) {
            if (projectile.isAlive()) {
                projectile.draw(canvas);
            }
        }

        // --- room ID label (tech-demo only) ---
        // TECH DEMO: visible room ID label; remove when real room art replaces buildDummy().
        // Reset position before adding — dummyLabel.move() was called during pan animations
        // and the label's stored location is no longer the centered position.
        if (dummyLabel != null) {
            double approxLabelWidth = roomId.length() * 22.0;
            double centerX = EXIT_LEFT_EDGE + (26 * 48) / 2.0 - approxLabelWidth / 2.0;
            double centerY = EXIT_BOTTOM_EDGE / 2.0 + 12;
            dummyLabel.setLocation(centerX, centerY);
            canvas.add(dummyLabel);
        }

        if (foregroundImage != null) {
            foregroundImage.setLocation(0, 0);
            canvas.add(foregroundImage);
        }

        for (WorldObject obj : objects) {
            if (obj instanceof DebugTileMarker) {
                ((DebugTileMarker) obj).bringToFront();
            }
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

        // --- background image ---
        if (backgroundImage != null) {
            canvas.remove(backgroundImage);
        }

        // --- tiles ---
        if (drawTileMap) {
            tileMap.removeFrom(canvas);
        }

        // --- dropped items ---
        for (Item item : droppedItems) {
            item.removeFrom(canvas);
        }

        // --- world objects ---
        for (WorldObject obj : objects) {
            obj.removeFrom(canvas);
        }

        // --- save point ---
        if (savePoint != null) {
            savePoint.removeFrom(canvas);
        }

        // --- entities ---
        for (Entity entity : entities) {
            entity.removeSpriteFromCanvas(canvas);
        }

        // --- projectiles ---
        for (Projectile projectile : projectiles) {
            projectile.removeSpriteFromCanvas(canvas);
        }

        // --- room ID label ---
        if (dummyLabel != null) {
            canvas.remove(dummyLabel);
        }

        if (foregroundImage != null) {
            canvas.remove(foregroundImage);
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
        for (Entity entity : entities) {
            if (entity instanceof Enemy) {
                ((Enemy) entity).update(dt, player);
            } else {
                entity.update(dt);
            }
        }

        for (Projectile projectile : projectiles) {
            if (projectile.isAlive()) {
                projectile.update(dt);
            }
        }

        // --- enemy-to-enemy separation (prevent stacking) ---
        for (int i = 0; i < entities.size(); i++) {
            if (!(entities.get(i) instanceof Enemy)) continue;
            Enemy a = (Enemy) entities.get(i);
            if (!a.isAlive()) continue;
            for (int j = i + 1; j < entities.size(); j++) {
                if (!(entities.get(j) instanceof Enemy)) continue;
                Enemy b = (Enemy) entities.get(j);
                if (!b.isAlive()) continue;
                double dx = b.getX() - a.getX();
                double dy = b.getY() - a.getY();
                double dist = Math.sqrt(dx * dx + dy * dy);
                double minDist = 52; // slightly larger than hitbox (48px)
                if (dist < minDist && dist > 0.01) {
                    double overlap = (minDist - dist) / 2.0;
                    double nx = dx / dist;
                    double ny = dy / dist;
                    a.nudge(-nx * overlap, -ny * overlap);
                    b.nudge( nx * overlap,  ny * overlap);
                }
            }
        }

        // --- player-to-enemy solid collision (prevent walking through enemies) ---
        // Skipped when the Intangible relic is active so the player can pass through.
        // Pushable enemies (most) are nudged away from the player — enemies can't shove the player.
        // Non-pushable enemies (ArmorEnemy, Boss) act as solid walls — the player is nudged away.
        if (!player.isIntangibleActive()) {
            for (Entity e : entities) {
                if (!(e instanceof Enemy) || !e.isAlive()) continue;
                double dx = player.getX() - e.getX();
                double dy = player.getY() - e.getY();
                double dist = Math.sqrt(dx * dx + dy * dy);
                double minDist = 44;
                if (dist < minDist && dist > 0.01) {
                    double overlap = minDist - dist;
                    double nx = dx / dist;
                    double ny = dy / dist;
                    if (((Enemy) e).isPushable()) {
                        e.nudge(-nx * overlap, -ny * overlap);
                    } else {
                        player.nudge(nx * overlap, ny * overlap);
                    }
                }
            }
        }

        // --- projectile hit resolution ---
        for (Projectile projectile : projectiles) {
            if (!projectile.isAlive()) {
                continue;
            }
            if (projectile.checkHit(player, true)) {
                continue;
            }
            if (!projectile.isReflected()) {
                continue;
            }
            for (Entity entity : entities) {
                if (!(entity instanceof Enemy) || entity.getHealth() <= 0) {
                    continue;
                }
                if (projectile.checkHit(entity, false)) {
                    break;
                }
            }
        }

        // --- remove dead projectiles before redraw so stale visuals disappear immediately ---
        java.util.Iterator<Projectile> projectileIt = projectiles.iterator();
        while (projectileIt.hasNext()) {
            Projectile projectile = projectileIt.next();
            if (!projectile.isAlive()) {
                if (canvas != null) {
                    projectile.removeSpriteFromCanvas(canvas);
                }
                projectileIt.remove();
            }
        }

        // --- redraw entities/projectiles so animation swaps remove old visuals from canvas ---
        // Without this, direction changes leave ghost sprites because draw() is the only
        // method that calls canvas.remove(lastDrawnVisual) before adding the new frame.
        if (canvas != null) {
            for (Entity entity : entities) {
                entity.draw(canvas);
            }
            for (Projectile projectile : projectiles) {
                projectile.draw(canvas);
            }
        }

        // --- world object per-tick logic (pressure buttons, animated objects) ---
        // RIG POINT: PressureButton and similar objects need update(dt) called here.
        for (WorldObject obj : objects) {
            obj.update(dt);
        }

        if (savePoint != null) {
            savePoint.update(dt);
        }

        // --- player contact with world objects (e.g. ThicketGate auto-opens on touch) ---
        // RIG POINT: onContact() for ThicketGate, Coin drops, and similar touch-triggered objects.
        for (WorldObject obj : objects) {
            if (player.getHitbox().overlaps(obj.getHitbox())) {
                obj.onContact(player);
            }
        }

        // --- dropped item pickup (player walks over a ground drop) ---
        java.util.Iterator<Item> itemIt = droppedItems.iterator();
        while (itemIt.hasNext()) {
            Item item = itemIt.next();
            if (player.getHitbox().overlaps(item.getPickupHitbox())) {
                item.onCollect(player);
                if (canvas != null) item.removeFrom(canvas);
                itemIt.remove();
            }
        }

        // --- sword hit detection (Grass cut, TrainingDummy react) ---
        SwordSwing swing = player.getActiveSwing();
        if (swing != null) {
            for (WorldObject obj : objects) {
                if (obj.isVisible() && swing.getHitbox().overlaps(obj.getHitbox())) {
                    obj.onHit();
                }
            }
        }

        // --- push block and pressure button logic ---
        java.util.List<PushBlock> pushBlocks = getPushBlocks();
        java.util.List<PressureButton> pressureButtons = getPressureButtons();
        // Reset all block-pressed states before any block checks so multiple blocks
        // don't overwrite each other's results.
        for (PressureButton button : pressureButtons) {
            button.setPressedByBlock(false);
        }
        if (!pushBlocks.isEmpty()) {
            Direction facing = player.getFacing();
            for (PushBlock block : pushBlocks) {
                block.tickCooldown();
                if (facing != null && player.getHitbox().overlaps(block.getHitbox())) {
                    boolean moved = block.tryPush(facing, tileMap, pushBlocks);
                    if (block.isFallen()) {
                        block.removeFrom(canvas);
                    } else if (moved) {
                        for (WorldObject obj : objects) {
                            if (obj instanceof Grass && !((Grass) obj).isCut()
                                    && obj.getHitbox().overlaps(block.getHitbox())) {
                                ((Grass) obj).onHit();
                            }
                        }
                    }
                }
                block.updateButtonOverlap(pressureButtons);
            }
            // Resolve any remaining player-block overlap so the player can't walk through blocks
            for (PushBlock block : pushBlocks) {
                if (!block.isFallen()) resolvePlayerBlockOverlap(player, block);
            }
        }
        if (!pressureButtons.isEmpty()) {
            for (PressureButton button : pressureButtons) {
                button.update(player);
            }
            if (!puzzleSolved) {
                boolean allPressed = true;
                for (PressureButton button : pressureButtons) {
                    if (!button.isPressed()) { allPressed = false; break; }
                }
                if (allPressed) {
                    puzzleSolved = true;
                    if (puzzleSolvedCallback != null) puzzleSolvedCallback.run();
                }
            }
        }

        // --- room lock check ---
        // RIG POINT: roomLock.update(entities) checks if all enemies are dead → opens exit.
        if (roomLock != null) {
            roomLock.update(entities);
        }

        // --- remove dead enemies, trigger coin drops ---
        java.util.Iterator<Entity> eit = entities.iterator();
        while (eit.hasNext()) {
            Entity e = eit.next();
            if (!e.isAlive()) {
                if (canvas != null) e.removeSpriteFromCanvas(canvas);
                if (e instanceof Enemy && ((Enemy) e).onDeath()) {
                    Coin coin = new Coin(e.getX(), e.getY());
                    addDroppedItem(coin);
                }
                eit.remove();
            }
        }

        if (!player.isAlive()) {
            return;
        }

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
            if (getExitAt(Direction.LEFT)) {
                if (exitCallback != null) exitCallback.accept(Direction.LEFT);
            } else {
                player.setPosition(EXIT_LEFT_EDGE + 1, py); // clamp back inside
            }
        } else if (px > EXIT_RIGHT_EDGE) {
            // --- right (east) edge ---
            if (getExitAt(Direction.RIGHT)) {
                if (exitCallback != null) exitCallback.accept(Direction.RIGHT);
            } else {
                player.setPosition(EXIT_RIGHT_EDGE - 1, py);
            }
        } else if (py < EXIT_TOP_EDGE) {
            // --- top (north) edge ---
            if (getExitAt(Direction.UP)) {
                if (exitCallback != null) exitCallback.accept(Direction.UP);
            } else {
                player.setPosition(px, EXIT_TOP_EDGE + 1);
            }
        } else if (py > EXIT_BOTTOM_EDGE) {
            // --- bottom (south) edge ---
            if (getExitAt(Direction.DOWN)) {
                if (exitCallback != null) exitCallback.accept(Direction.DOWN);
            } else {
                player.setPosition(px, EXIT_BOTTOM_EDGE - 1);
            }
        }
    }

    /**
     * Routes the room's generic use / talk input to the nearest interactable WorldObject.
     *
     * @param player the active Player
     * @return true if a room object handled the interaction
     */
    public boolean tryInteract(Player player) {
        WorldObject target = findInteractTarget(player);
        if (target == null) {
            return false;
        }
        target.onInteract(player);
        return true;
    }

    /** Picks the best nearby interactable based on distance plus the player's facing direction. */
    private WorldObject findInteractTarget(Player player) {
        if (player == null || objects.isEmpty()) {
            return null;
        }

        Direction facing = player.getFacing() == null ? Direction.DOWN : player.getFacing();
        double[] facingDelta = facing.toDelta();
        double bestScore = Double.NEGATIVE_INFINITY;
        WorldObject bestTarget = null;

        for (WorldObject obj : objects) {
            if (obj == null || !obj.isVisible() || !obj.isInteractable() || obj.getHitbox() == null) {
                continue;
            }

            Hitbox objectHitbox = obj.getHitbox();
            boolean overlapping = player.getHitbox().overlaps(objectHitbox);
            double[] objectCenter = objectHitbox.getCenter();
            double dx = objectCenter[0] - player.getX();
            double dy = objectCenter[1] - player.getY();
            double distance = Math.sqrt(dx * dx + dy * dy);

            if (!overlapping && distance > INTERACT_RADIUS_PX) {
                continue;
            }

            double alignment = 1.0;
            if (!overlapping && distance > 0.001) {
                alignment = (dx * facingDelta[0] + dy * facingDelta[1]) / distance;
                if (alignment < INTERACT_ALIGNMENT_MIN) {
                    continue;
                }
            }

            double score = (overlapping ? 500.0 : 0.0) + alignment * 100.0 - distance;
            if (score > bestScore) {
                bestScore = score;
                bestTarget = obj;
            }
        }

        return bestTarget;
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
        // --- transient dropped items ---
        // Grass/coin debug drops should not persist across room exits.
        if (canvas != null) {
            for (Item item : droppedItems) {
                item.removeFrom(canvas);
            }
        }
        droppedItems.clear();

        // --- projectiles ---
        if (canvas != null) {
            for (Projectile projectile : projectiles) {
                projectile.removeSpriteFromCanvas(canvas);
            }
        }
        projectiles.clear();

        // --- enemies ---
        // D1 currently registers respawn factories through addRespawningEntity().
        // Other combat rooms can opt into the same behavior once their real enemy layouts exist.
        if (!respawnEntitySuppliers.isEmpty()) {
            if (canvas != null) {
                for (Entity entity : entities) {
                    entity.removeSpriteFromCanvas(canvas);
                }
            }

            entities.clear();

            if (roomLock != null) {
                roomLock.reset();
            }

            for (Supplier<Entity> entitySupplier : respawnEntitySuppliers) {
                Entity entity = entitySupplier.get();
                if (entity == null) continue;
                prepareEntityForRoom(entity);
                entities.add(entity);
                if (initialized && canvas != null) {
                    entity.draw(canvas);
                }
            }
        }

        // --- resettable harvestables ---
        for (WorldObject obj : objects) {
            if (obj instanceof Grass) {
                ((Grass) obj).reset();
            }
        }

        // --- push blocks and pressure buttons (puzzle rooms) ---
        // Only reset if puzzle hasn't been solved — prevents D2 from resetting on backtrack.
        if (!puzzleSolved) {
            for (WorldObject obj : objects) {
                if (obj instanceof PushBlock) ((PushBlock) obj).reset();
                else if (obj instanceof PressureButton) ((PressureButton) obj).reset();
            }
        }
    }

    // =========================================================
    // ROOM LOCK
    // =========================================================

    /** Sets the callback fired once when all PressureButtons in this room are pressed. */
    public void setPuzzleSolvedCallback(Runnable r) { this.puzzleSolvedCallback = r; }

    /**
     * Resolves overlap between the player and a push block using AABB separation.
     * Moves the player the minimum distance needed to no longer overlap the block.
     */
    private void resolvePlayerBlockOverlap(Player player, PushBlock block) {
        Hitbox ph = player.getHitbox();
        Hitbox bh = block.getHitbox();
        if (!ph.overlaps(bh)) return;

        double overlapLeft   = (ph.x + ph.width)  - bh.x;
        double overlapRight  = (bh.x + bh.width)  - ph.x;
        double overlapTop    = (ph.y + ph.height)  - bh.y;
        double overlapBottom = (bh.y + bh.height)  - ph.y;

        double minX = Math.min(overlapLeft, overlapRight);
        double minY = Math.min(overlapTop,  overlapBottom);

        if (minX <= minY) {
            if (overlapLeft <= overlapRight) player.nudge(-overlapLeft, 0);
            else                             player.nudge(overlapRight,  0);
        } else {
            if (overlapTop <= overlapBottom) player.nudge(0, -overlapTop);
            else                             player.nudge(0,  overlapBottom);
        }
    }

    private java.util.List<PushBlock> getPushBlocks() {
        java.util.List<PushBlock> result = new java.util.ArrayList<>();
        for (WorldObject obj : objects) {
            if (obj instanceof PushBlock) result.add((PushBlock) obj);
        }
        return result;
    }

    private java.util.List<PressureButton> getPressureButtons() {
        java.util.List<PressureButton> result = new java.util.ArrayList<>();
        for (WorldObject obj : objects) {
            if (obj instanceof PressureButton) result.add((PressureButton) obj);
        }
        return result;
    }

    /** Attaches a RoomLock to this room. Call from WorldMap during dungeon setup. */
    public void setRoomLock(RoomLock lock) {
        setRoomLock(lock, null);
    }

    /**
     * Attaches a RoomLock and marks which exit direction it should temporarily block while locked.
     */
    public void setRoomLock(RoomLock lock, Direction blockedDirection) {
        this.roomLock = lock;
        this.roomLockDirection = blockedDirection;
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
        if (roomLock != null && roomLock.isLocked() && d == roomLockDirection) {
            return false;
        }
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
        // --- background image ---
        if (backgroundImage != null) {
            backgroundImage.move(panX, panY);
        }

        if (foregroundImage != null) {
            foregroundImage.move(panX, panY);
        }

        // --- tiles ---
        if (drawTileMap) {
            tileMap.panAll(panX, panY);
        }

        // --- world objects ---
        for (WorldObject obj : objects) {
            obj.panVisual(panX, panY);
        }

        if (savePoint != null) {
            savePoint.panVisual(panX, panY);
        }

        // --- entities (enemies, projectiles) ---
        for (Entity entity : entities) {
            entity.panVisual(panX, panY);
        }

        // --- projectiles ---
        for (Projectile projectile : projectiles) {
            projectile.panVisual(panX, panY);
        }

        // --- dropped items ---
        for (Item item : droppedItems) {
            item.panVisual(panX, panY);
        }

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

    /** Wires room-owned shared systems into entities as they are spawned. */
    private void prepareEntityForRoom(Entity entity) {
        if (entity instanceof RangedEnemy) {
            ((RangedEnemy) entity).setProjectileList(projectiles);
        } else if (entity instanceof Boss) {
            ((Boss) entity).setProjectileList(projectiles);
        }
    }

    /** Adds a resettable entity factory and spawns its initial instance immediately. */
    public void addRespawningEntity(Supplier<Entity> entitySupplier) {
        if (entitySupplier == null) return;

        Entity entity = entitySupplier.get();
        if (entity == null) return;

        prepareEntityForRoom(entity);
        respawnEntitySuppliers.add(entitySupplier);
        entities.add(entity);

        if (initialized && canvas != null) {
            entity.draw(canvas);
        }
    }

    /** Adds an entity (enemy) to this room's entity list. */
    public void addEntity(Entity e) {
        if (e == null) return;
        prepareEntityForRoom(e);
        entities.add(e);
        if (initialized && canvas != null) {
            e.draw(canvas);
        }
    }

    /** Adds a WorldObject (interactable) to this room. */
    public void addObject(WorldObject o)  { objects.add(o); }

    /** Keeps any room foreground overlay above actors after they redraw. */
    public void bringForegroundToFront() {
        if (foregroundImage != null) {
            foregroundImage.sendToFront();
        }
    }

    /** Adds a ThicketGate to this room's gate list. */
    public void addGate(ThicketGate gate) { gates.add(gate); }

    /** Assigns the room's single SavePoint placeholder. */
    public void setSavePoint(SavePoint savePoint) { this.savePoint = savePoint; }

    /** Adds a dropped Item (coin, etc.) to this room's ground-item list. */
    public void addDroppedItem(Item item) {
        if (item == null) return;
        if (item instanceof Coin) {
            ((Coin) item).snapToValidSpawn(tileMap);
        }
        droppedItems.add(item);
        if (initialized && canvas != null) {
            item.draw(canvas);
        }
    }

    // =========================================================
    // GETTERS
    // =========================================================

    public String           getRoomId()      { return roomId; }
    public TileMap          getTileMap()     { return tileMap; }
    public List<Entity>     getEntities()    { return entities; }
    public List<WorldObject> getObjects()    { return objects; }
    public List<Item>       getDroppedItems(){ return droppedItems; }
    public RoomLock         getRoomLock()    { return roomLock; }
    public SavePoint        getSavePoint()   { return savePoint; }
    public boolean          isInitialized()  { return initialized; }

    /** Returns only the Enemy instances from the entity list. */
    public List<Enemy> getEnemies() {
        List<Enemy> enemies = new ArrayList<>();
        for (Entity e : entities) {
            if (e instanceof Enemy) enemies.add((Enemy) e);
        }
        return enemies;
    }

    public List<Projectile> getProjectiles() {
        return projectiles;
    }
}
