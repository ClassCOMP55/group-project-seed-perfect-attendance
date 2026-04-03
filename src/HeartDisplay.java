import acm.graphics.GCanvas;
import acm.graphics.GRect;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Small reusable pixel-heart display that renders in half-heart units.
 * Owners pass the number of filled half-hearts and this class handles the visuals.
 */
public class HeartDisplay {

    private static final int[][] HEART_PIXEL_COORDS = {
        {1, 0}, {2, 0}, {4, 0}, {5, 0},
        {0, 1}, {1, 1}, {2, 1}, {3, 1}, {4, 1}, {5, 1}, {6, 1},
        {0, 2}, {1, 2}, {2, 2}, {3, 2}, {4, 2}, {5, 2}, {6, 2},
        {1, 3}, {2, 3}, {3, 3}, {4, 3}, {5, 3},
        {2, 4}, {3, 4}, {4, 4},
        {3, 5}
    };

    private static final double HEART_GRID_WIDTH = 7.0;
    private static final double HEART_GRID_HEIGHT = 6.0;
    private static final double HEART_GAP_CELLS = 3.0;
    private static final int HALF_HEARTS_PER_HEART = 2;
    private static final double SHADOW_OFFSET = 1.0;

    private static final Color DEFAULT_FULL_COLOR = new Color(214, 63, 47);
    private static final Color DEFAULT_EMPTY_COLOR = Color.LIGHT_GRAY;
    private static final Color DEFAULT_BORDER_COLOR = Color.BLACK;
    private static final Color DEFAULT_SHADOW_COLOR = new Color(54, 24, 24);

    private final int heartCount;
    private final double cellSize;
    private final List<HeartPixel> pixels = new ArrayList<>();

    private GraphicsPane host;
    private GCanvas canvasHost;
    private double originX;
    private double originY;
    private int filledHalfHearts;
    private Color fullColor = DEFAULT_FULL_COLOR;
    private Color emptyColor = DEFAULT_EMPTY_COLOR;
    private Color borderColor = DEFAULT_BORDER_COLOR;
    private Color shadowColor = DEFAULT_SHADOW_COLOR;

    public HeartDisplay(int heartCount, double cellSize) {
        this.heartCount = Math.max(0, heartCount);
        this.cellSize = Math.max(1.0, cellSize);
    }

    public static double widthFor(int heartCount, double cellSize) {
        int safeHeartCount = Math.max(0, heartCount);
        double safeCellSize = Math.max(1.0, cellSize);
        if (safeHeartCount == 0) {
            return 0.0;
        }
        double heartWidth = HEART_GRID_WIDTH * safeCellSize;
        double gapWidth = HEART_GAP_CELLS * safeCellSize;
        return safeHeartCount * heartWidth + Math.max(0, safeHeartCount - 1) * gapWidth;
    }

    public static double heightFor(double cellSize) {
        return HEART_GRID_HEIGHT * Math.max(1.0, cellSize);
    }

    public void setColors(Color fullColor, Color emptyColor, Color borderColor) {
        if (fullColor != null) {
            this.fullColor = fullColor;
        }
        if (emptyColor != null) {
            this.emptyColor = emptyColor;
        }
        if (borderColor != null) {
            this.borderColor = borderColor;
            this.shadowColor = borderColor.darker();
        }
        refreshColors();
    }

    public void setShadowColor(Color shadowColor) {
        if (shadowColor != null) {
            this.shadowColor = shadowColor;
        }
        refreshColors();
    }

    public void show(GraphicsPane host, double x, double y) {
        remove();
        this.host = host;
        this.canvasHost = null;
        this.originX = x;
        this.originY = y;
        buildPixels();
        refreshColors();
        bringToFront();
    }

    public void show(GCanvas canvas, double x, double y) {
        remove();
        this.host = null;
        this.canvasHost = canvas;
        this.originX = x;
        this.originY = y;
        buildPixels();
        refreshColors();
        bringToFront();
    }

    public void remove() {
        if (host != null) {
            for (HeartPixel pixel : pixels) {
                host.mainScreen.remove(pixel.shadowRect);
                host.mainScreen.remove(pixel.rect);
                host.contents.remove(pixel.shadowRect);
                host.contents.remove(pixel.rect);
            }
        } else if (canvasHost != null) {
            for (HeartPixel pixel : pixels) {
                canvasHost.remove(pixel.shadowRect);
                canvasHost.remove(pixel.rect);
            }
        }
        pixels.clear();
        host = null;
        canvasHost = null;
    }

    public void setFilledHalfHearts(int filledHalfHearts) {
        int maxHalfHearts = heartCount * HALF_HEARTS_PER_HEART;
        this.filledHalfHearts = Math.max(0, Math.min(maxHalfHearts, filledHalfHearts));
        refreshColors();
    }

    public void setVisible(boolean visible) {
        for (HeartPixel pixel : pixels) {
            pixel.shadowRect.setVisible(visible);
            pixel.rect.setVisible(visible);
        }
    }

    public void bringToFront() {
        for (HeartPixel pixel : pixels) {
            pixel.shadowRect.sendToFront();
        }
        for (HeartPixel pixel : pixels) {
            pixel.rect.sendToFront();
        }
    }

    public void setPosition(double x, double y) {
        if (pixels.isEmpty()) {
            originX = x;
            originY = y;
            return;
        }
        moveBy(x - originX, y - originY);
    }

    public void moveBy(double dx, double dy) {
        if (dx == 0.0 && dy == 0.0) {
            return;
        }
        originX += dx;
        originY += dy;
        for (HeartPixel pixel : pixels) {
            pixel.shadowRect.move(dx, dy);
            pixel.rect.move(dx, dy);
        }
    }

    public double getWidth() {
        return widthFor(heartCount, cellSize);
    }

    public double getHeight() {
        return heightFor(cellSize);
    }

    private void buildPixels() {
        if (host == null && canvasHost == null) {
            return;
        }
        double heartWidth = HEART_GRID_WIDTH * cellSize;
        double heartGap = HEART_GAP_CELLS * cellSize;

        for (int heartIndex = 0; heartIndex < heartCount; heartIndex++) {
            double heartX = originX + heartIndex * (heartWidth + heartGap);
            for (int[] cell : HEART_PIXEL_COORDS) {
                GRect shadowRect = new GRect(
                    heartX + cell[0] * cellSize + SHADOW_OFFSET,
                    originY + cell[1] * cellSize + SHADOW_OFFSET,
                    cellSize,
                    cellSize
                );
                shadowRect.setFilled(true);
                shadowRect.setColor(shadowColor);
                shadowRect.setFillColor(shadowColor);

                GRect pixelRect = new GRect(
                    heartX + cell[0] * cellSize,
                    originY + cell[1] * cellSize,
                    cellSize,
                    cellSize
                );
                pixelRect.setFilled(true);
                pixelRect.setColor(borderColor);
                pixelRect.setFillColor(emptyColor);
                if (host != null) {
                    host.place(shadowRect);
                    host.place(pixelRect);
                } else {
                    canvasHost.add(shadowRect);
                    canvasHost.add(pixelRect);
                }
                pixels.add(new HeartPixel(shadowRect, pixelRect, heartIndex, cell[0] <= 3));
            }
        }
    }

    private void refreshColors() {
        for (HeartPixel pixel : pixels) {
            int heartUnits = Math.max(
                0,
                Math.min(HALF_HEARTS_PER_HEART, filledHalfHearts - pixel.heartIndex * HALF_HEARTS_PER_HEART)
            );
            boolean isFilled = pixel.isLeftHalf ? heartUnits >= 1 : heartUnits >= 2;
            Color fill = isFilled ? fullColor : emptyColor;
            pixel.shadowRect.setColor(shadowColor);
            pixel.shadowRect.setFillColor(shadowColor);
            pixel.rect.setColor(borderColor);
            pixel.rect.setFillColor(fill);
        }
    }

    private static class HeartPixel {
        private final GRect shadowRect;
        private final GRect rect;
        private final int heartIndex;
        private final boolean isLeftHalf;

        private HeartPixel(GRect shadowRect, GRect rect, int heartIndex, boolean isLeftHalf) {
            this.shadowRect = shadowRect;
            this.rect = rect;
            this.heartIndex = heartIndex;
            this.isLeftHalf = isLeftHalf;
        }
    }
}
