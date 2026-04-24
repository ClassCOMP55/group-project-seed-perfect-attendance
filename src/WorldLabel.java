import acm.graphics.GCanvas;
import acm.graphics.GLabel;
import acm.graphics.GRect;

import java.awt.Color;

/**
 * Lightweight passive label for naming prototype map features inside a room.
 * Lives in the Room's WorldObject list so it pans and redraws with the rest of the room.
 */
public class WorldLabel extends WorldObject {
    private static final double LABEL_H_PADDING = 10.0;
    private static final double LABEL_V_PADDING = 6.0;

    private static final Color LABEL_TEXT_COLOR = new Color(247, 238, 198);
    private static final Color LABEL_FILL_COLOR = new Color(18, 18, 28, 210);
    private static final Color LABEL_EDGE_COLOR = new Color(120, 111, 86);

    /** Visual anchor in world space. The inherited hitbox stays tiny because the label is non-interactive. */
    private final double anchorX;
    private final double anchorY;

    private final GRect backdrop;
    private final GLabel label;

    private final double backdropWidth;
    private final double backdropHeight;

    public WorldLabel(double centerX, double centerY, String text) {
        super(centerX - 1.0, centerY - 1.0, 2.0, 2.0);
        this.anchorX = centerX;
        this.anchorY = centerY;

        String shownText = (text == null || text.trim().isEmpty()) ? "Label" : text.trim();

        this.label = new GLabel(shownText, 0, 0);
        this.label.setFont("Courier New-BOLD-14");
        this.label.setColor(LABEL_TEXT_COLOR);

        this.backdropWidth = label.getWidth() + LABEL_H_PADDING * 2.0;
        this.backdropHeight = label.getAscent() + label.getDescent() + LABEL_V_PADDING * 2.0;

        this.backdrop = new GRect(0, 0, backdropWidth, backdropHeight);
        this.backdrop.setFilled(true);
        this.backdrop.setFillColor(LABEL_FILL_COLOR);
        this.backdrop.setColor(LABEL_EDGE_COLOR);

        resetVisualPosition();
    }

    @Override
    public void draw(GCanvas canvas) {
        if (!visible) return;
        resetVisualPosition();
        canvas.add(backdrop);
        canvas.add(label);
    }

    @Override
    public void removeFrom(GCanvas canvas) {
        canvas.remove(backdrop);
        canvas.remove(label);
    }

    @Override
    public void panVisual(double panX, double panY) {
        backdrop.move(panX, panY);
        label.move(panX, panY);
    }

    @Override
    public void resetVisualPosition() {
        backdrop.setLocation(anchorX - backdropWidth / 2.0, anchorY - backdropHeight / 2.0);
        label.setLocation(
            anchorX - label.getWidth() / 2.0,
            anchorY + (label.getAscent() - label.getDescent()) / 2.0
        );
    }
}
