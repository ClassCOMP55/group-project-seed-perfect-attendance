import acm.graphics.GCanvas;
import acm.graphics.GImage;
import acm.graphics.GLabel;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

/**
 * A static world object that shows a sprite with an interaction hint label.
 * Used for decorative props (like the pickaxe next to the drunk) and one-time
 * ground pickups (like the miner's hat). Configure via setConsumeOnInteract().
 */
public class WorldProp extends WorldObject {

    private static final Color HINT_COLOR      = new Color(222, 241, 184);
    private static final double HINT_BASELINE_Y = 64.0;
    private static final double SPRITE_SIZE     = 48.0;

    private final GImage propSprite;
    private final GLabel hintLabel;
    private final Consumer<Player> onInteractAction;
    private boolean consumeOnInteract = false;

    // Stored on first draw so we can remove graphics from canvas on consume.
    private GCanvas lastCanvas;

    /**
     * @param x                 top-left world pixel X
     * @param y                 top-left world pixel Y
     * @param imagePath         path to the sprite PNG
     * @param hintText          label shown below the sprite (e.g. "e to pick up")
     * @param onInteractAction  called when player presses interact; may be null
     */
    public WorldProp(double x, double y, String imagePath, String hintText,
                     Consumer<Player> onInteractAction) {
        this(x, y, imagePath, hintText, onInteractAction, 1.0);
    }

    /**
     * Same as the main constructor but with a spriteScale multiplier applied to SPRITE_SIZE.
     * Use values above 1.0 to make the visual larger (e.g. 1.2 = 20% bigger).
     */
    public WorldProp(double x, double y, String imagePath, String hintText,
                     Consumer<Player> onInteractAction, double spriteScale) {
        super(x, y, 48, 48);
        this.onInteractAction = onInteractAction;
        this.propSprite = loadSprite(imagePath, spriteScale);

        this.hintLabel = new GLabel(hintText != null ? hintText : "e to interact", x, y);
        this.hintLabel.setFont("Courier New-BOLD-12");
        this.hintLabel.setColor(HINT_COLOR);
        positionLabel();
    }

    /** If true, the prop removes itself from the canvas after the first interaction (pickup behavior). */
    public void setConsumeOnInteract(boolean consume) {
        this.consumeOnInteract = consume;
    }

    /** Removes this prop's graphics from the canvas and deactivates its hitbox. */
    public void consume() {
        if (lastCanvas != null) {
            removeFrom(lastCanvas);
        }
        hide();
    }

    @Override
    public void draw(GCanvas canvas) {
        if (!visible) return;
        this.lastCanvas = canvas;
        resetVisualPosition();
        if (propSprite != null) canvas.add(propSprite);
        canvas.add(hintLabel);
    }

    @Override
    public void removeFrom(GCanvas canvas) {
        if (propSprite != null) canvas.remove(propSprite);
        canvas.remove(hintLabel);
    }

    @Override
    public boolean isInteractable() {
        return visible;
    }

    @Override
    public void onInteract(Player p) {
        if (!visible) return;
        if (onInteractAction != null) {
            onInteractAction.accept(p);
        }
        if (consumeOnInteract) {
            if (lastCanvas != null) {
                removeFrom(lastCanvas);
            }
            hide();
        }
    }

    @Override
    public void panVisual(double panX, double panY) {
        if (propSprite != null) propSprite.move(panX, panY);
        hintLabel.move(panX, panY);
    }

    @Override
    public void resetVisualPosition() {
        if (propSprite != null) propSprite.setLocation(x, y);
        positionLabel();
    }

    private void positionLabel() {
        hintLabel.setLocation(x + 24.0 - hintLabel.getWidth() / 2.0, y + HINT_BASELINE_Y);
    }

    private GImage loadSprite(String path, double spriteScale) {
        try {
            BufferedImage source = ImageIO.read(new File(path));
            if (source == null) return null;
            GImage image = new GImage(source);
            double displaySize = SPRITE_SIZE * spriteScale;
            double scale = displaySize / Math.max(source.getWidth(), source.getHeight());
            image.setSize(source.getWidth() * scale, source.getHeight() * scale);
            image.setLocation(x, y);
            return image;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }
}
