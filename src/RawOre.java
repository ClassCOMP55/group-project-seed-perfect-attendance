import acm.graphics.GCanvas;
import acm.graphics.GImage;
import acm.graphics.GRect;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Ore chunk dropped by OreNode when the player mines it with a Pickaxe.
 * Lies on the ground as a world drop; the player walks over it to collect it.
 * Bring it to the Blacksmith in B1 to have it crafted into a FixedLever.
 */
public class RawOre extends Item {

    public static final String ITEM_ID     = "raw_ore";
    private static final double DROP_SIZE   = 32.0;
    private static final double ICON_SIZE   = 32.0;
    private static final Color  FALLBACK_COLOR = new Color(130, 110, 80);

    /** World-drop sprite (shown on the ground in a Room). */
    private GImage worldSprite;
    /** Fallback rectangle used if the PNG fails to load. */
    private GRect  worldFallback;

    public RawOre() {
        this(0, 0);
    }

    public RawOre(double worldX, double worldY) {
        super(ITEM_ID, "Raw Ore", false);
        setWorldPosition(worldX, worldY);
        BufferedImage trimmed = loadTrimmed("assets/visuals/png's/raw_ore.png");
        if (trimmed != null) {
            worldSprite = new GImage(trimmed);
            worldSprite.setSize(DROP_SIZE, DROP_SIZE);
            worldSprite.setLocation(worldX, worldY);
            icon = new GImage(trimmed);
            icon.setSize(ICON_SIZE, ICON_SIZE);
        }
        worldFallback = new GRect(worldX, worldY, DROP_SIZE, DROP_SIZE);
        worldFallback.setFilled(true);
        worldFallback.setFillColor(FALLBACK_COLOR);
    }

    @Override
    public String getDescription() {
        return "A chunk of raw ore pried from the mine wall. The Blacksmith in B1 can work with this.";
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
