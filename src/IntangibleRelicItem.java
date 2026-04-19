import acm.graphics.GImage;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * The Intangible (Fade) relic item.
 * Received from a chest; grants the player a timed invulnerability ability.
 * Non-consumable — the effect is tied to the player's relic flag, not consumed on use.
 */
public class IntangibleRelicItem extends Item {

    public static final String ITEM_ID   = "relic_intangible";
    private static final double ICON_SIZE = 32.0;

    public IntangibleRelicItem() {
        super(ITEM_ID, "Relic of Courage", false);
        BufferedImage trimmed = loadTrimmed("assets/visuals/png's/fade_relic.png");
        if (trimmed != null) {
            icon = new GImage(trimmed);
            icon.setSize(ICON_SIZE, ICON_SIZE);
        }
    }

    @Override
    public String getDescription() {
        return "A glowing keepsake earned through the Trial of Courage. For a brief moment, nothing can stop you, enemies pass through you like you're made of wind. Recharges every ten seconds.";
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
