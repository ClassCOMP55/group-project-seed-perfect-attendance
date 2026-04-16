/**
 * Hurtbox.java
 *
 * An ellipse-shaped damage zone used for all damage detection in the game.
 * Separate from the rectangular Hitbox used for solid body / tile collision.
 *
 * Why an ellipse?
 *   Humanoid sprites are taller than they are wide, and their corners are
 *   empty pixels. A GOval-shaped zone means attacks that miss the visual
 *   body also miss the hurtbox — projectiles can graze corners of the tile
 *   square without registering a hit, which feels much fairer.
 *
 * This class is pure math — no ACM rendering. The "invisible GOval" is just
 * the conceptual shape; nothing is drawn to the canvas.
 *
 * Coordinate system: same as the rest of the game — x increases right,
 * y increases down. cx/cy is the CENTER of the ellipse (matches entity
 * x/y center convention).
 *
 * Person 3 — Combat & Enemies
 */
public class Hurtbox {

    // ==========================================================
    // FIELDS
    // ==========================================================

    /** Center of the ellipse in world-pixel coordinates. */
    private double cx, cy;

    /** Half-width (x-axis radius) and half-height (y-axis radius) of the ellipse, in pixels. */
    private final double rx, ry;

    // ==========================================================
    // CONSTRUCTOR
    // ==========================================================

    /**
     * Creates a new Hurtbox centered at (cx, cy) with the given radii.
     *
     * @param cx Center x position in world pixels
     * @param cy Center y position in world pixels
     * @param rx Half-width of the ellipse (x-axis radius), in pixels
     * @param ry Half-height of the ellipse (y-axis radius), in pixels
     */
    public Hurtbox(double cx, double cy, double rx, double ry) {
        this.cx = cx;
        this.cy = cy;
        this.rx = rx;
        this.ry = ry;
    }

    // ==========================================================
    // POSITION
    // ==========================================================

    /**
     * Moves the center of this hurtbox to (cx, cy).
     * Call this each tick after the owning entity moves, so the hurtbox
     * stays in sync with the entity's rendered position.
     *
     * @param cx New center x in world pixels
     * @param cy New center y in world pixels
     */
    public void updatePosition(double cx, double cy) {
        this.cx = cx;
        this.cy = cy;
    }

    // ==========================================================
    // OVERLAP TEST
    // ==========================================================

    /**
     * Returns true if this ellipse overlaps the given axis-aligned bounding rectangle.
     *
     * Algorithm: find the point on the rectangle closest to the ellipse center,
     * then test whether that point lies inside the ellipse using the standard
     * ellipse equation: (dx/rx)² + (dy/ry)² <= 1.
     *
     * This handles all cases cleanly:
     *   - rectangle corner inside ellipse
     *   - ellipse center inside rectangle
     *   - edge-to-edge grazing contact
     *
     * @param rect The axis-aligned hitbox to test against
     * @return true if the ellipse and rectangle overlap
     */
    public boolean overlapsHitbox(Hitbox rect) {
        // Closest point on the rectangle to the ellipse center (clamp to rect bounds)
        double closestX = Math.max(rect.x, Math.min(cx, rect.x + rect.width));
        double closestY = Math.max(rect.y, Math.min(cy, rect.y + rect.height));

        // Normalized distance from the closest point to the ellipse center
        double dx = closestX - cx;
        double dy = closestY - cy;
        return (dx / rx) * (dx / rx) + (dy / ry) * (dy / ry) <= 1.0;
    }

    // ==========================================================
    // GETTERS
    // ==========================================================

    /**
     * Returns true if this ellipse overlaps another ellipse (hurtbox).
     *
     * Uses the Minkowski-sum approximation: treat the two ellipses as a single
     * ellipse whose radii are the sum of each pair, then check if the distance
     * between centers falls inside that combined ellipse. This is fast, accurate
     * enough for game contact-damage checks, and exact when both ellipses are circles.
     *
     * @param other The other hurtbox to test against
     * @return true if the two ellipses overlap
     */
    public boolean overlapsHurtbox(Hurtbox other) {
        double[] otherCenter = other.getCenter();
        double sumRx = this.rx + other.getRx();
        double sumRy = this.ry + other.getRy();
        double dx = otherCenter[0] - this.cx;
        double dy = otherCenter[1] - this.cy;
        return (dx / sumRx) * (dx / sumRx) + (dy / sumRy) * (dy / sumRy) <= 1.0;
    }

    /**
     * Returns the center of this hurtbox as a two-element array.
     *
     * @return double[] { cx, cy }
     */
    public double[] getCenter() {
        return new double[]{ cx, cy };
    }

    /** @return x-axis radius of the ellipse */
    public double getRx() { return rx; }

    /** @return y-axis radius of the ellipse */
    public double getRy() { return ry; }
}
