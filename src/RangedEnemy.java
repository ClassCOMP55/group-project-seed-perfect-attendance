import acm.graphics.GImage;
import java.util.Collections;
import java.util.List;

/**
 * RangedEnemy.java — tree monster variant.
 *
 * A ranged enemy that fires projectiles at the player and actively retreats
 * to maintain a preferred distance. Vulnerable from all directions.
 */
public class RangedEnemy extends Enemy {

    private static final String SPRITE_DIR = "assets/visuals/tree-monster-mob(long ranged attacks)/";
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
        super(x, y, SPRITE_DIR + "tree_monster_idle_front.gif", tileMap,
              MAX_HEALTH, // maxHealth   — fragile, 2 hits to kill
              50.0,   // patrolSpeed (px/s)
              90.0,   // chaseSpeed  (px/s) — used as retreat speed
              256.0); // aggroRange  (px)   — 4 tiles, spots player early
        this.fireRate        = 90;
        this.retreatDistance = 160.0;
        this.projectiles     = null; // wired by Room via setProjectileList()
        loadAllSprites();
        setSpriteRenderSize(72, 72);
    }

    private void loadAllSprites() {
        String[][] names = {
            // IDLE
            { "tree_monster_idle_front.gif",          "tree monster idle back.gif",
              "tree monster idle left.gif",            "tree monster idle right.gif"          },
            // ATTACK (different word order in filenames)
            { "monster tree attack front.gif",         "monster tree attack back.gif",
              "monster tree attack left.gif",           "monster tree attack right.gif"        },
            // DAMAGE
            { "tree monster taking damage front.gif",  "tree monster taking damage back.gif",
              "tree monster taking damage left.gif",    "tree monster taking damage right.gif" },
            // DEATH (front/left/right use spaces; back uses underscore)
            { "tree monster death front.gif",           "tree_monster_death_back.gif",
              "tree monster death left.gif",             "tree monster death right.gif"         },
        };

        animSprites = new GImage[4][4];
        for (int state = 0; state < 4; state++) {
            for (int dir = 0; dir < 4; dir++) {
                GImage img = new GImage(SPRITE_DIR + names[state][dir]);
                img.setSize(72, 72); // lock size before canvas.add() so ACM imageUpdate won't reset it
                animSprites[state][dir] = img;
            }
        }

        SpriteAnimator anim = getAnimator();
        Direction[] dirs = { Direction.DOWN, Direction.UP, Direction.LEFT, Direction.RIGHT };
        for (int d = 0; d < dirs.length; d++) {
            anim.addFrames(dirs[d], Collections.singletonList(animSprites[0][d]));
        }

        // Single-GIF death (no per-frame PNGs available for tree monster)
        String[] deathFiles = {
            "tree monster death front.gif", "tree_monster_death_back.gif",
            "tree monster death left.gif",  "tree monster death right.gif"
        };
        deathFramePathsByDirection = new String[4][1];
        for (int dir = 0; dir < 4; dir++) {
            deathFramePathsByDirection[dir][0] = SPRITE_DIR + deathFiles[dir];
        }
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
