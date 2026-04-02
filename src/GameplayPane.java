/*
Person 2: GameplayPane — the screen that connects WorldMap + Player to the main application
Who RIGs it: MainApplication — creates one GameplayPane in run(), stores it as gameplayPane,
               and routes to it via switchToGameplayScreen().
             GameLoop — calls onTick(dt) every frame while this screen is active, driving
               all player input, room updates, and transition animations.

Extends: GraphicsPane
Owns: WorldMap (the full game world — all 12 rooms, transitions, dungeon entrance)
Does NOT own: Player (created and held by MainApplication; retrieved each tick via mainScreen.getPlayer())

===============
PLAN OF ACTION
===============

- CLASS ROLE
- GameplayPane is the "glue layer" between the game engine (WorldMap, Room, RoomTransition)
  and the main application shell (MainApplication, GameLoop, GraphicsPane).
- It bridges two things that cannot talk directly: the game loop (which calls onTick) and
  the room system (which needs a Player and a canvas to operate).
- It does NOT implement any game logic itself — that lives in Room, WorldMap, and RoomTransition.

- LIFECYCLE
  showContent():
    Called once when gameplay starts (start new game, or return from pause/save screen).
    On first call: positions the Player at the starting room center and sets their TileMap.
    On every call: adds the active room's graphics to canvas and draws the player sprite.

  hideContent():
    Called when switching away from gameplay (e.g. game over, save screen).
    Removes active room graphics and player sprite from canvas.
    Does NOT reset Player position — that is preserved for when the player returns.

  onTick(dt):
    Called every ~16ms (60fps) by GameLoop.
    1. If state is PLAYING: calls player.update() to process input and move the player.
       Also calls player.draw() to sync the animation frame and sprite position.
    2. Always calls worldMap.update(dt, player) — WorldMap handles state changes internally
       (PLAYING, TRANSITIONING, PAUSED are all handled by the freeze contracts).

- PLAYER INPUT DURING TRANSITIONS
  Player.update() is only called when GamePlayState == PLAYING.
  During TRANSITIONING, the player is visually carried by RoomTransition but receives no input.
  player.draw() is also skipped during TRANSITIONING to avoid overriding the pan offset.

- WHAT GAMEPLAYPANE DOES NOT DO
- Does not move enemies, run combat, or handle interactions — that is Room's responsibility.
- Does not manage the dungeon entrance trigger — WorldMap.checkDungeonEntranceTrigger() handles it.
- Does not read from or write to SaveData — that is SaveManager's job.
- Does not manage HUD, pause overlay, or dialogue — those are separate layers handled by MainApplication.
  (HUD and PauseModal are already wired into MainApplication and draw on top of everything.)
*/

import java.util.Collections;

import acm.graphics.GCanvas;

/**
 * The gameplay screen. Connects WorldMap and Player to the main application's
 * game loop and canvas. See the PLAN OF ACTION block above for full details.
 */
public class GameplayPane extends GraphicsPane {

    // =========================================================
    // CONSTANTS — player starting position
    // =========================================================

    /*
     * =====================
     * Starting position for the player in A1 (the first room).
     * Adjust these if the spawn point needs to move (e.g. to match a cutscene end position).
     * =====================
     */

    /**
     * Player starting X position in screen pixels (horizontal center of the room).
     * Room center X = MAP_OFFSET_X + 26 * 48 / 2 = 640.
     */
    private static final double PLAYER_START_X = TileMap.MAP_OFFSET_X + 26 * 48 / 2.0; // = 640

    /**
     * Player starting Y position in screen pixels (vertical center of the room).
     * Room center Y = 15 * 48 / 2 = 360.
     */
    private static final double PLAYER_START_Y = 15 * 48 / 2.0; // = 360

    /*
     * =====================
     * End of starting position constants.
     * =====================
     */

    // =========================================================
    // FIELDS
    // =========================================================

    /**
     * The game world: all 12 rooms, exit connections, transitions, and the dungeon entrance.
     * Created once in the constructor and kept for the lifetime of the application.
     */
    private final WorldMap worldMap;

    /**
     * Tracks whether showContent() has been called for the first time.
     * On first call the player is positioned at PLAYER_START_X/Y and their TileMap is set.
     * On subsequent calls (e.g. returning from pause) the player's position is preserved.
     *
     * // RIG POINT: When loading a saved game, reset this flag to false AND set the player's
     * //            position from SaveData before calling showContent(), so the player spawns
     * //            at their saved location rather than the default starting position.
     */
    private boolean firstShow = true;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    /**
     * Creates the GameplayPane and its WorldMap.
     * The WorldMap is initialised here (all 12 rooms built as dummies, exits wired).
     * Nothing is drawn yet — showContent() does that.
     *
     * @param mainScreen the main application (provides canvas, player, input handler)
     */
    public GameplayPane(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
        this.worldMap   = new WorldMap(mainScreen.getGCanvas());
    }

    // =========================================================
    // LIFECYCLE — show and hide
    // =========================================================

    /**
     * Adds the active room's graphics and the player sprite to the canvas.
     * Called by MainApplication when gameplay starts or resumes.
     *
     * On first call: positions the player at the starting room center and wires their TileMap.
     * On subsequent calls (e.g. returning from pause): just re-adds graphics to canvas.
     */
    @Override
    public void showContent() {
        GCanvas canvas = mainScreen.getGCanvas();
        Player player  = mainScreen.getPlayer();
        if (player == null) return;

        // --- first entry: place player at starting position ---
        if (firstShow) {
            Room startingRoom = worldMap.getActiveRoom();
            player.setTileMap(startingRoom.getTileMap());
            player.setPosition(PLAYER_START_X, PLAYER_START_Y);
            player.setSpawnPosition(PLAYER_START_X, PLAYER_START_Y);
            firstShow = false;
        }

        // --- add room graphics to canvas ---
        worldMap.getActiveRoom().addTo(canvas);

        // --- draw the player on top of the room ---
        player.draw(canvas);
    }

    /**
     * Removes the active room's graphics and the player sprite from the canvas.
     * Called by MainApplication when switching away from gameplay.
     * Player position and WorldMap state are preserved — they resume where they left off.
     */
    @Override
    public void hideContent() {
        GCanvas canvas = mainScreen.getGCanvas();
        Player player  = mainScreen.getPlayer();

        // --- remove room graphics ---
        worldMap.getActiveRoom().removeFrom(canvas);

        // --- remove player sprite ---
        if (player != null) {
            player.removeSpriteFromCanvas(canvas);
        }
    }

    // =========================================================
    // GAME LOOP — called every tick by GameLoop
    // =========================================================

    /**
     * Per-tick update called by GameLoop at ~60fps.
     *
     * Responsibilities this tick, in order:
     *   1. Process player input and movement (PLAYING state only).
     *   2. Sync player animation frame and sprite position (PLAYING state only).
     *   3. Update the world — room content, transitions, dungeon entrance check.
     *
     * Player update is skipped during TRANSITIONING so input is frozen and the
     * player's internal coordinates don't shift while the pan animation runs.
     *
     * @param dt delta-time in seconds (e.g. 0.016 for ~60fps)
     */
    @Override
    public void onTick(double dt) {
        Player player = mainScreen.getPlayer();
        if (player == null) return;

        GCanvas canvas = mainScreen.getGCanvas();

        // --- player input and animation (PLAYING state only) ---
        // Skipped during TRANSITIONING so the player cannot move mid-pan.
        // Also skipped during PAUSED, DIALOGUE, and CUTSCENE (those states freeze input).
        if (GamePlayState.PLAYING.is()) {
            // RIG POINT: Replace Collections.emptyList() with the active room's entity/projectile
            //            lists once enemies and projectiles are populated in rooms.
            player.update(
                mainScreen.getInputHandler(),
                Collections.emptyList(), // enemies — none in dummy rooms yet
                null,                    // projectiles — none in dummy rooms yet
                dt
            );

            // Sync animation frame and sprite position after movement
            player.draw(canvas);
        }

        // --- world update (always runs; WorldMap handles state internally) ---
        worldMap.update(dt, player);
    }

    /**
     * Returns true so MainApplication keeps the 60fps GameLoop running while this screen is active.
     *
     * @return true — gameplay always needs the game loop
     */
    @Override
    public boolean needsGameLoop() {
        return true;
    }

    // =========================================================
    // ACCESSOR
    // =========================================================

    /**
     * Returns the WorldMap owned by this pane.
     * Used by SaveManager to read the active room ID when saving.
     *
     * @return the WorldMap
     */
    public WorldMap getWorldMap() {
        return worldMap;
    }
}
