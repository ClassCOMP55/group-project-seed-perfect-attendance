import java.awt.Color;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import acm.graphics.GLabel;
import acm.graphics.GObject;
import acm.graphics.GRect;

/**
 * Cinematic transition between Scene 1 and Scene 2.
 *
 * Displays a time-of-day sky that shifts from bright blue afternoon
 * to warm amber/orange late afternoon, conveying the passage of time
 * between the guard encounter and the market debrief.
 *
 * Visual sequence:
 *   1. Opens on a blue afternoon sky with clouds and sun
 *   2. Sky smoothly transitions through golden hour colours
 *   3. Narrative text fades in: "Later that afternoon..."
 *   4. Sky settles at deep amber/orange with long shadows
 *   5. Auto-advances to Scene 2
 *
 * Uses the same threaded animation pattern as {@link SkyTransitionPane}.
 */
public class Scene1To2TransitionPane extends GraphicsPane {

    // =========================================================
    // COLOUR PALETTE — SKY GRADIENT STAGES
    // =========================================================

    // Stage 0: Blue afternoon sky
    private static final Color SKY_BLUE_TOP     = new Color(70, 130, 210);
    private static final Color SKY_BLUE_MID     = new Color(110, 170, 230);
    private static final Color SKY_BLUE_LOW     = new Color(160, 200, 240);

    // Stage 1: Golden transition
    private static final Color SKY_GOLD_TOP     = new Color(100, 140, 190);
    private static final Color SKY_GOLD_MID     = new Color(180, 170, 130);
    private static final Color SKY_GOLD_LOW     = new Color(230, 190, 110);

    // Stage 2: Amber/orange late afternoon
    private static final Color SKY_AMBER_TOP    = new Color(80, 100, 150);
    private static final Color SKY_AMBER_MID    = new Color(200, 140, 70);
    private static final Color SKY_AMBER_LOW    = new Color(240, 160, 60);

    // Sun colours
    private static final Color C_SUN_CORE       = new Color(255, 240, 180);
    private static final Color C_SUN_GLOW       = new Color(255, 220, 120, 80);
    private static final Color C_SUN_AMBER      = new Color(255, 180, 60);
    private static final Color C_SUN_AMBER_GLOW = new Color(255, 160, 40, 60);

    // Cloud colours
    private static final Color C_CLOUD_WHITE    = new Color(240, 240, 250, 180);
    private static final Color C_CLOUD_GOLD     = new Color(255, 220, 150, 160);
    private static final Color C_CLOUD_AMBER    = new Color(255, 180, 100, 140);

    // Silhouette / ground
    private static final Color C_GROUND         = new Color(30, 25, 20);
    private static final Color C_SILHOUETTE     = new Color(20, 18, 15);

    // Text
    private static final Color C_TEXT           = new Color(255, 245, 220);
    private static final Color C_TEXT_DIM       = new Color(200, 190, 160);

    // =========================================================
    // TIMING CONSTANTS
    // =========================================================

    /** Frames for each colour transition stage. */
    private static final int TRANSITION_FRAMES = 60;
    /** Milliseconds per frame. */
    private static final int FRAME_MS = 35;
    /** Pause at blue sky before transition starts (ms). */
    private static final int PAUSE_BLUE_MS = 1500;
    /** Pause at gold midpoint (ms). */
    private static final int PAUSE_GOLD_MS = 800;
    /** Pause at amber before text (ms). */
    private static final int PAUSE_AMBER_MS = 600;
    /** Hold on final scene with text (ms). */
    private static final int HOLD_FINAL_MS = 2200;
    /** Frames for fade-to-black at the end. */
    private static final int FADE_OUT_FRAMES = 40;
    /** Milliseconds per fade frame. */
    private static final int FADE_FRAME_MS = 30;

    // =========================================================
    // LAYOUT CONSTANTS
    // =========================================================

    /** Number of sky gradient bands. */
    private static final int SKY_BANDS = 20;
    private static final double BAND_HEIGHT = 500.0 / SKY_BANDS;

    // Cloud positions (logical coords)
    private static final double[][] CLOUD_POSITIONS = {
        {80,  60,  90, 22},
        {400, 40,  110, 26},
        {250, 110, 70, 18},
        {550, 90,  80, 20},
        {150, 150, 60, 16},
    };

    // =========================================================
    // STATE
    // =========================================================

    private volatile boolean animating = false;
    private volatile int runId = 0;

    /** Sky band rectangles (top to bottom). */
    private final List<GRect> skyBands = new ArrayList<>();
    /** Cloud rectangles. */
    private final List<GRect> clouds = new ArrayList<>();
    /** Sun elements (core + glow). */
    private GRect sunCore;
    private GRect sunGlow;
    /** Narrative text label. */
    private GLabel narrativeLabel;
    /** Ground / silhouette elements. */
    private final List<GRect> groundElements = new ArrayList<>();
    /** Full-screen black overlay for fade-to-black. */
    private GRect fadeOverlay;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    public Scene1To2TransitionPane(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
    }

    // =========================================================
    // LIFECYCLE
    // =========================================================

    @Override
    public void showContent() {
        animating = true;
        skyBands.clear();
        clouds.clear();
        groundElements.clear();

        buildScene();

        runId++;
        final int thisRun = runId;
        new Thread(() -> runCinematic(thisRun)).start();
    }

    @Override
    public void hideContent() {
        runId++;
        animating = false;
        for (GObject item : contents) {
            mainScreen.remove(item);
        }
        contents.clear();
        skyBands.clear();
        clouds.clear();
        groundElements.clear();
    }

    @Override
    public void refreshLayout() {
        int savedRun = runId;
        runId++;
        animating = false;

        for (GObject item : contents) {
            mainScreen.remove(item);
        }
        contents.clear();
        skyBands.clear();
        clouds.clear();
        groundElements.clear();

        buildScene();

        runId++;
        final int thisRun = runId;
        animating = true;
        // On resize, just hold the amber state and auto-advance after a beat
        setSkyColors(SKY_AMBER_TOP, SKY_AMBER_MID, SKY_AMBER_LOW);
        setCloudColor(C_CLOUD_AMBER);
        setSunAmber();
        showNarrativeText();
        new Thread(() -> {
            try {
                Thread.sleep(HOLD_FINAL_MS);
                if (animating && thisRun == runId) {
                    mainScreen.switchToScene2Screen();
                }
            } catch (InterruptedException e) { /* clean exit */ }
        }).start();
    }

    // =========================================================
    // BUILD SCENE
    // =========================================================

    private void buildScene() {
        // Sky gradient bands
        for (int i = 0; i < SKY_BANDS; i++) {
            double ly = i * BAND_HEIGHT;
            GRect band = srect(0, ly, 700, BAND_HEIGHT + 1, SKY_BLUE_TOP, SKY_BLUE_TOP);
            skyBands.add(band);
            place(band);
        }
        // Set initial blue gradient
        setSkyColors(SKY_BLUE_TOP, SKY_BLUE_MID, SKY_BLUE_LOW);

        // Sun glow (behind core)
        sunGlow = srect(530, 55, 50, 50, C_SUN_GLOW, C_SUN_GLOW);
        place(sunGlow);

        // Sun core
        sunCore = srect(540, 65, 30, 30, C_SUN_CORE, C_SUN_CORE);
        place(sunCore);

        // Clouds
        for (double[] cp : CLOUD_POSITIONS) {
            GRect cloud = srect(cp[0], cp[1], cp[2], cp[3], C_CLOUD_WHITE, C_CLOUD_WHITE);
            clouds.add(cloud);
            place(cloud);
        }

        // Ground silhouette strip
        GRect ground = srect(0, 420, 700, 80, C_GROUND, C_GROUND);
        groundElements.add(ground);
        place(ground);

        // Building silhouettes
        drawSilhouettes();

        // Narrative text (hidden initially)
        narrativeLabel = pixelLabel("Later that afternoon...", 14, C_TEXT);
        narrativeLabel.setLocation(centeredX(narrativeLabel), scaleY(240));
        narrativeLabel.setVisible(false);
        place(narrativeLabel);

        // Fade overlay (on top of everything, starts fully transparent)
        fadeOverlay = rect(0, 0, mainScreen.getWidth(), mainScreen.getHeight(),
            new Color(0, 0, 0, 0), new Color(0, 0, 0, 0));
        place(fadeOverlay);
    }

    private void drawSilhouettes() {
        // City rooftops silhouette along the ground line
        placeSilhouette(60,  385, 30, 35);
        placeSilhouette(95,  390, 25, 30);
        placeSilhouette(125, 380, 40, 40);

        placeSilhouette(220, 388, 20, 32);
        placeSilhouette(245, 395, 35, 25);

        placeSilhouette(340, 382, 15, 38);  // tower
        placeSilhouette(360, 390, 30, 30);
        placeSilhouette(395, 385, 22, 35);

        placeSilhouette(480, 392, 28, 28);
        placeSilhouette(515, 388, 20, 32);
        placeSilhouette(540, 395, 40, 25);

        placeSilhouette(620, 385, 12, 35);  // spire
        placeSilhouette(638, 392, 30, 28);
    }

    private void placeSilhouette(double lx, double ly, double lw, double lh) {
        GRect sil = srect(lx, ly, lw, lh, C_SILHOUETTE, C_SILHOUETTE);
        groundElements.add(sil);
        place(sil);
    }

    // =========================================================
    // ANIMATION
    // =========================================================

    private boolean still(int id) {
        return animating && id == runId;
    }

    private void runCinematic(int id) {
        try {
            // Phase 1: Hold blue sky
            Thread.sleep(PAUSE_BLUE_MS);
            if (!still(id)) return;

            // Phase 2: Transition blue → gold
            transitionSky(id,
                SKY_BLUE_TOP, SKY_BLUE_MID, SKY_BLUE_LOW,
                SKY_GOLD_TOP, SKY_GOLD_MID, SKY_GOLD_LOW,
                C_CLOUD_WHITE, C_CLOUD_GOLD,
                false);
            if (!still(id)) return;

            // Show narrative text
            showNarrativeText();

            // Phase 3: Hold gold
            Thread.sleep(PAUSE_GOLD_MS);
            if (!still(id)) return;

            // Phase 4: Transition gold → amber + sun shift
            transitionSky(id,
                SKY_GOLD_TOP, SKY_GOLD_MID, SKY_GOLD_LOW,
                SKY_AMBER_TOP, SKY_AMBER_MID, SKY_AMBER_LOW,
                C_CLOUD_GOLD, C_CLOUD_AMBER,
                true);
            if (!still(id)) return;

            // Phase 5: Hold amber final
            Thread.sleep(PAUSE_AMBER_MS);
            if (!still(id)) return;

            // Dim the narrative text slightly
            narrativeLabel.setColor(C_TEXT_DIM);

            // Phase 6: Final hold
            Thread.sleep(HOLD_FINAL_MS);
            if (!still(id)) return;

            // Phase 7: Fade to black
            for (int frame = 1; frame <= FADE_OUT_FRAMES && still(id); frame++) {
                double t = (double) frame / FADE_OUT_FRAMES;
                int alpha = (int) (t * 255);
                Color c = new Color(0, 0, 0, alpha);
                fadeOverlay.setFillColor(c);
                fadeOverlay.setColor(c);
                Thread.sleep(FADE_FRAME_MS);
            }
            if (!still(id)) return;

            // Brief hold on full black
            Thread.sleep(300);
            if (!still(id)) return;

            mainScreen.switchToScene2Screen();
        } catch (InterruptedException e) {
            // clean exit
        }
    }

    /**
     * Smoothly transitions sky bands, clouds, and optionally the sun
     * from one colour set to another over TRANSITION_FRAMES frames.
     */
    private void transitionSky(int id,
            Color fromTop, Color fromMid, Color fromLow,
            Color toTop, Color toMid, Color toLow,
            Color fromCloud, Color toCloud,
            boolean shiftSun) throws InterruptedException {

        for (int frame = 1; frame <= TRANSITION_FRAMES && still(id); frame++) {
            double t = (double) frame / TRANSITION_FRAMES;
            // Ease-in-out cubic
            double eased = t < 0.5
                ? 4 * t * t * t
                : 1 - Math.pow(-2 * t + 2, 3) / 2;

            Color curTop = lerpColor(fromTop, toTop, eased);
            Color curMid = lerpColor(fromMid, toMid, eased);
            Color curLow = lerpColor(fromLow, toLow, eased);
            setSkyColors(curTop, curMid, curLow);

            Color curCloud = lerpColor(fromCloud, toCloud, eased);
            setCloudColor(curCloud);

            if (shiftSun) {
                // Shift sun colour toward amber and move it lower
                Color sunCol = lerpColor(C_SUN_CORE, C_SUN_AMBER, eased);
                Color glowCol = lerpColor(C_SUN_GLOW, C_SUN_AMBER_GLOW, eased);
                sunCore.setFillColor(sunCol);
                sunCore.setColor(sunCol);
                sunGlow.setFillColor(glowCol);
                sunGlow.setColor(glowCol);

                // Move sun downward toward horizon
                double sunDrop = eased * (scaleY(80) - scaleY(0));
                double baseY = scaleY(65);
                double glowBaseY = scaleY(55);
                sunCore.setLocation(sunCore.getX(), baseY + sunDrop);
                sunGlow.setLocation(sunGlow.getX(), glowBaseY + sunDrop);
            }

            Thread.sleep(FRAME_MS);
        }
    }

    /**
     * Sets the sky band colours as a gradient from top to mid to low.
     */
    private void setSkyColors(Color top, Color mid, Color low) {
        int halfBands = SKY_BANDS / 2;
        for (int i = 0; i < SKY_BANDS; i++) {
            Color c;
            if (i < halfBands) {
                double t = (double) i / halfBands;
                c = lerpColor(top, mid, t);
            } else {
                double t = (double) (i - halfBands) / (SKY_BANDS - halfBands);
                c = lerpColor(mid, low, t);
            }
            skyBands.get(i).setFillColor(c);
            skyBands.get(i).setColor(c);
        }
    }

    private void setCloudColor(Color c) {
        for (GRect cloud : clouds) {
            cloud.setFillColor(c);
            cloud.setColor(c);
        }
    }

    private void setSunAmber() {
        sunCore.setFillColor(C_SUN_AMBER);
        sunCore.setColor(C_SUN_AMBER);
        sunGlow.setFillColor(C_SUN_AMBER_GLOW);
        sunGlow.setColor(C_SUN_AMBER_GLOW);
    }

    private void showNarrativeText() {
        narrativeLabel.setVisible(true);
    }

    // =========================================================
    // COLOUR UTILITY
    // =========================================================

    /**
     * Linearly interpolates between two colours.
     */
    private static Color lerpColor(Color a, Color b, double t) {
        int r = clamp((int) (a.getRed()   + (b.getRed()   - a.getRed())   * t));
        int g = clamp((int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t));
        int bl = clamp((int) (a.getBlue()  + (b.getBlue()  - a.getBlue())  * t));
        int al = clamp((int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
        return new Color(r, g, bl, al);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    // =========================================================
    // MOUSE EVENTS
    // =========================================================

    /**
     * Cinematic is non-skippable — auto-advances when complete.
     */
    @Override
    public void mouseClicked(MouseEvent e) {
        // Intentionally empty — cinematic auto-advances
    }
}
