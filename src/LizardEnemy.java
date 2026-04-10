import acm.graphics.GImage;
import java.util.Collections;

/**
 * LizardEnemy.java
 *
 * A close-range melee enemy using lizard sprite assets.
 * Behaves like MeleeEnemy — chases the player and deals contact damage.
 *
 * Stats:
 *   Health  3 hearts (6 half-hearts)
 *   Patrol  65 px/s — slightly faster than skeleton
 *   Chase   130 px/s — aggressive pursuit
 *   Aggro   210 px  — just over 4 tiles
 *
 * Note: death animation uses the death GIF directly (no extracted PNG frames).
 * The GIF plays for ~1.8s before the entity despawns.
 */
public class LizardEnemy extends Enemy {

    // ==========================================================
    // CONSTANTS
    // ==========================================================

    private static final String SPRITE_DIR = "assets/visuals/lizard mobs/";
    /**
     * Lizard GIFs are exported as multi-frame strips on a large shared canvas instead of the
     * skeletons' normalized 64x64 canvases. We scale each full GIF so its effective 64x64
     * animation cell lands at the same on-screen size as the other enemies' 72x72 renders.
     */
    private static final double SOURCE_FRAME_SIZE = 64.0;
    private static final double TARGET_FRAME_SIZE = 72.0;
    private static final double RUN_CANVAS_WIDTH = 512.0;
    private static final double ACTION_CANVAS_WIDTH = 448.0;
    private static final double DAMAGE_CANVAS_WIDTH = 320.0;
    private static final double CANVAS_HEIGHT = 256.0;

    /** 3 hearts in half-heart units, matching the player's default. */
    private static final int MAX_HEALTH = Player.DEFAULT_HEART_COUNT * Player.HALF_HEARTS_PER_HEART;

    // ==========================================================
    // FIELDS
    // ==========================================================

    /** Damage dealt per hit when hitboxes overlap. */
    private int meleeDamage;

    // ==========================================================
    // CONSTRUCTOR
    // ==========================================================

    /**
     * Creates a LizardEnemy centered at (x, y).
     *
     * @param x       Spawn center X in world pixels
     * @param y       Spawn center Y in world pixels
     * @param tileMap Tile map for collision and line-of-sight
     */
    public LizardEnemy(double x, double y, TileMap tileMap) {
        super(x, y, SPRITE_DIR + "melee mob run front (Lizard 1) .gif", tileMap,
              MAX_HEALTH, // maxHealth  — 3 hearts in half-heart units
              65.0,       // patrolSpeed (px/s)
              130.0,      // chaseSpeed  (px/s)
              210.0);     // aggroRange  (px)
        this.meleeDamage = 1;
        loadAllSprites();
        applyRenderSizeForState(animState);
    }

    /**
     * Loads all lizard GIFs (4 states × 4 directions) into the animSprites table.
     *
     * AnimState ordinals: IDLE=0, ATTACK=1, DAMAGE=2, DEATH=3
     * Direction indices:  DOWN=0, UP=1, LEFT=2, RIGHT=3
     *
     * IDLE uses the "run" animations — the lizard is always in motion.
     * Death uses GIF paths stored as single-entry arrays (no extracted PNG frames
     * available); the animation freezes on the first frame after ~0.14s and the
     * entity despawns after ~1.85s total.
     */
    private void loadAllSprites() {
        String[][] names = {
            // IDLE (run animations)
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

        // Set initial IDLE frames in the animator
        SpriteAnimator anim = getAnimator();
        Direction[] dirs = { Direction.DOWN, Direction.UP, Direction.LEFT, Direction.RIGHT };
        for (int d = 0; d < dirs.length; d++) {
            anim.addFrames(dirs[d], Collections.singletonList(animSprites[0][d]));
        }

        // Death: store GIF paths as single-entry arrays. advanceDeathFrame() detects
        // index 0 >= length - 1 = 0 and freezes immediately, letting the looping GIF
        // play until the entity despawns (~1.85s after death).
        String[] deathDirs = { "front", "back", "left", "right" };
        deathFramePathsByDirection = new String[4][1];
        for (int dir = 0; dir < deathDirs.length; dir++) {
            deathFramePathsByDirection[dir][0] =
                SPRITE_DIR + "melee mob death " + deathDirs[dir] + " (Lizard 1) .gif";
        }
    }

    /**
     * Matches the lizard's large GIF canvas to the same effective frame scale used by the
     * skeleton enemies so the visible body size is consistent in-game.
     */
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

    // ==========================================================
    // ATTACK
    // ==========================================================

    /**
     * Deals meleeDamage to the target when hitboxes overlap and
     * the attack cooldown has expired.
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
