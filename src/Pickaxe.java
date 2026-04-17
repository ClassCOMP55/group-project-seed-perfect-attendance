import acm.graphics.GImage;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * A mining tool given to the player from the chest in A1.
 * Must be in the player's inventory to interact with OreNode in B2.
 * Non-consumable — stays in inventory after use.
 */
public class Pickaxe extends Item {

    public static final String ITEM_ID   = "pickaxe";
    private static final double ICON_SIZE = 32.0;

    public Pickaxe() {
        super(ITEM_ID, "Pickaxe", false);
        BufferedImage trimmed = loadTrimmed("assets/visuals/png's/pickaxe.png");
        if (trimmed != null) {
            icon = new GImage(trimmed);
            icon.setSize(ICON_SIZE, ICON_SIZE);
        }
    }

    @Override
    public String getDescription() {
        return "A sturdy pickaxe. Take it to the ore vein in the mine to the north and start digging.";
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
