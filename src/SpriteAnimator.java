import acm.graphics.GImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * SpriteAnimator.java
 *
 * Manages sprite animation for entities. Holds a set of frame lists keyed by
 * Direction, advancing the current frame based on a tick counter. When the
 * entity is moving, call update() each tick to advance the animation. When
 * stationary, the animator shows frame 0 (idle pose).
 *
 * Usage:
 *   SpriteAnimator anim = new SpriteAnimator(6); // 6 ticks per frame
 *   anim.addFrames(Direction.DOWN, downFrames);
 *   anim.addFrames(Direction.UP, upFrames);
 *   // each tick while moving:
 *   anim.update();
 *   GImage frame = anim.getCurrentFrame();
 *   // when stopped:
 *   anim.reset();
 *
 * If only one direction's frames are loaded, all directions use that set.
 * This supports simple entities that only have one walk cycle.
 *
 * Person 1 — Engine & Sequences (Task 28)
 */
public class SpriteAnimator {

    // ==========================================================
    // FIELDS
    // ==========================================================

    /** Frame lists keyed by direction. */
    private final Map<Direction, List<GImage>> framesByDirection;

    /** The direction currently being animated. */
    private Direction currentDirection;

    /** Index into the current direction's frame list. */
    private int currentFrame;

    /** Game ticks between frame advances. Lower = faster animation. */
    private final int ticksPerFrame;

    /** Tick counter — incremented each update(), resets on frame advance. */
    private int tickCount;

    /** Fallback single frame if no directional frames are loaded. */
    private GImage fallbackFrame;

    // ==========================================================
    // CONSTRUCTOR
    // ==========================================================

    /**
     * Creates a SpriteAnimator with the given animation speed.
     *
     * @param ticksPerFrame game ticks between frame advances (e.g. 6 = 10fps at 60fps game)
     */
    public SpriteAnimator(int ticksPerFrame) {
        this.ticksPerFrame = Math.max(1, ticksPerFrame);
        this.framesByDirection = new EnumMap<>(Direction.class);
        this.currentDirection = Direction.DOWN;
        this.currentFrame = 0;
        this.tickCount = 0;
    }

    // ==========================================================
    // FRAME MANAGEMENT
    // ==========================================================

    /**
     * Adds a set of animation frames for a direction.
     *
     * @param dir    the direction these frames represent
     * @param frames ordered list of GImage frames for the walk cycle
     */
    public void addFrames(Direction dir, List<GImage> frames) {
        if (dir != null && frames != null && !frames.isEmpty()) {
            framesByDirection.put(dir, new ArrayList<>(frames));
        }
    }

    /**
     * Sets a single fallback frame used when no directional frames are available.
     * @param frame the default GImage to display
     */
    public void setFallbackFrame(GImage frame) {
        this.fallbackFrame = frame;
    }

    /**
     * Removes all direction-based frame lists so {@link #getCurrentFrame()}
     * will return the fallback frame. Used when an entity needs to force-display
     * a single sprite (e.g. death animation) regardless of direction.
     */
    public void clearFrames() {
        framesByDirection.clear();
        currentFrame = 0;
        tickCount = 0;
    }

    /**
     * Sets the current animation direction. Call when the entity changes facing.
     * Resets the frame counter if the direction actually changed.
     *
     * @param dir the new direction to animate
     */
    public void setDirection(Direction dir) {
        if (dir != null && dir != currentDirection) {
            currentDirection = dir;
            currentFrame = 0;
            tickCount = 0;
        }
    }

    // ==========================================================
    // UPDATE — call each tick while the entity is moving
    // ==========================================================

    /**
     * Advances the animation by one tick.
     * Call this each game tick while the entity is moving.
     * Do NOT call while stationary — call reset() instead.
     */
    public void update() {
        tickCount++;
        if (tickCount >= ticksPerFrame) {
            List<GImage> frames = getActiveFrames();
            if (frames != null && !frames.isEmpty()) {
                currentFrame = (currentFrame + 1) % frames.size();
            }
            tickCount = 0;
        }
    }

    /**
     * Resets the animation to frame 0 (idle pose).
     * Call when the entity stops moving.
     */
    public void reset() {
        currentFrame = 0;
        tickCount = 0;
    }

    // ==========================================================
    // FRAME ACCESS
    // ==========================================================

    /**
     * Returns the current animation frame to draw.
     * Falls back to: current direction → any loaded direction → fallback frame.
     *
     * @return the GImage to render, or null if no frames are loaded
     */
    public GImage getCurrentFrame() {
        List<GImage> frames = getActiveFrames();
        if (frames != null && !frames.isEmpty()) {
            int idx = Math.min(currentFrame, frames.size() - 1);
            return frames.get(idx);
        }
        return fallbackFrame;
    }

    /**
     * Returns the frame list for the current direction.
     * If the current direction has no frames, returns the first loaded set.
     */
    private List<GImage> getActiveFrames() {
        List<GImage> frames = framesByDirection.get(currentDirection);
        if (frames != null) return frames;

        // Fallback: use any loaded direction
        for (List<GImage> f : framesByDirection.values()) {
            if (f != null && !f.isEmpty()) return f;
        }
        return null;
    }

    // ==========================================================
    // POSITION SYNC
    // ==========================================================

    /**
     * Sets the position of the current frame's GImage.
     * Call after move() to keep the sprite in sync with the entity.
     *
     * @param topLeftX sprite top-left X (entity center X - half sprite width)
     * @param topLeftY sprite top-left Y (entity center Y - half sprite height)
     */
    public void setPosition(double topLeftX, double topLeftY) {
        GImage frame = getCurrentFrame();
        if (frame != null) {
            frame.setLocation(topLeftX, topLeftY);
        }
    }

    // ==========================================================
    // GETTERS
    // ==========================================================

    /** @return current frame index */
    public int getCurrentFrameIndex() { return currentFrame; }

    /** @return current animation direction */
    public Direction getCurrentDirection() { return currentDirection; }

    /** @return true if any frames are loaded for any direction */
    public boolean hasFrames() {
        if (!framesByDirection.isEmpty()) return true;
        return fallbackFrame != null;
    }
}
