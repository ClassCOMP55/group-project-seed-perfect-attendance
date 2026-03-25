import java.awt.Color;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import acm.graphics.GLabel;
import acm.graphics.GObject;
import acm.graphics.GRect;

/**
 * SkyTransitionPane — cinematic camera-pan transition between
 * character creation and the first game scene (Market).
 *
 * The scene is built as a tall vertical strip (1000 logical units high).
 * The bottom half (Y 0–500) is a ground-level scene with a path,
 * torches, and atmosphere. The top half (Y -500 to 0) is a night sky
 * filled with twinkling stars and narrative text.
 *
 * Visual sequence:
 *   1. Starts at ground level (player sees path + torches)
 *   2. Camera pans UP — ground scrolls away, sky scrolls into view
 *   3. Stars twinkle, "Your adventure begins..." appears
 *   4. Camera pans back DOWN — ground returns
 *   5. Auto-advances to Scene 1 (Market)
 *
 * The pan is achieved by calling {@code GObject.move()} on every
 * object in the contents list, shifting them vertically each frame
 * with ease-in-out cubic interpolation for a smooth feel.
 *
 * All coordinates use the logical 700 x 500 design space,
 * scaled via scaleX / scaleY for any window size.
 */
public class SkyTransitionPane extends GraphicsPane {

    // =========================================================
    // COLOUR PALETTE
    // =========================================================

    // -- Sky --
    /** Deep night sky base colour (top of sky). */
    private static final Color C_SKY_DEEP   = new Color(6, 8, 22);
    /** Mid sky colour. */
    private static final Color C_SKY_MID    = new Color(12, 16, 38);
    /** Lower sky — faint horizon glow. */
    private static final Color C_SKY_LOW    = new Color(22, 20, 48);

    // -- Ground --
    /** Dark earth fill. */
    private static final Color C_GROUND     = new Color(18, 14, 12);
    /** Dirt path colour. */
    private static final Color C_PATH       = new Color(42, 32, 24);
    /** Path edge / border. */
    private static final Color C_PATH_EDGE  = new Color(30, 22, 16);
    /** Grass tufts. */
    private static final Color C_GRASS      = new Color(28, 48, 22);
    /** Dark grass variation. */
    private static final Color C_GRASS_DK   = new Color(20, 36, 16);

    // -- Torch --
    private static final Color C_TORCH_WOOD = new Color(90, 60, 35);
    private static final Color C_FLAME_BASE = new Color(200, 75, 20);
    private static final Color C_FLAME_MID  = new Color(240, 140, 30);
    private static final Color C_FLAME_TIP  = new Color(255, 220, 60);
    private static final Color C_FLAME_GLOW = new Color(255, 200, 80, 40);

    // -- Stars --
    /** Bright star colour. */
    private static final Color C_STAR_BRIGHT = new Color(255, 250, 230);
    /** Medium star colour. */
    private static final Color C_STAR_MED    = new Color(200, 195, 180);
    /** Dim star colour. */
    private static final Color C_STAR_DIM    = new Color(100, 95, 85);
    /** Blue-tinted star. */
    private static final Color C_STAR_BLUE   = new Color(160, 180, 220);

    // -- Text --
    /** Narrative text colour. */
    private static final Color C_TEXT        = new Color(220, 215, 240);
    /** Dim secondary text. */
    private static final Color C_DIM         = new Color(110, 105, 130);

    // =========================================================
    // LAYOUT & TIMING CONSTANTS
    // =========================================================

    /**
     * Logical vertical distance the camera pans.
     * The sky section lives at Y offsets -500 to 0 (above the viewport).
     * Shifting everything down by PAN_DISTANCE brings the sky into view.
     */
    private static final double PAN_DISTANCE = 500;

    /** Number of stars in the sky section. */
    private static final int STAR_COUNT = 90;

    /** Frames for the pan-up animation. */
    private static final int PAN_UP_FRAMES   = 80;
    /** Milliseconds per frame during pan. */
    private static final int PAN_FRAME_MS    = 25;

    /** Frames for the pan-down animation. */
    private static final int PAN_DOWN_FRAMES = 70;

    /** Twinkle update interval (ms). */
    private static final int TWINKLE_MS = 180;
    /** Number of twinkle cycles while at the sky. */
    private static final int TWINKLE_CYCLES = 20;

    // =========================================================
    // STATE
    // =========================================================

    /** All star GRects (for twinkling). */
    private final List<GRect> stars = new ArrayList<>();

    /** Brightness per star: 0 = dim, 1 = med, 2 = bright. */
    private final List<Integer> starBrightness = new ArrayList<>();

    /** Accumulated pixel shift applied to all objects (for tracking). */
    private double currentShiftPx = 0;

    /** True while the animation thread should keep running. */
    private volatile boolean animating = false;

    /** Bumped to invalidate the cinematic thread (resize / hide). */
    private volatile int cinematicRunId = 0;

    /** Rough phase for resuming after a resize (see constants below). */
    private volatile int cinematicPhase = 0;

    private static final int PHASE_START = 0;
    private static final int PHASE_PAN_UP = 1;
    private static final int PHASE_TWINKLE = 2;
    private static final int PHASE_PAUSE_SKY = 3;
    private static final int PHASE_PAN_DOWN = 4;
    private static final int PHASE_END = 5;

    /**
     * Full pan distance in pixels at the scale used when the scene was built
     * ({@code scaleY(PAN_DISTANCE) - scaleY(0)}).
     */
    private double builtPanTargetPx;

    /** RNG for twinkling only — must not affect star positions on rebuild. */
    private final Random rng = new Random();

    /**
     * Fixed seed so every {@link #generateStars()} produces the same logical layout;
     * resize rebuilds then only rescale pixels and stars no longer jump.
     */
    private static final long STAR_LAYOUT_SEED = 0x535459534C41594FL;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    /**
     * Creates a SkyTransitionPane.
     * @param mainScreen the main application reference
     */
    public SkyTransitionPane(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
    }

    // =========================================================
    // LIFECYCLE
    // =========================================================

    @Override
    public void showContent() {
        animating = true;
        currentShiftPx = 0;
        cinematicPhase = PHASE_START;
        stars.clear();
        starBrightness.clear();

        builtPanTargetPx = scaleY(PAN_DISTANCE) - scaleY(0);

        buildGroundSection();   // visible at start (logical Y 0–500)
        buildSkySection();      // above viewport (logical Y -500 to 0)

        cinematicRunId++;
        final int runId = cinematicRunId;
        new Thread(() -> resumeCinematic(runId, PHASE_START)).start();
    }

    @Override
    public void hideContent() {
        cinematicRunId++;
        animating = false;
        for (GObject item : contents) {
            mainScreen.remove(item);
        }
        contents.clear();
        stars.clear();
        starBrightness.clear();
    }

    /**
     * Rebuilds ground + sky at the new scale, rescales the current camera shift, and resumes the cinematic.
     */
    @Override
    public void refreshLayout() {
        int phaseSnap = cinematicPhase;
        cinematicRunId++;
        animating = false;

        double savedShift = currentShiftPx;
        double oldTarget = builtPanTargetPx;
        if (oldTarget < 1e-3) {
            oldTarget = Math.max(1e-3, scaleY(PAN_DISTANCE) - scaleY(0));
        }

        for (GObject item : contents) {
            mainScreen.remove(item);
        }
        contents.clear();
        stars.clear();
        starBrightness.clear();

        builtPanTargetPx = scaleY(PAN_DISTANCE) - scaleY(0);
        double ratio = oldTarget > 1e-6 ? (builtPanTargetPx / oldTarget) : 1.0;
        double newShift = savedShift * ratio;
        currentShiftPx = 0;

        buildGroundSection();
        buildSkySection();
        for (GObject obj : contents) {
            obj.move(0, newShift);
        }
        currentShiftPx = newShift;

        cinematicRunId++;
        final int runId = cinematicRunId;
        animating = true;
        new Thread(() -> resumeCinematic(runId, phaseSnap)).start();
    }

    // =========================================================
    // GROUND SECTION (logical Y 0 – 500, visible at start)
    // =========================================================

    /**
     * Draws the ground-level scene the player sees first:
     * dark earth, a dirt path, torches on each side, grass tufts,
     * and a hint label.
     */
    private void buildGroundSection() {
        // --- Full dark ground fill ---
        place(srect(0, 0, 700, 500, C_GROUND, C_GROUND));

        // --- Dirt path (centre of screen, running vertically) ---
        place(srect(270, 0, 160, 500, C_PATH, C_PATH));
        // Path edges (slightly darker border strips)
        place(srect(266, 0, 4, 500, C_PATH_EDGE, C_PATH_EDGE));
        place(srect(430, 0, 4, 500, C_PATH_EDGE, C_PATH_EDGE));

        // --- Scattered grass tufts ---
        drawGrassTufts();

        // --- Left torch ---
        drawGroundTorch(220, 180);
        // --- Right torch ---
        drawGroundTorch(462, 180);

        // --- Atmospheric rocks ---
        Color rock = new Color(35, 30, 28);
        place(srect(80,  340, 20, 12, rock, rock));
        place(srect(560, 280, 16, 10, rock, rock));
        place(srect(140, 420, 14, 8,  rock, rock));

        // --- Hint text ---
        GLabel hint = pixelLabel("...", 14, C_DIM);
        hint.setLocation(centeredX(hint), scaleY(400));
        place(hint);
    }

    /** Draws small grass tuft rectangles scattered on both sides of the path. */
    private void drawGrassTufts() {
        // Left side tufts
        placeTuft(60,  150); placeTuft(120, 220); placeTuft(40,  310);
        placeTuft(160, 360); placeTuft(90,  440); placeTuft(200, 120);

        // Right side tufts
        placeTuft(480, 140); placeTuft(540, 250); placeTuft(600, 190);
        placeTuft(500, 380); placeTuft(620, 420); placeTuft(460, 300);
    }

    /**
     * Draws a single grass tuft (two small overlapping rects).
     * @param lx logical X
     * @param ly logical Y
     */
    private void placeTuft(double lx, double ly) {
        Color c = (rng.nextBoolean()) ? C_GRASS : C_GRASS_DK;
        place(srect(lx, ly, 10, 4, c, c));
        place(srect(lx + 3, ly - 3, 4, 6, c, c));
    }

    /**
     * Draws a standing torch at ground level.
     * @param lx logical X of torch centre
     * @param ly logical Y of torch top (flame base)
     */
    private void drawGroundTorch(double lx, double ly) {
        // Glow aura
        place(srect(lx - 14, ly - 22, 30, 30, C_FLAME_GLOW, C_FLAME_GLOW));

        // Flame layers
        place(srect(lx - 4, ly - 6,  10, 8, C_FLAME_BASE, C_FLAME_BASE));
        place(srect(lx - 3, ly - 13, 8,  8, C_FLAME_MID,  C_FLAME_MID));
        place(srect(lx - 2, ly - 20, 6,  8, C_FLAME_TIP,  C_FLAME_TIP));

        // Wooden post
        place(srect(lx - 2, ly, 6, 60, C_TORCH_WOOD, C_TORCH_WOOD));

        // Base stone
        place(srect(lx - 6, ly + 58, 14, 6, new Color(50, 45, 40), new Color(50, 45, 40)));
    }

    // =========================================================
    // SKY SECTION (logical Y -500 to 0, above viewport)
    // =========================================================

    /**
     * Draws the night sky section above the viewport.
     * Objects are placed at negative logical Y values so they
     * start off-screen and scroll into view when the camera pans up.
     */
    private void buildSkySection() {
        // --- Sky gradient layers (top to bottom, all at negative Y) ---
        place(srect(0, -500, 700, 200, C_SKY_DEEP, C_SKY_DEEP));
        place(srect(0, -300, 700, 150, C_SKY_MID,  C_SKY_MID));
        place(srect(0, -150, 700, 150, C_SKY_LOW,  C_SKY_LOW));

        // --- Town silhouette on the sky horizon (Y ~ -30 to 0) ---
        Color sil = new Color(10, 8, 18);
        place(srect(100, -35, 18, 35, sil, sil));
        place(srect(120, -25, 14, 25, sil, sil));
        place(srect(136, -20, 24, 20, sil, sil));

        place(srect(290, -32, 10, 32, sil, sil));   // tower
        place(srect(302, -22, 22, 22, sil, sil));
        place(srect(326, -28, 16, 28, sil, sil));

        place(srect(490, -26, 24, 26, sil, sil));
        place(srect(516, -20, 14, 20, sil, sil));
        place(srect(532, -34, 8,  34, sil, sil));   // spire

        // --- Crescent moon ---
        Color moon = new Color(240, 235, 200);
        place(srect(560, -420, 18, 18, moon, moon));
        // "Bite" out of the moon to make a crescent (dark circle over it)
        place(srect(566, -422, 16, 16, C_SKY_DEEP, C_SKY_DEEP));

        // --- Generate stars (not added to canvas yet — revealed during animation) ---
        generateStars();

        // --- Narrative text (placed in sky section, will be visible after pan) ---
        GLabel narrative = pixelLabel("Your adventure begins...", 16, C_TEXT);
        // Centred horizontally, at logical Y = -260 (middle of sky)
        narrative.setLocation(centeredX(narrative), scaleY(-260));
        place(narrative);
    }

    /**
     * Pre-generates star GRects at random positions within the sky section
     * (logical Y -480 to -40). Stars are added to the canvas immediately
     * but start off-screen; they become visible when the camera pans up.
     */
    private void generateStars() {
        Random layoutRng = new Random(STAR_LAYOUT_SEED);
        for (int i = 0; i < STAR_COUNT; i++) {
            double lx = layoutRng.nextDouble() * 680 + 10;
            double ly = -480 + layoutRng.nextDouble() * 440;  // Y -480 to -40

            // Size: mostly 2x2, some 3x3, rare 4x4
            int roll = layoutRng.nextInt(10);
            double size = (roll < 6) ? 2 : (roll < 9) ? 3 : 4;

            int brightness = layoutRng.nextInt(3);
            starBrightness.add(brightness);

            Color col = starColorForLayout(brightness, layoutRng);
            GRect star = srect(lx, ly, size, size, col, col);
            stars.add(star);
            place(star);   // on canvas but off-screen above
        }
    }

    /** Initial star colour from layout RNG (stable across resize rebuilds). */
    private Color starColorForLayout(int level, Random layoutRng) {
        switch (level) {
            case 0:  return C_STAR_DIM;
            case 1:  return C_STAR_MED;
            default: return (layoutRng.nextInt(4) == 0) ? C_STAR_BLUE : C_STAR_BRIGHT;
        }
    }

    /**
     * Returns a colour for the given star brightness level.
     * @param level 0 = dim, 1 = medium, 2 = bright
     * @return the star Color
     */
    private Color starColor(int level) {
        switch (level) {
            case 0:  return C_STAR_DIM;
            case 1:  return C_STAR_MED;
            default: return (rng.nextInt(4) == 0) ? C_STAR_BLUE : C_STAR_BRIGHT;
        }
    }

    // =========================================================
    // CINEMATIC ANIMATION
    // =========================================================

    private boolean still(int runId) {
        return animating && runId == cinematicRunId;
    }

    /**
     * Runs or resumes the cinematic from {@code resumePhase} (0 = full run from the start wait).
     */
    private void resumeCinematic(int runId, int resumePhase) {
        try {
            double tgt = builtPanTargetPx;
            if (tgt < 1e-3) {
                tgt = scaleY(PAN_DISTANCE) - scaleY(0);
                builtPanTargetPx = tgt;
            }

            if (resumePhase <= PHASE_START) {
                cinematicPhase = PHASE_START;
                Thread.sleep(1200);
                if (!still(runId)) {
                    return;
                }
            }

            if (resumePhase <= PHASE_PAN_UP) {
                cinematicPhase = PHASE_PAN_UP;
                if (currentShiftPx < tgt - 0.5) {
                    int frames = Math.max(6, (int) Math.round(PAN_UP_FRAMES * (tgt - currentShiftPx) / tgt));
                    panSmoothToward(runId, tgt, frames);
                }
                if (!still(runId)) {
                    return;
                }
            }

            if (resumePhase <= PHASE_TWINKLE) {
                cinematicPhase = PHASE_TWINKLE;
                for (int c = 0; c < TWINKLE_CYCLES && still(runId); c++) {
                    twinkleStars();
                    Thread.sleep(TWINKLE_MS);
                }
                if (!still(runId)) {
                    return;
                }
            }

            if (resumePhase <= PHASE_PAUSE_SKY) {
                cinematicPhase = PHASE_PAUSE_SKY;
                Thread.sleep(1000);
                if (!still(runId)) {
                    return;
                }
            }

            if (resumePhase <= PHASE_PAN_DOWN) {
                cinematicPhase = PHASE_PAN_DOWN;
                if (currentShiftPx > 0.5) {
                    int frames = Math.max(6, (int) Math.round(PAN_DOWN_FRAMES * (currentShiftPx / tgt)));
                    panSmoothToward(runId, 0, frames);
                }
                if (!still(runId)) {
                    return;
                }
            }

            if (resumePhase <= PHASE_END) {
                cinematicPhase = PHASE_END;
                Thread.sleep(600);
                if (!still(runId)) {
                    return;
                }
            }

            if (still(runId)) {
                mainScreen.switchToScene1Screen();
            }
        } catch (InterruptedException e) {
            // clean exit
        }
    }

    /**
     * Ease-in-out move from {@link #currentShiftPx} toward {@code endShift} over {@code frames} steps.
     */
    private void panSmoothToward(int runId, double endShift, int frames) throws InterruptedException {
        double startShift = currentShiftPx;
        double totalPx = endShift - startShift;
        if (Math.abs(totalPx) < 0.25) {
            return;
        }
        double movedAlong = 0;
        for (int i = 1; i <= frames && still(runId); i++) {
            double t = (double) i / frames;
            double eased = t < 0.5
                ? 4 * t * t * t
                : 1 - Math.pow(-2 * t + 2, 3) / 2;
            double targetPos = startShift + totalPx * eased;
            double delta = targetPos - startShift - movedAlong;
            movedAlong = targetPos - startShift;
            for (GObject obj : contents) {
                obj.move(0, delta);
            }
            currentShiftPx += delta;
            Thread.sleep(PAN_FRAME_MS);
        }
    }

    /**
     * Randomly changes brightness of ~25% of stars each cycle
     * to create a gentle twinkling effect.
     */
    private void twinkleStars() {
        for (int i = 0; i < stars.size(); i++) {
            if (rng.nextInt(4) == 0) {
                int b = rng.nextInt(3);
                starBrightness.set(i, b);
                Color col = starColor(b);
                stars.get(i).setFillColor(col);
                stars.get(i).setColor(col);
            }
        }
    }

    // =========================================================
    // MOUSE EVENTS
    // =========================================================

    /**
     * Clicking during the cinematic does nothing — the sequence
     * auto-advances to Scene 1 when complete.
     * (Prevents accidental skipping of the transition.)
     */
    @Override
    public void mouseClicked(MouseEvent e) {
        // Intentionally empty — cinematic auto-advances
    }

    // =========================================================
    // HELPERS
    // =========================================================

    /**
     * Adds a GObject to both the contents list and the canvas.
     * @param obj the object to add
     */
    private void place(GObject obj) {
        contents.add(obj);
        mainScreen.add(obj);
    }

    /**
     * Creates a GLabel in the pixel (Monospaced-BOLD) font.
     * @param text  label string
     * @param size  logical font size (auto-scaled)
     * @param color text colour
     * @return configured GLabel at (0, 0)
     */
    private GLabel pixelLabel(String text, int size, Color color) {
        GLabel lbl = new GLabel(text, 0, 0);
        lbl.setFont("Monospaced-BOLD-" + scaleFontSize(size));
        lbl.setColor(color);
        return lbl;
    }

    /**
     * Creates a filled GRect at raw pixel coordinates.
     * @param x      pixel X
     * @param y      pixel Y
     * @param w      pixel width
     * @param h      pixel height
     * @param fill   fill colour
     * @param border border colour
     * @return configured GRect
     */
    private GRect rect(double x, double y, double w, double h, Color fill, Color border) {
        GRect r = new GRect(x, y, w, h);
        r.setFilled(true);
        r.setFillColor(fill);
        r.setColor(border);
        return r;
    }

    /**
     * Creates a filled GRect using logical (700 x 500) coordinates,
     * automatically scaled to screen pixels.
     * @param lx     logical X
     * @param ly     logical Y
     * @param lw     logical width
     * @param lh     logical height
     * @param fill   fill colour
     * @param border border colour
     * @return configured GRect
     */
    private GRect srect(double lx, double ly, double lw, double lh, Color fill, Color border) {
        return rect(
            scaleX(lx),
            scaleY(ly),
            scaleX(lx + lw) - scaleX(lx),
            scaleY(ly + lh) - scaleY(ly),
            fill,
            border
        );
    }
}
