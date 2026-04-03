import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import acm.graphics.GLabel;
import acm.graphics.GObject;
import acm.graphics.GRect;
import acm.graphics.GRoundRect;

/**
 * P1 gameplay integration pane.
 * Walkable tile floor + full opening sequence:
 * free roam → 3 NPC talks → Goat approaches → dialogue → cutscene → tutorial fight → post-fight dialogue.
 */
public class P1GameplayPane extends GraphicsPane {

    private static final double NPC_TALK_RADIUS = 56.0;

    /** World pixel centers of three villagers (inside the opening room floor). */
    private static final double[][] NPC_CENTERS = {
        { 2.5 * 64, 2.5 * 64 },
        { 5.5 * 64, 4.5 * 64 },
        { 8.5 * 64, 2.5 * 64 }
    };
    private static final String[] NPC_IDS = { "merchant", "guard", "traveler" };

    private TileMap tileMap;
    private Player player;
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Projectile> projectiles = new ArrayList<>();
    private CutscenePlayer cutscenePlayer;
    private OpeningSequence openingSequence;
    private ThicketGate gateA2;

    private GRect worldBg;
    private GRect gateHintBg;
    private GLabel gateHintLabel;
    private GLabel helpLabel;
    private GLabel phaseLabel;

    /** Bottom dialogue panel (Goat lines). */
    private GRect dialogueBg;
    private GLabel dialogueLabel;

    private final List<GObject> npcMarkers = new ArrayList<>();

    private int npcTalkCount;
    private String gateHint = "";
    private boolean inputsWired;
    private boolean tilesOnCanvas;
    private boolean wasPausedLastTick;

    public P1GameplayPane(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
    }

    @Override
    public void showContent() {
        clearVisuals();
        wasPausedLastTick = false;
        GamePlayState.setCurrent(GamePlayState.PLAYING);
        setupWorld();
        drawBackdropBehindTiles();
        drawWorldOnce();
        drawStaticUi();
        wireInputOnce();
    }

    @Override
    public void hideContent() {
        clearVisuals();
    }

    @Override
    public void refreshLayout() {
        showContent();
    }

    @Override
    public boolean needsGameLoop() {
        return true;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        tryHandleSettingsCornerClick(e);
    }

    @Override
    public void onTick(double dt) {
        if (player == null || openingSequence == null) {
            return;
        }
        if (mainScreen.isPauseModalOpen() || GamePlayState.PAUSED.is()) {
            if (!wasPausedLastTick) {
                player.removeSwingFrom(mainScreen.getGCanvas());
                player.removeSpriteFromCanvas(mainScreen.getGCanvas());
                wasPausedLastTick = true;
            }
            return;
        }
        if (wasPausedLastTick) {
            wasPausedLastTick = false;
        }

        List<Enemy> combatEnemies = enemiesForCombat();
        if (GamePlayState.PLAYING.is()) {
            player.update(mainScreen.getInputHandler(), combatEnemies, projectiles, dt);
            for (Enemy enemy : combatEnemies) {
                enemy.update(dt, player);
            }
            for (Projectile p : projectiles) {
                p.update(dt);
            }
            removeDeadEnemies();
            gateContactChecks();
        }

        openingSequence.update(dt);
        updateDialogueUi();
        drawDynamic();
        updatePhaseLabel();
    }

    private void setupWorld() {
        tileMap = TileMap.createOpeningRoom();
        player = mainScreen.getPlayer();
        player.setTileMap(tileMap);
        double cx = tileMap.getWidthPixels() * 0.5;
        double cy = tileMap.getHeightPixels() * 0.55;
        player.setPosition(cx, cy);
        player.setSpawnPosition(cx, cy);

        player.setHasReflect(true);
        player.setHasHalfDamage(true);
        player.setHasIntangible(true);

        cutscenePlayer = new CutscenePlayer(mainScreen.getGCanvas());
        openingSequence = new OpeningSequence(player, cutscenePlayer, mainScreen.getGCanvas(), tileMap);
        openingSequence.setOnForceDialogue1(() -> { /* state already DIALOGUE */ });
        openingSequence.setOnForceDialogue2(() -> { });
        openingSequence.setOnPlacePathBlocker(() -> gateHint = "South path blocked after intro.");
        openingSequence.setOnSequenceComplete(() -> {
            GamePlayState.setCurrent(GamePlayState.PLAYING);
            gateHint = "Mark of Hero acquired. Thicket gate opens on contact.";
        });

        gateA2 = new ThicketGate(tileMap.getWidthPixels() - 80, tileMap.getHeightPixels() * 0.45, "gate_a2");
        npcTalkCount = 0;
        updatePhaseLabel();
    }

    /** Tiles + NPC markers — once per show; dynamic sprites redraw every tick. */
    private void drawWorldOnce() {
        if (tileMap != null && !tilesOnCanvas) {
            tileMap.draw(mainScreen.getGCanvas());
            tilesOnCanvas = true;
        }
        npcMarkers.clear();
        for (int i = 0; i < NPC_CENTERS.length; i++) {
            double mx = NPC_CENTERS[i][0] - 8;
            double my = NPC_CENTERS[i][1] - 8;
            GRoundRect dot = new GRoundRect(mx, my, 16, 16, 4, 4);
            dot.setFilled(true);
            dot.setFillColor(new Color(255, 215, 120, 180));
            dot.setColor(new Color(255, 180, 80));
            mainScreen.add(dot);
            npcMarkers.add(dot);
        }
    }

    private void drawBackdropBehindTiles() {
        worldBg = new GRect(0, 0, mainScreen.getWidth(), mainScreen.getHeight());
        worldBg.setFilled(true);
        worldBg.setFillColor(new Color(20, 24, 34));
        worldBg.setColor(new Color(20, 24, 34));
        place(worldBg);
        worldBg.sendToBack();
    }

    private void drawStaticUi() {
        helpLabel = new GLabel(
            "WASD walk  E talk near gold dots (3)  J attack  K relic ability  E advance dialogue", 0, 0);
        helpLabel.setFont("SansSerif-BOLD-12");
        helpLabel.setColor(new Color(220, 220, 235));
        helpLabel.setLocation(12, 22);
        place(helpLabel);

        phaseLabel = new GLabel("", 0, 0);
        phaseLabel.setFont("SansSerif-BOLD-13");
        phaseLabel.setColor(new Color(255, 215, 120));
        phaseLabel.setLocation(12, 42);
        place(phaseLabel);
        updatePhaseLabel();

        dialogueBg = new GRect(8, mainScreen.getHeight() - 118, mainScreen.getWidth() - 16, 72);
        dialogueBg.setFilled(true);
        dialogueBg.setFillColor(new Color(15, 12, 25, 230));
        dialogueBg.setColor(new Color(100, 80, 140));
        place(dialogueBg);
        dialogueBg.setVisible(false);

        dialogueLabel = new GLabel("", 16, mainScreen.getHeight() - 100);
        dialogueLabel.setFont("SansSerif-PLAIN-13");
        dialogueLabel.setColor(new Color(230, 230, 245));
        dialogueLabel.setVisible(false);
        place(dialogueLabel);

        gateHintBg = new GRect(10, mainScreen.getHeight() - 42, mainScreen.getWidth() - 20, 30);
        gateHintBg.setFilled(true);
        gateHintBg.setFillColor(new Color(0, 0, 0, 170));
        gateHintBg.setColor(new Color(0, 0, 0, 0));
        place(gateHintBg);

        gateHintLabel = new GLabel("", 16, mainScreen.getHeight() - 21);
        gateHintLabel.setFont("SansSerif-PLAIN-12");
        gateHintLabel.setColor(new Color(210, 210, 220));
        place(gateHintLabel);

        showPlayerHUD(player);
        addSettingsCornerButton();
        updateDialogueUi();
        drawDynamic();
    }

    private void updateDialogueUi() {
        boolean show = GamePlayState.DIALOGUE.is() && openingSequence != null;
        String line = show ? openingSequence.getActiveDialogueLine() : null;
        if (dialogueBg != null) {
            dialogueBg.setVisible(show && line != null);
        }
        if (dialogueLabel != null) {
            dialogueLabel.setVisible(show && line != null);
            if (line != null) {
                dialogueLabel.setLabel(line);
            }
        }
    }

    private void drawDynamic() {
        if (gateA2 != null) {
            gateA2.draw(mainScreen.getGCanvas());
        }
        if (player != null) {
            player.draw(mainScreen.getGCanvas());
            updatePlayerHUD(player);
        }
        for (Enemy enemy : enemiesForCombat()) {
            enemy.draw(mainScreen.getGCanvas());
        }
        for (Projectile p : projectiles) {
            if (p.isAlive()) {
                p.draw(mainScreen.getGCanvas());
            }
        }
        if (cutscenePlayer != null && cutscenePlayer.isPlaying()) {
            cutscenePlayer.draw();
        }
        if (gateHintLabel != null) {
            gateHintLabel.setLabel(gateHint == null ? "" : gateHint);
        }
    }

    private void gateContactChecks() {
        if (gateA2 == null) {
            return;
        }
        boolean opened = gateA2.onContact(player);
        if (opened) {
            gateA2.removeFrom(mainScreen.getGCanvas());
            gateHint = "Thicket gate opened.";
        } else {
            String blocked = gateA2.getBlockedMessage(player);
            if (blocked != null) {
                gateHint = blocked;
            }
        }
    }

    private void removeDeadEnemies() {
        Iterator<Enemy> it = enemies.iterator();
        while (it.hasNext()) {
            Enemy e = it.next();
            if (!e.isAlive()) {
                it.remove();
                e.getAnimator().reset();
            }
        }
    }

    private List<Enemy> enemiesForCombat() {
        List<Enemy> out = new ArrayList<>(enemies);
        if (openingSequence != null && openingSequence.isTutorialFightActive()) {
            TutorialMonster tm = openingSequence.getTutorialMonster();
            if (tm != null && tm.isAlive()) {
                out.add(tm);
            }
        }
        return out;
    }

    private String nearestNpcId() {
        double px = player.getX();
        double py = player.getY();
        for (int i = 0; i < NPC_CENTERS.length; i++) {
            double dx = px - NPC_CENTERS[i][0];
            double dy = py - NPC_CENTERS[i][1];
            if (dx * dx + dy * dy <= NPC_TALK_RADIUS * NPC_TALK_RADIUS) {
                return NPC_IDS[i];
            }
        }
        return null;
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
            if (!mainScreen.isPauseModalOpen() && GamePlayState.PLAYING.is()) {
                player.attack();
            }
        });
        // K: same entry point as GameplayPane; extra branches explain why activateIntangible() returned false.
        input.onPress(KeyEvent.VK_K, () -> {
            if (!mainScreen.isPauseModalOpen() && GamePlayState.PLAYING.is()) {
                if (player.activateIntangible()) {
                    gateHint = "Relic ability — invulnerable (blue aura).";
                } else if (!player.hasIntangible()) {
                    gateHint = "Need the intangible relic for K.";
                } else if (player.isIntangibleActive()) {
                    gateHint = "Intangible already active.";
                } else {
                    gateHint = "Intangible recharging.";
                }
                updatePhaseLabel();
            }
        });
        input.onPress(KeyEvent.VK_E, () -> {
            if (mainScreen.isPauseModalOpen()) {
                return;
            }
            if (openingSequence == null) {
                return;
            }
            if (GamePlayState.DIALOGUE.is()) {
                openingSequence.advanceDialogue();
                updatePhaseLabel();
                return;
            }
            if (GamePlayState.PLAYING.is() && openingSequence.getCurrentPhase() == OpeningSequence.Phase.FREE_ROAM) {
                String id = nearestNpcId();
                if (id != null) {
                    openingSequence.onNPCTalkedTo(id);
                    npcTalkCount = openingSequence.getNPCsTalkedTo();
                    gateHint = "Talked to " + id + ".";
                } else {
                    gateHint = "Move closer to a gold dot to talk.";
                }
                updatePhaseLabel();
            }
        });
        inputsWired = true;
    }

    private void updatePhaseLabel() {
        if (phaseLabel != null && openingSequence != null) {
            // Relic K-ability: active / cooldown / ready (ticks from Player.getIntangibleCooldownTicks).
            phaseLabel.setLabel("Phase: " + openingSequence.getCurrentPhase().name()
                + " | Villagers: " + openingSequence.getNPCsTalkedTo() + "/3"
                + " | Intangible: "
                + (player == null ? "—"
                    : player.isIntangibleActive() ? "active"
                    : (player.getIntangibleCooldownTicks() > 0 ? "CD" : "ready")));
        }
    }

    private void clearVisuals() {
        unbindInput();
        if (cutscenePlayer != null) {
            cutscenePlayer.cleanup();
        }
        if (tileMap != null && tilesOnCanvas) {
            tileMap.removeFrom(mainScreen.getGCanvas());
            tilesOnCanvas = false;
        }
        for (GObject m : npcMarkers) {
            mainScreen.remove(m);
        }
        npcMarkers.clear();
        for (GObject item : contents) {
            mainScreen.remove(item);
        }
        contents.clear();
        hidePlayerHUD();
    }

    private void unbindInput() {
        InputHandler input = mainScreen.getInputHandler();
        if (input == null) {
            inputsWired = false;
            return;
        }
        input.removeOnPress(KeyEvent.VK_SPACE);
        input.removeOnPress(KeyEvent.VK_J);
        input.removeOnPress(KeyEvent.VK_K);
        input.removeOnPress(KeyEvent.VK_E);
        inputsWired = false;
    }
}
