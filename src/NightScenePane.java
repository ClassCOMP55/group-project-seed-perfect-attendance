import java.awt.Color;

import acm.graphics.GLabel;
import acm.graphics.GLine;
import acm.graphics.GObject;
import acm.graphics.GOval;
import acm.graphics.GPolygon;
import acm.graphics.GRect;
import acm.graphics.GRoundRect;

/**
 * Shared night sky, stars, moon, hills, frame, and title banner used by the
 * landing page and main menu for a consistent look.
 */
public class NightScenePane extends GraphicsPane {

    /** Title banner labels (shared landing + main menu). */
    private GLabel titleShadow;
    private GLabel titleMain;
    private GLabel tagline;

    protected static final Color NIGHT_CREAM = new Color(255, 248, 220);
    protected static final Color NIGHT_GOLD = new Color(255, 215, 120);
    protected static final Color NIGHT_BUTTON_FILL = new Color(35, 40, 75);

    /**
     * Paints gradient sky, stars, moon, hills, and frame across the
     * <strong>entire</strong> graphics window so wide/tall windows never show
     * black bands at the sides (layout UI still uses {@link #scaleX}/{@link #scaleY}).
     */
    protected void paintNightSky() {
        double fw = mainScreen.getWidth();
        double fh = mainScreen.getHeight();
        addSkyGradient(0, 0, fw, fh);
        addStars(0, 0, fw, fh);
        addMoon(0, 0, fw, fh);
        addHills(0, 0, fw, fh);
        addVignetteFrame(0, 0, fw, fh);
    }

    /** Same title stack as the landing page (shadow, title, line, tagline). */
    protected void addTitleBanner() {
        int titleSize = 26;
        titleShadow = new GLabel("So There's This Wizard That's a Goat", 0, 0);
        titleShadow.setFont(displayFont(titleSize));
        titleShadow.setColor(new Color(20, 25, 55));
        titleShadow.setLocation(centeredX(titleShadow) + 3, scaleY(112) + 3);
        addGraphic(titleShadow);

        titleMain = new GLabel("So There's This Wizard That's a Goat", 0, 0);
        titleMain.setFont(displayFont(titleSize));
        titleMain.setColor(NIGHT_CREAM);
        titleMain.setLocation(centeredX(titleMain), scaleY(112));
        addGraphic(titleMain);

        GLine underline = new GLine(scaleX(72), scaleY(150), scaleX(628), scaleY(150));
        underline.setColor(new Color(100, 90, 140));
        addGraphic(underline);

        tagline = new GLabel("— a storytelling game —", 0, 0);
        tagline.setFont(displayFont(13));
        tagline.setColor(new Color(140, 150, 200));
        tagline.setLocation(centeredX(tagline), scaleY(168));
        addGraphic(tagline);
    }

    protected void addGraphic(GObject g) {
        contents.add(g);
        mainScreen.add(g);
    }

    protected void centerLabelInRect(GLabel g, GRect r) {
        double x = r.getX() + (r.getWidth() - g.getWidth()) / 2;
        double y = r.getY() + (r.getHeight() + g.getAscent()) / 2;
        g.setLocation(x, y);
    }

    protected String displayFont(int base) {
        return "Courier New-BOLD-" + Math.max(11, scaleFontSize(base));
    }

    /**
     * Font spec for emoji code points. {@link #displayFont(int)} uses SansSerif, which usually
     * has no color-emoji glyphs, so 🐐 / 🧙 render as blank — this picks an OS emoji font when present.
     */
    protected String emojiDisplayFont(int base) {
        return "Courier New-BOLD-" + Math.max(24, scaleFontSize(base));
    }

    /**
     * Button width in pixels, consistent with horizontal layout scaling.
     * (Avoid {@code N * uniformScale()} for width — use the same basis as {@link #scaleX}.)
     */
    protected double nightButtonWidth() {
        return scaleX(260) - scaleX(0);
    }

    /**
     * Button height in pixels, consistent with vertical layout scaling.
     * Slightly compact so three rows + margins fit without clipping at the window edge.
     */
    protected double nightButtonHeight() {
        return scaleY(36) - scaleY(0);
    }

    protected double nightButtonGap() {
        return scaleY(6) - scaleY(0);
    }

    protected double nightButtonCornerRadius() {
        double rx = scaleX(14) - scaleX(0);
        double ry = scaleY(14) - scaleY(0);
        return Math.min(rx, ry);
    }

    /** Gold-outlined rounded button — {@code y} is top edge in canvas pixels. */
    protected GRoundRect addNightButton(double ox, double lw, double y, double bw, double bh) {
        double bx = ox + (lw - bw) / 2;
        GRoundRect frame = new GRoundRect(bx, y, bw, bh, nightButtonCornerRadius());
        frame.setFilled(true);
        frame.setFillColor(NIGHT_BUTTON_FILL);
        frame.setColor(NIGHT_GOLD);
        addGraphic(frame);
        return frame;
    }

    /**
     * Vertical positions for three stacked menu buttons.
     * Uses a generous bottom inset so rounded strokes and OS window chrome never clip Quit.
     */
    protected double[] threeMenuButtonTops() {
        double bh = nightButtonHeight();
        double g = nightButtonGap();
        double margin = scaleY(52) - scaleY(0);
        double y3Top = scaleY(500) - margin - bh;
        double y2Top = y3Top - g - bh;
        double y1Top = y2Top - g - bh;
        return new double[] { y1Top, y2Top, y3Top };
    }

    private void addSkyGradient(double ox, double oy, double lw, double lh) {
        int bands = 14;
        for (int i = 0; i < bands; i++) {
            double t = i / (double) (bands - 1);
            int r = (int) (8 + t * 35);
            int g = (int) (12 + t * 28);
            int b = (int) (42 + t * 50);
            Color c = new Color(Math.min(255, r), Math.min(255, g), Math.min(255, b));
            double y0 = oy + lh * i / bands;
            double y1 = oy + lh * (i + 1) / bands + 1;
            GRect band = new GRect(ox, y0, lw, y1 - y0);
            band.setFilled(true);
            band.setColor(c);
            band.setFillColor(c);
            addGraphic(band);
        }
    }

    private void addStars(double ox, double oy, double lw, double lh) {
        java.util.Random rnd = new java.util.Random(42);
        for (int i = 0; i < 85; i++) {
            double sx = ox + rnd.nextDouble() * lw * 0.95 + lw * 0.025;
            double sy = oy + rnd.nextDouble() * lh * 0.55 + lh * 0.04;
            double sz = (rnd.nextDouble() < 0.85 ? 1.5 : 2.8) * uniformScale();
            GOval star = new GOval(sx, sy, sz, sz);
            star.setFilled(true);
            int v = 200 + rnd.nextInt(55);
            star.setColor(new Color(v, v, v));
            star.setFillColor(new Color(v, v, v));
            addGraphic(star);
        }
    }

    private void addMoon(double ox, double oy, double lw, double lh) {
        double r = 38 * uniformScale();
        double mx = ox + lw * 0.78;
        double my = oy + lh * 0.08;
        GOval moon = new GOval(mx, my, r * 2, r * 2);
        moon.setFilled(true);
        moon.setFillColor(new Color(255, 252, 235));
        moon.setColor(new Color(255, 252, 235));
        addGraphic(moon);
        GOval bite = new GOval(mx + r * 0.35, my - r * 0.1, r * 1.8, r * 1.9);
        bite.setFilled(true);
        bite.setFillColor(new Color(25, 32, 65));
        bite.setColor(new Color(25, 32, 65));
        addGraphic(bite);
    }

    private void addHills(double ox, double oy, double lw, double lh) {
        Color hill = new Color(8, 10, 22);
        Color hill2 = new Color(12, 14, 28);

        GPolygon h1 = new GPolygon();
        h1.addVertex(0, 0);
        h1.addVertex(lw * 0.45, -lh * 0.12);
        h1.addVertex(lw, 0);
        h1.setFilled(true);
        h1.setFillColor(hill);
        h1.setColor(hill);
        h1.setLocation(ox, oy + lh * 0.72);
        addGraphic(h1);

        GPolygon h2 = new GPolygon();
        h2.addVertex(0, 0);
        h2.addVertex(lw * 0.55, -lh * 0.08);
        h2.addVertex(lw, 0);
        h2.setFilled(true);
        h2.setFillColor(hill2);
        h2.setColor(hill2);
        h2.setLocation(ox, oy + lh * 0.78);
        addGraphic(h2);
    }

    private void addVignetteFrame(double ox, double oy, double lw, double lh) {
        double inset = 12 * uniformScale();
        GRoundRect frame = new GRoundRect(ox + inset, oy + inset, lw - 2 * inset, lh - 2 * inset, 18 * uniformScale());
        frame.setFilled(false);
        frame.setColor(new Color(255, 255, 255, 80));
        addGraphic(frame);
    }
}
