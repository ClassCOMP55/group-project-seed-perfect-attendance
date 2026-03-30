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
 *   Health 3 — survives 3 sword hits, feels sturdy but fair
 *   Patrol 55 px/s — slightly sluggish patrol
 *   Chase 110 px/s — faster than patrol, threatening pursuit
 *   Aggro 224 px — 3.5-tile detection radius
 *
 * Person 3 — Combat & Enemies
 */
public class MeleeEnemy extends Enemy {

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
        super(x, y, "assets/enemy_melee.png", tileMap,
              3,      // maxHealth  — 3 hits to kill
              55.0,   // patrolSpeed (px/s)
              110.0,  // chaseSpeed  (px/s) — fast pursuit
              224.0); // aggroRange  (px)   — 3.5 tiles
        this.meleeDamage = 1;
        this.meleeRange  = 48.0; // one entity-width; informational
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
     * Cooldown: 45 ticks ≈ 0.75s at 60fps.
     *
     * @param target Entity to attack (typically the Player)
     */
    @Override
    protected void tryAttack(Entity target) {
        if (target == null) return;
        if (attackCooldownTicks > 0) return;

        if (hitbox.overlaps(target.getHitbox())) {
            target.takeDamage(meleeDamage);
            attackCooldownTicks = 45;
        }
    }
}
