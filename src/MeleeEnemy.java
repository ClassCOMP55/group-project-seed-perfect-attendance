import acm.graphics.GImage;
import java.util.Collections;

/**
 * MeleeEnemy.java
 *
 * A close-range enemy that chases the player and deals damage on contact.
 * Vulnerable from all directions — no directional armor.
 *
 * Behavior:
 *   Patrol:  walks a 4-point square loop around spawn (inherited from Enemy).
 *   Chase:   moves directly toward player at chaseSpeed (inherited from Enemy).
 *   Attack:  deals meleeDamage when hitboxes overlap, on a 45-tick (~0.75s) cooldown.
 *
 * Stats (Zelda-feel):
 *   Health 3 hearts — survives 6 sword hits at half-heart damage per swing
 *   Patrol 55 px/s — slightly sluggish patrol
 *   Chase 110 px/s — faster than patrol, threatening pursuit
 *   Aggro 224 px — 3.5-tile detection radius
 *
 * Person 3 — Combat & Enemies
 */
public class MeleeEnemy extends Enemy {

    // ==========================================================
    // CONSTANTS — sprite asset paths
    // ==========================================================

    private static final String SPRITE_DIR = "assets/visuals/skeley-mob-1/normalized/";
    /** Matches the player's 3-heart scale in half-heart health units. */
    private static final int MAX_HEALTH = Player.DEFAULT_HEART_COUNT * Player.HALF_HEARTS_PER_HEART;

    // ==========================================================
    // FIELDS
    // ==========================================================

    /** Damage dealt per hit when hitboxes overlap. */
    private int meleeDamage;

    /**
     * Nominal melee range in pixels — informational for now.
     * Proximity is checked via hitbox.overlaps() rather than distance.
     * Retained for future use (e.g. a lunge hitbox that extends beyond the entity).
     */
    private double meleeRange;

    // ==========================================================
    // CONSTRUCTOR
    // ==========================================================

    /**
     * Creates a MeleeEnemy centered at (x, y).
     *
     * @param x       Spawn center X in world pixels
     * @param y       Spawn center Y in world pixels
     * @param tileMap Tile map for collision and line-of-sight
     */
    public MeleeEnemy(double x, double y, TileMap tileMap) {
        super(x, y, SPRITE_DIR + "skeley-mob-1-idle-front.gif", tileMap,
              MAX_HEALTH, // maxHealth  — 3 hearts in half-heart units
              55.0,   // patrolSpeed (px/s)
              110.0,  // chaseSpeed  (px/s) — fast pursuit
              224.0); // aggroRange  (px)   — 3.5 tiles
        this.meleeDamage = 1;
        this.meleeRange  = 48.0; // one entity-width; informational
        loadAllSprites();

        // Normalized sprites are 64x64 (cropped from padded originals).
        // Render at 72x72 so the skeleton is slightly larger than the 48x48 player.
        // Content fills ~70% of the 64px canvas, so visible skeleton ≈ 50x55 px.
        // Tweak this single value to scale enemies up/down.
        setSpriteRenderSize(72, 72);
    }

    /**
     * Loads all 16 skeleton GIFs (4 states × 4 directions) into the animSprites
     * lookup table and sets the initial IDLE frames in the SpriteAnimator.
     *
     * AnimState ordinals: IDLE=0, ATTACK=1, DAMAGE=2, DEATH=3
     * Direction indices:  DOWN=0, UP=1, LEFT=2, RIGHT=3
     */
    private void loadAllSprites() {
        String[][] names = {
            // IDLE
            { "skeley-mob-1-idle-front.gif",   "skeley-mob-1-idle-back.gif",
              "skeley-mob-1-idle-left.gif",     "skeley-mob-1-idle-right.gif"   },
            // ATTACK
            { "skeley-mob-1-attack-front.gif",  "skeley-mob-1-attack-back.gif",
              "skeley-mob-1-attack-left.gif",    "skeley-mob-1-attack-right.gif" },
            // DAMAGE
            { "skeley-mob-1-damage-front.gif",  "skeley-mob-1-damage-back.gif",
              "skeley-mob-1-damage-left.gif",    "skeley-mob-1-damage-right.gif" },
            // DEATH
            { "skeley-mob-1-death-front.gif",   "skeley-mob-1-death-back.gif",
              "skeley-mob-1-death-left.gif",     "skeley-mob-1-death-right.gif"  },
        };

        animSprites = new GImage[4][4];
        for (int state = 0; state < 4; state++) {
            for (int dir = 0; dir < 4; dir++) {
                animSprites[state][dir] = new GImage(SPRITE_DIR + names[state][dir]);
            }
        }

        // Set initial IDLE frames in the animator
        SpriteAnimator anim = getAnimator();
        Direction[] dirs = { Direction.DOWN, Direction.UP, Direction.LEFT, Direction.RIGHT };
        for (int d = 0; d < dirs.length; d++) {
            anim.addFrames(dirs[d], Collections.singletonList(animSprites[0][d]));
        }

        // Runtime death uses extracted PNG frames instead of the animated GIF,
        // so the sequence cannot loop back to a standing pose.
        String[] deathDirs = { "front", "back", "left", "right" };
        deathFramePathsByDirection = new String[4][10];
        for (int dir = 0; dir < deathDirs.length; dir++) {
            for (int frame = 0; frame < 10; frame++) {
                deathFramePathsByDirection[dir][frame] =
                    SPRITE_DIR + "skeley-mob-1-death-" + deathDirs[dir] + "-frame-" + frame + ".png";
            }
        }
    }

    // ==========================================================
    // ATTACK
    // ==========================================================

    /**
     * Deals meleeDamage to the target when hitboxes overlap and
     * the attack cooldown has expired.
     *
     * Uses hitbox.overlaps() so the attack fires exactly when bodies
     * are touching — consistent with the contact-damage feel of classic
     * Zelda slimes and bats.
     *
     * Cooldown: 180 ticks ≈ 3s at 60fps.
     *
     * @param target Entity to attack (typically the Player)
     */
    @Override
    protected void tryAttack(Entity target) {
        if (target == null) return;
        if (attackCooldownTicks > 0) return;
        if (animState == AnimState.DAMAGE || animState == AnimState.DEATH) return;

        if (hitbox.overlaps(target.getHitbox())) {
            target.takeDamage(meleeDamage);
            GameSFX.play(GameSFX.SFX.ENEMY_ATTACK);
            attackCooldownTicks = 180; // ~3s at 60fps
            if (animState != AnimState.DEATH) {
                setAnimState(AnimState.ATTACK);
                animTimer = attackAnimDuration;
            }
        }
    }
}
