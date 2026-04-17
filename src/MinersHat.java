import acm.graphics.GImage;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * A miner's hat rewarded by the Blacksmith after delivering raw ore.
 * Non-consumable — sits in inventory as a trophy/story item.
 */
public class MinersHat extends Item {

    public static final String ITEM_ID   = "miners_hat";
    private static final double ICON_SIZE = 32.0;

    public MinersHat() {
        super(ITEM_ID, "Miner's Hat", false);
        BufferedImage trimmed = loadTrimmed("assets/visuals/png's/miners_hat.png");
        if (trimmed != null) {
            icon = new GImage(trimmed);
            icon.setSize(ICON_SIZE, ICON_SIZE);
        }
    }

    @Override
    public String getDescription() {
        return "A well-worn miner's hat, given as thanks by the Blacksmith. Smells like iron and hard work.";
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
