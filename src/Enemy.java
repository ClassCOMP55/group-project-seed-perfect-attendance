/**
 * Enemy.java
 *
 * Base enemy class. Extends Entity. Handles patrol loop (waypoint following),
 * line-of-sight aggro detection (range + wall check), and stubs for chase/attack
 * that subclasses override.
 *
 * All three enemy subtypes (MeleeEnemy, RangedEnemy, ArmorEnemy) extend this class
 * and override tryAttack() with their specific combat logic.
 *
 * update() signature:
 *   update(double dt, Entity target) — not update(double dt, Player p) — because
 *   Player does not extend Entity yet. Once Player is refactored to extend Entity,
 *   change the parameter type to Player with no other logic changes needed.
 *
 * Aggro rule: enemy aggros if target is within aggroRange (default 192px = 3 tiles)
 * AND has an unobstructed line of sight (no wall tiles between enemy and target).
 * Both conditions must be true every tick to stay in chase mode, so hiding behind
 * a wall will cause the enemy to return to its patrol.
 *
 * Person 3 — Combat & Enemies
 */
import acm.graphics.*;
import acm.util.RandomGenerator;
import java.util.ArrayList;
import java.util.List;

public class Enemy extends Entity {

    // ==========================================================
    // ANIMATION STATE
    // ==========================================================

    /** Visual animation states for enemies. */
    public enum AnimState { IDLE, ATTACK, DAMAGE, DEATH }

    /** Current animation state — drives which GIF set the SpriteAnimator uses. */
    protected AnimState animState = AnimState.IDLE;

    /**
     * Countdown timer (seconds) for one-shot animations (ATTACK, DAMAGE, DEATH).
     * When this reaches 0, the enemy transitions back to IDLE (or stays dead).
     */
    protected double animTimer = 0;

    /**
     * Per-state GImage lookup: animSprites[state][direction].
     * Populated by subclass constructors (e.g. MeleeEnemy.loadAllSprites()).
     * Null entries are fine — the animator falls back to whatever is loaded.
     */
    protected GImage[][] animSprites;

    /** Duration in seconds for each one-shot animation. Set by subclass. */
    protected double attackAnimDuration = 1.12;
    protected double damageAnimDuration = 0.56;
    protected double deathAnimDuration  = 1.40;

    // ==========================================================
    // CONSTANTS
    // ==========================================================

    /** Distance threshold (pixels) at which a patrol waypoint is considered "reached". */
    private static final double WAYPOINT_THRESHOLD = 8.0;

    /** One tile in pixels — used to build the default patrol square. */
    private static final double TILE = 48.0; // matches TileMap.tileSize (locked 2026-04-01)

    /** Step size (pixels) for line-of-sight ray march — half a tile. */
    private static final double LOS_STEP = 32.0;

    /** Default patrol speed in pixels per second. */
    private static final double DEFAULT_PATROL_SPEED = 60.0;

    /** Default chase speed in pixels per second. */
    private static final double DEFAULT_CHASE_SPEED = 100.0;

    /** Default aggro range in pixels (3 tiles = 192px). */
    private static final double DEFAULT_AGGRO_RANGE = 192.0;

    /** Probability of dropping a coin on death (0.0–1.0). */
    private static final double COIN_DROP_CHANCE = 0.5;

    // ==========================================================
    // FIELDS
    // ==========================================================

    // ── Patrol ─────────────────────────────────────────────────────────────

    /** Ordered list of world-pixel waypoints {x, y} forming the patrol loop. */
    protected List<double[]> patrolPath;

    /** Index of the next waypoint to walk toward. */
    protected int patrolIndex;

    /** Movement speed in pixels/second while patrolling. */
    protected double patrolSpeed;

    /** Distance in pixels at which this enemy detects the target and switches to chase. */
    protected double aggroRange;

    /** Movement speed in pixels/second while chasing the target. */
    protected double chaseSpeed;

    /** True when the target is within aggroRange AND visible this tick. Re-evaluated every tick. */
    protected boolean isAggro;

    // ── Combat ─────────────────────────────────────────────────────────────

    /**
     * Cooldown counter in game ticks. Decremented by 1 each update() call.
     * Subclasses reset this to their attack rate (e.g. 60 = ~1s at 60fps) after attacking.
     */
    protected int attackCooldownTicks;

    // ── Spawn tracking ──────────────────────────────────────────────────────

    /** World-pixel X at construction — used as respawn point if enemy falls in a hole. */
    protected final double spawnX;

    /** World-pixel Y at construction — used as respawn point if enemy falls in a hole. */
    protected final double spawnY;

    // ── Shared RNG ─────────────────────────────────────────────────────────

    private static final RandomGenerator rgen = RandomGenerator.getInstance();

    // ==========================================================
    // CONSTRUCTORS
    // ==========================================================

    /**
     * Creates a basic enemy at (x, y) with default patrol speed, chase speed, and aggro range.
     * Automatically builds a 4-point square patrol path around the spawn point.
     *
     * @param x       Spawn center X in world pixels
     * @param y       Spawn center Y in world pixels
     * @param tileMap Tile map for movement collision and line-of-sight
     */
    public Enemy(double x, double y, TileMap tileMap) {
        this(x, y, "assets/enemy.png", tileMap,
             2, DEFAULT_PATROL_SPEED, DEFAULT_CHASE_SPEED, DEFAULT_AGGRO_RANGE);
    }

    /**
     * Protected constructor for subclasses to customise sprite, health, and speeds
     * without duplicating patrol setup logic. Called via super() in MeleeEnemy, etc.
     *
     * @param x           Spawn center X in world pixels
     * @param y           Spawn center Y in world pixels
     * @param spritePath  Path to the sprite asset (e.g. "assets/enemy_armor.png")
     * @param tileMap     Tile map for collision and line-of-sight
     * @param maxHealth   Starting health
     * @param patrolSpeed Movement speed while patrolling (px/s)
     * @param chaseSpeed  Movement speed while chasing (px/s)
     * @param aggroRange  Detection radius in pixels
     */
    protected Enemy(double x, double y, String spritePath, TileMap tileMap,
                    int maxHealth, double patrolSpeed, double chaseSpeed, double aggroRange) {
        // Pass patrolSpeed as Entity.speed so the inherited field is meaningful
        super(x, y, spritePath, tileMap, maxHealth, patrolSpeed);

        this.spawnX      = x;
        this.spawnY      = y;
        this.patrolSpeed = patrolSpeed;
        this.chaseSpeed  = chaseSpeed;
        this.aggroRange  = aggroRange;
        this.isAggro     = false;
        this.attackCooldownTicks = 0;
        this.patrolIndex = 0;

        buildDefaultPatrolPath();
    }

    // ==========================================================
    // PATROL PATH SETUP
    // ==========================================================

    /**
     * Builds a randomized patrol path centered on the spawn point.
     * Each enemy gets 3-6 waypoints scattered within a 1-3 tile radius,
     * so no two enemies walk the same route.
     */
    private void buildDefaultPatrolPath() {
        patrolPath = new ArrayList<>();
        int numPoints = 4 + rgen.nextInt(4); // 4 to 7 waypoints
        double radius = TILE * (4 + rgen.nextDouble() * 4); // 4-8 tiles from spawn

        for (int i = 0; i < numPoints; i++) {
            double angle = 2 * Math.PI * i / numPoints + rgen.nextDouble() * 0.6 - 0.3;
            double dist  = radius * (0.4 + rgen.nextDouble() * 0.6);
            double wx = spawnX + Math.cos(angle) * dist;
            double wy = spawnY + Math.sin(angle) * dist;

            // Only use waypoints that land on passable tiles
            if (tileMap != null && !tileMap.isPassable(wx, wy)) continue;

            patrolPath.add(new double[]{ wx, wy });
        }

        // Fallback: if all waypoints were culled, stay at spawn
        if (patrolPath.isEmpty()) {
            patrolPath.add(new double[]{ spawnX, spawnY });
        }
    }

    /**
     * Replaces the patrol path with a custom set of waypoints.
     * Resets the patrol index to 0 to prevent out-of-bounds on shorter paths.
     *
     * @param path List of {x, y} waypoints — must not be null or empty
     */
    public void setPatrolPath(List<double[]> path) {
        if (path != null && !path.isEmpty()) {
            this.patrolPath = path;
            this.patrolIndex = 0;
        }
    }

    // ==========================================================
    // CORE UPDATE
    // ==========================================================

    /**
     * Main per-tick update. Orchestrates patrol / aggro / chase / attack.
     *
     * Aggro is re-evaluated every tick using both distance and line of sight:
     *   isAggro = (dist <= aggroRange) && hasLineOfSight(target)
     * This means the player can break aggro by hiding behind a wall.
     *
     * NOTE: Parameter is Entity (not Player) because Player does not extend Entity yet.
     * Once Player is refactored to extend Entity, change the parameter type to Player —
     * no other logic changes are needed.
     *
     * Passing null is safe — the enemy will patrol when there is no target.
     *
     * @param dt     Delta-time in seconds (e.g. 0.016 at ~60fps)
     * @param target The entity to chase/attack (typically the Player)
     */
    public void update(double dt, Entity target) {

        // 1. Tick down attack cooldown
        if (attackCooldownTicks > 0) {
            attackCooldownTicks--;
        }

        // 2. Tick down one-shot animation timers (DAMAGE, DEATH, ATTACK)
        if (animTimer > 0) {
            animTimer -= dt;
            if (animState == AnimState.DEATH) {
                applyAnimVisual(); // keep syncing the death GIF to facing
                return; // frozen during death — no movement, no AI
            }
            if (animTimer <= 0) {
                setAnimState(AnimState.IDLE);
            } else if (animState == AnimState.DAMAGE) {
                applyAnimVisual(); // keep syncing the damage GIF to facing
                return; // stagger — skip movement while hurt
            }
        }

        // 3. Re-evaluate aggro every tick:
        //    Requires target in range AND unobstructed line of sight.
        if (target != null) {
            double dist = distanceTo(target);
            isAggro = (dist <= aggroRange) && hasLineOfSight(target);
        } else {
            isAggro = false;
        }

        // 4. Chase + attack if aggro; otherwise patrol
        boolean targetAlive = target != null && target.isAlive();
        if (isAggro && targetAlive) {
            chase(dt, target);
            tryAttack(target);
        } else {
            if (target != null && !targetAlive) {
                isAggro = false;
                if (animState == AnimState.ATTACK) {
                    setAnimState(AnimState.IDLE);
                    animTimer = 0;
                }
            }
            patrol(dt);
        }

        // 5. Hole-fall respawn: reset to spawn point, sync hitbox and sprite manually
        if (isOverHole()) {
            x = spawnX;
            y = spawnY;
            hitbox.updatePosition(x - 24, y - 24);
            sprite.setLocation(x - 24, y - 24);
        }

        // 6. Sync visual — pick the right GIF for (animState, facing) every tick
        applyAnimVisual();
    }

    // ==========================================================
    // PATROL
    // ==========================================================

    /**
     * Advances the enemy along its patrol path one tick.
     *
     * Each tick: get the vector toward patrolPath[patrolIndex]. If distance is within
     * WAYPOINT_THRESHOLD, advance the index (wrapping). Otherwise, normalize and
     * call move() with the patrol-speed-scaled delta.
     *
     * Entity.move() handles tile collision, hitbox/sprite sync, and facing update.
     *
     * @param dt Delta-time in seconds
     */
    protected void patrol(double dt) {
        if (patrolPath == null || patrolPath.isEmpty()) return;

        double[] wp   = patrolPath.get(patrolIndex);
        double   dx   = wp[0] - x;
        double   dy   = wp[1] - y;
        double   dist = Math.sqrt(dx * dx + dy * dy);

        if (dist <= WAYPOINT_THRESHOLD) {
            // Pick a random different waypoint to avoid predictable loops
            if (patrolPath.size() > 1) {
                int next;
                do { next = rgen.nextInt(patrolPath.size()); } while (next == patrolIndex);
                patrolIndex = next;
            }
            return;
        }

        // Normalize to unit vector, scale by patrolSpeed * dt
        double scale = patrolSpeed * dt / dist;
        move(dx * scale, dy * scale);
    }

    // ==========================================================
    // CHASE
    // ==========================================================

    /**
     * Moves directly toward the target's center at chaseSpeed.
     *
     * @param dt     Delta-time in seconds
     * @param target Entity to pursue
     */
    protected void chase(double dt, Entity target) {
        double dx   = target.getX() - x;
        double dy   = target.getY() - y;
        double dist = Math.sqrt(dx * dx + dy * dy);

        // Guard: prevent divide-by-zero if centers overlap (can occur in testing)
        if (dist < 1.0) return;

        double scale = chaseSpeed * dt / dist;
        move(dx * scale, dy * scale);
    }

    // ==========================================================
    // ATTACK
    // ==========================================================

    /**
     * Attempts to attack the target. Base implementation is a no-op.
     * Subclasses override with their specific combat behavior.
     *
     * Typical override pattern:
     *   if (attackCooldownTicks <= 0 && target.getHitbox().overlaps(hitbox)) {
     *       target.takeDamage(damage);
     *       attackCooldownTicks = 60;   // ~1s cooldown at 60fps
     *   }
     *
     * TODO: MeleeEnemy — contact damage when hitbox overlaps player
     * TODO: RangedEnemy — spawn Projectile toward player on cooldown
     * TODO: ArmorEnemy  — contact damage; only vulnerable to hits from behind
     *
     * @param target Entity to attack
     */
    protected void tryAttack(Entity target) {
        // no-op in base Enemy — subclasses override
    }

    // ==========================================================
    // ANIMATION STATE MANAGEMENT
    // ==========================================================

    /**
     * Transitions to a new animation state. The actual GIF swap happens in
     * applyAnimVisual(), which runs every tick — same pattern as Player's
     * applyDirectionalVisual().
     */
    protected void setAnimState(AnimState state) {
        if (state == animState) return;
        animState = state;
    }

    /**
     * Resolves the current (animState, facing) to a GImage and pushes it into
     * both the animator's frame list AND fallback frame. Called every tick at the
     * end of update() to seamlessly blend between idle/walk/attack/damage/death.
     *
     * Must update the frame list (not just the fallback) because getCurrentFrame()
     * checks framesByDirection first and only falls back when the list is empty.
     */
    protected void applyAnimVisual() {
        if (animSprites == null) return;
        int stateIdx = animState.ordinal();
        int dirIdx = dirToIndex(facing);
        GImage visual = animSprites[stateIdx][dirIdx];
        if (visual == null) {
            visual = animSprites[stateIdx][0]; // fallback to DOWN
        }
        if (visual != null) {
            SpriteAnimator anim = getAnimator();
            anim.addFrames(facing, java.util.Collections.singletonList(visual));
            anim.setFallbackFrame(visual);
            syncVisualPosition();
        }
    }

    /** Maps Direction enum to the animSprites column index. */
    private int dirToIndex(Direction d) {
        if (d == null) return 0;
        switch (d) {
            case DOWN:  return 0;
            case UP:    return 1;
            case LEFT:  return 2;
            case RIGHT: return 3;
            default:    return 0;
        }
    }

    /**
     * Override takeDamage to trigger the DAMAGE animation.
     * The enemy staggers (skips movement) for the duration of the hurt GIF.
     */
    @Override
    public void takeDamage(int amount) {
        super.takeDamage(amount);
        if (health <= 0) {
            setAnimState(AnimState.DEATH);
            animTimer = deathAnimDuration;
        } else if (animState != AnimState.DEATH) {
            setAnimState(AnimState.DAMAGE);
            animTimer = damageAnimDuration;
        }
    }

    /**
     * Enemy is only considered "dead" (removable) after the death animation finishes.
     * Health 0 + DEATH state + timer expired = truly dead.
     */
    @Override
    public boolean isAlive() {
        if (health > 0) return true;
        return animState == AnimState.DEATH && animTimer > 0;
    }

    // ==========================================================
    // DEATH
    // ==========================================================

    /**
     * Called by the Room when isAlive() first returns false.
     * Returns true if a Coin should be spawned at this enemy's last position.
     *
     * @return true ~50% of the time (COIN_DROP_CHANCE)
     */
    public boolean onDeath() {
        return rgen.nextDouble() < COIN_DROP_CHANCE;
    }

    // ==========================================================
    // LINE-OF-SIGHT
    // ==========================================================

    /**
     * Returns true if there are no wall tiles between this enemy and the target.
     *
     * Algorithm: march from enemy center toward target center in LOS_STEP (32px) increments.
     * At each step, call tileMap.isPassable(). If any point is not passable, a wall
     * is blocking the view → return false. If all points are passable → return true.
     *
     * 32px steps (half a tile) ensure the ray cannot skip over a single-tile-wide wall:
     * a 64px wall will always be hit by at least one step point.
     *
     * @param target The entity to test visibility toward
     * @return true if the path from this enemy's center to the target's center is clear
     */
    private boolean hasLineOfSight(Entity target) {
        double dx   = target.getX() - x;
        double dy   = target.getY() - y;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < 1.0) return true; // trivially visible if overlapping

        // Unit step vector scaled to LOS_STEP pixels
        double stepX = (dx / dist) * LOS_STEP;
        double stepY = (dy / dist) * LOS_STEP;

        // Number of full LOS_STEP increments that fit before reaching the target
        int steps = (int)(dist / LOS_STEP);

        for (int i = 1; i <= steps; i++) {
            double px = x + stepX * i;
            double py = y + stepY * i;
            if (!tileMap.isPassable(px, py)) {
                return false; // wall tile found — line of sight is blocked
            }
        }

        return true; // all intermediate points are passable
    }

    // ==========================================================
    // HELPERS
    // ==========================================================

    /**
     * Euclidean distance from this enemy's center to another entity's center.
     *
     * @param other Target entity
     * @return distance in pixels
     */
    private double distanceTo(Entity other) {
        double dx = other.getX() - this.x;
        double dy = other.getY() - this.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    // ==========================================================
    // GETTERS
    // ==========================================================

    /** @return true if this enemy is currently in chase/attack mode */
    public boolean isAggro() { return isAggro; }

    /** @return ticks remaining on attack cooldown (0 = ready to attack) */
    public int getAttackCooldown() { return attackCooldownTicks; }

    /** @return current animation state (IDLE, ATTACK, DAMAGE, DEATH) */
    public AnimState getAnimState() { return animState; }

    /** @return the patrol waypoint list (for debug overlay) */
    public java.util.List<double[]> getPatrolPath() { return patrolPath; }

    /** @return index of the current target waypoint */
    public int getPatrolIndex() { return patrolIndex; }
}
