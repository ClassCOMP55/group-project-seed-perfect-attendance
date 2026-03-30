/**
 * Direction.java
 *
 * Enum for the four cardinal directions. Used throughout the game for:
 * - Player and enemy movement
 * - Entity facing (for animation and melee arc placement)
 * - SwordSwing hitbox orientation
 * - Room transition sliding (WorldMap/RoomTransition)
 * - PushBlock direction
 *
 * Coordinate convention: y increases downward (standard Java 2D).
 * So UP means decreasing y, DOWN means increasing y.
 *
 * Person 3 — Combat & Enemies
 */
public enum Direction {

    UP, DOWN, LEFT, RIGHT;

    /**
     * Returns the opposite cardinal direction.
     *   UP ↔ DOWN,  LEFT ↔ RIGHT
     *
     * Used by ArmorEnemy (check if hit came from opposite of facing)
     * and Projectile reflect logic.
     *
     * @return the direction directly opposite to this one
     */
    public Direction opposite() {
        switch (this) {
            case UP:    return DOWN;
            case DOWN:  return UP;
            case LEFT:  return RIGHT;
            case RIGHT: return LEFT;
            default:    return this; // unreachable; satisfies compiler
        }
    }

    /**
     * Returns a unit delta vector for this direction.
     * Multiply by speed * dt in the caller to get a pixel displacement.
     *
     *   UP    → { 0, -1}
     *   DOWN  → { 0,  1}
     *   LEFT  → {-1,  0}
     *   RIGHT → { 1,  0}
     *
     * @return double[] { dx, dy }
     */
    public double[] toDelta() {
        switch (this) {
            case UP:    return new double[]{ 0, -1};
            case DOWN:  return new double[]{ 0,  1};
            case LEFT:  return new double[]{-1,  0};
            case RIGHT: return new double[]{ 1,  0};
            default:    return new double[]{ 0,  0}; // unreachable
        }
    }

    /**
     * Infers a Direction from a movement delta vector.
     * Picks the dominant axis (larger absolute value wins).
     * Tie-breaks to horizontal (LEFT/RIGHT).
     *
     * Returns DOWN if both dx and dy are zero — this is the Zelda-style
     * idle default (entity faces toward the camera when standing still).
     *
     * @param dx horizontal delta (positive = right)
     * @param dy vertical delta   (positive = down)
     * @return the closest cardinal Direction
     */
    public static Direction fromDelta(double dx, double dy) {
        if (dx == 0 && dy == 0) {
            return DOWN; // idle: face toward player/camera
        }
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx >= 0 ? RIGHT : LEFT;
        } else {
            return dy >= 0 ? DOWN : UP;
        }
    }
}
