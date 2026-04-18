import acm.graphics.GCanvas;
import acm.graphics.GImage;
import acm.graphics.GRect;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Simple consumable that restores one full heart when used from the pause inventory.
 */
public class HealingBread extends Item {

    public static final String ITEM_ID = "healing_bread";
    /** Base heal amount = 2 half-hearts = 1 full heart. Doubles with the health relic. */
    private static final int    HEAL_AMOUNT  = Player.HALF_HEARTS_PER_HEART;
    private static final double DROP_SIZE    = 32.0;
    private static final double ICON_SIZE    = 32.0;
    private static final Color  FALLBACK_COLOR = new Color(210, 170, 80);

    /** World-drop sprite (shown on the ground in a Room). */
    private GImage worldSprite;
    /** Fallback rectangle used if the PNG fails to load. */
    private GRect  worldFallback;

    public HealingBread() {
        super(ITEM_ID, "Healing Bread", true);
        BufferedImage trimmed = loadTrimmed("assets/visuals/png's/bread.png");
        if (trimmed != null) {
            worldSprite = new GImage(trimmed);
            worldSprite.setSize(DROP_SIZE, DROP_SIZE);
            icon = new GImage(trimmed);
            icon.setSize(ICON_SIZE, ICON_SIZE);
        }
        worldFallback = new GRect(0, 0, DROP_SIZE, DROP_SIZE);
        worldFallback.setFilled(true);
        worldFallback.setFillColor(FALLBACK_COLOR);
    }

    @Override
    public void onUse(Player p) {
        if (p == null) return;
        int hpBefore = p.getHP();
        if (hpBefore >= p.getMaxHealth()) return;

        // Health relic doubles the heal amount (2 HP → 4 HP).
        int healAmount = HEAL_AMOUNT;
        if (p.hasHalfDamage()) {
            healAmount *= 2;
        }

        p.setHP(hpBefore + healAmount);
        if (p.getHP() > hpBefore) {
            p.consumeInventoryItem(this);
        }
    }

    @Override
    public boolean isUsable() {
        return true;
    }

    @Override
    public String getDescription() {
        return "Restores 1 heart. (Space or E to eat)";
    }

    @Override
    public void draw(GCanvas canvas) {
        if (!inWorld) return;
        resetVisualPosition();
        if (worldSprite != null) {
            canvas.add(worldSprite);
        } else {
            canvas.add(worldFallback);
        }
    }

    @Override
    public void removeFrom(GCanvas canvas) {
        if (worldSprite   != null) canvas.remove(worldSprite);
        if (worldFallback != null) canvas.remove(worldFallback);
    }

    @Override
    public void panVisual(double panX, double panY) {
        if (worldSprite   != null) worldSprite.move(panX, panY);
        if (worldFallback != null) worldFallback.move(panX, panY);
    }

    @Override
    public void resetVisualPosition() {
        if (worldSprite   != null) worldSprite.setLocation(worldX, worldY);
        if (worldFallback != null) worldFallback.setLocation(worldX, worldY);
    }

    private static BufferedImage loadTrimmed(String path) {
        try {
            BufferedImage source = ImageIO.read(new File(path));
            if (source == null) return null;
            int w = source.getWidth(), h = source.getHeight();
            int minX = w, minY = h, maxX = -1, maxY = -1;
            for (int py = 0; py < h; py++) {
                for (int px = 0; px < w; px++) {
                    if (((source.getRGB(px, py) >>> 24) & 0xFF) == 0) continue;
                    if (px < minX) minX = px;
                    if (py < minY) minY = py;
                    if (px > maxX) maxX = px;
                    if (py > maxY) maxY = py;
                }
            }
            return (maxX < minX || maxY < minY) ? source
                : source.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }
}
