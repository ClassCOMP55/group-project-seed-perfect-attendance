/**
 * SwordSwing.java
 *
 * A short-lived combat hitbox and visual created in front of the player on attack.
 * Lives for LIFETIME = 10 ticks (~167ms at 60fps).
 *
 * Each tick, update() checks overlap against all active enemies (dealing 1 damage,
 * once per swing instance) and stubs projectile reflection for when Projectile.java
 * is written.
 *
 * Not an Entity subclass — SwordSwing has no health, movement, or TileMap dependency.
 * It is a pure combat effect owned and managed by the Player.
 *
 * Coordinate convention: same as Entity — x,y = CENTER of the player.
 * The swing hitbox is placed flush against the player's hitbox edge in the facing direction.
 *
 * Hitbox placement (swing box is 48×48, player hitbox is 48×48 centered on playerX,playerY):
 *   RIGHT → top-left: (playerX + 24,  playerY - 24)  — right of player
 *   LEFT  → top-left: (playerX - 72,  playerY - 24)  — left of player
 *   DOWN  → top-left: (playerX - 24,  playerY + 24)  — below player
 *   UP    → top-left: (playerX - 24,  playerY - 72)  — above player
 *
 * Person 3 — Combat & Enemies
 */
import acm.graphics.*;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class SwordSwing {

    // ==========================================================
    // CONSTANTS
    // ==========================================================

    /**
     * Lifetime in game ticks. At ~60fps, 10 ticks ≈ 167ms.
     * Short enough to require attack timing, long enough to reliably register hits.
     */
    private static final int LIFETIME = 10;

    /** Size of the swing hitbox in pixels. Matches entity sprite/hitbox size. */
    private static final int SWING_SIZE = 48;

    /**
     * Semi-transparent gold placeholder color for the swing visual.
     * TODO [GRAPHICS]: replace with a sprite sheet frame or swing arc animation.
     */
    private static final Color SWING_COLOR = new Color(255, 215, 0, 140);

    // ==========================================================
    // FIELDS
    // ==========================================================

    /** Axis-aligned bounding box for this swing — placed in front of the player. */
    private final Hitbox hitbox;

    /** Direction the player was facing when the swing was triggered. */
    private final Direction facing;

    /** Current age in ticks. Incremented each update(). Swing expires when >= LIFETIME. */
    private int currentAge;

    /**
     * Placeholder visual drawn on the canvas each frame.
     * TODO [GRAPHICS]: replace with animated sprite frame or arc visual.
     */
    private final GRect visual;

    /**
     * Tracks enemies already damaged this swing to prevent multi-hit within one swing instance.
     * Bounded by room enemy count (~5-10), so List.contains() is fine here.
     */
    private final List<Enemy> alreadyHit;

    // ==========================================================
    // CONSTRUCTOR
    // ==========================================================

    /**
     * Creates a SwordSwing in front of the player based on current facing direction.
     *
     * The swing hitbox is placed flush against the outer edge of the player's own
     * hitbox (which is 48×48 centered on playerX, playerY):
     *   RIGHT → (playerX + 24, playerY - 24)   LEFT → (playerX - 72, playerY - 24)
     *   DOWN  → (playerX - 24, playerY + 24)   UP   → (playerX - 24, playerY - 72)
     *
     * @param playerX  Player center X in world pixels
     * @param playerY  Player center Y in world pixels
     * @param facing   Direction the player is facing when attacking
     */
    public SwordSwing(double playerX, double playerY, Direction facing) {
        this.facing     = facing;
        this.currentAge = 0;
        this.alreadyHit = new ArrayList<>();

        // Compute hitbox top-left corner based on facing
        double hx, hy;
        switch (facing) {
            case RIGHT: hx = playerX + 24;  hy = playerY - 24;  break;
            case LEFT:  hx = playerX - 72;  hy = playerY - 24;  break;
            case DOWN:  hx = playerX - 24;  hy = playerY + 24;  break;
            case UP:    hx = playerX - 24;  hy = playerY - 72;  break;
            default:    hx = playerX + 24;  hy = playerY - 24;  break; // fallback to RIGHT
        }

        this.hitbox = new Hitbox(hx, hy, SWING_SIZE, SWING_SIZE);

        // Visual placeholder: a semi-transparent gold rectangle at the hitbox position
        // TODO [GRAPHICS]: replace with a sprite/animation at this position and size
        this.visual = new GRect(hx, hy, SWING_SIZE, SWING_SIZE);
        this.visual.setFilled(true);
        this.visual.setFillColor(SWING_COLOR);
        this.visual.setColor(SWING_COLOR);
    }

    // ==========================================================
    // UPDATE
    // ==========================================================

    /**
     * Advances the swing by one tick and performs hit detection.
     *
     * Each alive enemy whose hitbox overlaps this swing takes 1 damage.
     * Each enemy is only damaged once per swing instance (tracked via alreadyHit).
     *
     * Projectile reflection is stubbed with a TODO comment — parameter is List<Object>
     * because Projectile.java does not exist yet. When it does:
     *   1. Change List<Object> to List<Projectile>
     *   2. Uncomment the reflection loop inside
     *
     * @param enemies      All active enemies in the current room
     * @param projectiles  Active projectiles stub — replace with List<Projectile> when ready
     * @param canReflect   True if the player has the Reflect relic equipped
     */
    public void update(List<Enemy> enemies, List<Projectile> projectiles, boolean canReflect) {
        currentAge++;

        // Hit detection: damage each enemy in range, but only once per swing
        if (enemies != null) {
            for (Enemy e : enemies) {
                if (e.isAlive()
                        && hitbox.overlaps(e.getHitbox())
                        && !alreadyHit.contains(e)) {
                    e.takeDamage(1, this.facing);
                    alreadyHit.add(e);
                }
            }
        }

        // Projectile reflection: if the player has the Reflect relic,
        // reverse any projectile whose hitbox overlaps this swing.
        if (canReflect && projectiles != null) {
            for (Projectile p : projectiles) {
                if (p.isAlive() && hitbox.overlaps(p.getHitbox())) {
                    p.reflect();
                }
            }
        }
    }

    // ==========================================================
    // LIFETIME
    // ==========================================================

    /**
     * Returns true once this swing has lived for LIFETIME ticks.
     * The owner (Player) should stop calling update() and draw(),
     * then call removeFrom() and discard the instance.
     *
     * @return true if currentAge >= LIFETIME
     */
    public boolean isExpired() {
        return currentAge >= LIFETIME;
    }

    // ==========================================================
    // DRAW / REMOVE
    // ==========================================================

    /**
     * Draws the swing visual onto the canvas.
     * Currently renders a semi-transparent gold rectangle as a placeholder.
     *
     * TODO [GRAPHICS]: replace with a directional sprite frame or arc animation
     * aligned to this.facing and positioned at the hitbox location.
     *
     * @param canvas The ACM GCanvas to render onto
     */
    public void draw(GCanvas canvas) {
        // TODO [GRAPHICS]: replace placeholder GRect with a proper swing animation
        canvas.add(visual);
    }

    /**
     * Removes the swing visual from the canvas.
     * Call this when isExpired() returns true, before discarding the instance.
     *
     * @param canvas The ACM GCanvas to remove from
     */
    public void removeFrom(GCanvas canvas) {
        canvas.remove(visual);
    }

    // ==========================================================
    // GETTERS
    // ==========================================================

    /** @return this swing's collision hitbox */
    public Hitbox getHitbox() { return hitbox; }

    /** @return direction the swing is facing */
    public Direction getFacing() { return facing; }

    /** @return current age in ticks */
    public int getCurrentAge() { return currentAge; }
}
