/**
 * TutorialMonster.java
 *
 * A simple, slow enemy spawned during the opening sequence in A1.
 * Designed to teach the player basic combat. Low health, slow speed,
 * telegraphed attacks — a punching bag with teeth.
 *
 * Person 1 — Engine & Sequences (spawned by OpeningSequence)
 */
public class TutorialMonster extends Enemy {

    /**
     * Creates a TutorialMonster at (x, y).
     *
     * Stats:
     *   Health  2 — dies in 2 sword hits
     *   Patrol  0 px/s — stands still until aggro
     *   Chase  60 px/s — slow, non-threatening pursuit
     *   Aggro 300 px — wide detection so it engages quickly
     *
     * @param x       center X in world pixels
     * @param y       center Y in world pixels
     * @param tileMap tile map for collision
     */
    public TutorialMonster(double x, double y, TileMap tileMap) {
        super(x, y, "assets/enemy.png", tileMap,
              2,     // maxHealth — 2 hits to kill
              0.0,   // patrolSpeed — stands still
              60.0,  // chaseSpeed — slow
              300.0  // aggroRange — wide
        );
    }

    /**
     * Contact damage when hitboxes overlap.
     * Slow cooldown (60 ticks = ~1s) to be forgiving for new players.
     */
    @Override
    protected void tryAttack(Entity target) {
        if (target == null) return;
        if (attackCooldownTicks > 0) return;

        if (hitbox.overlaps(target.getHitbox())) {
            target.takeDamage(1);
            attackCooldownTicks = 60;
        }
    }

    /**
     * Tutorial monster never drops coins — the reward is progression.
     * @return false always
     */
    @Override
    public boolean onDeath() {
        return false;
    }
}
