import java.awt.Color;
import java.awt.event.MouseEvent;

import acm.graphics.GImage;
import acm.graphics.GObject;
import acm.graphics.GRect;

/**
 * Save-slot screen — displays create_save_or_load_save.png full-screen.
 * For each occupied slot, overlays the matching load_save_X.png (424x52) and
 * adds a delete (X) hit zone at the right edge of that overlay.
 */
public class GameSavesPane extends NightScenePane {

    private static final int NEW_GAME_STARTING_HEALING_BREAD =
        MainApplication.NEW_GAME_STARTING_HEALING_BREAD;
    private static final int MAX_LOADED_HEARTS       = 99;
    private static final int MAX_LOADED_COINS        = 999_999;
    private static final int MAX_LOADED_HEALING_BREAD = 99;

    private static final String BG = "assets/visuals/start screen/create_save_or_load_save.png";
    private static final String[] LOAD_IMGS = {
        "assets/visuals/start screen/load_save_1.png",
        "assets/visuals/start screen/load_save_2.png",
        "assets/visuals/start screen/load_save_3.png"
    };

    // Slot button hit zones in the base image — tune after first run
    private static final int SLOT_X = 464;
    private static final int SLOT_W = 365;
    private static final int SLOT_H = 47;
    private static final int[] SLOT_Y = { 410, 468, 528 };

    // Back button
    private static final int BACK_X = 464;
    private static final int BACK_Y = 585;
    private static final int BACK_W = 365;
    private static final int BACK_H = 45;

    // Load overlay images are 424px wide; center them in the 1280px window
    private static final int OVERLAY_X = 462;
    // The X delete box sits at the right edge of each overlay — tune after first run
    private static final int DELETE_OFFSET_X = 374; // pixels from overlay left edge
    private static final int DELETE_W        = 48;

    private GRect[] deleteZones = new GRect[3];

    public GameSavesPane(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
    }

    @Override
    public void showContent() {
        GImage bg = new GImage(BG, 0, 0);
        bg.setSize(mainScreen.getWidth(), mainScreen.getHeight());
        addGraphic(bg);

        for (int i = 0; i < 3; i++) {
            int slot = i + 1;
            if (SaveManager.slotOccupied(slot)) {
                // Overlay the load_save image to cover the "Create Save" text
                GImage overlay = new GImage(LOAD_IMGS[i], OVERLAY_X, SLOT_Y[i]);
                addGraphic(overlay);
                // X delete zone — right portion of the overlay
                deleteZones[i] = makeZone(OVERLAY_X + DELETE_OFFSET_X, SLOT_Y[i], DELETE_W, SLOT_H);
            } else {
                deleteZones[i] = null;
            }
            // Slot zone covers the full button row (delete check runs first in mouseClicked)
            makeZone(SLOT_X, SLOT_Y[i], SLOT_W, SLOT_H);
        }

        makeZone(BACK_X, BACK_Y, BACK_W, BACK_H);
    }

    private GRect makeZone(int x, int y, int w, int h) {
        GRect r = new GRect(x, y, w, h);
        r.setFilled(false);
        r.setColor(new Color(0, 0, 0, 0));
        addGraphic(r);
        return r;
    }

    @Override
    public void hideContent() {
        for (GObject item : contents) {
            mainScreen.remove(item);
        }
        contents.clear();
        deleteZones = new GRect[3];
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        int mx = e.getX(), my = e.getY();

        if (inZone(mx, my, BACK_X, BACK_Y, BACK_W, BACK_H)) {
            mainScreen.switchToStartMenuScreen();
            return;
        }

        // Check delete zones first so they take priority over the slot zone overlap
        for (int i = 0; i < 3; i++) {
            if (deleteZones[i] != null
                    && inZone(mx, my, OVERLAY_X + DELETE_OFFSET_X, SLOT_Y[i], DELETE_W, SLOT_H)) {
                handleClearSlot(i + 1);
                return;
            }
        }

        for (int i = 0; i < 3; i++) {
            if (inZone(mx, my, SLOT_X, SLOT_Y[i], SLOT_W, SLOT_H)) {
                handleSlot(i + 1);
                return;
            }
        }
    }

    private static boolean inZone(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
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
        int loadedHp    = clampInt(loaded.getHp(), 0, loadedMaxHp);
        int loadedCoins = clampInt(loaded.getCoins(), 0, MAX_LOADED_COINS);
        int loadedBreadCount = clampInt(loaded.getHealingBreadCount(), 0, MAX_LOADED_HEALING_BREAD);

        // Legacy saves stored whole hearts; current gameplay stores half-heart units.
        if (loadedMaxHp <= Player.DEFAULT_HEART_COUNT) {
            loadedMaxHp *= Player.HALF_HEARTS_PER_HEART;
            loadedHp    *= Player.HALF_HEARTS_PER_HEART;
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
            player.collectItem(createItemById(itemId));
        }
        return player;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Item createItemById(String itemId) {
        if (Pickaxe.ITEM_ID.equals(itemId))              return new Pickaxe();
        if (MinersHat.ITEM_ID.equals(itemId))            return new MinersHat();
        if (RawOre.ITEM_ID.equals(itemId))               return new RawOre();
        if (HalfDamageRelicItem.ITEM_ID.equals(itemId)) return new HalfDamageRelicItem();
        if (ReflectRelicItem.ITEM_ID.equals(itemId))     return new ReflectRelicItem();
        if (IntangibleRelicItem.ITEM_ID.equals(itemId))  return new IntangibleRelicItem();
        if (MarkOfHeroItem.ITEM_ID.equals(itemId))       return new MarkOfHeroItem();
        if (FixedLeverItem.ITEM_ID.equals(itemId))       return new FixedLeverItem();
        return new Item(itemId, formatInventoryDisplayName(itemId), false);
    }

    private static String formatInventoryDisplayName(String itemId) {
        if (itemId == null || itemId.isEmpty()) return "";
        String[] words = itemId.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (words[i].isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(words[i].charAt(0)));
            if (words[i].length() > 1) sb.append(words[i].substring(1));
        }
        return sb.length() == 0 ? itemId : sb.toString();
    }
}
