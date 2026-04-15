/*
Person 2: Chest — a one-time interactable container holding a PowerUp relic or item
Who RIGs it: Room — holds Chest in WorldObject list, routes J key to onInteract().
             SaveData — tracks which chests have been opened via List<String> collectedItemIds.
               On load, Room checks SaveData and calls chest.forceOpen() for already-opened chests.

Extends: WorldObject

===============
PLAN OF ACTION
===============

- CLASS ROLE
- Chest is a one-time interactable. Once opened, it stays open for the rest of the game session.
- Chests hold either a PowerUp relic (sets a flag on Player) or a regular Item (added to inventory).
- SaveData tracks open state by chestId so chests do not re-give their contents on room re-entry or reload.

- FIELDS
- String chestId     — unique ID for save tracking (e.g. "chest_a2", "chest_b3", "chest_a3")
- String itemId      — ID of what this chest contains (e.g. "relic_reflect", "healing_bread")
- boolean isOpen     — true once the player has opened this chest
- boolean givesRelic — true if the contents are a PowerUp relic (sets a flag on Player instead
                       of adding to inventory)

- onInteract() BEHAVIOR
  1. If isOpen, return immediately.
  2. Set isOpen = true.
  3. TODO: play chest-open animation (lid lifts, sparkle, or color change placeholder).
  4. If givesRelic:
       - Based on itemId, set the correct relic flag on the Player:
           "relic_reflect"    → p.setHasReflect(true)
           "relic_half_damage" → p.setHasHalfDamage(true)
           "relic_intangible"  → p.setHasIntangible(true)
       - Show a short dialogue/notification: "You obtained [relic name]!"
  5. If not givesRelic:
       - Create an Item instance and call p.collectItem(item) (or add to inventory).
  6. Add chestId to the player's collectedItemIds so SaveData captures it on next save.

- SAVE STATE TRACKING
- When a save is loaded, Room checks SaveData.collectedItemIds.
- If chestId is in that list, Room calls chest.forceOpen() to skip the animation and set isOpen = true.
- The relic flag or item was already applied when the chest was first opened; it was saved to SaveData then.

- RELIC ASSIGNMENT (TBD)
- Which relic (Reflect / Half-Damage / Intangible) is in which chest (A2 / A3 / B3) is TBD per design doc.
- Use itemId string constants so the assignment can be changed without touching this class's logic.

- WHAT CHEST DOES NOT DO
- Does not reset on room re-entry — isOpen stays true once set.
- Does not animate the Player — just sets a flag and shows a notification.
*/

import acm.graphics.GCanvas;
import acm.graphics.GImage;
import acm.graphics.GRect;

import java.awt.image.BufferedImage;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * One-time interactable container. Opens once to give a PowerUp relic or item.
 * SaveData tracks open state by chestId to prevent re-giving contents on reload.
 * See PLAN OF ACTION above before implementing.
 */
public class Chest extends WorldObject {

    // =========================================================
    // CONSTANTS
    // =========================================================

    private static final Color CHEST_CLOSED_COLOR = new Color(180, 140, 60);
    private static final Color CHEST_OPEN_COLOR   = new Color(200, 180, 120);
    private static final double CHEST_SPRITE_TARGET_HEIGHT = 72.0;

    // Relic itemId constants — assignment to specific chests is TBD
    public static final String RELIC_REFLECT     = "relic_reflect";
    public static final String RELIC_HALF_DAMAGE = "relic_half_damage";
    public static final String RELIC_INTANGIBLE  = "relic_intangible";

    // =========================================================
    // FIELDS
    // =========================================================

    /** Unique save-tracking ID for this chest (e.g. "chest_a2"). */
    private final String chestId;

    /** ID of the item or relic inside (e.g. "relic_reflect"). */
    private final String itemId;

    /** True once the chest has been opened. Never resets. */
    private boolean isOpen;

    /** True if the contents are a PowerUp relic; false if a regular inventory item. */
    private final boolean givesRelic;

    /** Optional Dialogue reference for the "You obtained X!" notification. */
    private Dialogue dialogue;

    /** Optional sprite used when chest art is available. */
    private final GImage chestSprite;
    private double spriteRenderWidth = 48.0;
    private double spriteRenderHeight = 48.0;

    /** Placeholder visual until real chest sprite is ready. */
    private final GRect placeholder;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    /**
     * @param x          top-left world pixel X
     * @param y          top-left world pixel Y
     * @param chestId    unique save ID (e.g. "chest_a2")
     * @param itemId     what's inside (use Chest.RELIC_xxx constants or an item ID string)
     * @param givesRelic true if contents are a relic; false if a regular item
     */
    public Chest(double x, double y, String chestId, String itemId, boolean givesRelic) {
        super(x, y, 48, 48);
        this.chestId    = chestId;
        this.itemId     = itemId;
        this.givesRelic = givesRelic;
        this.isOpen     = false;
        this.chestSprite = loadSprite("assets/visuals/png's/chest.png");

        this.placeholder = new GRect(x, y, 48, 48);
        this.placeholder.setFilled(true);
        this.placeholder.setFillColor(CHEST_CLOSED_COLOR);
    }

    // =========================================================
    // WORLD OBJECT OVERRIDES
    // =========================================================

    @Override
    public void draw(GCanvas canvas) {
        if (!visible) return;
        resetVisualPosition();
        if (chestSprite != null) {
            canvas.add(chestSprite);
        } else {
            canvas.add(placeholder);
        }
    }

    @Override
    public void removeFrom(GCanvas canvas) {
        if (chestSprite != null) {
            canvas.remove(chestSprite);
        }
        canvas.remove(placeholder);
    }

    /**
     * Opens the chest and delivers its contents to the player.
     * Called by Room when the player presses J while facing this chest.
     *
     * @param p the Player interacting
     */
    @Override
    public boolean isInteractable() {
        return !isOpen;
    }

    @Override
    public void onInteract(Player p) {
        if (isOpen) return;

        isOpen = true;
        GameSFX.play(GameSFX.SFX.CHEST_OPEN);
        if (chestSprite == null) {
            placeholder.setFillColor(CHEST_OPEN_COLOR);
        }

        String obtainedName;
        if (givesRelic) {
            switch (itemId) {
                case RELIC_REFLECT:
                    p.setHasReflect(true);
                    obtainedName = "Reflect Relic";
                    break;
                case RELIC_HALF_DAMAGE:
                    p.setHasHalfDamage(true);
                    obtainedName = "Half-Damage Relic";
                    break;
                case RELIC_INTANGIBLE:
                    p.setHasIntangible(true);
                    obtainedName = "Intangible Relic";
                    break;
                default:
                    obtainedName = itemId;
                    break;
            }
        } else {
            String displayName = formatDisplayName(itemId);
            p.collectItem(new Item(itemId, displayName, false));
            obtainedName = displayName;
        }

        if (dialogue != null && !dialogue.isOpen()) {
            GamePlayState.setCurrent(GamePlayState.DIALOGUE);
            dialogue.open(
                new String[]{"You obtained " + obtainedName + "!"},
                "Chest",
                false,
                () -> GamePlayState.setCurrent(GamePlayState.PLAYING)
            );
        }
    }

    @Override
    public void panVisual(double panX, double panY) {
        if (chestSprite != null) {
            chestSprite.move(panX, panY);
        }
        placeholder.move(panX, panY);
    }

    @Override
    public void resetVisualPosition() {
        if (chestSprite != null) {
            chestSprite.setLocation(x + (48.0 - spriteRenderWidth) / 2.0, y + (48.0 - spriteRenderHeight));
        }
        placeholder.setLocation(x, y);
    }

    // =========================================================
    // FORCE OPEN — called on save load when this chest was already opened
    // =========================================================

    /**
     * Silently opens this chest without giving any contents.
     * Called by Room during setup when SaveData shows this chestId was already collected.
     */
    public void forceOpen() {
        isOpen = true;
        if (placeholder != null) placeholder.setFillColor(CHEST_OPEN_COLOR);
    }

    // =========================================================
    // SETTERS / GETTERS
    // =========================================================

    public void    setDialogue(Dialogue d) { this.dialogue = d; }
    public String  getChestId()            { return chestId; }
    public boolean isOpen()                { return isOpen; }

    /** Converts "broken_lever" → "Broken Lever", "pickaxe" → "Pickaxe", etc. */
    private static String formatDisplayName(String id) {
        if (id == null || id.isEmpty()) return id;
        String[] words = id.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(Character.toUpperCase(words[i].charAt(0)));
            if (words[i].length() > 1) sb.append(words[i].substring(1));
        }
        return sb.toString();
    }

    private GImage loadSprite(String path) {
        try {
            BufferedImage source = ImageIO.read(new File(path));
            if (source == null) return null;
            BufferedImage trimmed = trimTransparentBounds(source);
            GImage image = new GImage(trimmed);
            double nativeWidth = Math.max(1.0, image.getWidth());
            double nativeHeight = Math.max(1.0, image.getHeight());
            double scale = CHEST_SPRITE_TARGET_HEIGHT / nativeHeight;
            spriteRenderWidth = Math.max(48.0, nativeWidth * scale);
            spriteRenderHeight = CHEST_SPRITE_TARGET_HEIGHT;
            image.setSize(spriteRenderWidth, spriteRenderHeight);
            image.setLocation(x + (48.0 - spriteRenderWidth) / 2.0, y + (48.0 - spriteRenderHeight));
            return image;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private BufferedImage trimTransparentBounds(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;

        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                int alpha = (source.getRGB(px, py) >>> 24) & 0xFF;
                if (alpha == 0) continue;
                if (px < minX) minX = px;
                if (py < minY) minY = py;
                if (px > maxX) maxX = px;
                if (py > maxY) maxY = py;
            }
        }

        if (maxX < minX || maxY < minY) {
            return source;
        }
        return source.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }
}
