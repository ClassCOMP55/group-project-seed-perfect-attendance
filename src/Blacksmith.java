import acm.graphics.GCanvas;
import acm.graphics.GImage;
import acm.graphics.GLabel;
import acm.graphics.GRect;

import java.awt.image.BufferedImage;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Stationary NPC in B1. Accepts Ore + BrokenLever and gives FixedLever.
 * Dialogue changes based on what the player is carrying.
 */
public class Blacksmith extends WorldObject {
    private static final double NPC_SPRITE_TARGET_HEIGHT = 48.0;
    private static final double TITLE_BASELINE_Y = -10.0;
    private static final double HINT_BASELINE_Y = 64.0;

    private static final Color BODY_COLOR = new Color(140, 90, 60);
    private static final Color BODY_EDGE  = new Color(80, 50, 30);
    private static final Color APRON_COLOR = new Color(90, 90, 95);
    private static final Color TITLE_COLOR = new Color(255, 220, 170);
    private static final Color HINT_COLOR  = new Color(222, 241, 184);

    public static final String FIXED_LEVER_ID = DrawbridgeLever.FIXED_LEVER_ID;

    private final Dialogue dialogue;

    private final GImage blacksmithSprite;
    private final GRect body;
    private final GRect apron;
    private final GLabel titleLabel;
    private final GLabel hintLabel;
    private double spriteRenderWidth = 48.0;
    private double spriteRenderHeight = 48.0;

    public Blacksmith(double x, double y, Dialogue dialogue) {
        super(x, y, 48, 48);
        this.dialogue = dialogue;
        this.blacksmithSprite = loadSprite("assets/visuals/png's/smith at forge.png");

        body = new GRect(x, y, 48, 48);
        body.setFilled(true);
        body.setFillColor(BODY_COLOR);
        body.setColor(BODY_EDGE);

        apron = new GRect(x + 6, y + 26, 36, 16);
        apron.setFilled(true);
        apron.setFillColor(APRON_COLOR);
        apron.setColor(APRON_COLOR.darker());

        titleLabel = new GLabel("Blacksmith", x, y);
        titleLabel.setFont("SansSerif-BOLD-12");
        titleLabel.setColor(TITLE_COLOR);

        hintLabel = new GLabel("e to talk", x, y);
        hintLabel.setFont("SansSerif-PLAIN-12");
        hintLabel.setColor(HINT_COLOR);

        resetVisualPosition();
    }

    @Override
    public void draw(GCanvas canvas) {
        if (!visible) return;
        resetVisualPosition();
        if (blacksmithSprite != null) {
            canvas.add(blacksmithSprite);
        } else {
            canvas.add(body);
            canvas.add(apron);
        }
        canvas.add(titleLabel);
        canvas.add(hintLabel);
        positionLabels();
    }

    @Override
    public void removeFrom(GCanvas canvas) {
        if (blacksmithSprite != null) {
            canvas.remove(blacksmithSprite);
        }
        canvas.remove(body);
        canvas.remove(apron);
        canvas.remove(titleLabel);
        canvas.remove(hintLabel);
    }

    @Override
    public boolean isInteractable() {
        return true;
    }

    @Override
    public void onInteract(Player p) {
        if (dialogue == null || dialogue.isOpen()) return;

        Item ore = p.findInventoryItem(OreNode.ORE_ID);
        Item brokenLever = p.findInventoryItem(OreNode.BROKEN_LEVER_ID);

        if (ore != null && brokenLever != null) {
            p.consumeInventoryItem(ore);
            p.consumeInventoryItem(brokenLever);
            p.collectItem(new Item(FIXED_LEVER_ID, "Fixed Lever", false));

            GamePlayState.setCurrent(GamePlayState.DIALOGUE);
            dialogue.open(
                new String[]{
                    "Ah, you brought ore and that broken lever head!",
                    "Let me fire up the forge...",
                    "There you go — good as new. This should fit the drawbridge mechanism in the east."
                },
                "Blacksmith",
                true,
                () -> GamePlayState.setCurrent(GamePlayState.PLAYING)
            );
        } else if (p.findInventoryItem(FIXED_LEVER_ID) != null) {
            GamePlayState.setCurrent(GamePlayState.DIALOGUE);
            dialogue.open(
                new String[]{
                    "You already have the repaired lever.",
                    "Try the drawbridge mechanism east of here."
                },
                "Blacksmith",
                true,
                () -> GamePlayState.setCurrent(GamePlayState.PLAYING)
            );
        } else {
            GamePlayState.setCurrent(GamePlayState.DIALOGUE);
            dialogue.open(
                new String[]{
                    "I'm the blacksmith. I can fix just about anything — if you bring me the right materials.",
                    "I've heard there's a broken lever buried in an ore vein to the north.",
                    "Bring me the ore and the lever head, and I'll forge you a proper replacement."
                },
                "Blacksmith",
                true,
                () -> GamePlayState.setCurrent(GamePlayState.PLAYING)
            );
        }
    }

    @Override
    public void panVisual(double panX, double panY) {
        if (blacksmithSprite != null) {
            blacksmithSprite.move(panX, panY);
        }
        body.move(panX, panY);
        apron.move(panX, panY);
        titleLabel.move(panX, panY);
        hintLabel.move(panX, panY);
    }

    @Override
    public void resetVisualPosition() {
        if (blacksmithSprite != null) {
            blacksmithSprite.setLocation(x + (48.0 - spriteRenderWidth) / 2.0, y + (48.0 - spriteRenderHeight));
        }
        body.setLocation(x, y);
        apron.setLocation(x + 6, y + 26);
        positionLabels();
    }

    private GImage loadSprite(String path) {
        try {
            BufferedImage source = ImageIO.read(new File(path));
            if (source == null) return null;
            BufferedImage trimmed = trimTransparentBounds(source);
            GImage image = new GImage(trimmed);
            double nativeWidth = Math.max(1.0, image.getWidth());
            double nativeHeight = Math.max(1.0, image.getHeight());
            double scale = NPC_SPRITE_TARGET_HEIGHT / nativeHeight;
            spriteRenderWidth = Math.max(48.0, nativeWidth * scale);
            spriteRenderHeight = NPC_SPRITE_TARGET_HEIGHT;
            image.setSize(spriteRenderWidth, spriteRenderHeight);
            image.setLocation(x + (48.0 - spriteRenderWidth) / 2.0, y + (48.0 - spriteRenderHeight));
            return image;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private void positionLabels() {
        double centerX = x + body.getWidth() / 2.0;
        titleLabel.setLocation(centerX - titleLabel.getWidth() / 2.0, y + TITLE_BASELINE_Y);
        hintLabel.setLocation(centerX - hintLabel.getWidth() / 2.0, y + HINT_BASELINE_Y);
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
