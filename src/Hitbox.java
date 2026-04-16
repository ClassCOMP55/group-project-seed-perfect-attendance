/**
 * Hitbox.java
 *
 * Axis-aligned bounding rectangle (AABB). This is the core collision primitive
 * used by every entity, world object, and combat effect in the game.
 *
 * Every moving thing (Player, Enemy, Projectile) and every interactable object
 * (Chest, Grass, SwordSwing, etc.) holds a Hitbox. Collision checks are done
 * by calling overlaps() between two Hitbox instances each game tick.
 *
 * Coordinate system: x increases right, y increases down (standard Java 2D).
 * The hitbox describes the top-left corner (x, y) plus width and height.
 *
 * Person 3 — Combat & Enemies
 */
public class Hitbox {

    // Top-left corner of the bounding box, in pixels.
    public double x, y;

    // Dimensions of the bounding box, in pixels.
    public double width, height;

    /**
     * Creates a new Hitbox with the given position and size.
     *
     * @param x      Top-left x position (pixels)
     * @param y      Top-left y position (pixels)
     * @param width  Width of the box (pixels)
     * @param height Height of the box (pixels)
     */
    public Hitbox(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Returns true if this hitbox overlaps with another hitbox.
     *
     * Uses strict less-than-or-equal so boxes that merely share an edge
     * (touching but not penetrating) return false. This is the correct
     * behavior for tile collision and melee hitboxes.
     *
     * @param other The other hitbox to test against
     * @return true if the two boxes overlap, false if they are separate or only touching
     */
    public boolean overlaps(Hitbox other) {
        return !(other.x + other.width  <= this.x
              || this.x  + this.width   <= other.x
              || other.y + other.height <= this.y
              || this.y  + this.height  <= other.y);
    }

    /**
     * Returns true if the given point falls inside this hitbox.
     * Points on the edge of the box are considered inside.
     *
     * @param px X coordinate of the point (pixels)
     * @param py Y coordinate of the point (pixels)
     * @return true if (px, py) is within the bounds of this hitbox
     */
    public boolean contains(double px, double py) {
        return px >= x && px <= x + width
            && py >= y && py <= y + height;
    }

    /**
     * Updates the top-left position of this hitbox.
     * Called each tick by the owning entity after it moves, so the hitbox
     * stays in sync with the entity's rendered position.
     *
     * @param x New top-left x position (pixels)
     * @param y New top-left y position (pixels)
     */
    public void updatePosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Returns the center point of this hitbox as a two-element array.
     * Useful for distance checks, aggro range detection, and projectile targeting.
     *
     * @return double[] { centerX, centerY }
     */
    public double[] getCenter() {
        return new double[]{ x + width / 2.0, y + height / 2.0 };
    }

    /**
     * Returns true if this hitbox (rectangle) overlaps the given hurtbox (ellipse).
     * Delegates to Hurtbox.overlapsHitbox() so the ellipse math stays in one place.
     *
     * @param h The hurtbox to test against
     * @return true if they overlap
     */
    public boolean overlapsHurtbox(Hurtbox h) {
        return h.overlapsHitbox(this);
    }
}
