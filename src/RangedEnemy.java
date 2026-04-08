import java.util.List;

/**
 * RangedEnemy.java
 *
 * A ranged enemy that fires projectiles at the player and actively retreats
 * to maintain a preferred distance. Vulnerable from all directions.
 *
 * Behavior:
 *   Patrol:  walks a 4-point square loop around spawn (inherited from Enemy).
 *   Chase:   retreats if player is closer than retreatDistance;
 *            holds position if at or beyond that distance (does NOT advance).
 *   Attack:  spawns a Projectile toward the player every fireRate ticks.
 *
 * Stats (Zelda-feel):
 *   Health  2 — fragile glass-cannon; 2 sword hits to kill
 *   Patrol 50 px/s — slow, methodical pacing
 *   Chase  90 px/s — repurposed as retreat speed; fast enough to maintain distance
 *   Aggro 256 px — 4-tile radius; spots the player first
 *
 * Person 3 — Combat & Enemies
 */
public class RangedEnemy extends Enemy {

    private static final String SPRITE_DIR = "assets/visuals/skeley-mob-1/normalized/";
    private static final String SPRITE_PREFIX = "skeley-mob-1";
    private static final int MAX_HEALTH = Player.HALF_HEARTS_PER_HEART;

    // ==========================================================
    // FIELDS
    // ==========================================================

    /**
     * Ticks between each projectile fire. Reset to this value after shooting.
     * At 60fps, 90 ticks = ~1.5 seconds between shots.
     */
    private int fireRate;

    /**
     * Minimum preferred distance from the player in pixels.
     * Below this threshold the enemy retreats. At or above, it holds position.
     * Default 160px = 2.5 tiles.
     */
    private double retreatDistance;

    /**
     * Active projectile list shared with the Room. Set by Room via
     * setProjectileList() at spawn time. Null until wired — tryAttack()
     * guards against null so compilation is safe.
     */
    private List<Projectile> projectiles;

    // ==========================================================
    // CONSTRUCTOR
    // ==========================================================

    /**
     * Creates a RangedEnemy centered at (x, y).
     *
     * @param x       Spawn center X in world pixels
     * @param y       Spawn center Y in world pixels
     * @param tileMap Tile map for collision and line-of-sight
     */
    public RangedEnemy(double x, double y, TileMap tileMap) {
        super(x, y, SPRITE_DIR + SPRITE_PREFIX + "-idle-front.gif", tileMap,
              MAX_HEALTH, // maxHealth   — fragile, 2 hits to kill
              50.0,   // patrolSpeed (px/s)
              90.0,   // chaseSpeed  (px/s) — used as retreat speed
              256.0); // aggroRange  (px)   — 4 tiles, spots player early
        this.fireRate        = 90;
        this.retreatDistance = 160.0;
        this.projectiles     = null; // wired by Room via setProjectileList()
        loadSkeletonAnimations(SPRITE_DIR, SPRITE_PREFIX);
        setSpriteRenderSize(72, 72);
    }

    /**
     * Wires in the Room's active projectile list so this enemy can spawn Projectiles.
     * Call immediately after constructing the RangedEnemy in the Room.
     *
     * @param list the Room's shared active projectile list
     */
    public void setProjectileList(List<Projectile> list) {
        this.projectiles = list;
    }

    // ==========================================================
    // CHASE OVERRIDE — retreat behavior
    // ==========================================================

    /**
     * Overrides Enemy.chase() to keep distance from the player instead of closing in.
     *
     * If the player is closer than retreatDistance: move directly away at chaseSpeed.
     * If the player is at or beyond retreatDistance: hold position and let tryAttack() fire.
     *
     * The inherited chaseSpeed field is reused semantically as "response speed" —
     * the speed at which the enemy moves when repositioning under pressure.
     *
     * Perpendicular "cornered" movement is deferred — TODO for a future sprint.
     *
     * @param dt     Delta-time in seconds
     * @param target Entity to keep distance from (typically the Player)
     */
    @Override
    protected void chase(double dt, Entity target) {
        if (target == null) return;

        double dx   = target.getX() - x;
        double dy   = target.getY() - y;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < 1.0) return; // guard: prevent divide-by-zero if centers overlap

        if (dist < retreatDistance) {
            // Too close — back away from the player
            double scale = chaseSpeed * dt / dist;
            move(-dx * scale, -dy * scale); // negate: move AWAY from target
        }
        // dist >= retreatDistance: hold position, allow tryAttack() to fire this tick
        // TODO [future]: add perpendicular strafe behavior when cornered against a wall
    }

    // ==========================================================
    // ATTACK — projectile spawn stub
    // ==========================================================

    /**
     * Fires a projectile toward the target when the cooldown has expired.
     * No-ops if the projectile list hasn't been wired by Room yet.
     *
     * @param target Entity to fire at (typically the Player)
     */
    @Override
    protected void tryAttack(Entity target) {
        if (target == null) return;
        if (attackCooldownTicks > 0) return;
        if (animState == AnimState.DAMAGE || animState == AnimState.DEATH) return;
        if (projectiles == null) return;

        projectiles.add(new Projectile(x, y, target.getX(), target.getY(), tileMap, this));
        // # rig — play GameSFX.SFX.RANGED_FIRE here once a projectile-fire sound is added to the catalog
        attackCooldownTicks = fireRate;
        setAnimState(AnimState.ATTACK);
        animTimer = attackAnimDuration;
    }

    /** @return preferred minimum player distance in pixels */
    public double getRetreatDistance() { return retreatDistance; }

    /** @return cooldown between shots in ticks */
    public int getFireRate() { return fireRate; }
}
