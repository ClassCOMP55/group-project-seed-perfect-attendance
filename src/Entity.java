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

    /** Asset path used to create the sprite (kept for rebuild on resize). */
    protected String spritePath;

    /** Tile map used for passability and hole checks during move(). */
    protected TileMap tileMap;

    /**
     * When true, the next {@link #draw(GCanvas)} call will re-create the
     * sprite GImage at the current render dimensions. Animated GIFs in
     * ACM may not honor {@code setSize()} after they start animating, so
     * a fresh GImage is the most reliable way to apply a new size.
     */
    private boolean spriteNeedsRebuild;
    /** Last visual added to canvas so swaps don't leave ghost images behind. */
    private GImage lastDrawnVisual;

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
        this.spritePath = spritePath;
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

    /**
     * Override render dimensions (pixels) stored here so every call to
     * {@link #syncVisualPosition()} reapplies them to whatever animation
     * frame is current. 0 means "use the sprite's natural size".
     */
    private double renderWidth  = SPRITE_SIZE;
    private double renderHeight = SPRITE_SIZE;

    /**
     * Resizes the visual sprite while keeping hitbox/collision dimensions unchanged.
     * The target size is remembered and reapplied every tick so animation frame
     * swaps do not silently reset the sprite back to its default 48×48 size.
     *
     * @param width  target render width in pixels (pass SPRITE_SIZE to reset)
     * @param height target render height in pixels (pass SPRITE_SIZE to reset)
     */
    public void setSpriteRenderSize(double width, double height) {
        double newW = Math.max(1.0, width);
        double newH = Math.max(1.0, height);
        boolean changed = Math.abs(newW - renderWidth) > 0.5
                       || Math.abs(newH - renderHeight) > 0.5;
        this.renderWidth  = newW;
        this.renderHeight = newH;
        if (changed && spritePath != null) {
            spriteNeedsRebuild = true;
        }
        syncVisualPosition();
    }

    /**
     * Re-centers the current sprite/animation frame on the entity's (x, y) position
     * and reapplies the stored render dimensions so animated GIF frames are always
     * the correct size regardless of which frame the animator is currently on.
     */
    protected void syncVisualPosition() {
        double hw = renderWidth  / 2.0;
        double hh = renderHeight / 2.0;
        if (sprite != null) {
            sprite.setSize(renderWidth, renderHeight);
            sprite.setLocation(x - hw, y - hh);
        }
        GImage frame = animator.getCurrentFrame();
        if (frame != null) {
            frame.setSize(renderWidth, renderHeight);
            frame.setLocation(x - hw, y - hh);
        }
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
     * Reapplies {@link #renderWidth}/{@link #renderHeight} so the frame is always
     * the correct size at render time even if the animator swapped frames since the
     * last {@link #syncVisualPosition()} call.
     *
     * @param canvas The ACM GCanvas to draw onto
     */
    public void draw(GCanvas canvas) {
        if (spriteNeedsRebuild && spritePath != null) {
            GImage oldSprite = sprite;
            if (oldSprite != null) {
                canvas.remove(oldSprite);
            }
            sprite = new GImage(spritePath, 0, 0);
            sprite.setSize(renderWidth, renderHeight);
            animator.setFallbackFrame(sprite);
            spriteNeedsRebuild = false;
        }
        syncVisualPosition();
        GImage frame = animator.getCurrentFrame();
        GImage currentVisual = frame != null ? frame : sprite;
        if (lastDrawnVisual != null && lastDrawnVisual != currentVisual) {
            canvas.remove(lastDrawnVisual);
        }
        if (currentVisual != null) {
            canvas.add(currentVisual);
            lastDrawnVisual = currentVisual;
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
            syncVisualPosition();
            if (dx != 0 || dy != 0) {
                setFacing(Direction.fromDelta(dx, dy));
                animator.update();
                syncVisualPosition();
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
        syncVisualPosition();

        // Update facing from the net movement vector
        if (dx != 0 || dy != 0) {
            setFacing(Direction.fromDelta(dx, dy));
            animator.update(); // advance walk animation while moving
            syncVisualPosition();
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

    /**
     * Raw position shift without tile collision — used for separation pushes.
     */
    public void nudge(double dx, double dy) {
        x += dx;
        y += dy;
        hitbox.updatePosition(x - HITBOX_HALF, y - HITBOX_HALF);
        syncVisualPosition();
    }

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

    /**
     * Shifts this entity's currently displayed sprite by (panX, panY) pixels on the canvas.
     * Only the visual moves — internal world-position coordinates (x, y) are NOT changed.
     * Used by RoomTransition to carry the entity along with the room pan animation.
     *
     * @param panX horizontal pixels to shift (negative = left, positive = right)
     * @param panY vertical pixels to shift (negative = up, positive = down)
     */
    public void panVisual(double panX, double panY) {
        GImage frame = animator.getCurrentFrame();
        GImage currentVisual = (frame != null) ? frame : sprite;
        if (currentVisual != null) {
            currentVisual.move(panX, panY);
        }
    }

    /**
     * Removes this entity's sprite (and current animation frame) from the
     * canvas. Call during scene teardown to prevent orphaned visuals.
     */
    public void removeSpriteFromCanvas(GCanvas canvas) {
        if (canvas == null) return;
        if (lastDrawnVisual != null) {
            canvas.remove(lastDrawnVisual);
            lastDrawnVisual = null;
        }
        if (sprite != null) {
            canvas.remove(sprite);
        }
        GImage frame = animator.getCurrentFrame();
        if (frame != null && frame != sprite) {
            canvas.remove(frame);
        }
    }
}
