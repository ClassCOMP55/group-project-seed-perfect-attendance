import acm.graphics.*;
import java.awt.Color;
import java.util.List;

/**
 * CutscenePlayer.java
 *
 * Plays a sequence of CutsceneFrames with fade-in / hold / fade-out transitions.
 * Sets GamePlayState to CUTSCENE during playback, restoring the previous state
 * when complete.
 *
 * Usage:
 *   CutscenePlayer cp = new CutscenePlayer(canvas);
 *   cp.play(frames, () -> { /* on complete  });
 *
 * Each tick call update() and draw(). The CutscenePlayer manages its own state.
 *
 * Used for:
 *   - Opening sequence monster-burst cutscene (Task 18)
 *   - Ending sequence after boss dies (Boss.onDeath)
 *
 * Person 1 — Engine & Sequences
**/
public class CutscenePlayer {

    // ==========================================================
    // PHASE ENUM
    // ==========================================================

    private enum Phase {
        IDLE,       // Not playing
        FADE_IN,    // Alpha increasing toward 1.0
        HOLD,       // Fully visible, waiting
        FADE_OUT    // Alpha decreasing toward 0.0
    }

    // ==========================================================
    // FIELDS
    // ==========================================================

    private final GCanvas canvas;

    private List<CutsceneFrame> frames;
    private int currentFrameIndex;
    private Phase phase;

    /** Current alpha for rendering (0.0 = invisible, 1.0 = fully visible). */
    private float alpha;

    /** Elapsed time in the current phase, in milliseconds. */
    private int phaseElapsedMs;

    /** Callback to run when the entire cutscene sequence finishes. */
    private Runnable onComplete;

    /** The GamePlayState that was active before the cutscene started. */
    private GamePlayState previousState;

    /** Text overlay label. */
    private GLabel textLabel;

    /** Semi-transparent black background for cinematic bars. */
    private GRect overlay;

    // ==========================================================
    // CONSTRUCTOR
    // ==========================================================

    /**
     * Creates a CutscenePlayer bound to the given canvas.
     *
     * @param canvas the GCanvas to render cutscene frames onto
     */
    public CutscenePlayer(GCanvas canvas) {
        this.canvas = canvas;
        this.phase = Phase.IDLE;
    }

    // ==========================================================
    // PLAYBACK CONTROL
    // ==========================================================

    /**
     * Starts playing the given sequence of cutscene frames.
     * Sets GamePlayState to CUTSCENE.
     *
     * @param frames     the frames to play in order
     * @param onComplete callback when the entire sequence finishes
     */
    public void play(List<CutsceneFrame> frames, Runnable onComplete) {
        if (frames == null || frames.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        this.frames = frames;
        this.onComplete = onComplete;
        this.currentFrameIndex = 0;
        this.previousState = GamePlayState.getCurrent();

        GamePlayState.setCurrent(GamePlayState.CUTSCENE);
        startPhase(Phase.FADE_IN);
    }

    /** @return true if a cutscene is currently playing */
    public boolean isPlaying() {
        return phase != Phase.IDLE;
    }

    // ==========================================================
    // UPDATE — call each tick
    // ==========================================================

    /**
     * Advances the cutscene by one tick (~16ms).
     * Call this every frame regardless of GamePlayState — the cutscene
     * manages its own state internally.
     */
    public void update() {
        if (phase == Phase.IDLE) return;

        int tickMs = 16; // ~60fps
        phaseElapsedMs += tickMs;

        CutsceneFrame frame = frames.get(currentFrameIndex);

        switch (phase) {
            case FADE_IN:
                if (frame.getFadeDurationMs() <= 0) {
                    alpha = 1.0f;
                    startPhase(Phase.HOLD);
                } else {
                    alpha = Math.min(1.0f, (float) phaseElapsedMs / frame.getFadeDurationMs());
                    if (alpha >= 1.0f) {
                        startPhase(Phase.HOLD);
                    }
                }
                break;

            case HOLD:
                if (phaseElapsedMs >= frame.getHoldDurationMs()) {
                    startPhase(Phase.FADE_OUT);
                }
                break;

            case FADE_OUT:
                if (frame.getFadeDurationMs() <= 0) {
                    alpha = 0.0f;
                    advanceFrame();
                } else {
                    alpha = Math.max(0.0f, 1.0f - (float) phaseElapsedMs / frame.getFadeDurationMs());
                    if (alpha <= 0.0f) {
                        advanceFrame();
                    }
                }
                break;

            default:
                break;
        }
    }

    // ==========================================================
    // DRAW — call each tick after update
    // ==========================================================

    /**
     * Renders the current cutscene frame onto the canvas.
     * Draws a black overlay behind the frame content for cinematic effect.
     */
    public void draw() {
        if (phase == Phase.IDLE) return;

        CutsceneFrame frame = frames.get(currentFrameIndex);

        // Black cinematic overlay
        if (overlay == null) {
            overlay = new GRect(0, 0, canvas.getWidth(), canvas.getHeight());
            overlay.setFilled(true);
        }
        overlay.setSize(canvas.getWidth(), canvas.getHeight());
        overlay.setFillColor(new Color(0, 0, 0, (int)(alpha * 200)));
        overlay.setColor(new Color(0, 0, 0, 0));
        canvas.add(overlay);
        overlay.sendToFront();

        // Frame image
        GImage img = frame.getImage();
        if (img != null) {
            // Center the image on the canvas
            double ix = (canvas.getWidth() - img.getWidth()) / 2.0;
            double iy = (canvas.getHeight() - img.getHeight()) / 2.0;
            img.setLocation(ix, iy);
            canvas.add(img);
            img.sendToFront();
        }

        // Text overlay
        String text = frame.getText();
        if (text != null && !text.isEmpty()) {
            if (textLabel == null) {
                textLabel = new GLabel("", 0, 0);
                textLabel.setFont("Courier New-BOLD-18");
                textLabel.setColor(Color.WHITE);
            }
            textLabel.setLabel(text);
            double tx = (canvas.getWidth() - textLabel.getWidth()) / 2.0;
            double ty = canvas.getHeight() * 0.82;
            textLabel.setLocation(tx, ty);
            canvas.add(textLabel);
            textLabel.sendToFront();
        }
    }

    /**
     * Removes all cutscene visuals from the canvas.
     * Call after the cutscene finishes or on screen transition.
     */
    public void cleanup() {
        if (overlay != null) {
            canvas.remove(overlay);
        }
        if (textLabel != null) {
            canvas.remove(textLabel);
        }
        if (frames != null) {
            for (CutsceneFrame f : frames) {
                GImage img = f.getImage();
                if (img != null) {
                    canvas.remove(img);
                }
            }
        }
    }

    // ==========================================================
    // INTERNAL
    // ==========================================================

    private void startPhase(Phase newPhase) {
        this.phase = newPhase;
        this.phaseElapsedMs = 0;
        if (newPhase == Phase.FADE_IN) {
            alpha = 0.0f;
        }
    }

    private void advanceFrame() {
        // Clean up current frame's image from canvas
        CutsceneFrame old = frames.get(currentFrameIndex);
        if (old.getImage() != null) {
            canvas.remove(old.getImage());
        }

        currentFrameIndex++;
        if (currentFrameIndex >= frames.size()) {
            // Cutscene complete
            finish();
        } else {
            startPhase(Phase.FADE_IN);
        }
    }

    private void finish() {
        phase = Phase.IDLE;
        cleanup();

        // Restore previous gameplay state
        if (previousState != null) {
            GamePlayState.setCurrent(previousState);
        } else {
            GamePlayState.setCurrent(GamePlayState.PLAYING);
        }

        if (onComplete != null) {
            onComplete.run();
        }
    }
}
