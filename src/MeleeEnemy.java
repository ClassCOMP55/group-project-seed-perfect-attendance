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

    private static final String SPRITE_DIR = "assets/visuals/lizard mobs/";

    // Lizard GIFs are multi-frame strips on large shared canvases — must scale the
    // whole canvas so each 64px source frame lands at TARGET_FRAME_SIZE on screen.
    private static final double SOURCE_FRAME_SIZE    = 64.0;
    private static final double TARGET_FRAME_SIZE    = 72.0;
    private static final double RUN_CANVAS_WIDTH     = 512.0;
    private static final double ACTION_CANVAS_WIDTH  = 448.0;
    private static final double DAMAGE_CANVAS_WIDTH  = 320.0;
    private static final double CANVAS_HEIGHT        = 256.0;

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
        super(x, y, SPRITE_DIR + "melee mob run front (Lizard 1) .gif", tileMap,
              MAX_HEALTH,
              55.0,   // patrolSpeed (px/s)
              110.0,  // chaseSpeed  (px/s)
              224.0); // aggroRange  (px)
        this.meleeDamage = 1;
        this.meleeRange  = 48.0;
        loadAllSprites();
        applyRenderSizeForState(animState);
    }

    private void applyRenderSizeForState(AnimState state) {
        double canvasWidth = RUN_CANVAS_WIDTH;
        if (state == AnimState.ATTACK || state == AnimState.DEATH) {
            canvasWidth = ACTION_CANVAS_WIDTH;
        } else if (state == AnimState.DAMAGE) {
            canvasWidth = DAMAGE_CANVAS_WIDTH;
        }
        double scale = TARGET_FRAME_SIZE / SOURCE_FRAME_SIZE;
        setSpriteRenderSize(canvasWidth * scale, CANVAS_HEIGHT * scale);
    }

    @Override
    protected void setAnimState(AnimState state) {
        super.setAnimState(state);
        applyRenderSizeForState(state);
    }

    private void loadAllSprites() {
        // All Lizard 1 filenames have a trailing space before the extension (e.g. "... (Lizard 1) .gif")
        String[][] names = {
            // IDLE — no separate idle; reuse run sprites
            { "melee mob run front (Lizard 1) .gif",    "melee mob run back (Lizard 1) .gif",
              "melee mob run left (Lizard 1) .gif",     "melee mob run right (Lizard 1) .gif"   },
            // ATTACK
            { "melee mob attack front (Lizard 1) .gif", "melee mob attack back (Lizard 1) .gif",
              "melee mob attack left (Lizard 1) .gif",  "melee mob attack right (Lizard 1) .gif" },
            // DAMAGE
            { "melee mob damage front (Lizard 1) .gif", "melee mob damage back (Lizard 1) .gif",
              "melee mob damage left (Lizard 1) .gif",  "melee mob damage right (Lizard 1) .gif" },
            // DEATH
            { "melee mob death front (Lizard 1) .gif",  "melee mob death back (Lizard 1) .gif",
              "melee mob death left (Lizard 1) .gif",   "melee mob death right (Lizard 1) .gif"  },
        };

        animSprites = new GImage[4][4];
        for (int state = 0; state < 4; state++) {
            for (int dir = 0; dir < 4; dir++) {
                animSprites[state][dir] = new GImage(SPRITE_DIR + names[state][dir]);
            }
        }

        SpriteAnimator anim = getAnimator();
        Direction[] dirs = { Direction.DOWN, Direction.UP, Direction.LEFT, Direction.RIGHT };
        for (int d = 0; d < dirs.length; d++) {
            anim.addFrames(dirs[d], Collections.singletonList(animSprites[0][d]));
        }

        // Single-GIF death (no per-frame PNGs for lizard)
        String[] deathFiles = {
            "melee mob death front (Lizard 1) .gif", "melee mob death back (Lizard 1) .gif",
            "melee mob death left (Lizard 1) .gif",  "melee mob death right (Lizard 1) .gif"
        };
        deathFramePathsByDirection = new String[4][1];
        for (int dir = 0; dir < 4; dir++) {
            deathFramePathsByDirection[dir][0] = SPRITE_DIR + deathFiles[dir];
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

        if (hurtbox.overlapsHurtbox(target.getHurtbox())) {
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
