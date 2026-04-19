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
 * Stationary NPC that opens the shop overlay.
 */
public class BreadMerchant extends WorldObject {
    private static final double NPC_SPRITE_TARGET_HEIGHT = 32.0;
    private static final double TITLE_BASELINE_Y = -10.0;
    private static final double HINT_BASELINE_Y = 64.0;

    private static final Color NPC_BODY_COLOR = new Color(174, 124, 78);
    private static final Color NPC_BODY_EDGE = new Color(90, 58, 31);
    private static final Color APRON_COLOR = new Color(238, 214, 171);
    private static final Color TITLE_COLOR = new Color(251, 239, 204);
    private static final Color HINT_COLOR = new Color(222, 241, 184);

    private final String merchantName;
    private final ShopMenu shopMenu;
    private Dialogue dialogue;

    private final GImage merchantSprite;
    private final GRect body;
    private final GRect apron;
    private final GLabel titleLabel;
    private final GLabel hintLabel;
    private double spriteRenderWidth = 48.0;
    private double spriteRenderHeight = 48.0;

    public BreadMerchant(double x, double y, String merchantName, ShopMenu shopMenu) {
        this(x, y, merchantName, shopMenu, null);
    }

    public BreadMerchant(double x, double y, String merchantName, ShopMenu shopMenu, Dialogue dialogue) {
        super(x, y, 48, 48);
        this.merchantName = (merchantName == null || merchantName.trim().isEmpty())
            ? "Bread Merchant"
            : merchantName.trim();
        this.shopMenu = shopMenu;
        this.dialogue = dialogue;

        merchantSprite = loadSprite("assets/visuals/png's/bread maker.png");

        body = new GRect(x, y, 48, 48);
        body.setFilled(true);
        body.setFillColor(NPC_BODY_COLOR);
        body.setColor(NPC_BODY_EDGE);

        apron = new GRect(x + 8, y + 24, 32, 18);
        apron.setFilled(true);
        apron.setFillColor(APRON_COLOR);
        apron.setColor(APRON_COLOR.darker());

        titleLabel = new GLabel(this.merchantName, x, y);
        titleLabel.setFont("SansSerif-BOLD-12");
        titleLabel.setColor(TITLE_COLOR);

        hintLabel = new GLabel("e to shop", x, y);
        hintLabel.setFont("SansSerif-PLAIN-12");
        hintLabel.setColor(HINT_COLOR);

        resetVisualPosition();
    }

    @Override
    public void draw(GCanvas canvas) {
        if (!visible) return;
        resetVisualPosition();
        if (merchantSprite != null) {
            canvas.add(merchantSprite);
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
        if (merchantSprite != null) {
            canvas.remove(merchantSprite);
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
        if (shopMenu == null || p == null) {
            return;
        }
        if (shopMenu.isOpen()) {
            return;
        }
        if (dialogue != null && !dialogue.isOpen()) {
            GamePlayState.setCurrent(GamePlayState.DIALOGUE);
            dialogue.open(
                new String[]{
                    "Oh, hello there, friend! Isn't it just the loveliest day?",
                    "I'm not entirely sure what all that ruckus was earlier, but it doesn't matter!",
                    "I've got something much better. My magical yummy bread! It heals your body and your soul."
                },
                merchantName,
                true,
                () -> {
                    GamePlayState.setCurrent(GamePlayState.PLAYING);
                    shopMenu.openFor(p, merchantName, () -> {
                        if (dialogue != null && !dialogue.isOpen()) {
                            GamePlayState.setCurrent(GamePlayState.DIALOGUE);
                            dialogue.open(
                                new String[]{
                                    "You take care now, friend!",
                                    "And remember, if you ever need more magical yummy bread to brighten your day, you know where to find me!"
                                },
                                merchantName,
                                true,
                                () -> GamePlayState.setCurrent(GamePlayState.PLAYING)
                            );
                        }
                    });
                }
            );
        } else {
            shopMenu.openFor(p, merchantName, null);
        }
    }

    @Override
    public void panVisual(double panX, double panY) {
        if (merchantSprite != null) {
            merchantSprite.move(panX, panY);
        }
        body.move(panX, panY);
        apron.move(panX, panY);
        titleLabel.move(panX, panY);
        hintLabel.move(panX, panY);
    }

    @Override
    public void resetVisualPosition() {
        if (merchantSprite != null) {
            merchantSprite.setLocation(x + (48.0 - spriteRenderWidth) / 2.0, y + (48.0 - spriteRenderHeight));
        }
        body.setLocation(x, y);
        apron.setLocation(x + 8, y + 24);
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
            spriteRenderWidth = nativeWidth * scale;
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
