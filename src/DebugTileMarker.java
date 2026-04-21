import acm.graphics.GCanvas;
import acm.graphics.GRect;

import java.awt.Color;

/**
 * Visual-only debug marker for a single tile.
 * Does not block or interact with gameplay; it only shows a colored square.
 */
public class DebugTileMarker extends WorldObject {

    private final GRect placeholder;
    private final double renderSize;
    private GCanvas lastCanvas;
    private boolean drawn;

    public DebugTileMarker(double x, double y, Color fillColor) {
        this(x, y, fillColor, 48.0);
    }

    public DebugTileMarker(double x, double y, Color fillColor, double renderSize) {
        super(x, y, 0, 0);
        this.renderSize = renderSize;
        this.placeholder = new GRect(x, y, renderSize, renderSize);
        this.placeholder.setFilled(true);
        this.placeholder.setFillColor(fillColor);
        this.placeholder.setColor(fillColor.darker());
        this.drawn = false;
    }

    @Override
    public void draw(GCanvas canvas) {
        this.lastCanvas = canvas;
        resetVisualPosition();
        syncCanvasVisibility();
    }

    @Override
    public void removeFrom(GCanvas canvas) {
        canvas.remove(placeholder);
        drawn = false;
        if (lastCanvas == canvas) {
            lastCanvas = null;
        }
    }

    @Override
    public void update(double dt) {
        syncCanvasVisibility();
    }

    @Override
    public void panVisual(double panX, double panY) {
        placeholder.move(panX, panY);
    }

    @Override
    public void resetVisualPosition() {
        placeholder.setLocation(x, y);
    }

    /** Debug markers should render above room foreground overlays. */
    public void bringToFront() {
        if (drawn) {
            placeholder.sendToFront();
        }
    }

    private void syncCanvasVisibility() {
        if (lastCanvas == null) {
            return;
        }

        boolean shouldShow = visible && GameplayPane.areWorldDebugMarkersVisible();
        if (shouldShow) {
            placeholder.setLocation(x, y);
            if (!drawn) {
                lastCanvas.add(placeholder);
                drawn = true;
            }
            placeholder.setVisible(true);
            placeholder.sendToFront();
        } else if (drawn) {
            lastCanvas.remove(placeholder);
            drawn = false;
        }
    }
}
