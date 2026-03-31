/**
 * Entity.java
 *
 * Abstract base class for every moving object in the game:
 * Player, NPC, Enemy (and its subclasses), Boss, Projectile.
 *
 * Holds position, health, facing direction, a collision hitbox,
 * and a sprite image. Provides tile-aware movement via move(),
 * health management via takeDamage()/isAlive(), and facing
 * tracking via setFacing().
 *
 * Coordinate convention:
 *   x, y  =  CENTER of the entity in world-pixel coordinates.
 *   y increases downward (standard Java 2D / ACM).
 *   Sprite top-left  = (x - SPRITE_HALF, y - SPRITE_HALF)
 *   Hitbox top-left  = (x - HITBOX_HALF, y - HITBOX_HALF)
 *
 * SpriteAnimator (Person 1, Task 28):
 *   The sprite field below is a plain GImage placeholder.
 *   When P1 implements SpriteAnimator, replace:
 *     protected GImage sprite;
 *   with:
 *     protected SpriteAnimator animator;
 *   and update draw() and setFacing() to call animator methods.
 *   Mark those TODOs are labeled "TODO Task 28 (P1)".
 *
 * Person 3 — Combat & Enemies
 */
import acm.graphics.*;

public abstract class Entity {

    // ==========================================================
    // CONSTANTS
    // ==========================================================

    /** Default sprite size in pixels. Matches SimpleEntity's 48px convention. */
    private static final int SPRITE_SIZE = 48;
    private static final int SPRITE_HALF = SPRITE_SIZE / 2;  // 24

    /** Default hitbox size in pixels. Matches sprite size for full-body collision. */
    private static final int HITBOX_SIZE = 48;
    private static final int HITBOX_HALF = HITBOX_SIZE / 2;  // 24

    // ==========================================================
    // FIELDS
    // ==========================================================

    /** CENTER of entity in world-pixel coordinates. */
    protected double x, y;

    /** Movement speed in pixels per second. Subclasses multiply by dt to get dx/dy. */
    protected double speed;

    /** Current and maximum health. Health is clamped to [0, maxHealth]. */
    protected int health;
    protected int maxHealth;

    /** Direction this entity is currently facing (for animation, melee arc, AI). */
    protected Direction facing;

    /** Axis-aligned bounding box. Top-left = (x - HITBOX_HALF, y - HITBOX_HALF). */
    protected Hitbox hitbox;

    /** Sprite animator for directional walk cycles. */
    protected SpriteAnimator animator;

    /**
     * Static sprite image — used as the default/fallback frame.
     * Kept for backward compatibility; draw() prefers animator frames.
     */
    protected GImage sprite;

    /** Tile map used for passability and hole checks during move(). */
    protected TileMap tileMap;

    // ==========================================================
    // CONSTRUCTOR
    // ==========================================================

    /**
     * Creates a new Entity centered at (x, y).
     *
     * @param x          Center x position in world pixels
     * @param y          Center y position in world pixels
     * @param spritePath Path to the sprite image asset (e.g. "assets/enemy.png")
     * @param tileMap    The tile map for collision checks
     * @param maxHealth  Starting (and maximum) health
     * @param speed      Movement speed in pixels per second
     */
    public Entity(double x, double y, String spritePath,
                  TileMap tileMap, int maxHealth, double speed) {
        this.x         = x;
        this.y         = y;
        this.tileMap   = tileMap;
        this.maxHealth = maxHealth;
        this.health    = maxHealth;
        this.speed     = speed;
        this.facing    = Direction.DOWN; // idle default: face toward camera

        // Sprite: top-left offset so image is centered on (x, y)
        this.sprite = new GImage(spritePath, x - SPRITE_HALF, y - SPRITE_HALF);
        this.sprite.setSize(SPRITE_SIZE, SPRITE_SIZE);

        // Animator: initialized with 6 ticks/frame; fallback to static sprite
        this.animator = new SpriteAnimator(6);
        this.animator.setFallbackFrame(this.sprite);

        // Hitbox: top-left = (x - HITBOX_HALF, y - HITBOX_HALF)
        this.hitbox = new Hitbox(
            x - HITBOX_HALF,
            y - HITBOX_HALF,
            HITBOX_SIZE,
            HITBOX_SIZE
        );
    }

    // ==========================================================
    // CORE LIFECYCLE
    // ==========================================================

    /**
     * Called once per game tick. Override in subclasses for AI, input, animation, etc.
     * Always call super.update(dt) first in subclasses to stay compatible with
     * any shared base-class tick logic added in the future.
     *
     * @param dt delta-time in seconds since the last tick (e.g. 0.016 for ~60fps)
     */
    public void update(double dt) {
        // Base: no-op. Subclasses override this.
    }

    /**
     * Draws the entity's sprite onto the canvas.
     * Uses the animator's current frame if available, falls back to static sprite.
     *
     * @param canvas The ACM GCanvas to draw onto
     */
    public void draw(GCanvas canvas) {
        GImage frame = animator.getCurrentFrame();
        if (frame != null) {
            canvas.add(frame);
        } else {
            canvas.add(sprite);
        }
    }

    // ==========================================================
    // MOVEMENT & COLLISION
    // ==========================================================

    /**
     * Moves the entity by (dx, dy) pixels with tile collision.
     *
     * dx and dy are already-scaled pixel deltas for this frame
     * (caller should compute: dx = directionX * speed * dt).
     *
     * Uses axis-separated collision so the entity slides along walls:
     * X axis is resolved first, then Y axis using the updated X position.
     *
     * Only the two corners on the leading edge of each axis are probed
     * (not all four corners) — this prevents "corner catching" when moving
     * diagonally past a convex wall corner. The perpendicular probe points
     * are shrunk inward by 1px to avoid false positives when the entity
     * is exactly flush with a tile boundary.
     *
     * Fixes SimpleEntity's bug where bottom probes were at y+48 instead
     * of y+23 (24px below center is the actual bottom edge of the hitbox).
     *
     * After resolving both axes, the hitbox and sprite are synced to the
     * new (x, y) center, and facing is updated from the net movement.
     *
     * @param dx Horizontal pixel delta for this frame (positive = right)
     * @param dy Vertical pixel delta for this frame (positive = down)
     */
    public void move(double dx, double dy) {
        if (tileMap == null) {
            x += dx;
            y += dy;
            hitbox.updatePosition(x - HITBOX_HALF, y - HITBOX_HALF);
            sprite.setLocation(x - SPRITE_HALF, y - SPRITE_HALF);
            animator.setPosition(x - SPRITE_HALF, y - SPRITE_HALF);
            if (dx != 0 || dy != 0) {
                setFacing(Direction.fromDelta(dx, dy));
                animator.update();
            }
            return;
        }

        // --- X axis ---
        double newX = x + dx;
        boolean xClear;

        if (dx > 0) {
            // Moving right: probe the right edge (newX + 24) at top and bottom,
            // inset 1px from the hitbox corners to avoid tile-grid false positives.
            xClear = tileMap.isPassable(newX + HITBOX_HALF, y - HITBOX_HALF + 1)
                  && tileMap.isPassable(newX + HITBOX_HALF, y + HITBOX_HALF - 1);

        } else if (dx < 0) {
            // Moving left: probe the left edge (newX - 24)
            xClear = tileMap.isPassable(newX - HITBOX_HALF, y - HITBOX_HALF + 1)
                  && tileMap.isPassable(newX - HITBOX_HALF, y + HITBOX_HALF - 1);

        } else {
            xClear = true; // no horizontal movement — nothing to check
        }

        if (xClear) {
            x = newX;
        }

        // --- Y axis (uses updated x so diagonal movement slides correctly) ---
        double newY = y + dy;
        boolean yClear;

        if (dy > 0) {
            // Moving down: probe the bottom edge (newY + 24)
            yClear = tileMap.isPassable(x - HITBOX_HALF + 1, newY + HITBOX_HALF)
                  && tileMap.isPassable(x + HITBOX_HALF - 1, newY + HITBOX_HALF);

        } else if (dy < 0) {
            // Moving up: probe the top edge (newY - 24)
            yClear = tileMap.isPassable(x - HITBOX_HALF + 1, newY - HITBOX_HALF)
                  && tileMap.isPassable(x + HITBOX_HALF - 1, newY - HITBOX_HALF);

        } else {
            yClear = true; // no vertical movement — nothing to check
        }

        if (yClear) {
            y = newY;
        }

        // Sync hitbox top-left corner to new center
        hitbox.updatePosition(x - HITBOX_HALF, y - HITBOX_HALF);

        // Sync sprite and animator position to new center
        sprite.setLocation(x - SPRITE_HALF, y - SPRITE_HALF);
        animator.setPosition(x - SPRITE_HALF, y - SPRITE_HALF);

        // Update facing from the net movement vector
        if (dx != 0 || dy != 0) {
            setFacing(Direction.fromDelta(dx, dy));
            animator.update(); // advance walk animation while moving
        }
    }

    /**
     * Convenience: returns true if the entity's center is over a hole tile.
     * Call from subclass update() to trigger hole-fall/respawn logic.
     *
     * @return true if the center point (x, y) is inside a HOLE tile
     */
    public boolean isOverHole() {
        return tileMap != null && tileMap.isHole(x, y);
    }

    // ==========================================================
    // HEALTH
    // ==========================================================

    /**
     * Reduces health by the given amount. Clamps to 0 (no negative health).
     * Subclasses can override to apply half-damage relics, invincibility frames, etc.
     *
     * @param amount damage to apply (positive integer)
     */
    public void takeDamage(int amount) {
        health = Math.max(0, health - amount);
    }

    /**
     * Directional damage overload. Default ignores the hit direction and
     * delegates to takeDamage(amount). Override in subclasses that need
     * directional armor checks (e.g. ArmorEnemy blocks all frontal hits).
     *
     * @param amount  Damage to apply (positive integer)
     * @param hitFrom Direction the hit arrived from (the attacker's facing direction)
     */
    public void takeDamage(int amount, Direction hitFrom) {
        takeDamage(amount);
    }

    /**
     * Returns true while this entity has health remaining.
     * False means the entity is dead and should be removed from the room.
     *
     * @return true if health > 0
     */
    public boolean isAlive() {
        return health > 0;
    }

    // ==========================================================
    // FACING
    // ==========================================================

    /**
     * Sets the direction this entity is currently facing.
     * Notifies the animator so the correct directional walk cycle plays.
     * Null-guarded — callers may pass null without risk.
     *
     * @param d the new facing direction (null is silently ignored)
     */
    public void setFacing(Direction d) {
        if (d != null) {
            facing = d;
            animator.setDirection(d);
        }
    }

    // ==========================================================
    // GETTERS
    // ==========================================================

    /** @return center x position in world pixels */
    public double getX()            { return x; }

    /** @return center y position in world pixels */
    public double getY()            { return y; }

    /** @return current health */
    public int getHealth()          { return health; }

    /** @return maximum health */
    public int getMaxHealth()       { return maxHealth; }

    /** @return direction this entity is currently facing */
    public Direction getFacing()    { return facing; }

    /** @return this entity's axis-aligned bounding hitbox */
    public Hitbox getHitbox()       { return hitbox; }

    /** @return movement speed in pixels per second */
    public double getSpeed()        { return speed; }

    /** @return the sprite animator for this entity */
    public SpriteAnimator getAnimator() { return animator; }
}
