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

    // =========================================================
    // CONTROLS HINT OVERLAY (tech-demo placeholder)
    // =========================================================

    /*
     * =====================
     * TECH DEMO: Simple on-screen control labels so playtesters know what buttons to press.
     * RIG POINT: Remove these labels once HUDoverlay.showAll() is wired into GameplayPane and
     *            a proper controls indicator is part of the real HUD design.
     *            To remove: delete the four GLabel fields below, the addControlsHint() call in
     *            showContent(), and the removeControlsHint() call in hideContent().
     * =====================
     */

    /** "Move: WASD" hint label — bottom-right corner. */
    private acm.graphics.GLabel hintMove;
    /** "Attack: J" hint label. */
    private acm.graphics.GLabel hintAttack;
    /** "Ability: K" hint label. */
    private acm.graphics.GLabel hintAbility;
    /** "Pause: ESC" hint label. */
    private acm.graphics.GLabel hintPause;
    /** Debug shortcut hints — separated visually from the gameplay controls. */
    private acm.graphics.GLabel hintDebug;
    private acm.graphics.GLabel hintDebug2;

    // =========================================================
    // DEBUG OVERLAY (F6)
    // =========================================================

    /** True when the F6 debug overlay is visible. */
    private boolean debugOverlayOn = false;

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

        deathDelayTicks = 0;

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

        // --- controls hint (tech-demo only) ---
        // TECH DEMO: added last so it renders above tiles and entities.
        // RIG POINT: remove this call once HUDoverlay.showAll() is wired in.
        addControlsHint(canvas);

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

        // --- remove player sprite ---
        if (player != null) {
            player.removeSpriteFromCanvas(canvas);
        }

        // --- remove controls hint (tech-demo only) ---
        // RIG POINT: remove this call once HUDoverlay.showAll() is wired in.
        removeControlsHint(canvas);

        // --- remove debug overlay ---
        clearDebugOverlay(canvas);
    }

    /**
     * Resets pane state so the next showContent() starts a fresh game.
     * Called from MainApplication.switchToGameOverScreen() before screen switch.
     */
    public void resetForNewGame() {
        firstShow = true;
        deathDelayTicks = 0;
        debugOverlayOn = false;
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
            worldMap.update(dt, player); // keep enemies roaming while dead
            if (debugOverlayOn) drawDebugOverlay(canvas, player);
            restackHintsOnTop(canvas);
            if (deathDelayTicks <= 0) {
                deathDelayTicks = 0;
                player.removeSpriteFromCanvas(canvas); // clean up death visual
                player.resetDeathState();
                player.draw(canvas); // draw fresh idle sprite at spawn
            }
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

            // --- player death check (before draw so the death sprite renders immediately) ---
            if (!player.isAlive()) {
                player.triggerDeathAnimation();
                deathDelayTicks = DEATH_DELAY;
            }

            // Sync animation frame and sprite position after movement
            player.draw(canvas);
        }

        // --- world update (always runs; WorldMap handles state internally) ---
        worldMap.update(dt, player);

        // --- debug overlay (F6) ---
        if (debugOverlayOn && GamePlayState.PLAYING.is()) {
            drawDebugOverlay(canvas, player);
        }

        // --- keep hint labels on top of room tiles (tech-demo only) ---
        // New room tiles added during a transition would bury the labels; re-stacking
        // them here every tick ensures they remain visible regardless of draw order.
        // RIG POINT: remove this call once HUDoverlay.showAll() is wired in.
        restackHintsOnTop(canvas);
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
    // INPUT WIRING — attack (J) and relic ability (K)
    // =========================================================

    /**
     * Binds discrete-action keys (attack, ability) via InputHandler.onPress().
     * WASD movement is already handled inside Player.update() via isHeld().
     * K → {@link Player#activateIntangible()} (relic + cooldown gated inside Player; no on-screen hint here).
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
                if (p != null) {
                    p.activateIntangible();
                }
            }
        });

        // Debug: toggle combat overlay
        input.onPress(KeyEvent.VK_F6, () -> {
            debugOverlayOn = !debugOverlayOn;
            if (!debugOverlayOn) clearDebugOverlay(mainScreen.getGCanvas());
        });

        // Debug: teleport to dungeon D1
        input.onPress(KeyEvent.VK_F5, () -> {
            if (!mainScreen.isPauseModalOpen() && GamePlayState.PLAYING.is()) {
                Player p = mainScreen.getPlayer();
                if (p != null) worldMap.enterDungeon(p);
            }
        });

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
        input.removeOnPress(KeyEvent.VK_F5);
        input.removeOnPress(KeyEvent.VK_F6);
        debugOverlayOn = false;
        clearDebugOverlay(mainScreen.getGCanvas());
        inputsWired = false;
    }

    // =========================================================
    // CONTROLS HINT HELPERS (tech-demo only)
    // =========================================================

    /**
     * Adds the four controls hint labels to the canvas in the bottom-right corner.
     * Called from showContent() each time gameplay becomes the active screen.
     *
     * // TECH DEMO: placeholder until HUDoverlay is wired in.
     * // RIG POINT: delete this method and its call in showContent() when the real HUD is ready.
     */
    private void addControlsHint(acm.graphics.GCanvas canvas) {
        // Fixed anchor: 150px from the right edge, bottom label 80px from the bottom edge.
        // Using a fixed X instead of label.getWidth() because ACM's GLabel.getWidth() returns 0
        // before the label is added to the canvas, which would push every label off-screen right.
        // 150px is wide enough to contain the longest hint text ("Move: WASD") at SansSerif-BOLD-13.
        // TECH DEMO: adjust these two constants if the hint needs to move.
        double hintX      = mainScreen.getLayoutWidth()  - 150;
        double hintBottom = mainScreen.getLayoutHeight() -  80;
        double lineHeight = 18;

        // “Relic ability” = intangible invuln after obtaining relic (see Player.hasIntangible / chests / save).
        String[] lines = { "Pause: ESC", "Relic ability: K", "Attack: J", "Move: WASD" };
        acm.graphics.GLabel[] targets = { hintPause, hintAbility, hintAttack, hintMove };

        for (int i = 0; i < lines.length; i++) {
            acm.graphics.GLabel label = new acm.graphics.GLabel(lines[i]);
            label.setFont("SansSerif-BOLD-13");
            label.setColor(java.awt.Color.WHITE);
            label.setLocation(hintX, hintBottom - i * lineHeight);
            canvas.add(label);
            targets[i] = label;
        }

        hintPause   = targets[0];
        hintAbility = targets[1];
        hintAttack  = targets[2];
        hintMove    = targets[3];

        String[] debugLines = { "Debug: F6", "Dungeon: F5" };
        acm.graphics.GLabel prev = null;
        for (int i = 0; i < debugLines.length; i++) {
            acm.graphics.GLabel lbl = new acm.graphics.GLabel(debugLines[i]);
            lbl.setFont("SansSerif-BOLD-13");
            lbl.setColor(new java.awt.Color(255, 200, 100));
            lbl.setLocation(hintX, hintBottom + (i + 1) * lineHeight + 6);
            canvas.add(lbl);
            if (i == 0) hintDebug = lbl;
            else prev = lbl;
        }
        // Store second debug label for cleanup — reuse hintDebug for the first one
        hintDebug2 = prev;
    }

    /**
     * Removes the four controls hint labels from the canvas.
     * Called from hideContent() whenever gameplay is no longer the active screen.
     *
     * // TECH DEMO: paired with addControlsHint().
     * // RIG POINT: delete this method and its call in hideContent() when the real HUD is ready.
     */
    private void removeControlsHint(acm.graphics.GCanvas canvas) {
        if (hintMove    != null) { canvas.remove(hintMove);    hintMove    = null; }
        if (hintAttack  != null) { canvas.remove(hintAttack);  hintAttack  = null; }
        if (hintAbility != null) { canvas.remove(hintAbility); hintAbility = null; }
        if (hintPause   != null) { canvas.remove(hintPause);   hintPause   = null; }
        if (hintDebug   != null) { canvas.remove(hintDebug);   hintDebug   = null; }
        if (hintDebug2  != null) { canvas.remove(hintDebug2);  hintDebug2  = null; }
    }

    /**
     * Re-stacks every hint label on top of the canvas z-order.
     * Called once per tick after the world update so that room tiles added during a
     * room-transition animation never permanently bury the hint labels.
     *
     * Why this is needed: when {@code RoomTransition.start()} calls {@code toRoom.addTo(canvas)},
     * the incoming room's tiles are added to the canvas after the hint labels were originally
     * added. In ACM, later additions sit on top, covering earlier ones. The hints become invisible
     * and never recover on their own.
     *
     * In ACM, calling {@code canvas.add(object)} on an object already present in the canvas
     * moves it to the front of the draw order — this is the standard ACM pattern for keeping
     * a static overlay always visible above dynamic content.
     *
     * // TECH DEMO: only needed because hint labels are plain GLabels, not part of a managed
     * //            HUD layer. Four canvas.add() calls per tick is negligible cost.
     * // RIG POINT: delete this method and its call in onTick() once HUDoverlay.showAll() is
     * //            wired in — a proper HUD layer will manage its own z-order.
     */
    private void restackHintsOnTop(acm.graphics.GCanvas canvas) {
        if (hintMove    != null) canvas.add(hintMove);
        if (hintAttack  != null) canvas.add(hintAttack);
        if (hintAbility != null) canvas.add(hintAbility);
        if (hintPause   != null) canvas.add(hintPause);
        if (hintDebug   != null) canvas.add(hintDebug);
        if (hintDebug2  != null) canvas.add(hintDebug2);
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

        // Intangible active (blue glow)
        if (player.isIntangibleActive()) {
            drawCooldownBar(canvas, pBarX, pBarY, pBarW, pBarH,
                player.getIntangibleActiveTicks(), player.getIntangibleActiveMax(),
                new java.awt.Color(100, 150, 255), "INTG");
            pBarY -= 10;
        }

        // Intangible cooldown (dark blue)
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
        pHp.setFont("SansSerif-BOLD-11");
        pHp.setColor(java.awt.Color.CYAN);
        canvas.add(pHp);
        debugObjects.add(pHp);

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
            double aggroR = 224; // MeleeEnemy aggroRange
            acm.graphics.GOval aggroCircle = new acm.graphics.GOval(
                ex - aggroR, ey - aggroR, aggroR * 2, aggroR * 2);
            aggroCircle.setColor(new java.awt.Color(255, 255, 0, 80));
            canvas.add(aggroCircle);
            debugObjects.add(aggroCircle);

            // --- health bar (green/red) above enemy ---
            double barW = 40, barH = 4;
            double barX = ex - barW / 2;
            double barY = eh.y - 14;

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
            double eBarY = barY - 8;
            if (e.getAttackCooldown() > 0) {
                drawCooldownBar(canvas, barX, eBarY, barW, barH,
                    e.getAttackCooldown(), 180,
                    java.awt.Color.MAGENTA, "ATK");
                eBarY -= 10;
            }

            // --- state label ---
            String stateText = (e.isAggro() ? "CHASE" : "PATROL")
                + " | " + e.getAnimState().name()
                + " | HP " + e.getHealth();
            acm.graphics.GLabel stateLbl = new acm.graphics.GLabel(stateText, barX, eBarY - 2);
            stateLbl.setFont("SansSerif-BOLD-9");
            stateLbl.setColor(java.awt.Color.WHITE);
            canvas.add(stateLbl);
            debugObjects.add(stateLbl);
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
        lbl.setFont("SansSerif-BOLD-8");
        lbl.setColor(color);
        canvas.add(lbl);
        debugObjects.add(lbl);
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
