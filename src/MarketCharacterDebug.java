import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import acm.graphics.GImage;
import acm.graphics.GLabel;
import acm.graphics.GObject;
import acm.graphics.GOval;
import acm.graphics.GRect;

/**
 * market-character-debug
 *
 * Walkable market scene for testing player movement on a real background.
 * Uses the market background PNG scaled to fill the window, with the player
 * sprite walking via WASD. Includes a live FPS counter.
 *
 * No TileMap dependency — collision is a simple bounding box matching the
 * background edges so the player stays within the visible scene.
 */
public class MarketCharacterDebug extends GraphicsPane {

    private static final String BG_PATH =
        "assets/visuals/market-background/market-scene-1-w-characters.png";
    private static final double BG_NATIVE_WIDTH = 336.0;
    private static final double BG_NATIVE_HEIGHT = 288.0;
    private static final int MAP_COLS = 3;
    private static final int MAP_ROWS = 3;
    private static final double ROOM_PAN_DURATION_SEC = 0.35;
    private static final double ROOM_EDGE_COMMIT_SEC = 0.08;
    private static final double ZONE_BANNER_DURATION_SEC = 2.0;
    private static final double ZONE_BANNER_FADE_SEC = 0.35;

    private static final String NORMALIZED_SPRITE_DIR =
        "assets/visuals/characters/normalized/";

    // Walk animations
    private static final String WALK_FRONT = NORMALIZED_SPRITE_DIR + "player-walk-forward.gif";
    private static final String WALK_BACK  = NORMALIZED_SPRITE_DIR + "player-walking-back.gif";
    private static final String WALK_LEFT  = NORMALIZED_SPRITE_DIR + "player-walk-left.gif";
    private static final String WALK_RIGHT = NORMALIZED_SPRITE_DIR + "player-walking-right.gif";

    // Directional idle sprites — player faces the last direction they walked
    private static final String IDLE_FRONT = NORMALIZED_SPRITE_DIR + "player-1-idle-front.gif";
    private static final String IDLE_BACK  = NORMALIZED_SPRITE_DIR + "player-1-idle-back.gif";
    private static final String IDLE_LEFT  = NORMALIZED_SPRITE_DIR + "player-1-idle-left.gif";
    private static final String IDLE_RIGHT = NORMALIZED_SPRITE_DIR + "player-1-idle-right.gif";

    // Debug action previews
    private static final String ATTACK_FRONT = NORMALIZED_SPRITE_DIR + "player-attack-front.gif";
    private static final String ATTACK_BACK  = NORMALIZED_SPRITE_DIR + "player-attack-back.gif";
    private static final String ATTACK_LEFT  = NORMALIZED_SPRITE_DIR + "player-attack-left.gif";
    private static final String ATTACK_RIGHT = NORMALIZED_SPRITE_DIR + "player-attack-right.gif";

    private static final String DEATH_FRONT = NORMALIZED_SPRITE_DIR + "player-death-animation-front.gif";
    private static final String DEATH_BACK  = NORMALIZED_SPRITE_DIR + "player-death-animation-back.gif";
    private static final String DEATH_LEFT  = NORMALIZED_SPRITE_DIR + "player-death-animation-left.gif";
    private static final String DEATH_RIGHT = NORMALIZED_SPRITE_DIR + "player-death-animation-right.gif";

    // Ambient market props
    private static final String TRADER_WEAPON = NORMALIZED_SPRITE_DIR + "trader-with-weapon.gif";
    private static final String TRADER_FRUIT  = NORMALIZED_SPRITE_DIR + "trader-fruits-animation.gif";
    private static final double TRADER_FOOT_X_FRAC = 16.0 / 32.0;
    private static final double TRADER_FOOT_Y_FRAC = 34.0 / 36.0;
    private static final double FRUIT_TRADER_BG_X = 37.0;
    private static final double FRUIT_TRADER_BG_Y = 177.0;
    private static final double WEAPON_TRADER_BG_X = 272.0;
    private static final double WEAPON_TRADER_BG_Y = 210.0;
    private static final double DUMMY_BG_X = 168.0;
    private static final double DUMMY_BG_Y = 196.0;
    private static final double ATTACK_ANIM_DURATION_SEC = 0.56;
    private static final double DEATH_ANIM_DURATION_SEC = 0.49;
    private static final double DEATH_ANIM_DURATION_ALT_SEC = 0.56;
    private static final double DEATH_DRIFT_SPEED = 85.0;
    private static final double DEATH_DRIFT_DECAY = 210.0;
    private static final double DUMMY_RESPAWN_SEC = 1.0;
    private static final double DUMMY_HIT_FLASH_SEC = 0.12;

    private static final double PLAYER_SPEED = 275.0;
    private static final double SPRITE_SCALE_MULTIPLIER = 2.75;
    private static final double ATTACK_SCALE_MULTIPLIER = SPRITE_SCALE_MULTIPLIER;
    private static final double DEATH_SCALE_MULTIPLIER = SPRITE_SCALE_MULTIPLIER;
    private static final double MAX_SPRITE_HEIGHT_FRAC = 0.60;
    private static final double FALLBACK_SPRITE_SIZE = 24.0;

    /**
     * After changing walk direction, hold the previous walk sprite for this many
     * ticks before swapping. Smooths rapid WASD flicking.
     */
    private static final int DIR_CHANGE_HOLD_TICKS = 3;
    private static final Color DUMMY_POST_COLOR = new Color(144, 95, 58);
    private static final Color DUMMY_POST_HIT_COLOR = new Color(190, 90, 90);
    private static final Color DUMMY_OUTLINE_COLOR = new Color(70, 45, 26);
    private static final Color DUMMY_TARGET_OUTER = new Color(220, 70, 70);
    private static final Color DUMMY_TARGET_INNER = new Color(245, 230, 180);
    private static final Color DUMMY_DEAD_COLOR = new Color(95, 95, 105);
    private static final Color DUMMY_HEART_FULL = new Color(220, 60, 70);
    private static final Color DUMMY_HEART_EMPTY = new Color(80, 62, 72);
    private static final Color DEBUG_PANEL_BG = new Color(8, 10, 18, 220);
    private static final Color DEBUG_PANEL_BORDER = new Color(120, 180, 255, 200);
    private static final Color DEBUG_TEXT_COLOR = new Color(210, 235, 255);
    private static final Color DEBUG_PLAYER_BOX = new Color(80, 220, 255, 180);
    private static final Color DEBUG_ENEMY_BOX = new Color(255, 110, 110, 180);
    private static final Color DEBUG_SWING_BOX = new Color(255, 215, 80, 200);
    private static final Color DEBUG_PLAYER_DOT = new Color(80, 220, 255);
    private static final Color DEBUG_ENEMY_DOT = new Color(255, 110, 110);
    private static final int DEBUG_LINE_COUNT = 13;
    private static final double DEBUG_PANEL_WIDTH = 360.0;
    private static final double DEBUG_MAP_PANEL_WIDTH = 150.0;
    private static final double DEBUG_MAP_PANEL_HEIGHT = 140.0;
    private static final double DEBUG_MAP_CELL_GAP = 6.0;
    private static final double DEBUG_MAP_HEADER_HEIGHT = 24.0;
    private static final double DEBUG_PANEL_PADDING = 8.0;
    private static final double DEBUG_LINE_HEIGHT = 14.0;
    private static final double DEBUG_DOT_SIZE = 6.0;
    private static final int[][] HEART_PIXEL_COORDS = {
        {1, 0}, {2, 0}, {4, 0}, {5, 0},
        {0, 1}, {1, 1}, {2, 1}, {3, 1}, {4, 1}, {5, 1}, {6, 1},
        {0, 2}, {1, 2}, {2, 2}, {3, 2}, {4, 2}, {5, 2}, {6, 2},
        {1, 3}, {2, 3}, {3, 3}, {4, 3}, {5, 3},
        {2, 4}, {3, 4}, {4, 4},
        {3, 5}
    };

    private enum PreviewMode {
        NORMAL,
        ATTACKING,
        DYING
    }

    /** 0=front, 1=back, 2=left, 3=right */
    private int lastFacing = 0;

    private GImage bgImage;
    private GImage transitionBgImage;
    private GImage weaponTraderSprite;
    private GImage fruitTraderSprite;
    private GImage playerSprite;
    private GImage dummySprite;
    private GRect debugPanelBg;
    private GRect debugMapPanelBg;
    private GRect zoneBannerBg;
    private GRect playerHitboxFrame;
    private GRect swingHitboxFrame;
    private GRect dummyHitboxFrame;
    private GOval playerCenterDot;
    private GOval dummyCenterDot;
    private GOval debugMapMarker;
    private GLabel debugMapTitleLabel;
    private GLabel zoneBannerTitle;
    private GLabel zoneBannerSubtitle;
    private GLabel dummyRespawnLabel;
    private String currentSpritePath;
    private String dummySpritePath = IDLE_FRONT;
    private double playerX, playerY;
    private double playerBgX = BG_NATIVE_WIDTH / 2.0;
    private double playerBgY = BG_NATIVE_HEIGHT / 2.0;
    private double spriteWidth;
    private double spriteHeight;
    private double dummySpriteWidth;
    private double dummySpriteHeight;
    private PreviewMode previewMode = PreviewMode.NORMAL;
    private double previewTimerSec;
    private double dummyHitFlashTimer;
    private double dummyRespawnTimer;
    private double deathDriftVelX;
    private double deathDriftVelY;
    private int deathFacing = 0;
    private int currentZoneCol;
    private int currentZoneRow;
    private int transitionTargetCol;
    private int transitionTargetRow;
    private int transitionDirX;
    private int transitionDirY;
    private double transitionTimerSec;
    private double transitionStartBgX;
    private double transitionStartBgY;
    private double transitionEndBgX;
    private double transitionEndBgY;
    private boolean roomTransitionActive;
    private double transitionEdgeHoldSec;
    private int transitionIntentDirX;
    private int transitionIntentDirY;
    private double zoneBannerTimerSec;

    private int dirChangeCountdown;
    private String pendingSprite;

    private GLabel debugTitle;
    private GLabel zoneLabel;
    private GLabel controlsLabel;

    private long lastTickNano;
    private double fpsSmoothed = 60.0;

    private final List<Enemy> combatTargets = new ArrayList<>();
    private final InputHandler combatInput = new InputHandler();
    private final List<GLabel> debugOverlayLabels = new ArrayList<>();
    private final List<GRect> debugMapCells = new ArrayList<>();
    private final List<GLabel> debugMapCellLabels = new ArrayList<>();
    private final List<HeartPixel> dummyHeartPixels = new ArrayList<>();
    private Player combatPlayer;
    private DebugTrainingDummy trainingDummy;
    private boolean debugOverlayEnabled;
    private boolean inputsWired;
    private boolean preserveStateOnNextShow;

    public MarketCharacterDebug(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
    }

    @Override
    public boolean needsGameLoop() {
        return true;
    }

    @Override
    public void showContent() {
        clearAll();
        double w = mainScreen.getWidth();
        double h = mainScreen.getHeight();

        bgImage = new GImage(BG_PATH, 0, 0);
        bgImage.setSize(w, h);
        place(bgImage);
        transitionBgImage = new GImage(BG_PATH, 0, 0);
        transitionBgImage.setSize(w, h);
        transitionBgImage.setVisible(false);
        place(transitionBgImage);

        placeAmbientProps();
        setUpDebugCombatState();
        showPlayerHUD(combatPlayer);

        if (!preserveStateOnNextShow) {
            playerBgX = BG_NATIVE_WIDTH / 2.0;
            playerBgY = BG_NATIVE_HEIGHT / 2.0;
            lastFacing = 0;
            pendingSprite = null;
            dirChangeCountdown = 0;
            previewMode = PreviewMode.NORMAL;
            previewTimerSec = 0.0;
            currentSpritePath = IDLE_FRONT;
            debugOverlayEnabled = false;
            currentZoneCol = 0;
            currentZoneRow = 0;
            roomTransitionActive = false;
            transitionTimerSec = 0.0;
        }
        resetTransitionIntent();
        preserveStateOnNextShow = false;
        if (currentSpritePath == null) {
            currentSpritePath = IDLE_FRONT;
        }
        playerX = bgToScreenX(playerBgX);
        playerY = bgToScreenY(playerBgY);
        playerSprite = new GImage(currentSpritePath, 0, 0);
        resizePlayerSprite(playerSprite, currentSpritePath);
        positionPlayerSprite();
        place(playerSprite);
        syncDebugAvatarVisibility();

        debugTitle = new GLabel("market-character-debug", 0, 0);
        debugTitle.setFont("Courier New-BOLD-14");
        debugTitle.setColor(new Color(255, 80, 80));
        debugTitle.setLocation(10, playerHudBottomY() + 18);
        place(debugTitle);

        zoneLabel = new GLabel("", 0, 0);
        zoneLabel.setFont("Courier New-BOLD-13");
        zoneLabel.setColor(new Color(255, 215, 120));
        zoneLabel.setLocation(10, playerHudBottomY() + 36);
        place(zoneLabel);

        placeZoneBannerVisuals();

        controlsLabel = new GLabel("J/LMB attack  |  K relic intangible  |  F2 death  |  F3 debug  |  ESC menu", 0, 0);
        controlsLabel.setFont("Courier New-BOLD-12");
        controlsLabel.setColor(new Color(255, 240, 170));
        controlsLabel.setLocation(10, h - 12);
        place(controlsLabel);

        updateBackgroundPositions();
        syncCombatPlayerToDebugAvatar();
        placeTrainingDummyVisuals();
        placeDebugOverlayVisuals();
        updateTrainingDummyVisuals();
        updateDebugOverlayVisuals();
        if (zoneBannerTimerSec <= 0.0) {
            triggerZoneBanner();
        }
        updateZoneBannerVisuals();

        lastTickNano = System.nanoTime();
        wireInputOnce();
    }

    @Override
    public void hideContent() {
        unbindInput();
        hidePlayerHUD();
        clearAll();
    }

    @Override
    public void refreshLayout() {
        captureResizeState();
        preserveStateOnNextShow = true;
        hideContent();
        showContent();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (tryHandleSettingsCornerClick(e)) {
            return;
        }
        if (SwingUtilities.isLeftMouseButton(e)) {
            startAttackPreview();
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_F3) {
            debugOverlayEnabled = !debugOverlayEnabled;
            updateDebugOverlayVisuals();
            return;
        }
        if (e.getKeyCode() == KeyEvent.VK_F2) {
            startDeathPreview();
        }
    }

    @Override
    public void onTick(double dt) {
        if (playerSprite == null) {
            return;
        }

        long now = System.nanoTime();
        double elapsed = (now - lastTickNano) / 1_000_000_000.0;
        lastTickNano = now;
        if (elapsed > 0) {
            double instantFps = 1.0 / elapsed;
            fpsSmoothed = fpsSmoothed * 0.92 + instantFps * 0.08;
        }
        if (zoneBannerTimerSec > 0.0) {
            zoneBannerTimerSec = Math.max(0.0, zoneBannerTimerSec - dt);
        }

        if (roomTransitionActive) {
            updateRoomTransition(dt);
            tickCombat(dt);
            positionPlayerSprite();
            syncDebugAvatarVisibility();
            updatePlayerHUD(combatPlayer);
            if (playerSprite != null) {
                playerSprite.sendToFront();
            }
            updateTrainingDummyVisuals();
            updateDebugOverlayVisuals();
            updateZoneBannerVisuals();
            if (controlsLabel != null) controlsLabel.sendToFront();
            if (debugTitle != null) debugTitle.sendToFront();
            if (zoneLabel != null) zoneLabel.sendToFront();
            return;
        }

        InputHandler input = mainScreen.getInputHandler();
        if (input == null) {
            return;
        }

        // Keep background-space as the source of truth so resize does not
        // corrupt the stored player position during the debounce window.
        playerX = bgToScreenX(playerBgX);
        playerY = bgToScreenY(playerBgY);

        if (previewMode != PreviewMode.NORMAL) {
            previewTimerSec -= dt;
            if (previewTimerSec <= 0) {
                previewTimerSec = 0;
                if (previewMode == PreviewMode.DYING) {
                    respawnPlayerToScreenOrigin();
                }
                previewMode = PreviewMode.NORMAL;
            }
        }
        if (dummyHitFlashTimer > 0) {
            dummyHitFlashTimer = Math.max(0.0, dummyHitFlashTimer - dt);
        }
        if (trainingDummy != null && !trainingDummy.isAlive()) {
            dummyRespawnTimer -= dt;
            if (dummyRespawnTimer <= 0) {
                trainingDummy.revive();
                dummyRespawnTimer = 0.0;
            }
        }

        double inputDx = 0;
        double inputDy = 0;
        if (previewMode == PreviewMode.NORMAL) {
            if (input.isHeld(KeyEvent.VK_W) || input.isHeld(KeyEvent.VK_UP))    inputDy -= 1;
            if (input.isHeld(KeyEvent.VK_S) || input.isHeld(KeyEvent.VK_DOWN))  inputDy += 1;
            if (input.isHeld(KeyEvent.VK_A) || input.isHeld(KeyEvent.VK_LEFT))  inputDx -= 1;
            if (input.isHeld(KeyEvent.VK_D) || input.isHeld(KeyEvent.VK_RIGHT)) inputDx += 1;
        }

        boolean moving = previewMode == PreviewMode.NORMAL && (inputDx != 0 || inputDy != 0);
        String wantSprite;

        if (previewMode != PreviewMode.DYING && moving) {
            if (Math.abs(inputDx) >= Math.abs(inputDy)) {
                lastFacing = inputDx < 0 ? 2 : 3;
            } else {
                lastFacing = inputDy < 0 ? 1 : 0;
            }
        }

        if (previewMode == PreviewMode.ATTACKING) {
            wantSprite = getAttackSprite(lastFacing);
            pendingSprite = null;
            dirChangeCountdown = 0;
        } else if (previewMode == PreviewMode.DYING) {
            wantSprite = getDeathSprite(deathFacing);
            pendingSprite = null;
            dirChangeCountdown = 0;
        } else if (!moving) {
            wantSprite = getIdleSprite(lastFacing);
            pendingSprite = null;
            dirChangeCountdown = 0;
        } else {
            String rawWant = getWalkSprite(lastFacing);

            // Delay swapping walk sprites on rapid direction flicks.
            if (!rawWant.equals(currentSpritePath) && !isIdleSprite(currentSpritePath)) {
                if (pendingSprite == null || !pendingSprite.equals(rawWant)) {
                    pendingSprite = rawWant;
                    dirChangeCountdown = DIR_CHANGE_HOLD_TICKS;
                }
                if (dirChangeCountdown > 0) {
                    dirChangeCountdown--;
                    wantSprite = currentSpritePath;
                } else {
                    wantSprite = rawWant;
                    pendingSprite = null;
                }
            } else {
                wantSprite = rawWant;
                pendingSprite = null;
                dirChangeCountdown = 0;
            }
        }

        double dx = 0;
        double dy = 0;
        double moveScale = getMovementScale();
        if (moving) {
            double len = Math.sqrt(inputDx * inputDx + inputDy * inputDy);
            dx += (inputDx / len) * PLAYER_SPEED * moveScale * dt;
            dy += (inputDy / len) * PLAYER_SPEED * moveScale * dt;
        }

        if (previewMode == PreviewMode.DYING) {
            dx += deathDriftVelX * dt;
            dy += deathDriftVelY * dt;
            deathDriftVelX = approachZero(deathDriftVelX, DEATH_DRIFT_DECAY * moveScale * dt);
            deathDriftVelY = approachZero(deathDriftVelY, DEATH_DRIFT_DECAY * moveScale * dt);
        }

        double w = mainScreen.getWidth();
        double h = mainScreen.getHeight();
        double leftInset = spriteWidth * getSpriteAnchorXFrac(wantSprite);
        double topInset = spriteHeight * getSpriteAnchorYFrac(wantSprite);
        double rightInset = spriteWidth - leftInset;
        double bottomInset = spriteHeight - topInset;
        double wantedX = playerX + dx;
        double wantedY = playerY + dy;
        if (previewMode == PreviewMode.NORMAL
            && tryStartRoomTransition(wantedX, wantedY, leftInset, topInset, rightInset, bottomInset, dt)) {
            updateRoomTransition(0.0);
            positionPlayerSprite();
            if (playerSprite != null) {
                playerSprite.sendToFront();
            }
            updateTrainingDummyVisuals();
            updateDebugOverlayVisuals();
            updateZoneBannerVisuals();
            if (controlsLabel != null) controlsLabel.sendToFront();
            if (debugTitle != null) debugTitle.sendToFront();
            if (zoneLabel != null) zoneLabel.sendToFront();
            return;
        }

        playerX = clamp(wantedX, leftInset, w - rightInset);
        playerY = clamp(wantedY, topInset, h - bottomInset);
        updatePlayerBgPosition(w, h);
        syncCombatPlayerToDebugAvatar();
        tickCombat(dt);

        if (!wantSprite.equals(currentSpritePath)) {
            mainScreen.remove(playerSprite);
            contents.remove(playerSprite);
            currentSpritePath = wantSprite;
            playerSprite = new GImage(currentSpritePath, 0, 0);
            resizePlayerSprite(playerSprite, currentSpritePath);
            positionPlayerSprite();
            place(playerSprite);
        } else {
            positionPlayerSprite();
        }
        syncDebugAvatarVisibility();
        playerSprite.sendToFront();
        updatePlayerHUD(combatPlayer);
        updateTrainingDummyVisuals();
        updateDebugOverlayVisuals();
        updateZoneBannerVisuals();
        if (controlsLabel != null) controlsLabel.sendToFront();
        if (debugTitle != null) debugTitle.sendToFront();
        if (zoneLabel != null) zoneLabel.sendToFront();
    }

    private void wireInputOnce() {
        if (inputsWired) {
            return;
        }
        InputHandler input = mainScreen.getInputHandler();
        if (input == null) {
            return;
        }
        input.onPress(KeyEvent.VK_J, () -> {
            if (!mainScreen.isPauseModalOpen()) {
                startAttackPreview();
            }
        });
        // K: Player.activateIntangible — relic forced on in setUpDebugCombatState so the ability is testable.
        // This pane draws playerSprite, not combatPlayer.draw(); blue aura from Player may not appear here.
        input.onPress(KeyEvent.VK_K, () -> {
            if (!mainScreen.isPauseModalOpen() && combatPlayer != null) {
                combatPlayer.activateIntangible();
                syncDebugAvatarVisibility();
                updateDebugOverlayVisuals();
            }
        });
        inputsWired = true;
    }

    private void unbindInput() {
        InputHandler input = mainScreen.getInputHandler();
        if (input == null) {
            inputsWired = false;
            return;
        }
        input.removeOnPress(KeyEvent.VK_J);
        input.removeOnPress(KeyEvent.VK_K);
        inputsWired = false;
    }

    private void syncDebugAvatarVisibility() {
        if (playerSprite != null) {
            playerSprite.setVisible(combatPlayer == null || combatPlayer.shouldShowSprite());
        }
    }

    private void clearAll() {
        for (GObject item : contents) {
            mainScreen.remove(item);
        }
        contents.clear();
    }

    private String getIdleSprite(int facing) {
        switch (facing) {
            case 1:  return IDLE_BACK;
            case 2:  return IDLE_LEFT;
            case 3:  return IDLE_RIGHT;
            default: return IDLE_FRONT;
        }
    }

    private String getWalkSprite(int facing) {
        switch (facing) {
            case 1:  return WALK_BACK;
            case 2:  return WALK_LEFT;
            case 3:  return WALK_RIGHT;
            default: return WALK_FRONT;
        }
    }

    private String getAttackSprite(int facing) {
        switch (facing) {
            case 1:  return ATTACK_BACK;
            case 2:  return ATTACK_LEFT;
            case 3:  return ATTACK_RIGHT;
            default: return ATTACK_FRONT;
        }
    }

    private String getDeathSprite(int facing) {
        switch (facing) {
            case 1:  return DEATH_BACK;
            case 2:  return DEATH_LEFT;
            case 3:  return DEATH_RIGHT;
            default: return DEATH_FRONT;
        }
    }

    private boolean isIdleSprite(String spritePath) {
        return IDLE_FRONT.equals(spritePath)
            || IDLE_BACK.equals(spritePath)
            || IDLE_LEFT.equals(spritePath)
            || IDLE_RIGHT.equals(spritePath);
    }

    private void startAttackPreview() {
        if (previewMode == PreviewMode.DYING) {
            return;
        }
        if (combatPlayer != null) {
            syncCombatPlayerToDebugAvatar();
            SwordSwing before = combatPlayer.getActiveSwing();
            combatPlayer.attack();
            if (combatPlayer.getActiveSwing() == before) {
                return;
            }
        }
        previewMode = PreviewMode.ATTACKING;
        pendingSprite = null;
        dirChangeCountdown = 0;
        previewTimerSec = getAnimationDurationSeconds(getAttackSprite(lastFacing));
    }

    private void startDeathPreview() {
        deathFacing = lastFacing;
        double moveScale = getMovementScale();
        deathDriftVelX = getFacingUnitX(deathFacing) * DEATH_DRIFT_SPEED * moveScale;
        deathDriftVelY = getFacingUnitY(deathFacing) * DEATH_DRIFT_SPEED * moveScale;
        previewMode = PreviewMode.DYING;
        pendingSprite = null;
        dirChangeCountdown = 0;
        previewTimerSec = getAnimationDurationSeconds(getDeathSprite(deathFacing));
    }

    private double getAnimationDurationSeconds(String spritePath) {
        if (ATTACK_FRONT.equals(spritePath)) return ATTACK_ANIM_DURATION_SEC;
        if (ATTACK_BACK.equals(spritePath)) return ATTACK_ANIM_DURATION_SEC;
        if (ATTACK_LEFT.equals(spritePath)) return ATTACK_ANIM_DURATION_SEC;
        if (ATTACK_RIGHT.equals(spritePath)) return ATTACK_ANIM_DURATION_SEC;
        if (DEATH_FRONT.equals(spritePath)) return DEATH_ANIM_DURATION_SEC;
        if (DEATH_BACK.equals(spritePath)) return DEATH_ANIM_DURATION_SEC;
        if (DEATH_LEFT.equals(spritePath)) return DEATH_ANIM_DURATION_ALT_SEC;
        if (DEATH_RIGHT.equals(spritePath)) return DEATH_ANIM_DURATION_SEC;
        return 0.0;
    }

    private void placeAmbientProps() {
        fruitTraderSprite = createAmbientSprite(TRADER_FRUIT, FRUIT_TRADER_BG_X, FRUIT_TRADER_BG_Y);
        weaponTraderSprite = createAmbientSprite(TRADER_WEAPON, WEAPON_TRADER_BG_X, WEAPON_TRADER_BG_Y);
        if (weaponTraderSprite != null) {
            place(weaponTraderSprite);
        }
        if (fruitTraderSprite != null) {
            place(fruitTraderSprite);
        }
    }

    private void setUpDebugCombatState() {
        if (!preserveStateOnNextShow || combatPlayer == null) {
            combatPlayer = new Player();
        }
        // So K always exercises the real relic path in this debug scene (not representative of new saves).
        if (combatPlayer != null) {
            combatPlayer.setHasIntangible(true);
        }
        Player statePlayer = mainScreen.getPlayer();
        if (combatPlayer != null && statePlayer != null) {
            combatPlayer.setName(statePlayer.getName());
            combatPlayer.setProfession(statePlayer.getProfession());
        }
        if (!preserveStateOnNextShow || trainingDummy == null) {
            trainingDummy = new DebugTrainingDummy(0, 0);
            combatTargets.clear();
            combatTargets.add(trainingDummy);
            dummyHitFlashTimer = 0.0;
            dummyRespawnTimer = 0.0;
        }
        if (trainingDummy != null) {
            trainingDummy.setDebugPosition(bgToScreenX(DUMMY_BG_X), bgToScreenY(DUMMY_BG_Y));
        }
    }

    private GImage createAmbientSprite(String spritePath, double bgAnchorX, double bgAnchorY) {
        GImage sprite = new GImage(spritePath, 0, 0);
        double baseWidth = sprite.getWidth() > 0 ? sprite.getWidth() : FALLBACK_SPRITE_SIZE;
        double baseHeight = sprite.getHeight() > 0 ? sprite.getHeight() : FALLBACK_SPRITE_SIZE;
        double width = scaleBgWidth(baseWidth);
        double height = scaleBgHeight(baseHeight);
        sprite.setSize(width, height);
        double x = bgToScreenX(bgAnchorX) - width * TRADER_FOOT_X_FRAC;
        double y = bgToScreenY(bgAnchorY) - height * TRADER_FOOT_Y_FRAC;
        sprite.setLocation(x, y);
        return sprite;
    }

    private void placeTrainingDummyVisuals() {
        double boxW = scaleBgWidth(48);
        double boxH = scaleBgHeight(48);
        double centerX = bgToScreenX(DUMMY_BG_X);
        double centerY = bgToScreenY(DUMMY_BG_Y);
        double boxX = centerX - boxW / 2.0;
        double boxY = centerY - boxH / 2.0;

        dummyHitboxFrame = new GRect(boxX, boxY, boxW, boxH);
        dummyHitboxFrame.setFilled(false);
        dummyHitboxFrame.setColor(new Color(255, 220, 140, 110));
        place(dummyHitboxFrame);

        dummySpritePath = IDLE_FRONT;
        dummySprite = new GImage(dummySpritePath, 0, 0);
        resizeDummySprite(dummySprite);
        positionDummySprite();
        place(dummySprite);

        dummyHeartPixels.clear();
        double heartY = boxY - scaleBgHeight(18);
        double heartWidth = scaleBgWidth(8);
        double totalHeartsWidth = heartWidth * 3 + scaleBgWidth(6);
        double startX = centerX - totalHeartsWidth / 2.0;
        for (int heartIndex = 0; heartIndex < 3; heartIndex++) {
            createHeartPixels(heartIndex, startX + heartIndex * (heartWidth + scaleBgWidth(3)), heartY);
        }

        dummyRespawnLabel = new GLabel("", 0, 0);
        dummyRespawnLabel.setFont("Courier New-BOLD-11");
        dummyRespawnLabel.setColor(new Color(255, 235, 190));
        place(dummyRespawnLabel);
    }

    private void placeZoneBannerVisuals() {
        zoneBannerBg = new GRect(0, 0, 260, 54);
        zoneBannerBg.setFilled(true);
        zoneBannerBg.setFillColor(new Color(34, 24, 18, 230));
        zoneBannerBg.setColor(new Color(224, 180, 92, 240));
        place(zoneBannerBg);

        zoneBannerTitle = new GLabel("", 0, 0);
        zoneBannerTitle.setFont("Courier New-BOLD-18");
        zoneBannerTitle.setColor(new Color(248, 225, 150));
        place(zoneBannerTitle);

        zoneBannerSubtitle = new GLabel("", 0, 0);
        zoneBannerSubtitle.setFont("Courier New-BOLD-13");
        zoneBannerSubtitle.setColor(new Color(255, 242, 214));
        place(zoneBannerSubtitle);
    }

    private void triggerZoneBanner() {
        zoneBannerTimerSec = ZONE_BANNER_DURATION_SEC;
    }

    private void placeDebugOverlayVisuals() {
        debugMapPanelBg = new GRect(0, 0, DEBUG_MAP_PANEL_WIDTH, DEBUG_MAP_PANEL_HEIGHT);
        debugMapPanelBg.setFilled(true);
        debugMapPanelBg.setFillColor(DEBUG_PANEL_BG);
        debugMapPanelBg.setColor(DEBUG_PANEL_BORDER);
        place(debugMapPanelBg);

        debugMapTitleLabel = new GLabel("", 0, 0);
        debugMapTitleLabel.setFont("Courier New-BOLD-11");
        debugMapTitleLabel.setColor(DEBUG_TEXT_COLOR);
        place(debugMapTitleLabel);

        debugMapCells.clear();
        debugMapCellLabels.clear();
        for (int i = 0; i < MAP_COLS * MAP_ROWS; i++) {
            GRect cell = new GRect(0, 0, 1, 1);
            cell.setFilled(true);
            cell.setFillColor(new Color(25, 30, 45));
            cell.setColor(DEBUG_PANEL_BORDER);
            place(cell);
            debugMapCells.add(cell);

            GLabel label = new GLabel("", 0, 0);
            label.setFont("Courier New-BOLD-10");
            label.setColor(DEBUG_TEXT_COLOR);
            place(label);
            debugMapCellLabels.add(label);
        }

        debugMapMarker = new GOval(0, 0, 10, 10);
        debugMapMarker.setFilled(true);
        debugMapMarker.setFillColor(new Color(255, 225, 120));
        debugMapMarker.setColor(new Color(255, 245, 210));
        place(debugMapMarker);

        debugPanelBg = new GRect(0, 0, DEBUG_PANEL_WIDTH, 20);
        debugPanelBg.setFilled(true);
        debugPanelBg.setFillColor(DEBUG_PANEL_BG);
        debugPanelBg.setColor(DEBUG_PANEL_BORDER);
        place(debugPanelBg);

        debugOverlayLabels.clear();
        for (int i = 0; i < DEBUG_LINE_COUNT; i++) {
            GLabel label = new GLabel("", 0, 0);
            label.setFont("Courier New-BOLD-11");
            label.setColor(DEBUG_TEXT_COLOR);
            place(label);
            debugOverlayLabels.add(label);
        }

        playerHitboxFrame = new GRect(0, 0, 1, 1);
        playerHitboxFrame.setFilled(false);
        playerHitboxFrame.setColor(DEBUG_PLAYER_BOX);
        place(playerHitboxFrame);

        swingHitboxFrame = new GRect(0, 0, 1, 1);
        swingHitboxFrame.setFilled(false);
        swingHitboxFrame.setColor(DEBUG_SWING_BOX);
        place(swingHitboxFrame);

        playerCenterDot = new GOval(0, 0, DEBUG_DOT_SIZE, DEBUG_DOT_SIZE);
        playerCenterDot.setFilled(true);
        playerCenterDot.setColor(DEBUG_PLAYER_DOT);
        playerCenterDot.setFillColor(DEBUG_PLAYER_DOT);
        place(playerCenterDot);

        dummyCenterDot = new GOval(0, 0, DEBUG_DOT_SIZE, DEBUG_DOT_SIZE);
        dummyCenterDot.setFilled(true);
        dummyCenterDot.setColor(DEBUG_ENEMY_DOT);
        dummyCenterDot.setFillColor(DEBUG_ENEMY_DOT);
        place(dummyCenterDot);
    }

    private void updateTrainingDummyVisuals() {
        if (trainingDummy == null || dummySprite == null) {
            return;
        }

        boolean showMarketObjects = isCurrentZoneMarket() && !roomTransitionActive;
        if (weaponTraderSprite != null) {
            weaponTraderSprite.setVisible(showMarketObjects);
        }
        if (fruitTraderSprite != null) {
            fruitTraderSprite.setVisible(showMarketObjects);
        }
        if (dummySprite != null) {
            dummySprite.setVisible(showMarketObjects);
        }
        if (dummyRespawnLabel != null) {
            dummyRespawnLabel.setVisible(showMarketObjects && !trainingDummy.isAlive());
        }
        for (HeartPixel heartPixel : dummyHeartPixels) {
            heartPixel.rect.setVisible(showMarketObjects);
        }
        if (!showMarketObjects) {
            if (dummyHitboxFrame != null && !debugOverlayEnabled) {
                dummyHitboxFrame.setVisible(false);
            }
            return;
        }

        double boxW = scaleBgWidth(48);
        double boxH = scaleBgHeight(48);
        double centerX = bgToScreenX(DUMMY_BG_X);
        double centerY = bgToScreenY(DUMMY_BG_Y);
        double boxX = centerX - boxW / 2.0;
        double boxY = centerY - boxH / 2.0;

        if (dummyHitboxFrame != null) {
            dummyHitboxFrame.setLocation(boxX, boxY);
            dummyHitboxFrame.setSize(boxW, boxH);
        }

        boolean dead = !trainingDummy.isAlive();
        boolean flashing = dummyHitFlashTimer > 0.0 && !dead;
        String targetSpritePath = dead ? DEATH_FRONT : IDLE_FRONT;
        if (!targetSpritePath.equals(dummySpritePath)) {
            mainScreen.remove(dummySprite);
            contents.remove(dummySprite);
            dummySpritePath = targetSpritePath;
            dummySprite = new GImage(dummySpritePath, 0, 0);
            resizeDummySprite(dummySprite);
            positionDummySprite();
            place(dummySprite);
        } else {
            positionDummySprite();
        }

        double heartY = boxY - scaleBgHeight(18);
        double heartWidth = scaleBgWidth(8);
        double totalHeartsWidth = heartWidth * 3 + scaleBgWidth(6);
        double startX = centerX - totalHeartsWidth / 2.0;
        layoutHeartPixels(startX, heartY);
        updateHeartColors(flashing ? DUMMY_POST_HIT_COLOR : DUMMY_HEART_FULL, dead);

        if (dummyRespawnLabel != null) {
            dummyRespawnLabel.setVisible(dead);
            dummyRespawnLabel.setLabel(dead ? String.format("respawn %.1fs", Math.max(0.0, dummyRespawnTimer)) : "");
            dummyRespawnLabel.setLocation(centerX - dummyRespawnLabel.getWidth() / 2.0, boxY + boxH + scaleBgHeight(14));
        }

        if (dummySprite != null) dummySprite.sendToFront();
        if (dummyRespawnLabel != null) dummyRespawnLabel.sendToFront();
    }

    private void updateDebugOverlayVisuals() {
        boolean show = debugOverlayEnabled && combatPlayer != null && trainingDummy != null;
        boolean showMarketObjects = isCurrentZoneMarket() && !roomTransitionActive;

        if (debugMapPanelBg != null) {
            debugMapPanelBg.setVisible(show);
        }
        if (debugMapTitleLabel != null) {
            debugMapTitleLabel.setVisible(show);
        }
        if (debugMapMarker != null) {
            debugMapMarker.setVisible(show);
        }
        for (GRect cell : debugMapCells) {
            cell.setVisible(show);
        }
        for (GLabel label : debugMapCellLabels) {
            label.setVisible(show);
        }
        if (debugPanelBg != null) {
            debugPanelBg.setVisible(show);
        }
        if (playerHitboxFrame != null) {
            playerHitboxFrame.setVisible(show);
        }
        if (playerCenterDot != null) {
            playerCenterDot.setVisible(show);
        }
        if (dummyCenterDot != null) {
            dummyCenterDot.setVisible(show);
        }
        if (dummyHitboxFrame != null) {
            dummyHitboxFrame.setVisible(show && showMarketObjects);
            dummyHitboxFrame.setColor(DEBUG_ENEMY_BOX);
        }
        if (swingHitboxFrame != null) {
            swingHitboxFrame.setVisible(false);
        }
        for (GLabel label : debugOverlayLabels) {
            label.setVisible(show);
        }

        if (!show) {
            return;
        }

        Hitbox playerHitbox = combatPlayer.getHitbox();
        Hitbox dummyHitbox = trainingDummy.getHitbox();
        SwordSwing swing = combatPlayer.getActiveSwing();
        Hitbox swingHitbox = swing != null ? swing.getHitbox() : null;

        double panelHeight = DEBUG_PANEL_PADDING * 2 + DEBUG_LINE_COUNT * DEBUG_LINE_HEIGHT + 6;
        double panelX = mainScreen.getWidth() - DEBUG_PANEL_WIDTH - 10;
        double panelY = 10;
        double mapPanelX = panelX - DEBUG_MAP_PANEL_WIDTH - 8;
        double mapPanelY = panelY;
        layoutDebugMap(mapPanelX, mapPanelY);
        debugPanelBg.setLocation(panelX, panelY);
        debugPanelBg.setSize(DEBUG_PANEL_WIDTH, panelHeight);

        double transitionProgress = roomTransitionActive && ROOM_PAN_DURATION_SEC > 0.0
            ? transitionTimerSec / ROOM_PAN_DURATION_SEC : 0.0;
        String targetZone = roomTransitionActive
            ? zoneId(transitionTargetCol, transitionTargetRow) : "--";
        String debugDir = roomTransitionActive
            ? directionName(transitionDirX, transitionDirY)
            : directionName(transitionIntentDirX, transitionIntentDirY);
        double bgNormX = BG_NATIVE_WIDTH <= 0.0 ? 0.0 : playerBgX / BG_NATIVE_WIDTH;
        double bgNormY = BG_NATIVE_HEIGHT <= 0.0 ? 0.0 : playerBgY / BG_NATIVE_HEIGHT;

        List<String> lines = new ArrayList<>();
        lines.add(String.format("debug=on fps=%.1f win=%.0fx%.0f",
            fpsSmoothed, mainScreen.getWidth(), mainScreen.getHeight()));
        lines.add(String.format("state=%s face=%s timer=%.2f hp=%d intangible=%s cd=%d",
            previewMode.name().toLowerCase(),
            facingName(previewMode == PreviewMode.DYING ? deathFacing : lastFacing),
            previewTimerSec,
            combatPlayer.getHP(),
            combatPlayer.isIntangibleActive() ? "on" : "off",
            combatPlayer.getIntangibleCooldownTicks()));
        lines.add(String.format("zone=%s (%s) target=%s trans=%s prog=%.2f",
            zoneId(currentZoneCol, currentZoneRow),
            zoneShortName(currentZoneCol, currentZoneRow),
            targetZone,
            roomTransitionActive ? "yes" : "no",
            transitionProgress));
        lines.add(String.format("dir=%s hold=%.2f/%.2f banner=%.2f",
            debugDir,
            transitionEdgeHoldSec,
            ROOM_EDGE_COMMIT_SEC,
            zoneBannerTimerSec));
        lines.add(String.format("player scr=(%.1f, %.1f) bg=(%.1f, %.1f) norm=(%.3f, %.3f)",
            playerX, playerY, playerBgX, playerBgY, bgNormX, bgNormY));
        lines.add(String.format("sprite size=%.0fx%.0f vis=%s scale=%.2f move=%.2f",
            spriteWidth,
            spriteHeight,
            playerSprite != null && playerSprite.isVisible() ? "yes" : "no",
            getSpriteResizeFactor(),
            getMovementScale()));
        lines.add("player hb=" + formatHitbox(playerHitbox));
        if (showMarketObjects) {
            lines.add(String.format("dummy hp=%.1f/3.0 alive=%s center=(%.1f, %.1f)",
                trainingDummy.getHealth() / 2.0,
                trainingDummy.isAlive() ? "yes" : "no",
                bgToScreenX(DUMMY_BG_X),
                bgToScreenY(DUMMY_BG_Y)));
            lines.add(String.format("dummy hb=%s respawn=%.1f", formatHitbox(dummyHitbox), dummyRespawnTimer));
        } else {
            lines.add("dummy inactive outside A1");
            lines.add("dummy hb=hidden");
        }
        lines.add(String.format("body overlap=%s drift=(%.1f, %.1f)",
            showMarketObjects && playerHitbox.overlaps(dummyHitbox) ? "yes" : "no",
            deathDriftVelX, deathDriftVelY));
        if (swingHitbox != null) {
            lines.add(String.format("swing age=%d overlap=%s",
                swing.getCurrentAge(), showMarketObjects && swingHitbox.overlaps(dummyHitbox) ? "yes" : "no"));
            lines.add("swing hb=" + formatHitbox(swingHitbox));
        } else {
            lines.add("swing inactive");
            lines.add("swing hb=none");
        }

        if (debugPanelBg != null) {
            debugPanelBg.sendToFront();
        }

        for (int i = 0; i < debugOverlayLabels.size(); i++) {
            GLabel label = debugOverlayLabels.get(i);
            String text = i < lines.size() ? lines.get(i) : "";
            label.setLabel(text);
            label.setLocation(panelX + DEBUG_PANEL_PADDING, panelY + 18 + i * DEBUG_LINE_HEIGHT);
            label.sendToFront();
        }

        if (debugMapPanelBg != null) {
            debugMapPanelBg.sendToFront();
        }
        if (debugMapTitleLabel != null) {
            debugMapTitleLabel.sendToFront();
        }
        for (GRect cell : debugMapCells) {
            cell.sendToFront();
        }
        for (GLabel label : debugMapCellLabels) {
            label.sendToFront();
        }
        if (debugMapMarker != null) {
            debugMapMarker.sendToFront();
        }

        playerHitboxFrame.setLocation(playerHitbox.x, playerHitbox.y);
        playerHitboxFrame.setSize(playerHitbox.width, playerHitbox.height);
        playerHitboxFrame.sendToFront();

        if (dummyHitboxFrame != null && showMarketObjects) {
            dummyHitboxFrame.sendToFront();
        }

        if (swingHitbox != null && swingHitboxFrame != null) {
            swingHitboxFrame.setVisible(true);
            swingHitboxFrame.setLocation(swingHitbox.x, swingHitbox.y);
            swingHitboxFrame.setSize(swingHitbox.width, swingHitbox.height);
            swingHitboxFrame.sendToFront();
        }

        if (playerCenterDot != null) {
            playerCenterDot.setLocation(playerX - DEBUG_DOT_SIZE / 2.0, playerY - DEBUG_DOT_SIZE / 2.0);
            playerCenterDot.sendToFront();
        }
        if (dummyCenterDot != null) {
            double dummyCenterX = bgToScreenX(DUMMY_BG_X);
            double dummyCenterY = bgToScreenY(DUMMY_BG_Y);
            dummyCenterDot.setVisible(show && showMarketObjects);
            if (showMarketObjects) {
                dummyCenterDot.setLocation(dummyCenterX - DEBUG_DOT_SIZE / 2.0, dummyCenterY - DEBUG_DOT_SIZE / 2.0);
                dummyCenterDot.sendToFront();
            }
        }
    }

    private void updateZoneBannerVisuals() {
        if (zoneBannerBg == null || zoneBannerTitle == null || zoneBannerSubtitle == null) {
            return;
        }

        boolean show = zoneBannerTimerSec > 0.0;
        zoneBannerBg.setVisible(show);
        zoneBannerTitle.setVisible(show);
        zoneBannerSubtitle.setVisible(show);
        if (!show) {
            return;
        }

        double alpha = 1.0;
        if (zoneBannerTimerSec < ZONE_BANNER_FADE_SEC && ZONE_BANNER_FADE_SEC > 0.0) {
            alpha = clamp(zoneBannerTimerSec / ZONE_BANNER_FADE_SEC, 0.0, 1.0);
        }

        String zoneId = zoneId(currentZoneCol, currentZoneRow);
        String zoneName = zoneDisplayName(currentZoneCol, currentZoneRow);
        zoneBannerTitle.setLabel(zoneId);
        zoneBannerSubtitle.setLabel(zoneName);

        double paddingX = 18.0;
        double titleY = 26.0;
        double subtitleGap = 16.0;
        double bannerWidth = Math.max(220.0,
            Math.max(zoneBannerTitle.getWidth(), zoneBannerSubtitle.getWidth()) + paddingX * 2.0);
        double bannerHeight = 54.0;
        double bannerX = (mainScreen.getWidth() - bannerWidth) / 2.0;
        double bannerY = 18.0;

        zoneBannerBg.setSize(bannerWidth, bannerHeight);
        zoneBannerBg.setLocation(bannerX, bannerY);
        zoneBannerTitle.setLocation(bannerX + (bannerWidth - zoneBannerTitle.getWidth()) / 2.0, bannerY + titleY);
        zoneBannerSubtitle.setLocation(
            bannerX + (bannerWidth - zoneBannerSubtitle.getWidth()) / 2.0,
            zoneBannerTitle.getY() + subtitleGap);

        zoneBannerBg.setFillColor(withAlpha(new Color(34, 24, 18), (int) Math.round(220 * alpha)));
        zoneBannerBg.setColor(withAlpha(new Color(224, 180, 92), (int) Math.round(235 * alpha)));
        zoneBannerTitle.setColor(withAlpha(new Color(248, 225, 150), (int) Math.round(255 * alpha)));
        zoneBannerSubtitle.setColor(withAlpha(new Color(255, 242, 214), (int) Math.round(245 * alpha)));

        zoneBannerBg.sendToFront();
        zoneBannerTitle.sendToFront();
        zoneBannerSubtitle.sendToFront();
    }

    private void layoutDebugMap(double panelX, double panelY) {
        if (debugMapPanelBg == null || debugMapTitleLabel == null || debugMapMarker == null) {
            return;
        }

        debugMapPanelBg.setLocation(panelX, panelY);
        debugMapPanelBg.setSize(DEBUG_MAP_PANEL_WIDTH, DEBUG_MAP_PANEL_HEIGHT);
        debugMapTitleLabel.setLabel("map: " + zoneId(currentZoneCol, currentZoneRow) + " " + zoneShortName(currentZoneCol, currentZoneRow));
        debugMapTitleLabel.setLocation(panelX + DEBUG_PANEL_PADDING, panelY + 16);

        double availableW = DEBUG_MAP_PANEL_WIDTH - DEBUG_PANEL_PADDING * 2.0;
        double availableH = DEBUG_MAP_PANEL_HEIGHT - DEBUG_MAP_HEADER_HEIGHT - DEBUG_PANEL_PADDING;
        double cellW = (availableW - DEBUG_MAP_CELL_GAP * (MAP_COLS - 1)) / MAP_COLS;
        double cellH = (availableH - DEBUG_MAP_CELL_GAP * (MAP_ROWS - 1)) / MAP_ROWS;
        double gridX = panelX + DEBUG_PANEL_PADDING;
        double gridY = panelY + DEBUG_MAP_HEADER_HEIGHT;

        for (int row = 0; row < MAP_ROWS; row++) {
            for (int col = 0; col < MAP_COLS; col++) {
                int idx = row * MAP_COLS + col;
                int mapRow = MAP_ROWS - 1 - row;
                GRect cell = debugMapCells.get(idx);
                GLabel label = debugMapCellLabels.get(idx);
                double cellX = gridX + col * (cellW + DEBUG_MAP_CELL_GAP);
                double cellY = gridY + row * (cellH + DEBUG_MAP_CELL_GAP);

                cell.setLocation(cellX, cellY);
                cell.setSize(cellW, cellH);
                Color baseZoneColor = zoneColor(col, mapRow);
                double shade = 0.52;
                if (col == currentZoneCol && mapRow == currentZoneRow) {
                    shade = 1.0;
                } else if (roomTransitionActive && col == transitionTargetCol && mapRow == transitionTargetRow) {
                    shade = 0.78;
                }
                cell.setFillColor(shadeColor(baseZoneColor, shade));
                cell.setColor(DEBUG_PANEL_BORDER);

                String id = zoneId(col, mapRow);
                label.setLabel(id);
                label.setLocation(cellX + (cellW - label.getWidth()) / 2.0,
                    cellY + (cellH + label.getAscent()) / 2.0 - 2);
            }
        }

        double sourceCenterX = cellCenterX(gridX, cellW, currentZoneCol);
        double sourceCenterY = cellCenterY(gridY, cellH, currentZoneRow);
        double targetCenterX = sourceCenterX;
        double targetCenterY = sourceCenterY;
        if (roomTransitionActive) {
            targetCenterX = cellCenterX(gridX, cellW, transitionTargetCol);
            targetCenterY = cellCenterY(gridY, cellH, transitionTargetRow);
        }
        double progress = roomTransitionActive && ROOM_PAN_DURATION_SEC > 0.0
            ? transitionTimerSec / ROOM_PAN_DURATION_SEC : 0.0;
        double markerCx = lerp(sourceCenterX, targetCenterX, progress);
        double markerCy = lerp(sourceCenterY, targetCenterY, progress);
        double markerSize = Math.max(8.0, Math.min(cellW, cellH) * 0.24);
        debugMapMarker.setSize(markerSize, markerSize);
        debugMapMarker.setLocation(markerCx - markerSize / 2.0, markerCy - markerSize / 2.0);
    }

    private void syncCombatPlayerToDebugAvatar() {
        if (combatPlayer == null) {
            return;
        }
        combatPlayer.setPosition(playerX, playerY);
        combatPlayer.setFacing(toDirection(lastFacing));
        if (trainingDummy != null) {
            trainingDummy.setDebugPosition(bgToScreenX(DUMMY_BG_X), bgToScreenY(DUMMY_BG_Y));
        }
    }

    private void tickCombat(double dt) {
        if (combatPlayer == null || trainingDummy == null) {
            return;
        }
        boolean allowCombatHits = isCurrentZoneMarket() && !roomTransitionActive;
        int hpBefore = trainingDummy.getHealth();
        combatPlayer.update(combatInput, allowCombatHits ? combatTargets : null, null, dt);
        if (allowCombatHits && trainingDummy.getHealth() < hpBefore) {
            dummyHitFlashTimer = DUMMY_HIT_FLASH_SEC;
            if (!trainingDummy.isAlive()) {
                dummyRespawnTimer = DUMMY_RESPAWN_SEC;
            }
        }
    }

    private boolean tryStartRoomTransition(double wantedX, double wantedY,
                                           double leftInset, double topInset,
                                           double rightInset, double bottomInset,
                                           double dt) {
        double w = mainScreen.getWidth();
        double h = mainScreen.getHeight();

        double overflowLeft = leftInset - wantedX;
        double overflowRight = wantedX - (w - rightInset);
        double overflowUp = topInset - wantedY;
        double overflowDown = wantedY - (h - bottomInset);

        int bestDirX = 0;
        int bestDirY = 0;
        double bestOverflow = 0.0;

        if (overflowLeft > 0 && canTravelTo(currentZoneCol - 1, currentZoneRow) && overflowLeft > bestOverflow) {
            bestDirX = -1;
            bestDirY = 0;
            bestOverflow = overflowLeft;
        }
        if (overflowRight > 0 && canTravelTo(currentZoneCol + 1, currentZoneRow) && overflowRight > bestOverflow) {
            bestDirX = 1;
            bestDirY = 0;
            bestOverflow = overflowRight;
        }
        if (overflowUp > 0 && canTravelTo(currentZoneCol, currentZoneRow + 1) && overflowUp > bestOverflow) {
            bestDirX = 0;
            bestDirY = -1;
            bestOverflow = overflowUp;
        }
        if (overflowDown > 0 && canTravelTo(currentZoneCol, currentZoneRow - 1) && overflowDown > bestOverflow) {
            bestDirX = 0;
            bestDirY = 1;
            bestOverflow = overflowDown;
        }

        if (bestOverflow <= 0.0) {
            resetTransitionIntent();
            return false;
        }

        if (transitionIntentDirX != bestDirX || transitionIntentDirY != bestDirY) {
            transitionIntentDirX = bestDirX;
            transitionIntentDirY = bestDirY;
            transitionEdgeHoldSec = 0.0;
        }
        transitionEdgeHoldSec += Math.max(0.0, dt);
        if (transitionEdgeHoldSec < ROOM_EDGE_COMMIT_SEC) {
            return false;
        }
        resetTransitionIntent();

        roomTransitionActive = true;
        transitionTimerSec = 0.0;
        transitionDirX = bestDirX;
        transitionDirY = bestDirY;
        transitionTargetCol = currentZoneCol + bestDirX;
        transitionTargetRow = currentZoneRow - bestDirY;
        transitionStartBgX = playerBgX;
        transitionStartBgY = playerBgY;
        transitionEndBgX = transitionStartBgX;
        transitionEndBgY = transitionStartBgY;

        if (bestDirX > 0) {
            transitionEndBgX = screenToBgX(leftInset);
        } else if (bestDirX < 0) {
            transitionEndBgX = screenToBgX(w - rightInset);
        } else if (bestDirY < 0) {
            transitionEndBgY = screenToBgY(h - bottomInset);
        } else if (bestDirY > 0) {
            transitionEndBgY = screenToBgY(topInset);
        }

        updateBackgroundPositions();
        return true;
    }

    private void updateRoomTransition(double dt) {
        if (!roomTransitionActive) {
            updateBackgroundPositions();
            return;
        }

        transitionTimerSec = Math.min(ROOM_PAN_DURATION_SEC, transitionTimerSec + dt);
        double progress = ROOM_PAN_DURATION_SEC <= 0.0 ? 1.0 : transitionTimerSec / ROOM_PAN_DURATION_SEC;
        playerBgX = lerp(transitionStartBgX, transitionEndBgX, progress);
        playerBgY = lerp(transitionStartBgY, transitionEndBgY, progress);
        playerX = bgToScreenX(playerBgX);
        playerY = bgToScreenY(playerBgY);
        updateBackgroundPositions();

        if (progress >= 1.0) {
            roomTransitionActive = false;
            transitionTimerSec = 0.0;
            currentZoneCol = transitionTargetCol;
            currentZoneRow = transitionTargetRow;
            triggerZoneBanner();
            updateBackgroundPositions();
        }

        syncCombatPlayerToDebugAvatar();
    }

    private void captureResizeState() {
        playerBgX = clamp(playerBgX, 0.0, BG_NATIVE_WIDTH);
        playerBgY = clamp(playerBgY, 0.0, BG_NATIVE_HEIGHT);
    }

    private void resetTransitionIntent() {
        transitionEdgeHoldSec = 0.0;
        transitionIntentDirX = 0;
        transitionIntentDirY = 0;
    }

    private void updateBackgroundPositions() {
        if (bgImage == null) {
            return;
        }

        double w = mainScreen.getWidth();
        double h = mainScreen.getHeight();
        bgImage.setSize(w, h);

        if (!roomTransitionActive) {
            bgImage.setLocation(0, 0);
            if (transitionBgImage != null) {
                transitionBgImage.setSize(w, h);
                transitionBgImage.setLocation(0, 0);
                transitionBgImage.setVisible(false);
            }
        } else if (transitionBgImage != null) {
            double progress = ROOM_PAN_DURATION_SEC <= 0.0 ? 1.0 : transitionTimerSec / ROOM_PAN_DURATION_SEC;
            double currentOffsetX = -transitionDirX * w * progress;
            double currentOffsetY = -transitionDirY * h * progress;
            double nextOffsetX = currentOffsetX + transitionDirX * w;
            double nextOffsetY = currentOffsetY + transitionDirY * h;
            bgImage.setLocation(currentOffsetX, currentOffsetY);
            transitionBgImage.setSize(w, h);
            transitionBgImage.setLocation(nextOffsetX, nextOffsetY);
            transitionBgImage.setVisible(true);
        }

        bgImage.sendToBack();
        if (transitionBgImage != null) {
            transitionBgImage.sendToBack();
        }

        if (zoneLabel != null) {
            String label = "zone: " + zoneId(currentZoneCol, currentZoneRow)
                + " - " + zoneDisplayName(currentZoneCol, currentZoneRow);
            if (roomTransitionActive) {
                label += " -> " + zoneId(transitionTargetCol, transitionTargetRow)
                    + " - " + zoneDisplayName(transitionTargetCol, transitionTargetRow);
            }
            zoneLabel.setLabel(label);
        }
    }

    private boolean canTravelTo(int toCol, int toRow) {
        if (toCol < 0 || toCol >= MAP_COLS || toRow < 0 || toRow >= MAP_ROWS) {
            return false;
        }
        if (Math.abs(toCol - currentZoneCol) + Math.abs(toRow - currentZoneRow) != 1) {
            return false;
        }

        return true;
    }

    private boolean isCurrentZoneMarket() {
        return currentZoneCol == 0 && currentZoneRow == 0;
    }

    private String zoneId(int col, int row) {
        char colLetter = (char) ('A' + col);
        return "" + colLetter + (row + 1);
    }

    private String zoneDisplayName(int col, int row) {
        String id = zoneId(col, row);
        if ("A1".equals(id)) return "Town Market";
        if ("B1".equals(id)) return "Town Inn";
        if ("C1".equals(id)) return "Bridge Crossing";
        if ("A2".equals(id)) return "Push Block Puzzle";
        if ("B2".equals(id)) return "Ore Route";
        if ("C2".equals(id)) return "Forest Path";
        if ("A3".equals(id)) return "Timed Gauntlet";
        if ("B3".equals(id)) return "Riddle Trial";
        if ("C3".equals(id)) return "Dungeon Entrance";
        return "Unknown Zone";
    }

    private String zoneShortName(int col, int row) {
        String id = zoneId(col, row);
        if ("A1".equals(id)) return "Market";
        if ("B1".equals(id)) return "Inn";
        if ("C1".equals(id)) return "Bridge";
        if ("A2".equals(id)) return "Push";
        if ("B2".equals(id)) return "Ore";
        if ("C2".equals(id)) return "Forest";
        if ("A3".equals(id)) return "Gauntlet";
        if ("B3".equals(id)) return "Riddle";
        if ("C3".equals(id)) return "Dungeon";
        return "?";
    }

    private Color zoneColor(int col, int row) {
        String id = zoneId(col, row);
        if ("A1".equals(id)) return new Color(184, 132, 78);
        if ("B1".equals(id)) return new Color(92, 120, 164);
        if ("C1".equals(id)) return new Color(108, 128, 142);
        if ("A2".equals(id)) return new Color(112, 142, 92);
        if ("B2".equals(id)) return new Color(128, 132, 148);
        if ("C2".equals(id)) return new Color(74, 130, 86);
        if ("A3".equals(id)) return new Color(166, 92, 88);
        if ("B3".equals(id)) return new Color(128, 96, 160);
        if ("C3".equals(id)) return new Color(96, 82, 142);
        return new Color(60, 70, 88);
    }

    private Color shadeColor(Color color, double amount) {
        return new Color(
            (int) clamp(Math.round(color.getRed() * amount), 0, 255),
            (int) clamp(Math.round(color.getGreen() * amount), 0, 255),
            (int) clamp(Math.round(color.getBlue() * amount), 0, 255));
    }

    private Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
            (int) clamp(alpha, 0, 255));
    }

    private double cellCenterX(double gridX, double cellW, int col) {
        return gridX + col * (cellW + DEBUG_MAP_CELL_GAP) + cellW / 2.0;
    }

    private double cellCenterY(double gridY, double cellH, int row) {
        int displayRow = MAP_ROWS - 1 - row;
        return gridY + displayRow * (cellH + DEBUG_MAP_CELL_GAP) + cellH / 2.0;
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private void createHeartPixels(int heartIndex, double heartX, double heartY) {
        double cellW = scaleBgWidth(1.15);
        double cellH = scaleBgHeight(1.15);
        for (int[] cell : HEART_PIXEL_COORDS) {
            GRect pixel = new GRect(
                heartX + cell[0] * cellW,
                heartY + cell[1] * cellH,
                Math.max(1.0, cellW),
                Math.max(1.0, cellH));
            pixel.setFilled(true);
            pixel.setColor(DUMMY_HEART_EMPTY);
            pixel.setFillColor(DUMMY_HEART_EMPTY);
            place(pixel);
            dummyHeartPixels.add(new HeartPixel(pixel, heartIndex, cell[0] <= 3, cell[0], cell[1]));
        }
    }

    private void layoutHeartPixels(double startX, double heartY) {
        double heartWidth = scaleBgWidth(8);
        double heartGap = scaleBgWidth(3);
        double cellW = scaleBgWidth(1.15);
        double cellH = scaleBgHeight(1.15);
        for (HeartPixel heartPixel : dummyHeartPixels) {
            double hx = startX + heartPixel.heartIndex * (heartWidth + heartGap);
            heartPixel.rect.setLocation(
                hx + heartPixel.cellX * cellW,
                heartY + heartPixel.cellY * cellH);
            heartPixel.rect.setSize(Math.max(1.0, cellW), Math.max(1.0, cellH));
        }
    }

    private void updateHeartColors(Color fullColor, boolean dead) {
        int halfHearts = Math.max(0, trainingDummy.getHealth());
        for (HeartPixel heartPixel : dummyHeartPixels) {
            int heartUnits = Math.max(0, Math.min(2, halfHearts - heartPixel.heartIndex * 2));
            boolean fill = heartPixel.isLeftHalf ? heartUnits >= 1 : heartUnits >= 2;
            Color color = dead ? DUMMY_HEART_EMPTY : (fill ? fullColor : DUMMY_HEART_EMPTY);
            heartPixel.rect.setColor(color);
            heartPixel.rect.setFillColor(color);
            heartPixel.rect.sendToFront();
        }
    }

    private void positionDummySprite() {
        if (dummySprite == null) {
            return;
        }
        double centerX = bgToScreenX(DUMMY_BG_X);
        double centerY = bgToScreenY(DUMMY_BG_Y);
        double anchorX = dummySpriteWidth * getSpriteAnchorXFrac(dummySpritePath);
        double anchorY = dummySpriteHeight * getSpriteAnchorYFrac(dummySpritePath);
        dummySprite.setLocation(centerX - anchorX, centerY - anchorY);
    }

    private void resizeDummySprite(GImage sprite) {
        double baseWidth = sprite.getWidth() > 0 ? sprite.getWidth() : FALLBACK_SPRITE_SIZE;
        double baseHeight = sprite.getHeight() > 0 ? sprite.getHeight() : FALLBACK_SPRITE_SIZE;
        double scale = getSpriteResizeFactor();
        double targetWidth = baseWidth * SPRITE_SCALE_MULTIPLIER * scale;
        double targetHeight = baseHeight * SPRITE_SCALE_MULTIPLIER * scale;
        double maxHeight = mainScreen.getHeight() * MAX_SPRITE_HEIGHT_FRAC;
        if (targetHeight > maxHeight && maxHeight > 0) {
            double scaleDown = maxHeight / targetHeight;
            targetWidth *= scaleDown;
            targetHeight *= scaleDown;
        }
        sprite.setSize(targetWidth, targetHeight);
        dummySpriteWidth = targetWidth;
        dummySpriteHeight = targetHeight;
    }

    /**
     * Center the visible character inside each GIF rather than the full
     * transparent frame, which differs slightly between directions.
     */
    private void positionPlayerSprite() {
        if (playerSprite == null) {
            return;
        }
        double anchorX = spriteWidth * getSpriteAnchorXFrac(currentSpritePath);
        double anchorY = spriteHeight * getSpriteAnchorYFrac(currentSpritePath);
        playerSprite.setLocation(playerX - anchorX, playerY - anchorY);
    }

    private double getSpriteAnchorXFrac(String spritePath) {
        if (IDLE_FRONT.equals(spritePath)) return 0.507812;
        if (IDLE_BACK.equals(spritePath)) return 0.519531;
        if (IDLE_LEFT.equals(spritePath)) return 0.508523;
        if (IDLE_RIGHT.equals(spritePath)) return 0.515625;
        if (WALK_FRONT.equals(spritePath)) return 0.505208;
        if (WALK_BACK.equals(spritePath)) return 0.482422;
        if (WALK_LEFT.equals(spritePath)) return 0.500000;
        if (WALK_RIGHT.equals(spritePath)) return 0.496094;
        if (ATTACK_FRONT.equals(spritePath)) return 0.528409;
        if (ATTACK_BACK.equals(spritePath)) return 0.580966;
        if (ATTACK_LEFT.equals(spritePath)) return 0.602273;
        if (ATTACK_RIGHT.equals(spritePath)) return 0.529830;
        if (DEATH_FRONT.equals(spritePath)) return 0.519841;
        if (DEATH_BACK.equals(spritePath)) return 0.527778;
        if (DEATH_LEFT.equals(spritePath)) return 0.480159;
        if (DEATH_RIGHT.equals(spritePath)) return 0.482143;
        return 0.5;
    }

    private double getSpriteAnchorYFrac(String spritePath) {
        if (IDLE_FRONT.equals(spritePath)) return 0.543981;
        if (IDLE_BACK.equals(spritePath)) return 0.590278;
        if (IDLE_LEFT.equals(spritePath)) return 0.571970;
        if (IDLE_RIGHT.equals(spritePath)) return 0.592593;
        if (WALK_FRONT.equals(spritePath)) return 0.557870;
        if (WALK_BACK.equals(spritePath)) return 0.541667;
        if (WALK_LEFT.equals(spritePath)) return 0.541667;
        if (WALK_RIGHT.equals(spritePath)) return 0.552083;
        if (ATTACK_FRONT.equals(spritePath)) return 0.430990;
        if (ATTACK_BACK.equals(spritePath)) return 0.630208;
        if (ATTACK_LEFT.equals(spritePath)) return 0.596354;
        if (ATTACK_RIGHT.equals(spritePath)) return 0.588542;
        if (DEATH_FRONT.equals(spritePath)) return 0.692857;
        if (DEATH_BACK.equals(spritePath)) return 0.671429;
        if (DEATH_LEFT.equals(spritePath)) return 0.632143;
        if (DEATH_RIGHT.equals(spritePath)) return 0.662500;
        return 0.5;
    }

    private void resizePlayerSprite(GImage sprite, String spritePath) {
        double baseWidth = sprite.getWidth() > 0 ? sprite.getWidth() : FALLBACK_SPRITE_SIZE;
        double baseHeight = sprite.getHeight() > 0 ? sprite.getHeight() : FALLBACK_SPRITE_SIZE;
        double scale = getSpriteResizeFactor();
        double targetWidth = baseWidth * getPlayerScaleMultiplier(spritePath) * scale;
        double targetHeight = baseHeight * getPlayerScaleMultiplier(spritePath) * scale;

        double maxHeight = mainScreen.getHeight() * MAX_SPRITE_HEIGHT_FRAC;
        if (targetHeight > maxHeight && maxHeight > 0) {
            double scaleDown = maxHeight / targetHeight;
            targetWidth *= scaleDown;
            targetHeight *= scaleDown;
        }

        sprite.setSize(targetWidth, targetHeight);
        spriteWidth = targetWidth;
        spriteHeight = targetHeight;
    }

    private double getPlayerScaleMultiplier(String spritePath) {
        if (ATTACK_FRONT.equals(spritePath) || ATTACK_BACK.equals(spritePath)
            || ATTACK_LEFT.equals(spritePath) || ATTACK_RIGHT.equals(spritePath)) {
            return ATTACK_SCALE_MULTIPLIER;
        }
        if (DEATH_FRONT.equals(spritePath) || DEATH_BACK.equals(spritePath)
            || DEATH_LEFT.equals(spritePath) || DEATH_RIGHT.equals(spritePath)) {
            return DEATH_SCALE_MULTIPLIER;
        }
        return SPRITE_SCALE_MULTIPLIER;
    }

    private double getSpriteResizeFactor() {
        return Math.max(0.25, uniformScale());
    }

    private double getMovementScale() {
        double widthRatio = mainScreen.getWidth() / (double) MainApplication.WINDOW_WIDTH;
        double heightRatio = mainScreen.getHeight() / (double) MainApplication.WINDOW_HEIGHT;
        return Math.max(0.25, (widthRatio + heightRatio) * 0.5);
    }

    private String directionName(int dx, int dy) {
        if (dx > 0) return "right";
        if (dx < 0) return "left";
        if (dy > 0) return "down";
        if (dy < 0) return "up";
        return "none";
    }

    private double getFacingUnitX(int facing) {
        if (facing == 2) return -1.0;
        if (facing == 3) return 1.0;
        return 0.0;
    }

    private double getFacingUnitY(int facing) {
        if (facing == 1) return -1.0;
        if (facing == 0) return 1.0;
        return 0.0;
    }

    private Direction toDirection(int facing) {
        switch (facing) {
            case 1:  return Direction.UP;
            case 2:  return Direction.LEFT;
            case 3:  return Direction.RIGHT;
            default: return Direction.DOWN;
        }
    }

    private void respawnPlayerToScreenOrigin() {
        lastFacing = 0;
        deathFacing = 0;
        deathDriftVelX = 0.0;
        deathDriftVelY = 0.0;
        pendingSprite = null;
        dirChangeCountdown = 0;

        GImage temp = new GImage(IDLE_FRONT, 0, 0);
        resizePlayerSprite(temp, IDLE_FRONT);
        playerX = spriteWidth * getSpriteAnchorXFrac(IDLE_FRONT);
        playerY = spriteHeight * getSpriteAnchorYFrac(IDLE_FRONT);
        updatePlayerBgPosition(mainScreen.getWidth(), mainScreen.getHeight());
    }

    private static double approachZero(double value, double amount) {
        if (value > 0) {
            return Math.max(0.0, value - amount);
        }
        if (value < 0) {
            return Math.min(0.0, value + amount);
        }
        return 0.0;
    }

    private String formatHitbox(Hitbox hitbox) {
        if (hitbox == null) {
            return "none";
        }
        return String.format("[%.0f,%.0f %.0fx%.0f]", hitbox.x, hitbox.y, hitbox.width, hitbox.height);
    }

    private String facingName(int facing) {
        switch (facing) {
            case 1:  return "up";
            case 2:  return "left";
            case 3:  return "right";
            default: return "down";
        }
    }

    private void updatePlayerBgPosition(double w, double h) {
        if (w > 0) {
            playerBgX = playerX * BG_NATIVE_WIDTH / w;
        }
        if (h > 0) {
            playerBgY = playerY * BG_NATIVE_HEIGHT / h;
        }
    }

    private double bgToScreenX(double bgX) {
        return bgX * mainScreen.getWidth() / BG_NATIVE_WIDTH;
    }

    private double bgToScreenY(double bgY) {
        return bgY * mainScreen.getHeight() / BG_NATIVE_HEIGHT;
    }

    private double screenToBgX(double screenX) {
        double width = mainScreen.getWidth();
        if (width <= 0.0) {
            return playerBgX;
        }
        return screenX * BG_NATIVE_WIDTH / width;
    }

    private double screenToBgY(double screenY) {
        double height = mainScreen.getHeight();
        if (height <= 0.0) {
            return playerBgY;
        }
        return screenY * BG_NATIVE_HEIGHT / height;
    }

    private double scaleBgWidth(double bgWidth) {
        return bgWidth * mainScreen.getWidth() / BG_NATIVE_WIDTH;
    }

    private double scaleBgHeight(double bgHeight) {
        return bgHeight * mainScreen.getHeight() / BG_NATIVE_HEIGHT;
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    private static class DebugTrainingDummy extends Enemy {
        DebugTrainingDummy(double x, double y) {
            super(x, y, IDLE_FRONT, null,
                6, 0.0, 0.0, 0.0);
        }

        @Override
        public void update(double dt, Entity target) {
            // Training dummy stays still and never attacks.
        }

        @Override
        protected void tryAttack(Entity target) {
            // no-op
        }

        @Override
        public boolean onDeath() {
            return false;
        }

        void setDebugPosition(double newX, double newY) {
            this.x = newX;
            this.y = newY;
            this.hitbox.updatePosition(newX - 24, newY - 24);
            this.sprite.setLocation(newX - 24, newY - 24);
            this.animator.setPosition(newX - 24, newY - 24);
        }

        void revive() {
            this.health = this.maxHealth;
        }
    }

    private static class HeartPixel {
        private final GRect rect;
        private final int heartIndex;
        private final boolean isLeftHalf;
        private final int cellX;
        private final int cellY;

        HeartPixel(GRect rect, int heartIndex, boolean isLeftHalf, int cellX, int cellY) {
            this.rect = rect;
            this.heartIndex = heartIndex;
            this.isLeftHalf = isLeftHalf;
            this.cellX = cellX;
            this.cellY = cellY;
        }
    }
}
