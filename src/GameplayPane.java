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

import java.awt.event.KeyEvent;
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
     * Player starting X position in screen pixels.
     * Starts at the center of tile (10,7) in A1.
     */
    private static final double PLAYER_START_X = TileMap.MAP_OFFSET_X + 10 * 48 + 24; // = 520

    /**
     * Player starting Y position in screen pixels.
     * Starts at the center of tile (10,7) in A1.
     */
    private static final double PLAYER_START_Y = 7 * 48 + 24; // = 360
    /** Opening room ID used by the debug spawn teleport. */
    private static final String PLAYER_START_ROOM_ID = "A1";
    /** First dungeon room: plays the dungeon theme while active. */
    private static final String DUNGEON_FLOOR_ONE_ROOM_ID = "D1";

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
    private WorldMap worldMap;

    /** HUD overlay — draws hearts, coins, relics, and ability buttons using art assets. */
    private final HUDoverlay hud = new HUDoverlay();

    /** Prevents double-wiring input keys across multiple showContent() calls. */
    private boolean inputsWired;

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

    /** Spawn position to apply on the next fresh or loaded session start. */
    private double pendingSpawnX = PLAYER_START_X;
    private double pendingSpawnY = PLAYER_START_Y;
    /** Last room ID that audio sync logic observed while gameplay was active. */
    private String lastMusicRoomId;

    /**
     * Mirrors {@link Player#hasIntangible()} for HUD / controls-card rows. When the Courage relic
     * is granted mid-session (chest), this flips so we can rebuild the HUD without re-entering the pane.
     */
    private boolean hudBuiltWithIntangibleOwned;

    /**
     * Mirrors {@link Player#hasHalfDamage()} so the HUD switches to the upgraded heart bar
     * (quarter-step art) when the Strength relic is picked up mid-session.
     */
    private boolean hudBuiltWithHalfDamageOwned;

    // =========================================================
    // CONTROLS CARD OVERLAY
    // =========================================================

    /** Right-side gameplay help card. Visible by default on each fresh / loaded session. */
    private boolean controlsCardVisible = true;
    /** Every visual object that belongs to the controls card, back to front. */
    private final java.util.List<acm.graphics.GObject> controlsCardObjects =
        new java.util.ArrayList<>();
    /** Small close button in the card header. */
    private acm.graphics.GRoundRect controlsCardCloseFrame;
    private acm.graphics.GLabel controlsCardCloseLabel;

    /** Shown before the Courage relic is awarded (no K row). */
    private static final String[][] BASE_CONTROLS_CARD_ROWS_NO_ABILITY = {
        { "MOVE", "WASD" },
        { "ATTACK", "J" },
        { "SAVE / USE", "E" },
        { "PAUSE", "ESC" }
    };

    private static final String[][] BASE_CONTROLS_CARD_ROWS = {
        { "MOVE", "WASD" },
        { "ATTACK", "J" },
        { "ABILITY", "K" },
        { "SAVE / USE", "E" },
        { "PAUSE", "ESC" }
    };

    private static final String[][] DEBUG_CONTROLS_CARD_ROWS = {
        { "DEBUG", "F6" },
        { "DUNGEON", "F5" },
        { "SPAWN", "F7" }
    };

    private static final java.awt.Color CONTROLS_CARD_SHADOW =
        new java.awt.Color(54, 34, 18, 110);
    private static final java.awt.Color CONTROLS_CARD_EDGE =
        new java.awt.Color(131, 92, 51);
    private static final java.awt.Color CONTROLS_CARD_TAB =
        new java.awt.Color(167, 124, 78);
    private static final java.awt.Color CONTROLS_CARD_PARCHMENT =
        new java.awt.Color(237, 221, 179);
    private static final java.awt.Color CONTROLS_CARD_PARCHMENT_INNER =
        new java.awt.Color(246, 235, 201);
    private static final java.awt.Color CONTROLS_CARD_TITLE =
        new java.awt.Color(71, 43, 23);
    private static final java.awt.Color CONTROLS_CARD_BUTTON =
        new java.awt.Color(95, 196, 143);
    private static final java.awt.Color CONTROLS_CARD_BUTTON_DARK =
        new java.awt.Color(42, 113, 80);
    private static final java.awt.Color CONTROLS_CARD_BUTTON_HIGHLIGHT =
        new java.awt.Color(160, 235, 189);

    // =========================================================
    // DEBUG OVERLAY (F6)
    // =========================================================

    /** True when the F6 debug overlay is visible. */
    private boolean debugOverlayOn = false;
    /** Shared world-debug toggle for room objects like A2 blocker markers. */
    private static boolean worldDebugMarkersVisible = false;

    // =========================================================
    // PLAYER DEATH
    // =========================================================

    /** Ticks remaining in the death animation before transitioning to game-over. 0 = not dying. */
    private int deathDelayTicks = 0;

    /** ~5 seconds at 60fps — lets the death animation breathe before respawn. */
    private static final int DEATH_DELAY = 300;

    /** Transient GObjects drawn each tick for the debug overlay — cleared and redrawn every frame. */
    private final java.util.List<acm.graphics.GObject> debugObjects = new java.util.ArrayList<>();

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
        rebuildWorldMap();
    }

    private void rebuildWorldMap() {
        this.worldMap = new WorldMap(
            mainScreen.getGCanvas(),
            mainScreen.getDialogue(),
            mainScreen.getShopMenu()
        );
        this.worldMap.setBossDefeatedCallback(this::triggerEndingSequence);
    }

    private static final String[] ENDING_DIALOGUE_LINES = {
        "Warrior... you've done it. You defeated Bastian and destroyed the wand.",
        "I can feel my power returning. The polymorph is breaking at last!",
        "You've saved this town, warrior. I always knew you were the right choice.",
        "Now then. Off you go. Peace is restored. Do... hero things."
    };

    private void triggerEndingSequence() {
        Dialogue dialogue = mainScreen.getDialogue();
        if (dialogue == null) return;
        GamePlayState.setCurrent(GamePlayState.DIALOGUE);
        dialogue.open(
            ENDING_DIALOGUE_LINES,
            "Calumund Vaen Solmare",
            true,
            () -> mainScreen.switchToEndingNarrativeScreen()
        );
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

        deathDelayTicks = 0;

        // --- first entry: place player at starting position ---
        if (firstShow) {
            Room startingRoom = worldMap.getActiveRoom();
            player.setTileMap(startingRoom.getTileMap());
            player.setPosition(pendingSpawnX, pendingSpawnY);
            player.setSpawnPosition(pendingSpawnX, pendingSpawnY);
            firstShow = false;
        }

        // --- add room graphics to canvas ---
        worldMap.getActiveRoom().addTo(canvas);
        worldMap.showSpecialMarkersForActiveRoom();
        syncActiveRoomMusic();

        // --- draw the player on top of the room ---
        player.draw(canvas);
        bringActiveRoomForegroundToFront();

        // --- player HUD ---
        // showPlayerHUD(player); // OLD: temp HUD from GraphicsPane — kept as fallback
        hud.showAll(this, buildHudSnapshot(player));
        hudBuiltWithIntangibleOwned = player.hasIntangible();
        hudBuiltWithHalfDamageOwned = player.hasHalfDamage();
        if (controlsCardVisible) {
            showControlsCard();
        }
        // --- wire attack / ability keys ---
        wireInputOnce();
    }

    /**
     * Removes the active room's graphics and the player sprite from the canvas.
     * Called by MainApplication when switching away from gameplay.
     * Player position and WorldMap state are preserved — they resume where they left off.
     */
    @Override
    public void hideContent() {
        unwireInput();

        GCanvas canvas = mainScreen.getGCanvas();
        Player player  = mainScreen.getPlayer();

        // --- remove room graphics ---
        worldMap.getActiveRoom().removeFrom(canvas);
        worldMap.hideSpecialMarkers();

        // --- remove player sprite ---
        if (player != null) {
            player.removeSpriteFromCanvas(canvas);
        }

        // hidePlayerHUD(); // OLD: temp HUD from GraphicsPane — kept as fallback
        hud.hideAll(this);
        clearControlsCardVisuals();
        lastMusicRoomId = null;
        GameMusic.stopJourneyBeginsMusic();
        GameMusic.stopMysteriousDungeonMusic();
        GameMusic.stopOverworldMusic();
        GameMusic.stopBossMusic();

        // --- remove debug overlay ---
        clearDebugOverlay(canvas);
        worldDebugMarkersVisible = false;
    }

    /**
     * Resets pane state so the next showContent() starts a fresh game.
     * Called from MainApplication.switchToGameOverScreen() before screen switch.
     */
    public void resetForNewGame() {
        firstShow = true;
        pendingSpawnX = PLAYER_START_X;
        pendingSpawnY = PLAYER_START_Y;
        deathDelayTicks = 0;
        debugOverlayOn = false;
        worldDebugMarkersVisible = false;
        controlsCardVisible = true;
        lastMusicRoomId = null;
    }

    /** Rebuilds gameplay state for a brand-new session that starts in A1. */
    public void prepareNewSession() {
        rebuildWorldMap();
        firstShow = true;
        pendingSpawnX = PLAYER_START_X;
        pendingSpawnY = PLAYER_START_Y;
        deathDelayTicks = 0;
        debugOverlayOn = false;
        worldDebugMarkersVisible = false;
        controlsCardVisible = true;
        lastMusicRoomId = null;
        GamePlayState.setCurrent(GamePlayState.PLAYING);
    }

    /** Rebuilds gameplay state, reapplies world progression, and queues the saved room/spawn. */
    public void prepareLoadedSession(SaveData loadedData) {
        rebuildWorldMap();
        if (loadedData != null) {
            worldMap.applyPersistentState(
                loadedData.getCollectedItemIds(),
                loadedData.getStoryFlags()
            );
        }
        String roomId = loadedData == null ? "" : loadedData.getRoomId();
        double spawnX = loadedData == null ? PLAYER_START_X : loadedData.getSpawnX();
        double spawnY = loadedData == null ? PLAYER_START_Y : loadedData.getSpawnY();
        boolean roomFound = worldMap.setActiveRoomById(roomId);
        firstShow = true;
        pendingSpawnX = roomFound ? spawnX : PLAYER_START_X;
        pendingSpawnY = roomFound ? spawnY : PLAYER_START_Y;
        deathDelayTicks = 0;
        debugOverlayOn = false;
        worldDebugMarkersVisible = false;
        controlsCardVisible = true;
        lastMusicRoomId = null;
        GamePlayState.setCurrent(GamePlayState.PLAYING);
    }

    private boolean saveCurrentSession(String roomId, double spawnX, double spawnY) {
        return mainScreen.saveCurrentGameplay(roomId, spawnX, spawnY);
    }

    /**
     * Instantly returns the player to the opening A1 spawn and makes that spot their new respawn.
     * Mirrors the dungeon debug warp by swapping rooms without a sliding transition.
     */
    private void teleportPlayerToOpeningSpawn(Player player) {
        if (player == null || worldMap == null) return;

        GCanvas canvas = mainScreen.getGCanvas();
        Room previousRoom = worldMap.getActiveRoom();
        if (previousRoom != null) {
            previousRoom.removeFrom(canvas);
        }
        worldMap.hideSpecialMarkers();

        if (!worldMap.setActiveRoomById(PLAYER_START_ROOM_ID)) {
            if (previousRoom != null) {
                previousRoom.addTo(canvas);
                worldMap.showSpecialMarkersForActiveRoom();
                player.draw(canvas);
            }
            return;
        }

        Room spawnRoom = worldMap.getActiveRoom();
        player.removeSwingFrom(canvas);
        spawnRoom.addTo(canvas);
        spawnRoom.reset();
        worldMap.showSpecialMarkersForActiveRoom();

        player.setTileMap(spawnRoom.getTileMap());
        player.setPosition(PLAYER_START_X, PLAYER_START_Y);
        player.setSpawnPosition(PLAYER_START_X, PLAYER_START_Y);
        player.draw(canvas);
        bringActiveRoomForegroundToFront();

        syncActiveRoomMusic();
        // updatePlayerHUD(player); // OLD: temp HUD from GraphicsPane — kept as fallback
        hud.updateHearts(this, player.getHP());
        hud.updateCoins(this, player.getCoins());
        hud.updateIntangibleAbilityButton(this, player.getIntangibleCooldownTicks() > 0);
        bringControlsCardToFront();

        if (debugOverlayOn) {
            drawDebugOverlay(canvas, player);
        } else {
            clearDebugOverlay(canvas);
        }

        GamePlayState.setCurrent(GamePlayState.PLAYING);
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

        // --- freeze everything while pause modal is open ---
        if (mainScreen.isPauseModalOpen()) return;

        GCanvas canvas = mainScreen.getGCanvas();

        // --- death delay countdown ---
        if (deathDelayTicks > 0) {
            deathDelayTicks--;
            player.tickDeathAnimation(); // swap to static frame once GIF cycle ends
            player.draw(canvas);
            bringActiveRoomForegroundToFront();
            // Freeze room logic during the death pause so enemies, hazards, and stale
            // combat hitboxes cannot keep advancing into the respawn.
            if (debugOverlayOn) drawDebugOverlay(canvas, player);
            if (deathDelayTicks <= 0) {
                deathDelayTicks = 0;
                player.removeSpriteFromCanvas(canvas); // clean up death visual
                player.resetDeathState();
                player.draw(canvas); // draw fresh idle sprite at spawn
                bringActiveRoomForegroundToFront();
            }
            // updatePlayerHUD(player); // OLD: temp HUD from GraphicsPane — kept as fallback
            hud.updateHearts(this, player.getHP());
            hud.updateCoins(this, player.getCoins());
            hud.updateIntangibleAbilityButton(this, player.getIntangibleCooldownTicks() > 0);
            bringControlsCardToFront();
            hud.bringToFront();
            return; // no player input while dying
        }

        // --- player input and animation (PLAYING state only) ---
        // Skipped during TRANSITIONING so the player cannot move mid-pan.
        // Also skipped during PAUSED, DIALOGUE, and CUTSCENE (those states freeze input).
        if (GamePlayState.PLAYING.is()) {
            Room activeRoom = worldMap.getActiveRoom();
            player.update(
                mainScreen.getInputHandler(),
                activeRoom.getEnemies(),
                activeRoom.getProjectiles(),
                dt
            );

            // Catch lethal projectile or hazard damage that happened during player.update().
            startDeathSequenceIfNeeded(player, canvas);

            // Sync animation frame and sprite position after movement
            player.draw(canvas);
        }

        // --- world update (always runs; WorldMap handles state internally) ---
        worldMap.update(dt, player);
        syncHudIntangibleOwnershipIfChanged(player);
        syncHudHalfDamageOwnershipIfChanged(player);
        syncActiveRoomMusic();
        bringActiveRoomForegroundToFront();

        // Enemy contact damage lands inside Room.update(), so re-check after the room tick too.
        startDeathSequenceIfNeeded(player, canvas);

        // --- debug overlay (F6) ---
        if (debugOverlayOn && GamePlayState.PLAYING.is()) {
            drawDebugOverlay(canvas, player);
        }

        // updatePlayerHUD(player); // OLD: temp HUD from GraphicsPane — kept as fallback
        hud.updateHearts(this, player.getHP());
        hud.updateCoins(this, player.getCoins());
        hud.updateIntangibleAbilityButton(this, player.getIntangibleCooldownTicks() > 0);
        bringControlsCardToFront();
        hud.bringToFront();
    }

    @Override
    public boolean tryHandleOverlayKeyPressed(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.VK_H) {
            return false;
        }
        if (controlsCardVisible) {
            hideControlsCard();
        } else {
            showControlsCard();
        }
        return true;
    }

    @Override
    public boolean tryHandleOverlayClick(java.awt.event.MouseEvent e) {
        if (!controlsCardVisible) {
            return false;
        }
        double x = e.getX();
        double y = e.getY();
        if (controlsCardCloseFrame != null && controlsCardCloseFrame.contains(x, y)) {
            hideControlsCard();
            return true;
        }
        if (controlsCardCloseLabel != null && controlsCardCloseLabel.contains(x, y)) {
            hideControlsCard();
            return true;
        }
        return false;
    }

    private void startDeathSequenceIfNeeded(Player player, GCanvas canvas) {
        if (player == null || player.isAlive() || player.isDying() || deathDelayTicks > 0) {
            return;
        }
        if (worldMap != null) {
            worldMap.handlePlayerDeath(player);
        }
        player.removeSwingFrom(canvas);
        player.triggerDeathAnimation();
        deathDelayTicks = DEATH_DELAY;
        player.draw(canvas);
        bringActiveRoomForegroundToFront();
    }

    private void syncActiveRoomMusic() {
        if (worldMap == null) {
            return;
        }
        Room activeRoom = worldMap.getActiveRoom();
        String roomId = activeRoom == null ? null : activeRoom.getRoomId();
        if (roomId == null ? lastMusicRoomId == null : roomId.equals(lastMusicRoomId)) {
            return;
        }
        lastMusicRoomId = roomId;
        if ("D3".equals(roomId)) {
            GameMusic.startBossMusic();
        } else if (roomId != null && roomId.startsWith("D")) {
            GameMusic.startMysteriousDungeonMusic();
        } else if (roomId != null) {
            GameMusic.startOverworldMusic();
        } else {
            GameMusic.stopJourneyBeginsMusic();
            GameMusic.stopMysteriousDungeonMusic();
            GameMusic.stopOverworldMusic();
            GameMusic.stopBossMusic();
        }
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
    // INPUT WIRING — attack, use/talk, and relic ability
    // =========================================================

    /**
     * Binds discrete-action keys (attack, use / talk, ability) via InputHandler.onPress().
     * WASD movement is already handled inside Player.update() via isHeld().
     * K → {@link Player#activateIntangible()} (Courage relic shield + cooldown gated inside Player).
     * Called from showContent(); safe to call multiple times (no-ops after first).
     */
    private void wireInputOnce() {
        if (inputsWired) return;
        InputHandler input = mainScreen.getInputHandler();
        if (input == null) return;

        input.onPress(KeyEvent.VK_J, () -> {
            if (!mainScreen.isPauseModalOpen() && GamePlayState.PLAYING.is()) {
                Player p = mainScreen.getPlayer();
                if (p != null) p.attack();
            }
        });

        // Relic intangible: logic + blue aura live in Player; return value ignored (no toast HUD yet).
        input.onPress(KeyEvent.VK_K, () -> {
            if (!mainScreen.isPauseModalOpen() && GamePlayState.PLAYING.is()) {
                Player p = mainScreen.getPlayer();
                if (p != null && p.hasIntangible()) {
                    p.activateIntangible();
                }
            }
        });

        input.onPress(KeyEvent.VK_E, () -> {
            if (!mainScreen.isPauseModalOpen() && GamePlayState.PLAYING.is()) {
                Player p = mainScreen.getPlayer();
                Room activeRoom = worldMap.getActiveRoom();
                if (p != null && activeRoom != null && activeRoom.tryInteract(p)) {
                    return;
                }
                SavePoint savePoint = worldMap.getActiveSavePoint();
                if (p != null && savePoint != null) {
                    savePoint.tryInteract(
                        p,
                        mainScreen.getDialogue(),
                        () -> saveCurrentSession(
                            savePoint.getRoomId(),
                            savePoint.getSpawnX(),
                            savePoint.getSpawnY()
                        )
                    );
                }
            }
        });

        if (MainApplication.DEBUG_SHORTCUTS_ENABLED) {
            // Debug: toggle combat overlay
            input.onPress(KeyEvent.VK_F6, () -> {
                debugOverlayOn = !debugOverlayOn;
                worldDebugMarkersVisible = debugOverlayOn;
                if (debugOverlayOn) {
                    logTrialDebugSnapshot(mainScreen.getPlayer());
                }
                if (!debugOverlayOn) clearDebugOverlay(mainScreen.getGCanvas());
            });

            // Debug: teleport to dungeon D1
            input.onPress(KeyEvent.VK_F5, () -> {
                if (!mainScreen.isPauseModalOpen() && GamePlayState.PLAYING.is()) {
                    Player p = mainScreen.getPlayer();
                    if (p != null) worldMap.enterDungeon(p);
                }
            });

            // Debug: return to the opening spawn in A1.
            input.onPress(KeyEvent.VK_F7, () -> {
                if (!mainScreen.isPauseModalOpen() && GamePlayState.PLAYING.is()) {
                    Player p = mainScreen.getPlayer();
                    if (p != null) teleportPlayerToOpeningSpawn(p);
                }
            });

            // Debug: quick-save the current room + exact player position.
            input.onPress(KeyEvent.VK_F8, () -> {
                if (!mainScreen.isPauseModalOpen() && GamePlayState.PLAYING.is()) {
                    Player p = mainScreen.getPlayer();
                    Room activeRoom = worldMap.getActiveRoom();
                    if (p != null && activeRoom != null) {
                        saveCurrentSession(activeRoom.getRoomId(), p.getX(), p.getY());
                    }
                }
            });
        }

        inputsWired = true;
    }

    /**
     * Unbinds the keys wired in wireInputOnce(). Called from hideContent().
     */
    private void unwireInput() {
        InputHandler input = mainScreen.getInputHandler();
        if (input == null) { inputsWired = false; return; }
        input.removeOnPress(KeyEvent.VK_J);
        input.removeOnPress(KeyEvent.VK_K);
        input.removeOnPress(KeyEvent.VK_E);
        input.removeOnPress(KeyEvent.VK_F5);
        input.removeOnPress(KeyEvent.VK_F6);
        input.removeOnPress(KeyEvent.VK_F7);
        input.removeOnPress(KeyEvent.VK_F8);
        debugOverlayOn = false;
        worldDebugMarkersVisible = false;
        clearDebugOverlay(mainScreen.getGCanvas());
        inputsWired = false;
    }

    /** Returns true when gameplay debug visuals that live inside rooms should be shown. */
    public static boolean areWorldDebugMarkersVisible() {
        return worldDebugMarkersVisible;
    }

    // =========================================================
    // CONTROLS CARD HELPERS
    // =========================================================

    private void showControlsCard() {
        clearControlsCardVisuals();
        controlsCardVisible = true;
        String[][] rows = getControlsCardRows(mainScreen.getPlayer());

        double scale = uniformScale();
        double marginRight = 34.0 * scale;
        double bottomMargin = 42.0 * scale;
        double cardW = 186.0 * scale;
        double topPad = 10.0 * scale;
        double sidePad = 10.0 * scale;
        double closeSide = 19.0 * scale;
        double headerH = 22.0 * scale;
        double rowH = 26.0 * scale;
        double rowGap = 6.0 * scale;
        double bottomPad = 12.0 * scale;
        double shadowOffset = 4.0 * scale;
        double tabW = 6.0 * scale;
        double tabH = 16.0 * scale;
        double cardH = topPad + headerH + 8.0 * scale
            + rows.length * rowH
            + (rows.length - 1) * rowGap
            + bottomPad;
        double cardX = originX() + mainScreen.getLayoutWidth() - cardW - marginRight;
        double cardY = originY() + mainScreen.getLayoutHeight() - cardH - bottomMargin;
        double cardArc = 14.0 * scale;

        acm.graphics.GRoundRect shadow =
            new acm.graphics.GRoundRect(
                cardX + shadowOffset, cardY + shadowOffset, cardW, cardH, cardArc, cardArc);
        shadow.setFilled(true);
        shadow.setFillColor(CONTROLS_CARD_SHADOW);
        shadow.setColor(CONTROLS_CARD_SHADOW);
        addControlsCardObject(shadow);

        acm.graphics.GRect leftTab =
            new acm.graphics.GRect(cardX - tabW + 1.0, cardY + 30.0 * scale, tabW, tabH);
        leftTab.setFilled(true);
        leftTab.setFillColor(CONTROLS_CARD_TAB);
        leftTab.setColor(CONTROLS_CARD_EDGE);
        addControlsCardObject(leftTab);

        acm.graphics.GRect rightTab =
            new acm.graphics.GRect(cardX + cardW - 1.0, cardY + cardH - 46.0 * scale, tabW, tabH);
        rightTab.setFilled(true);
        rightTab.setFillColor(CONTROLS_CARD_TAB);
        rightTab.setColor(CONTROLS_CARD_EDGE);
        addControlsCardObject(rightTab);

        acm.graphics.GRoundRect outer =
            new acm.graphics.GRoundRect(cardX, cardY, cardW, cardH, cardArc, cardArc);
        outer.setFilled(true);
        outer.setFillColor(CONTROLS_CARD_PARCHMENT);
        outer.setColor(CONTROLS_CARD_EDGE);
        addControlsCardObject(outer);

        double innerInset = 7.0 * scale;
        acm.graphics.GRoundRect inner =
            new acm.graphics.GRoundRect(
                cardX + innerInset,
                cardY + innerInset,
                cardW - innerInset * 2.0,
                cardH - innerInset * 2.0,
                Math.max(8.0, cardArc - innerInset),
                Math.max(8.0, cardArc - innerInset));
        inner.setFilled(true);
        inner.setFillColor(CONTROLS_CARD_PARCHMENT_INNER);
        inner.setColor(CONTROLS_CARD_EDGE);
        addControlsCardObject(inner);

        acm.graphics.GLabel title = pixelLabel("CONTROLS", 14, CONTROLS_CARD_TITLE);
        double titleY = cardY + topPad + title.getAscent();
        title.setLocation(cardX + sidePad, titleY);
        addControlsCardObject(title);

        controlsCardCloseFrame =
            new acm.graphics.GRoundRect(
                cardX + cardW - sidePad - closeSide,
                cardY + topPad - 2.0 * scale,
                closeSide,
                closeSide,
                5.0 * scale,
                5.0 * scale);
        controlsCardCloseFrame.setFilled(true);
        controlsCardCloseFrame.setFillColor(CONTROLS_CARD_TITLE);
        controlsCardCloseFrame.setColor(CONTROLS_CARD_EDGE);
        addControlsCardObject(controlsCardCloseFrame);

        controlsCardCloseLabel = pixelLabel("X", 10, CONTROLS_CARD_PARCHMENT_INNER);
        addControlsCardObject(controlsCardCloseLabel);
        centerControlsCardLabel(
            controlsCardCloseLabel,
            controlsCardCloseFrame.getX(),
            controlsCardCloseFrame.getY(),
            controlsCardCloseFrame.getWidth(),
            controlsCardCloseFrame.getHeight());

        acm.graphics.GLine divider =
            new acm.graphics.GLine(
                cardX + sidePad,
                cardY + topPad + headerH,
                cardX + cardW - sidePad,
                cardY + topPad + headerH);
        divider.setColor(CONTROLS_CARD_EDGE);
        addControlsCardObject(divider);

        double rowX = cardX + sidePad;
        double rowW = cardW - sidePad * 2.0;
        double rowY = cardY + topPad + headerH + 8.0 * scale;
        double rowTextPad = 10.0 * scale;

        for (String[] row : rows) {
            acm.graphics.GRoundRect rowShadow =
                new acm.graphics.GRoundRect(
                    rowX + 1.5 * scale,
                    rowY + 2.0 * scale,
                    rowW,
                    rowH,
                    8.0 * scale,
                    8.0 * scale);
            rowShadow.setFilled(true);
            rowShadow.setFillColor(new java.awt.Color(45, 83, 63, 95));
            rowShadow.setColor(new java.awt.Color(45, 83, 63, 95));
            addControlsCardObject(rowShadow);

            acm.graphics.GRoundRect rowFrame =
                new acm.graphics.GRoundRect(rowX, rowY, rowW, rowH, 8.0 * scale, 8.0 * scale);
            rowFrame.setFilled(true);
            rowFrame.setFillColor(CONTROLS_CARD_BUTTON);
            rowFrame.setColor(CONTROLS_CARD_BUTTON_DARK);
            addControlsCardObject(rowFrame);

            acm.graphics.GLine highlight =
                new acm.graphics.GLine(
                    rowX + 8.0 * scale,
                    rowY + 5.0 * scale,
                    rowX + rowW - 8.0 * scale,
                    rowY + 5.0 * scale);
            highlight.setColor(CONTROLS_CARD_BUTTON_HIGHLIGHT);
            addControlsCardObject(highlight);

            acm.graphics.GLabel actionLabel = pixelLabel(row[0], 10, CONTROLS_CARD_TITLE);
            double actionBaseY = rowY + (rowH + actionLabel.getAscent() - actionLabel.getDescent()) / 2.0;
            actionLabel.setLocation(rowX + rowTextPad, actionBaseY);
            addControlsCardObject(actionLabel);

            acm.graphics.GLabel keyLabel = pixelLabel(row[1], 10, CONTROLS_CARD_TITLE);
            addControlsCardObject(keyLabel);
            double keyBaseY = rowY + (rowH + keyLabel.getAscent() - keyLabel.getDescent()) / 2.0;
            keyLabel.setLocation(rowX + rowW - rowTextPad - keyLabel.getWidth(), keyBaseY);

            rowY += rowH + rowGap;
        }

        bringControlsCardToFront();
    }

    private void hideControlsCard() {
        controlsCardVisible = false;
        clearControlsCardVisuals();
    }

    private void clearControlsCardVisuals() {
        if (controlsCardObjects.isEmpty()) {
            controlsCardCloseFrame = null;
            controlsCardCloseLabel = null;
            return;
        }
        for (acm.graphics.GObject obj : controlsCardObjects) {
            mainScreen.remove(obj);
            contents.remove(obj);
        }
        controlsCardObjects.clear();
        controlsCardCloseFrame = null;
        controlsCardCloseLabel = null;
    }

    private void addControlsCardObject(acm.graphics.GObject obj) {
        controlsCardObjects.add(obj);
        place(obj);
    }

    private void bringControlsCardToFront() {
        if (!controlsCardVisible) {
            return;
        }
        if (mainScreen.getDialogue() != null && mainScreen.getDialogue().isOpen()) {
            return;
        }
        for (acm.graphics.GObject obj : controlsCardObjects) {
            if (obj != null) {
                obj.sendToFront();
            }
        }
    }

    private void bringActiveRoomForegroundToFront() {
        if (worldMap == null) {
            return;
        }
        Room activeRoom = worldMap.getActiveRoom();
        if (activeRoom != null) {
            activeRoom.bringForegroundToFront();
        }
    }

    private String[][] getControlsCardRows(Player player) {
        String[][] base = (player != null && player.hasIntangible())
            ? BASE_CONTROLS_CARD_ROWS
            : BASE_CONTROLS_CARD_ROWS_NO_ABILITY;

        if (!MainApplication.DEBUG_SHORTCUTS_ENABLED) {
            return base;
        }

        String[][] rows = new String[base.length + DEBUG_CONTROLS_CARD_ROWS.length][];
        System.arraycopy(base, 0, rows, 0, base.length);
        System.arraycopy(
            DEBUG_CONTROLS_CARD_ROWS,
            0,
            rows,
            base.length,
            DEBUG_CONTROLS_CARD_ROWS.length
        );
        return rows;
    }

    /** Rebuilds relic HUD + optional controls row when {@link Player#hasIntangible()} changes in-world. */
    private void syncHudIntangibleOwnershipIfChanged(Player player) {
        boolean owned = player.hasIntangible();
        if (owned == hudBuiltWithIntangibleOwned) {
            return;
        }
        hudBuiltWithIntangibleOwned = owned;
        hud.showAll(this, buildHudSnapshot(player));
        if (controlsCardVisible) {
            showControlsCard();
        }
    }

    /** Rebuilds the HUD to switch to the upgraded quarter-heart bar when the Strength relic is picked up. */
    private void syncHudHalfDamageOwnershipIfChanged(Player player) {
        boolean owned = player.hasHalfDamage();
        if (owned == hudBuiltWithHalfDamageOwned) {
            return;
        }
        hudBuiltWithHalfDamageOwned = owned;
        hud.showAll(this, buildHudSnapshot(player));
        if (controlsCardVisible) {
            showControlsCard();
        }
    }

    private void centerControlsCardLabel(
        acm.graphics.GLabel label, double x, double y, double width, double height) {
        label.setLocation(
            x + (width - label.getWidth()) / 2.0,
            y + (height + label.getAscent() - label.getDescent()) / 2.0);
    }

    /**
     * Builds a HudSnapshot from the current player state for passing to HUDoverlay.
     * useUpgradedHeartBar is true when the player's max health exceeds the default (health relic active).
     */
    private HUDoverlay.HudSnapshot buildHudSnapshot(Player p) {
        boolean upgraded = p.getMaxHealth() > Player.DEFAULT_HEART_COUNT * Player.HALF_HEARTS_PER_HEART;
        return new HUDoverlay.HudSnapshot(
            p.getHP(),
            upgraded,
            false,
            p.getCoins(),
            p.hasIntangible(),
            p.hasHalfDamage(),
            p.hasReflect(),
            p.getIntangibleCooldownTicks() > 0
        );
    }

    // =========================================================
    // DEBUG OVERLAY (F6)
    // =========================================================

    /**
     * Draws hitbox outlines, health bars, AI state, and animation state for
     * the player and all enemies in the active room. Redrawn every tick.
     */
    private void drawDebugOverlay(acm.graphics.GCanvas canvas, Player player) {
        clearDebugOverlay(canvas);

        Room activeRoom = worldMap.getActiveRoom();
        java.util.List<Enemy> enemies = activeRoom.getEnemies();
        java.util.List<Projectile> projectiles = activeRoom.getProjectiles();

        // --- player hitbox (blue) ---
        Hitbox ph = player.getHitbox();
        acm.graphics.GRect pRect = new acm.graphics.GRect(ph.x, ph.y, ph.width, ph.height);
        pRect.setColor(java.awt.Color.CYAN);
        canvas.add(pRect);
        debugObjects.add(pRect);

        // --- player cooldown bars (stacked above hitbox) ---
        double pBarW = 60, pBarH = 4, pBarX = ph.x - 6, pBarY = ph.y - 8;

        // Attack cooldown (magenta)
        drawCooldownBar(canvas, pBarX, pBarY, pBarW, pBarH,
            player.getAttackCooldownTicks(), player.getAttackCooldownMax(),
            java.awt.Color.MAGENTA, "ATK");
        pBarY -= 10;

        // iFrames (yellow)
        drawCooldownBar(canvas, pBarX, pBarY, pBarW, pBarH,
            player.getIframeTicks(), player.getIframeMax(),
            java.awt.Color.YELLOW, "iFRM");
        pBarY -= 10;

        // Courage shield active (blue glow)
        if (player.isIntangibleActive()) {
            drawCooldownBar(canvas, pBarX, pBarY, pBarW, pBarH,
                player.getIntangibleActiveTicks(), player.getIntangibleActiveMax(),
                new java.awt.Color(100, 150, 255), "SHLD");
            pBarY -= 10;
        }

        // Courage shield cooldown (dark blue)
        if (player.getIntangibleCooldownTicks() > 0) {
            drawCooldownBar(canvas, pBarX, pBarY, pBarW, pBarH,
                player.getIntangibleCooldownTicks(), player.getIntangibleCooldownMax(),
                new java.awt.Color(60, 60, 180), "K cd");
            pBarY -= 10;
        }

        // Death respawn timer (red)
        if (deathDelayTicks > 0) {
            drawCooldownBar(canvas, pBarX, pBarY, pBarW, pBarH,
                deathDelayTicks, DEATH_DELAY,
                java.awt.Color.RED, "DEAD");
            pBarY -= 10;
        }

        // Player state label
        String pState = "HP " + player.getHealth() + "/" + player.getMaxHealth();
        if (player.isDying()) pState += " | DYING";
        acm.graphics.GLabel pHp = new acm.graphics.GLabel(pState, pBarX, pBarY);
        pHp.setFont("Courier New-BOLD-11");
        pHp.setColor(java.awt.Color.CYAN);
        canvas.add(pHp);
        debugObjects.add(pHp);

        // --- player position readout (tile col,row + pixel x,y) ---
        int playerTileCol = (int) Math.floor((player.getX() - TileMap.MAP_OFFSET_X) / 48.0);
        int playerTileRow = (int) Math.floor(player.getY() / 48.0);
        String posText = String.format("Tile: %d,%d  Px: %.0f,%.0f  Room: %s",
            playerTileCol, playerTileRow,
            player.getX(), player.getY(),
            activeRoom.getRoomId());
        acm.graphics.GLabel posLabel = new acm.graphics.GLabel(posText, 10, 14);
        posLabel.setFont("Courier New-BOLD-12");
        posLabel.setColor(java.awt.Color.WHITE);
        canvas.add(posLabel);
        debugObjects.add(posLabel);
        drawTrialDebugPanel(canvas, activeRoom, player);

        // --- sword swing hitbox (magenta) ---
        SwordSwing swing = player.getActiveSwing();
        if (swing != null) {
            Hitbox sh = swing.getHitbox();
            acm.graphics.GRect sRect = new acm.graphics.GRect(sh.x, sh.y, sh.width, sh.height);
            sRect.setColor(java.awt.Color.MAGENTA);
            canvas.add(sRect);
            debugObjects.add(sRect);
        }

        for (Enemy e : enemies) {
            double ex = e.getX();
            double ey = e.getY();

            // --- enemy hitbox (red) ---
            Hitbox eh = e.getHitbox();
            acm.graphics.GRect eRect = new acm.graphics.GRect(eh.x, eh.y, eh.width, eh.height);
            eRect.setColor(java.awt.Color.RED);
            canvas.add(eRect);
            debugObjects.add(eRect);

            // --- line from enemy to player (red when aggro, dim when patrolling) ---
            acm.graphics.GLine toPlayer = new acm.graphics.GLine(ex, ey, player.getX(), player.getY());
            toPlayer.setColor(e.isAggro() ? java.awt.Color.RED : new java.awt.Color(255, 0, 0, 60));
            canvas.add(toPlayer);
            debugObjects.add(toPlayer);

            // --- aggro range circle (yellow) ---
            double aggroR = e.getAggroRange();
            acm.graphics.GOval aggroCircle = new acm.graphics.GOval(
                ex - aggroR, ey - aggroR, aggroR * 2, aggroR * 2);
            aggroCircle.setColor(new java.awt.Color(255, 255, 0, 80));
            canvas.add(aggroCircle);
            debugObjects.add(aggroCircle);

            if (e instanceof RangedEnemy) {
                double retreatR = ((RangedEnemy) e).getRetreatDistance();
                acm.graphics.GOval retreatCircle = new acm.graphics.GOval(
                    ex - retreatR, ey - retreatR, retreatR * 2, retreatR * 2);
                retreatCircle.setColor(new java.awt.Color(120, 220, 255, 80));
                canvas.add(retreatCircle);
                debugObjects.add(retreatCircle);
            }

            // --- debug health bar (green/red) below enemy ---
            double barW = 40, barH = 4;
            double barX = ex - barW / 2;
            double barY = eh.y + eh.height + 8;

            acm.graphics.GRect bgBar = new acm.graphics.GRect(barX, barY, barW, barH);
            bgBar.setFilled(true);
            bgBar.setFillColor(java.awt.Color.DARK_GRAY);
            canvas.add(bgBar);
            debugObjects.add(bgBar);

            double hpRatio = (double) e.getHealth() / e.getMaxHealth();
            if (hpRatio > 0) {
                acm.graphics.GRect hpBar = new acm.graphics.GRect(barX, barY, barW * hpRatio, barH);
                hpBar.setFilled(true);
                hpBar.setFillColor(hpRatio > 0.5 ? java.awt.Color.GREEN : java.awt.Color.RED);
                canvas.add(hpBar);
                debugObjects.add(hpBar);
            }

            // --- patrol path (orange waypoints + lines) ---
            java.util.List<double[]> path = e.getPatrolPath();
            if (path != null && !path.isEmpty()) {
                int pidx = e.getPatrolIndex();
                for (int i = 0; i < path.size(); i++) {
                    double[] wp = path.get(i);
                    boolean isTarget = (i == pidx);

                    // Waypoint dot
                    double dotSize = isTarget ? 8 : 5;
                    acm.graphics.GOval dot = new acm.graphics.GOval(
                        wp[0] - dotSize / 2, wp[1] - dotSize / 2, dotSize, dotSize);
                    dot.setFilled(true);
                    dot.setFillColor(isTarget ? java.awt.Color.ORANGE : new java.awt.Color(255, 165, 0, 120));
                    dot.setColor(isTarget ? java.awt.Color.ORANGE : new java.awt.Color(255, 165, 0, 120));
                    canvas.add(dot);
                    debugObjects.add(dot);

                    // Line to next waypoint
                    double[] next = path.get((i + 1) % path.size());
                    acm.graphics.GLine seg = new acm.graphics.GLine(wp[0], wp[1], next[0], next[1]);
                    seg.setColor(new java.awt.Color(255, 165, 0, 80));
                    canvas.add(seg);
                    debugObjects.add(seg);
                }

                // Line from enemy to current target waypoint
                double[] curWp = path.get(pidx);
                acm.graphics.GLine toWp = new acm.graphics.GLine(ex, ey, curWp[0], curWp[1]);
                toWp.setColor(java.awt.Color.ORANGE);
                canvas.add(toWp);
                debugObjects.add(toWp);
            }

            // --- enemy attack cooldown bar (magenta) ---
            double eBarY = barY + barH + 8;
            if (e.getAttackCooldown() > 0) {
                drawCooldownBar(canvas, barX, eBarY, barW, barH,
                    e.getAttackCooldown(), 180,
                    java.awt.Color.MAGENTA, "ATK");
                eBarY += 12;
            }

            // --- state label ---
            String stateText = (e.isAggro() ? "CHASE" : "PATROL")
                + " | " + e.getAnimState().name()
                + " | HP " + e.getHealth() + "/" + e.getMaxHealth();
            acm.graphics.GLabel stateLbl = new acm.graphics.GLabel(stateText, 0, 0);
            stateLbl.setFont("Courier New-BOLD-9");
            stateLbl.setColor(java.awt.Color.WHITE);
            stateLbl.setLocation(barX, eBarY + stateLbl.getAscent());
            canvas.add(stateLbl);
            debugObjects.add(stateLbl);
        }

        for (Projectile projectile : projectiles) {
            java.awt.Color projColor = projectile.isReflected()
                ? new java.awt.Color(100, 210, 255)
                : new java.awt.Color(255, 180, 90);

            Hitbox projHitbox = projectile.getHitbox();
            acm.graphics.GRect projRect = new acm.graphics.GRect(
                projHitbox.x, projHitbox.y, projHitbox.width, projHitbox.height);
            projRect.setColor(projColor);
            canvas.add(projRect);
            debugObjects.add(projRect);

            java.util.List<double[]> trail = projectile.getTrailPoints();
            for (int i = 1; i < trail.size(); i++) {
                double[] a = trail.get(i - 1);
                double[] b = trail.get(i);
                acm.graphics.GLine seg = new acm.graphics.GLine(a[0], a[1], b[0], b[1]);
                seg.setColor(projectile.isReflected()
                    ? new java.awt.Color(120, 230, 255, 110)
                    : new java.awt.Color(255, 190, 110, 110));
                canvas.add(seg);
                debugObjects.add(seg);
            }

            double headingLen = 24.0;
            double vx = projectile.getVelocityX();
            double vy = projectile.getVelocityY();
            double speed = Math.max(1.0, projectile.getCurrentSpeed());
            acm.graphics.GLine heading = new acm.graphics.GLine(
                projectile.getX(),
                projectile.getY(),
                projectile.getX() + (vx / speed) * headingLen,
                projectile.getY() + (vy / speed) * headingLen
            );
            heading.setColor(projColor);
            canvas.add(heading);
            debugObjects.add(heading);

            String ownerName = projectile.getOwner() == null
                ? "Unknown"
                : projectile.getOwner().getClass().getSimpleName();
            String projLabelText = String.format(
                "%s shot | %s | %.0f px/s | age %.2fs | %s",
                ownerName,
                projectile.getMotionProfileLabel(),
                projectile.getCurrentSpeed(),
                projectile.getAgeSeconds(),
                projectile.isReflected() ? "REFLECTED" : projectile.getDirection().name()
            );
            acm.graphics.GLabel projLabel = new acm.graphics.GLabel(projLabelText, 0, 0);
            projLabel.setFont("Courier New-BOLD-9");
            projLabel.setColor(projColor);
            projLabel.setLocation(projectile.getX() + 12, projectile.getY() - 10);
            canvas.add(projLabel);
            debugObjects.add(projLabel);
        }

        for (Item item : activeRoom.getDroppedItems()) {
            Hitbox itemHitbox = item.getPickupHitbox();
            acm.graphics.GRect itemRect =
                new acm.graphics.GRect(itemHitbox.x, itemHitbox.y, itemHitbox.width, itemHitbox.height);
            itemRect.setColor(new java.awt.Color(255, 215, 80));
            canvas.add(itemRect);
            debugObjects.add(itemRect);

            String itemText = item.getDisplayName();
            if (item instanceof Coin) {
                itemText = "coin +" + ((Coin) item).getValue();
            }
            acm.graphics.GLabel itemLabel = new acm.graphics.GLabel(itemText, 0, 0);
            itemLabel.setFont("Courier New-BOLD-10");
            itemLabel.setColor(new java.awt.Color(255, 220, 120));
            canvas.add(itemLabel);
            double labelX = itemHitbox.x + (itemHitbox.width - itemLabel.getWidth()) / 2.0;
            double labelY = Math.max(
                originY() + itemLabel.getAscent() + 6.0,
                itemHitbox.y - 4.0
            );
            itemLabel.setLocation(labelX, labelY);
            debugObjects.add(itemLabel);
        }

        // --- tile grid overlay ---
        int tileSize = 48;
        int mapOffsetX = TileMap.MAP_OFFSET_X; // 16
        int gridCols = 26;
        int gridRows = 15;
        java.awt.Color gridLineColor  = new java.awt.Color(255, 255, 255, 55);
        java.awt.Color gridLabelColor = new java.awt.Color(255, 255, 100, 200);
        java.awt.Color wallOverlay    = new java.awt.Color(255, 0, 0, 60);
        java.awt.Color floorOverlay   = new java.awt.Color(0, 255, 0, 30);

        TileMap debugTileMap = activeRoom.getTileMap();

        // tile type shading: red tint = WALL (blocked), green tint = FLOOR (walkable)
        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                Tile t = debugTileMap.getTileAt(c, r);
                boolean passable = (t != null && t.isPassable());
                double tx = mapOffsetX + c * tileSize;
                double ty = r * tileSize;
                acm.graphics.GRect tileShade = new acm.graphics.GRect(tx, ty, tileSize, tileSize);
                tileShade.setFilled(true);
                tileShade.setFillColor(passable ? floorOverlay : wallOverlay);
                tileShade.setColor(passable ? floorOverlay : wallOverlay);
                canvas.add(tileShade);
                debugObjects.add(tileShade);
            }
        }

        // vertical lines (one per column boundary: col 0 … col 26)
        for (int c = 0; c <= gridCols; c++) {
            double lx = mapOffsetX + c * tileSize;
            acm.graphics.GLine vLine = new acm.graphics.GLine(lx, 0, lx, gridRows * tileSize);
            vLine.setColor(gridLineColor);
            canvas.add(vLine);
            debugObjects.add(vLine);
        }

        // horizontal lines (one per row boundary: row 0 … row 15)
        for (int r = 0; r <= gridRows; r++) {
            double ly = r * tileSize;
            acm.graphics.GLine hLine = new acm.graphics.GLine(
                mapOffsetX, ly, mapOffsetX + gridCols * tileSize, ly);
            hLine.setColor(gridLineColor);
            canvas.add(hLine);
            debugObjects.add(hLine);
        }

        // (col,row) label at the top-left corner of every cell
        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                double lx = mapOffsetX + c * tileSize + 2;
                double ly = r * tileSize + 9;
                acm.graphics.GLabel coordLabel = new acm.graphics.GLabel(c + "," + r, lx, ly);
                coordLabel.setFont("Courier New-BOLD-8");
                coordLabel.setColor(gridLabelColor);
                canvas.add(coordLabel);
                debugObjects.add(coordLabel);
            }
        }
    }

    /**
     * Draws a labeled cooldown bar: dark background + colored fill proportional to remaining/max.
     */
    private void drawCooldownBar(acm.graphics.GCanvas canvas,
            double x, double y, double w, double h,
            int remaining, int max, java.awt.Color color, String label) {
        if (max <= 0) return;

        acm.graphics.GRect bg = new acm.graphics.GRect(x, y, w, h);
        bg.setFilled(true);
        bg.setFillColor(java.awt.Color.DARK_GRAY);
        canvas.add(bg);
        debugObjects.add(bg);

        double ratio = (double) remaining / max;
        if (ratio > 0) {
            acm.graphics.GRect bar = new acm.graphics.GRect(x, y, w * ratio, h);
            bar.setFilled(true);
            bar.setFillColor(color);
            canvas.add(bar);
            debugObjects.add(bar);
        }

        acm.graphics.GLabel lbl = new acm.graphics.GLabel(label, x + w + 3, y + h);
        lbl.setFont("Courier New-BOLD-8");
        lbl.setColor(color);
        canvas.add(lbl);
        debugObjects.add(lbl);
    }

    /** Trial-chamber F6 panel: condensed room-specific state for A2/A3/B3 plus console snapshot on toggle. */
    private void drawTrialDebugPanel(acm.graphics.GCanvas canvas, Room activeRoom, Player player) {
        java.util.List<String> lines = worldMap.getTrialDebugLines(activeRoom, player);
        if (lines == null || lines.isEmpty()) {
            return;
        }

        double panelX = 10.0;
        double panelY = 22.0;
        double panelW = 430.0;
        double lineH = 15.0;
        double panelH = 10.0 + lines.size() * lineH;

        acm.graphics.GRect panel = new acm.graphics.GRect(panelX, panelY, panelW, panelH);
        panel.setFilled(true);
        panel.setFillColor(new java.awt.Color(15, 18, 28, 195));
        panel.setColor(new java.awt.Color(115, 180, 255, 210));
        canvas.add(panel);
        debugObjects.add(panel);

        for (int i = 0; i < lines.size(); i++) {
            acm.graphics.GLabel line = new acm.graphics.GLabel(lines.get(i), panelX + 8.0, panelY + 16.0 + i * lineH);
            line.setFont("Courier New-BOLD-11");
            line.setColor(i == 0 ? new java.awt.Color(150, 220, 255) : java.awt.Color.WHITE);
            canvas.add(line);
            debugObjects.add(line);
        }
    }

    private void logTrialDebugSnapshot(Player player) {
        Room activeRoom = worldMap == null ? null : worldMap.getActiveRoom();
        java.util.List<String> lines = worldMap == null ? null : worldMap.getTrialDebugLines(activeRoom, player);
        if (lines == null || lines.isEmpty()) {
            System.out.println("[TRIAL DEBUG] F6 enabled outside a trial chamber.");
            return;
        }
        for (String line : lines) {
            System.out.println("[TRIAL DEBUG] " + line);
        }
    }

    /** Removes all debug overlay GObjects from the canvas. */
    private void clearDebugOverlay(acm.graphics.GCanvas canvas) {
        if (canvas == null) return;
        for (acm.graphics.GObject obj : debugObjects) {
            canvas.remove(obj);
        }
        debugObjects.clear();
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
