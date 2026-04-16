import java.awt.Color;
import java.awt.event.MouseEvent;

import acm.graphics.GLabel;
import acm.graphics.GObject;
import acm.graphics.GRoundRect;

/**
 * Three save slots — same night sky / gold button look as {@link StartMenuPane}.
 * Occupied slots show a wordless “×” control (same idiom as closing/dismissing) to clear the file.
 */
public class GameSavesPane extends NightScenePane {

    /** Fresh saves start with the release-default Healing Bread count. */
    private static final int NEW_GAME_STARTING_HEALING_BREAD =
        MainApplication.NEW_GAME_STARTING_HEALING_BREAD;
    private static final int MAX_LOADED_HEARTS = 99;
    private static final int MAX_LOADED_COINS = 999_999;
    private static final int MAX_LOADED_HEALING_BREAD = 99;

    private GLabel subtitleLabel;
    private GRoundRect[] slotFrames = new GRoundRect[3];
    private GLabel[] slotLabels = new GLabel[3];
    /** Minimal clear control per slot — non-null only when that slot has a save. */
    private GRoundRect[] clearFrames = new GRoundRect[3];
    private GLabel[] clearLabels = new GLabel[3];
    private GRoundRect backFrame;
    private GLabel backLabel;

    public GameSavesPane(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
    }

    @Override
    public void showContent() {
        double ox = originX();
        double lw = mainScreen.getLayoutWidth();
        double bw = nightButtonWidth();
        double bh = nightButtonHeight();
        double g = nightButtonGap();

        paintNightSky();
        addTitleBanner();

        subtitleLabel = new GLabel("Start Game", 0, 0);
        subtitleLabel.setFont(displayFont(20));
        subtitleLabel.setColor(NIGHT_GOLD);
        subtitleLabel.setLocation(centeredX(subtitleLabel), scaleY(198));
        addGraphic(subtitleLabel);

        GLabel hint = new GLabel("Choose a save slot", 0, 0);
        hint.setFont(displayFont(13));
        hint.setColor(new Color(140, 150, 200));
        hint.setLocation(centeredX(hint), scaleY(224));
        addGraphic(hint);

        double margin = scaleY(52) - scaleY(0);
        double yBack = scaleY(500) - margin - bh;
        double y3 = yBack - g - bh;
        double y2 = y3 - g - bh;
        double y1 = y2 - g - bh;

        double side = Math.max(scaleY(30) - scaleY(0), 24);
        double hGap = scaleX(10) - scaleX(0);
        // Same horizontal origin as {@link #addNightButton} / Back — × sits to the right without shifting the bar.
        double buttonLeft = ox + (lw - bw) / 2;

		for (int i = 0; i < 3; i++) {
            int slot = i + 1;
            double y = (i == 0) ? y1 : (i == 1) ? y2 : y3;
            slotFrames[i] = addNightRowButton(buttonLeft, y, bw, bh);

            String text = SaveManager.slotOccupied(slot)
                ? "Load Save #" + slot
                : "Create Save #" + slot;
            slotLabels[i] = new GLabel(text, 0, 0);
            slotLabels[i].setFont(displayFont(18));
            slotLabels[i].setColor(NIGHT_GOLD);
            centerLabelInRect(slotLabels[i], slotFrames[i]);
            addGraphic(slotLabels[i]);

            clearFrames[i] = null;
            clearLabels[i] = null;
            if (SaveManager.slotOccupied(slot)) {
                addClearControl(i, buttonLeft, y, bw, bh, side, hGap, ox, lw);
            }
        }

        backFrame = addNightButton(ox, lw, yBack, bw, bh);
        backLabel = new GLabel("Back", 0, 0);
        backLabel.setFont(displayFont(18));
        backLabel.setColor(NIGHT_GOLD);
        centerLabelInRect(backLabel, backFrame);
        addGraphic(backLabel);
    }

    private GRoundRect addNightRowButton(double left, double y, double bw, double bh) {
        GRoundRect frame = new GRoundRect(left, y, bw, bh, nightButtonCornerRadius());
        frame.setFilled(true);
        frame.setFillColor(NIGHT_BUTTON_FILL);
        frame.setColor(NIGHT_GOLD);
        addGraphic(frame);
        return frame;
    }

    /** Small gold square with × — reads as “remove” without a word label. */
    private void addClearControl(int index, double buttonLeft, double y, double bw, double bh,
            double side, double hGap, double ox, double lw) {
        double clearLeft = buttonLeft + bw + hGap;
        double maxLeft = ox + lw - side - 2;
        if (clearLeft > maxLeft) {
            clearLeft = Math.max(ox + 2, maxLeft);
        }
        double clearTop = y + (bh - side) / 2;
        double arc = Math.min(scaleX(6) - scaleX(0), scaleY(6) - scaleY(0));
        GRoundRect cf = new GRoundRect(clearLeft, clearTop, side, side, arc, arc);
        cf.setFilled(true);
        cf.setFillColor(NIGHT_BUTTON_FILL);
        cf.setColor(NIGHT_GOLD);
        addGraphic(cf);
        clearFrames[index] = cf;

        GLabel cx = new GLabel("\u00D7", 0, 0);
        cx.setFont("SansSerif-BOLD-" + Math.max(10, scaleFontSize(16)));
        cx.setColor(NIGHT_GOLD);
        double labX = clearLeft + (side - cx.getWidth()) / 2;
        double labY = clearTop + (side + cx.getAscent() - cx.getDescent()) / 2;
        cx.setLocation(labX, labY);
        addGraphic(cx);
        clearLabels[index] = cx;
    }

    @Override
    public void hideContent() {
        for (GObject item : contents) {
            mainScreen.remove(item);
        }
        contents.clear();
        slotFrames = new GRoundRect[3];
        slotLabels = new GLabel[3];
        clearFrames = new GRoundRect[3];
        clearLabels = new GLabel[3];
        backFrame = null;
        backLabel = null;
        subtitleLabel = null;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        GObject hit = mainScreen.getElementAtLocation(e.getX(), e.getY());
        if (hit == backLabel || hit == backFrame) {
            mainScreen.switchToStartMenuScreen();
            return;
        }
        for (int i = 0; i < 3; i++) {
            if (clearFrames[i] != null
                    && (hit == clearFrames[i] || hit == clearLabels[i])) {
                handleClearSlot(i + 1);
                return;
            }
        }
        for (int i = 0; i < 3; i++) {
            if (slotFrames[i] != null && (hit == slotFrames[i] || hit == slotLabels[i])) {
                handleSlot(i + 1);
                return;
            }
        }
    }

    private void handleClearSlot(int slot) {
        SaveManager.deleteSave(slot);
        hideContent();
        showContent();
    }

    private void handleSlot(int slot) {
        try {
            SaveData loaded = null;
            if (SaveManager.slotOccupied(slot)) {
                loaded = SaveManager.loadSave(slot);
                System.out.println("Loaded save slot " + slot + ": " + loaded);
            }
            if (loaded != null) {
                Player player = buildLoadedPlayer(loaded);
                mainScreen.startLoadedGameplaySession(player, loaded, slot);
            } else {
                mainScreen.startNewGameplaySession(buildNewPlayer(), slot);
            }
        } catch (Exception ex) {
            System.err.println("Save slot " + slot + ": " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private Player buildNewPlayer() {
        Player player = new Player();
        if (MainApplication.DEV_GRANT_INTANGIBLE_RELIC_ON_NEW_GAME) {
            player.setHasIntangible(true);
        }
        for (int i = 0; i < NEW_GAME_STARTING_HEALING_BREAD; i++) {
            player.collectItem(new HealingBread());
        }
        return player;
    }

    private Player buildLoadedPlayer(SaveData loaded) {
        Player player = new Player();
        int loadedMaxHp = clampInt(loaded.getMaxHp(), 1, MAX_LOADED_HEARTS);
        int loadedHp = clampInt(loaded.getHp(), 0, loadedMaxHp);
        int loadedCoins = clampInt(loaded.getCoins(), 0, MAX_LOADED_COINS);
        int loadedBreadCount = clampInt(loaded.getHealingBreadCount(), 0, MAX_LOADED_HEALING_BREAD);

        // Legacy saves stored whole hearts; current gameplay stores half-heart units.
        if (loadedMaxHp <= Player.DEFAULT_HEART_COUNT) {
            loadedMaxHp *= Player.HALF_HEARTS_PER_HEART;
            loadedHp *= Player.HALF_HEARTS_PER_HEART;
        }
        loadedHp = clampInt(loadedHp, 0, loadedMaxHp);

        player.setMaxHealth(loadedMaxHp);
        player.setHP(loadedHp);
        player.setCoins(loadedCoins);
        player.setHasHalfDamage(loaded.isHasHalfDamage());
        player.setHasReflect(loaded.isHasReflect());
        player.setHasIntangible(loaded.isHasIntangible());
        player.setHasMarkOfHero(loaded.isHasMarkOfHero());
        if (loadedBreadCount > 0) {
            HealingBread breadStack = new HealingBread();
            breadStack.incrementStackBy(loadedBreadCount - 1);
            player.collectItem(breadStack);
        }
        for (String itemId : loaded.getInventoryItemIds()) {
            if (itemId == null || itemId.trim().isEmpty()) continue;
            if (HealingBread.ITEM_ID.equals(itemId)) continue;
            if (player.findInventoryItem(itemId) != null) continue;
            player.collectItem(new Item(itemId, formatInventoryDisplayName(itemId), false));
        }
        return player;
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String formatInventoryDisplayName(String itemId) {
        if (itemId == null || itemId.isEmpty()) return "";
        String[] words = itemId.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (words[i].isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(words[i].charAt(0)));
            if (words[i].length() > 1) {
                sb.append(words[i].substring(1));
            }
        }
        return sb.length() == 0 ? itemId : sb.toString();
    }
}
