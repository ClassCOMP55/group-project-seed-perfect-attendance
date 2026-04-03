/**
 * ArmorEnemy.java
 *
 * An armored enemy that always faces the player and can only be damaged
 * by a sword hit from behind. Contact with this enemy damages the player.
 *
 * Behavior:
 *   Patrol:  walks a 4-point square loop around spawn (inherited from Enemy).
 *   Chase:   moves directly toward player (inherited from Enemy).
 *   Facing:  always rotated toward the player — facing is overwritten each tick
 *            AFTER normal AI runs, so it never shows its back voluntarily.
 *   Attack:  no swing animation — deals contactDamage when hitbox overlaps
 *            the player's hitbox, on a 30-tick (~0.5s) cooldown.
 *   Defense: immune to all damage EXCEPT a sword hit from behind.
 *
 * "From behind" definition:
 *   A hit is from behind when the sword's facing direction equals this enemy's
 *   current facing direction. Example:
 *     Enemy faces RIGHT (back is on the LEFT side).
 *     Player is to the LEFT, swings RIGHT → SwordSwing.facing = RIGHT.
 *     RIGHT == RIGHT → hit lands from behind → damage applied. ✓
 *
 * Stats (Zelda-feel):
 *   Health  5 hearts — tank; requires positional play to defeat
 *   Patrol 40 px/s — slow, heavy patrol
 *   Chase  75 px/s — sluggish pursuit; player can outmaneuver
 *   Aggro 192 px — 3-tile detection radius (default)
 *
 * Only appears in rooms B2 (Ore Location) and C2 (Forest).
 *
 * Person 3 — Combat & Enemies
 */
public class ArmorEnemy extends Enemy {

    private static final String SPRITE_DIR = "assets/visuals/skeley-mob-1/normalized/";
    private static final String SPRITE_PREFIX = "skeley-mob-1";
    private static final int MAX_HEALTH = 5 * Player.HALF_HEARTS_PER_HEART;

    // ==========================================================
    // FIELDS
    // ==========================================================

    /** Damage dealt to the player when hitboxes overlap. */
    private int contactDamage;

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
        super(x, y, SPRITE_DIR + SPRITE_PREFIX + "-idle-front.gif", tileMap,
              MAX_HEALTH, // maxHealth   — 5 full hearts in half-heart units
              40.0,   // patrolSpeed (px/s) — slow, deliberate patrol
              75.0,   // chaseSpeed  (px/s) — heavy, outmaneuverable
              192.0); // aggroRange  (px)   — 3 tiles
        this.contactDamage = 1;
        loadSkeletonAnimations(SPRITE_DIR, SPRITE_PREFIX);
        setSpriteRenderSize(72, 72);
    }

    // ==========================================================
    // UPDATE OVERRIDE — always face the player
    // ==========================================================

    /**
     * Runs normal AI (patrol/chase/attack) then overwrites the facing direction
     * to always point toward the player.
     *
     * Ordering is critical:
     *   1. super.update() runs patrol/chase/tryAttack. Internally, move() sets
     *      facing from the movement direction.
     *   2. AFTER super.update() returns, we overwrite facing to point at the
     *      target — this is the "never shows its back" design requirement.
     *
     * If target is null (no player reference yet), the enemy behaves exactly
     * like a base Enemy — patrol only, facing from movement direction.
     *
     * @param dt     Delta-time in seconds
     * @param target Entity to face and pursue (typically the Player)
     */
    @Override
    public void update(double dt, Entity target) {
        super.update(dt, target); // patrol / chase / tryAttack / hole check

        // Overwrite the movement-derived facing to always look at the player
        if (target != null && health > 0) {
            double dx = target.getX() - x;
            double dy = target.getY() - y;
            Direction toward = Direction.fromDelta(dx, dy);
            if (toward != null) {
                setFacing(toward);
            }
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

        if (hitbox.overlaps(target.getHitbox())) {
            target.takeDamage(contactDamage);
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
     * Applies damage only when the hit arrives from behind the armor.
     *
     * "From behind" = the attacker's facing direction equals this enemy's
     * current facing direction at the moment of impact.
     *
     * Proof:
     *   Enemy faces RIGHT → back is on the LEFT.
     *   Player to the LEFT swings RIGHT → SwordSwing.facing = RIGHT.
     *   hitFrom (RIGHT) == this.facing (RIGHT) → damage applied. ✓
     *
     *   Enemy faces RIGHT, player attacks from the RIGHT (front):
     *   Player faces LEFT → SwordSwing.facing = LEFT.
     *   hitFrom (LEFT) != this.facing (RIGHT) → armor blocks. ✓
     *
     * Behind hits intentionally route through Enemy.takeDamage(int) so the
     * normal hurt/death animation flow still runs.
     *
     * @param amount  Damage to apply if the hit is from behind
     * @param hitFrom Direction the hit arrived from (the attacker's facing direction)
     */
    @Override
    public void takeDamage(int amount, Direction hitFrom) {
        if (hitFrom == null) return; // defensive null guard
        if (hitFrom == this.facing) {
            // Hit landed from behind — apply normal enemy damage flow/animations.
            super.takeDamage(amount);
        }
        // Otherwise: frontal or side hit — armor absorbs it
    }
}
