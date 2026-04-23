import acm.graphics.GImage;
import java.util.Collections;

/**
 * ArmorEnemy.java
 *
 * An armored enemy that always faces the player and can only be damaged
 * by a sword hit from behind. Contact with this enemy damages the player.
 *
 * Behavior:
 *   Patrol:  walks a 4-point square loop around spawn (inherited from Enemy).
 *   Chase:   moves directly toward player (inherited from Enemy).
 *   Facing:  slowly rotates toward the player in discrete 90-degree steps.
 *            Turn rate is intentionally limited so players can flank.
 *   Attack:  no swing animation — deals contactDamage when hitbox overlaps
 *            the player's hitbox, on a 30-tick (~0.5s) cooldown.
 *   Defense: only frontal swings are blocked ({@code hitFrom == facing.opposite()}).
 *            Back and side hits deal damage.
 *
 * Stats (heavy creeping threat):
 *   Health  5 hearts — tank; requires positional play to defeat
 *   Patrol 28 px/s — very slow patrol
 *   Chase  46 px/s — slow pursuit (pressure, not chase-down)
 *   Aggro 320 px — broad awareness ring
 *
 * Only appears in rooms B2 (Ore Location) and C2 (Forest).
 *
 * Person 3 — Combat & Enemies
 */
public class ArmorEnemy extends Enemy {

    private static final String SPRITE_DIR = "assets/visuals/mushroom mob/";
    private static final int MAX_HEALTH = 3 * Player.HALF_HEARTS_PER_HEART;
    private static final double PATROL_SPEED = 28.0;
    private static final double CHASE_SPEED = 30.0;
    private static final double AGGRO_RANGE = 320.0;
    /** How many ticks must pass before the armor can rotate one 90-degree step. Larger = slower turns (easier to flank). */
    private static final int TURN_INTERVAL_TICKS = 90;

    // ==========================================================
    // FIELDS
    // ==========================================================

    /** Damage dealt to the player when hitboxes overlap. */
    private int contactDamage;
    /** Countdown until this enemy is allowed to rotate again. */
    private int turnCooldownTicks;
    /** When true, the next {@link #setFacing} call is allowed through. */
    private boolean facingUnlocked;

    // ==========================================================
    // CONSTRUCTOR
    // ==========================================================

    /**
     * Creates an ArmorEnemy centered at (x, y).
     *
     * @param x       Spawn center X in world pixels
     * @param y       Spawn center Y in world pixels
     * @param tileMap Tile map for collision and line-of-sight
     */
    public ArmorEnemy(double x, double y, TileMap tileMap) {
        super(x, y, SPRITE_DIR + "mushroom run foward.gif", tileMap,
              MAX_HEALTH,
              PATROL_SPEED,
              CHASE_SPEED,
              AGGRO_RANGE);
        this.contactDamage = 1;
        this.turnCooldownTicks = 0;
        this.isPushable = false;
        loadAllSprites();
        setSpriteRenderSize(114, 114);
    }

    private void loadAllSprites() {
        // Mushroom asset filenames have typos preserved exactly as on disk
        String[][] names = {
            // IDLE — no separate idle; reuse run sprites
            { "mushroom run foward.gif",          "mushroom run back.gif",
              "mushroom run left.gif",              "mushroom run right.gif"           },
            // ATTACK (front filename: "attck" not "attack")
            { "mushroom attck front.gif",           "mushroom attack back.gif",
              "mushroom attack left.gif",            "mushroom attack right.gif"        },
            // DAMAGE
            { "mushroom taking damage front.gif",   "mushroom taking damage back.gif",
              "mushroom taking damage left.gif",     "mushroom taking damage right.gif" },
            // DEATH — front is a PNG; rest are GIFs
            { "mushroom death front.png",            "mushroom death back.gif",
              "mushroom death left.gif",              "mushroom death right.gif"         },
        };

        animSprites = new GImage[4][4];
        for (int state = 0; state < 4; state++) {
            for (int dir = 0; dir < 4; dir++) {
                GImage img = new GImage(SPRITE_DIR + names[state][dir]);
                img.setSize(114, 114); // pre-size before canvas.add() so ACM imageUpdate won't reset
                animSprites[state][dir] = img;
            }
        }

        SpriteAnimator anim = getAnimator();
        Direction[] dirs = { Direction.DOWN, Direction.UP, Direction.LEFT, Direction.RIGHT };
        for (int d = 0; d < dirs.length; d++) {
            anim.addFrames(dirs[d], java.util.Collections.singletonList(animSprites[0][d]));
        }

        // Single-file death animations (mushroom has no per-frame PNGs)
        String[] deathFiles = {
            "mushroom death front.png", "mushroom death back.gif",
            "mushroom death left.gif",  "mushroom death right.gif"
        };
        deathFramePathsByDirection = new String[4][1];
        for (int dir = 0; dir < 4; dir++) {
            deathFramePathsByDirection[dir][0] = SPRITE_DIR + deathFiles[dir];
        }
    }

    // ==========================================================
    // UPDATE OVERRIDE — slow-turn toward the player
    // ==========================================================

    /**
     * Runs normal AI (patrol/chase/attack) then adjusts facing toward the player
     * only when the turn cooldown allows.
     *
     * Ordering is critical:
     *   1. super.update() runs patrol/chase/tryAttack. Internally, move() sets
     *      facing from the movement direction.
     *   2. AFTER super.update() returns, we rotate one step toward the target
     *      if the cooldown has elapsed.
     *
     * If target is null (no player reference yet), the enemy behaves exactly
     * like a base Enemy — patrol only, facing from movement direction.
     *
     * @param dt     Delta-time in seconds
     * @param target Entity to face and pursue (typically the Player)
     */
    /** Ticks until the next ambient noise plays. Randomized so enemies don't sync up. */
    private int noiseCooldownTicks = 60 + (int)(Math.random() * 180);

    @Override
    public void update(double dt, Entity target) {
        super.update(dt, target); // patrol / chase / tryAttack / hole check

        if (turnCooldownTicks > 0) {
            turnCooldownTicks--;
        }

        if (target != null && health > 0 && turnCooldownTicks <= 0) {
            double dx = target.getX() - x;
            double dy = target.getY() - y;
            Direction toward = Direction.fromDelta(dx, dy);
            if (toward != null) {
                facingUnlocked = true;
                setFacing(stepTurnToward(getFacing(), toward));
                facingUnlocked = false;
                turnCooldownTicks = TURN_INTERVAL_TICKS;
            }
        }

        if (animState != AnimState.DEATH && --noiseCooldownTicks <= 0) {
            GameSFX.play(GameSFX.SFX.MUSHROOM_NOISE);
            noiseCooldownTicks = 180 + (int)(Math.random() * 120); // 3–5s between sounds
        }
    }

    @Override
    public boolean onDeath() {
        GameSFX.play(GameSFX.SFX.MUSHROOM_DEATH);
        return super.onDeath();
    }

    /**
     * Gates facing changes so {@code Entity.move()} cannot snap facing every frame.
     * Only the controlled step-turn above (which sets {@link #facingUnlocked}) can rotate.
     */
    @Override
    public void setFacing(Direction d) {
        if (facingUnlocked) {
            super.setFacing(d);
        }
    }

    /**
     * Rotates at most one 90-degree step toward the desired direction.
     * This makes facing changes readable and prevents instant spin-locking.
     */
    private Direction stepTurnToward(Direction current, Direction desired) {
        if (desired == null) return current;
        if (current == null || current == desired) return desired;

        switch (current) {
            case UP:
                return desired == Direction.LEFT ? Direction.LEFT : Direction.RIGHT;
            case DOWN:
                return desired == Direction.LEFT ? Direction.LEFT : Direction.RIGHT;
            case LEFT:
                return desired == Direction.UP ? Direction.UP : Direction.DOWN;
            case RIGHT:
                return desired == Direction.UP ? Direction.UP : Direction.DOWN;
            default:
                return desired;
        }
    }

    // ==========================================================
    // CONTACT DAMAGE — tryAttack override
    // ==========================================================

    /**
     * Deals contactDamage to the target when hitboxes overlap.
     * No swing or animation — the armor itself is the weapon.
     *
     * Cooldown: 30 ticks ≈ 0.5s at 60fps (shorter than MeleeEnemy since
     * contact damage should feel persistent when the player stays close).
     *
     * Note: target.takeDamage(contactDamage) uses the plain int overload.
     * This is intentional — the player has no directional armor, so no
     * Direction is needed on the outgoing hit.
     *
     * @param target Entity to deal contact damage to (typically the Player)
     */
    @Override
    protected void tryAttack(Entity target) {
        if (target == null) return;
        if (attackCooldownTicks > 0) return;

        if (hitbox.overlapsHurtbox(target.getHurtbox())) {
            target.takeDamage(contactDamage);
            GameSFX.play(GameSFX.SFX.ENEMY_ATTACK);
            attackCooldownTicks = 30;
        }
    }

    // ==========================================================
    // DIRECTIONAL ARMOR — takeDamage overrides
    // ==========================================================

    /**
     * Blocks all undirected damage. Any source that calls takeDamage(amount)
     * without specifying a direction is absorbed by the armor with no effect.
     *
     * This override is necessary because Entity.takeDamage(int, Direction)
     * delegates to takeDamage(int) by default — without this override,
     * undirected sources (future traps, area effects, spells) would bypass
     * the armor by falling through to the base Entity implementation.
     *
     * @param amount Damage amount — ignored, armor absorbs all undirected hits
     */
    @Override
    public void takeDamage(int amount) {
        // Armor blocks all undirected damage — no-op
    }

    /**
     * Applies damage for any sword hit that is not purely frontal.
     *
     * Frontal = swing direction matches the direction the enemy is facing toward
     * ({@code hitFrom == facing.opposite()}). Those hits clang off the shield.
     * Back hits ({@code hitFrom == facing}) and side hits (perpendicular) deal damage.
     *
     * @param amount  damage to apply when not blocked
     * @param hitFrom direction of the sword swing (player attack facing)
     */
    @Override
    public void takeDamage(int amount, Direction hitFrom) {
        if (hitFrom == null) return;
        Direction front = facing == null ? null : facing.opposite();
        if (hitFrom == front) {
            return; // frontal — blocked
        }
        super.takeDamage(amount);
        turnCooldownTicks = TURN_INTERVAL_TICKS;
    }
}
