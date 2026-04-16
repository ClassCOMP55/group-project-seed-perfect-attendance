import acm.graphics.GCanvas;
import acm.graphics.GImage;
import acm.graphics.GRect;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Small reusable heart display that renders in half-heart units.
 * Owners pass the number of filled half-hearts and this class handles the visuals.
 *
 * Two rendering modes:
 *   - Image mode: one GImage per heart (full / half / empty PNG assets).
 *     Enable by calling {@link #setImages(String, String, String)} before {@link #show}.
 *   - Pixel mode (default): pixel-art heart drawn with GRect squares.
 *     Used as a fallback when no image paths are set.
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

    // Image mode: one GImage per heart slot
    private String imgFull, imgHalf, imgEmpty;
    private double imgWidthOverride  = -1;  // -1 = use cellSize-based default
    private double imgHeightOverride = -1;
    private double imgGapOverride    = -1;
    private final List<GImage> heartImages = new ArrayList<>();

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

    /**
     * Switches this display to image mode. Call before {@link #show}.
     * Each heart slot will render one PNG instead of pixel-art squares.
     *
     * @param fullPath  path to the full-heart image  (e.g. "assets/visuals/hearts/pixel heart full single.png")
     * @param halfPath  path to the half-heart image  (e.g. "assets/visuals/hearts/pixel heart half.png")
     * @param emptyPath path to the empty-heart image (e.g. "assets/visuals/hearts/pixel heart empty.png")
     */
    public void setImages(String fullPath, String halfPath, String emptyPath) {
        this.imgFull  = fullPath;
        this.imgHalf  = halfPath;
        this.imgEmpty = emptyPath;
    }

    /**
     * Overrides the rendered width and height of each heart image.
     * Call before {@link #show}. Only applies in image mode.
     *
     * @param width  pixels wide per heart
     * @param height pixels tall per heart
     */
    public void setImageSize(double width, double height) {
        this.imgWidthOverride  = Math.max(1.0, width);
        this.imgHeightOverride = Math.max(1.0, height);
    }

    /**
     * Overrides the pixel gap between heart images.
     * Call before {@link #show}. Only applies in image mode.
     *
     * @param gap pixels between adjacent hearts
     */
    public void setImageGap(double gap) {
        this.imgGapOverride = Math.max(0.0, gap);
    }

    private boolean isImageMode() {
        return imgFull != null && imgHalf != null && imgEmpty != null;
    }

    private double imageWidth()  { return imgWidthOverride  > 0 ? imgWidthOverride  : HEART_GRID_WIDTH  * cellSize; }
    private double imageHeight() { return imgHeightOverride > 0 ? imgHeightOverride : HEART_GRID_HEIGHT * cellSize; }
    private double imageGap()    { return imgGapOverride    >= 0 ? imgGapOverride   : HEART_GAP_CELLS   * cellSize; }

    /** Returns the correct image path for heart slot {@code heartIndex} given current fill state. */
    private String imagePathFor(int heartIndex) {
        int units = Math.max(0, Math.min(HALF_HEARTS_PER_HEART,
                filledHalfHearts - heartIndex * HALF_HEARTS_PER_HEART));
        return units == 2 ? imgFull : units == 1 ? imgHalf : imgEmpty;
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
            for (GImage img : heartImages) {
                host.mainScreen.remove(img);
                host.contents.remove(img);
            }
        } else if (canvasHost != null) {
            for (HeartPixel pixel : pixels) {
                canvasHost.remove(pixel.shadowRect);
                canvasHost.remove(pixel.rect);
            }
            for (GImage img : heartImages) {
                canvasHost.remove(img);
            }
        }
        pixels.clear();
        heartImages.clear();
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
        for (GImage img : heartImages) {
            img.setVisible(visible);
        }
    }

    public void bringToFront() {
        for (HeartPixel pixel : pixels) {
            pixel.shadowRect.sendToFront();
        }
        for (HeartPixel pixel : pixels) {
            pixel.rect.sendToFront();
        }
        for (GImage img : heartImages) {
            img.sendToFront();
        }
    }

    public void setPosition(double x, double y) {
        if (pixels.isEmpty() && heartImages.isEmpty()) {
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
        for (GImage img : heartImages) {
            img.move(dx, dy);
        }
    }

    public double getWidth() {
        if (isImageMode() && imgWidthOverride > 0) {
            return heartCount * imgWidthOverride + Math.max(0, heartCount - 1) * imageGap();
        }
        return widthFor(heartCount, cellSize);
    }

    public double getHeight() {
        if (isImageMode() && imgHeightOverride > 0) {
            return imgHeightOverride;
        }
        return heightFor(cellSize);
    }

    private void buildPixels() {
        if (host == null && canvasHost == null) {
            return;
        }
        double heartWidth = HEART_GRID_WIDTH * cellSize;
        double heartGap   = HEART_GAP_CELLS  * cellSize;
        double heartHeight = HEART_GRID_HEIGHT * cellSize;

        if (isImageMode()) {
            double iw  = imageWidth();
            double ih  = imageHeight();
            double gap = imageGap();
            for (int i = 0; i < heartCount; i++) {
                double heartX = originX + i * (iw + gap);
                GImage img = loadScaled(imagePathFor(i), (int) iw, (int) ih);
                img.setLocation(heartX, originY);
                addToHost(img);
                heartImages.add(img);
            }
        } else {
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
    }

    private void refreshColors() {
        if (isImageMode()) {
            refreshImages();
            return;
        }
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

    /**
     * Updates each heart image to the correct full/half/empty PNG for the current fill state.
     * Because ACM's GImage does not support changing its source after creation, we remove the
     * old image and add a new one whenever the health value changes.
     */
    private void refreshImages() {
        if (heartImages.isEmpty()) {
            return;
        }
        double iw  = imageWidth();
        double ih  = imageHeight();
        double gap = imageGap();

        for (int i = 0; i < heartImages.size(); i++) {
            GImage old = heartImages.get(i);
            removeFromHost(old);

            double heartX = originX + i * (iw + gap);
            GImage img = loadScaled(imagePathFor(i), (int) iw, (int) ih);
            img.setLocation(heartX, originY);
            addToHost(img);
            heartImages.set(i, img);
        }
    }

    // ---- image scaling ----

    /**
     * Loads a PNG from disk and draws it into a fresh BufferedImage at exactly
     * (w x h) pixels using bilinear interpolation. Returns a GImage backed by
     * that pre-scaled pixel buffer so ACM never has a chance to ignore setSize().
     *
     * Falls back to a plain GImage(path) if ImageIO fails (e.g. file not found).
     */
    private static GImage loadScaled(String path, int w, int h) {
        if (w < 1) w = 1;
        if (h < 1) h = 1;
        try {
            BufferedImage src = ImageIO.read(new File(path));
            if (src == null) throw new Exception("null image");
            BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = dst.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                               RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, w, h, null);
            g.dispose();
            GImage result = new GImage(dst);
            return result;
        } catch (Exception e) {
            // fallback: let ACM load it; setSize will do its best
            GImage fallback = new GImage(path);
            fallback.setSize(w, h);
            return fallback;
        }
    }

    // ---- host helpers ----

    private void addToHost(GImage img) {
        if (host != null) {
            host.place(img);
        } else if (canvasHost != null) {
            canvasHost.add(img);
        }
    }

    private void removeFromHost(GImage img) {
        if (host != null) {
            host.mainScreen.remove(img);
            host.contents.remove(img);
        } else if (canvasHost != null) {
            canvasHost.remove(img);
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
