import javax.swing.Timer;
import acm.graphics.GCanvas;

/**
 * GameLoop.java
 *
 * Drives the game at ~60fps using a javax.swing.Timer. Each tick:
 *   1. Calls the registered Updatable with delta-time.
 *   2. Calls repaint() on the canvas so the frame renders.
 *
 * The loop always fires the Updatable callback regardless of GamePlayState.
 * The Updatable (typically a Room or Scene pane) is responsible for checking
 * GamePlayState and deciding what to update — this allows cutscenes,
 * dialogue, and transitions to advance their own logic during non-PLAYING states.
 *
 * Person 1 — Engine & Sequences
 */
public class GameLoop {

    /** Callback interface for the game's per-tick update logic. */
    public interface Updatable {
        /**
         * Called once per tick regardless of GamePlayState.
         * Implementors should check GamePlayState to decide what to update.
         * @param dt delta-time in seconds (e.g. 0.016 for ~60fps)
         */
        void update(double dt);
    }

    /** Default tick interval in milliseconds (~60fps). */
    private static final int DEFAULT_TICK_MS = 16;

    /** The Swing timer that fires each tick. */
    private final Timer timer;

    /** Current tick interval in milliseconds. */
    private int tickRateMs;

    /** The canvas to repaint each frame. */
    private final GCanvas canvas;

    /** The update callback — set by the active room/scene. */
    private Updatable updatable;

    /**
     * Creates a GameLoop attached to the given canvas.
     * The loop starts stopped — call start() to begin.
     *
     * @param canvas the GCanvas to repaint each frame
     */
    public GameLoop(GCanvas canvas) {
        this.canvas = canvas;
        this.tickRateMs = DEFAULT_TICK_MS;

        this.timer = new Timer(tickRateMs, e -> tick());
        this.timer.setRepeats(true);
    }

    /**
     * Main tick method — called by the timer every tickRateMs.
     * Always calls the updatable so it can handle state-specific logic
     * (cutscenes, dialogue, transitions) internally. The updatable is
     * responsible for checking GamePlayState and acting accordingly.
     * Always repaints so transitions, cutscenes, and dialogue can animate.
     */
    private void tick() {
        double dt = tickRateMs / 1000.0;

        if (updatable != null) {
            updatable.update(dt);
        }

        if (canvas != null) {
            canvas.repaint();
        }
    }

    /** Starts the game loop. */
    public void start() {
        timer.start();
    }

    /** Stops the game loop. */
    public void stop() {
        timer.stop();
    }

    /** Returns true if the loop is currently running. */
    public boolean isRunning() {
        return timer.isRunning();
    }

    /**
     * Sets the update callback. Call this when switching rooms/scenes.
     * @param updatable the new update target (null to clear)
     */
    public void setUpdatable(Updatable updatable) {
        this.updatable = updatable;
    }

    /**
     * Changes the tick rate. Useful for slow-motion effects or debug.
     * @param ms tick interval in milliseconds (minimum 1)
     */
    public void setTickRate(int ms) {
        this.tickRateMs = Math.max(1, ms);
        timer.setDelay(this.tickRateMs);
    }

    /** @return current tick interval in milliseconds */
    public int getTickRate() {
        return tickRateMs;
    }
}
