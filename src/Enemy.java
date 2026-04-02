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
     * Builds a 4-point clockwise square patrol path centered on the spawn point,
     * with each waypoint offset by one tile (64px) in a cardinal direction:
     *   North → East → South → West
     *
     * If walls block some waypoints, Entity.move() self-corrects — the enemy simply
     * stops at the wall and the next tick advances to the following waypoint once the
     * threshold is reached. No passability check needed at construction time.
     */
    private void buildDefaultPatrolPath() {
        patrolPath = new ArrayList<>();
        patrolPath.add(new double[]{ spawnX,        spawnY - TILE }); // north
        patrolPath.add(new double[]{ spawnX + TILE, spawnY        }); // east
        patrolPath.add(new double[]{ spawnX,        spawnY + TILE }); // south
        patrolPath.add(new double[]{ spawnX - TILE, spawnY        }); // west
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

        // 2. Re-evaluate aggro every tick:
        //    Requires target in range AND unobstructed line of sight.
        if (target != null) {
            double dist = distanceTo(target);
            isAggro = (dist <= aggroRange) && hasLineOfSight(target);
        } else {
            isAggro = false;
        }

        // 3. Chase + attack if aggro; otherwise patrol
        if (isAggro && target != null) {
            chase(dt, target);
            tryAttack(target);
        } else {
            patrol(dt);
        }

        // 4. Hole-fall respawn: reset to spawn point, sync hitbox and sprite manually
        if (isOverHole()) {
            x = spawnX;
            y = spawnY;
            hitbox.updatePosition(x - 24, y - 24);
            sprite.setLocation(x - 24, y - 24);
        }
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
            // Reached this waypoint — advance to the next, wrapping around the list
            patrolIndex = (patrolIndex + 1) % patrolPath.size();
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
    // DEATH
    // ==========================================================

    /**
     * Called by the Room when isAlive() first returns false.
     * Returns true if a Coin should be spawned at this enemy's last position.
     *
     * TODO [ANIMATION]: trigger death animation here before removing the enemy from the canvas.
     * TODO [COIN]: Room checks the return value and spawns a Coin at (getX(), getY()).
     *              Coin class does not exist yet — the return boolean is the interface.
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
}
