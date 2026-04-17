/**
 * Boss.java
 *
 * Two-phase final boss in Dungeon Room 3. Unlike every other enemy in the game,
 * the Boss is not limited to one attack type — it combines melee contact damage,
 * ranged projectiles, and a phase-2 charge-lunge. Think of it as having learned
 * from every enemy the player has already fought.
 *
 * Phase 1 (full health → half health):
 *   - Walks slowly toward the player.
 *   - Fires 1 Projectile toward the player every ~3 seconds.
 *   - Deals contact damage when hitboxes overlap.
 *   - Vulnerable from all directions (no armor).
 *
 * Phase 2 (half health → death):
 *   - Movement speed increases.
 *   - Fires a spread of 3 Projectiles instead of 1, every ~1.5 seconds.
 *   - Occasionally performs a charge-lunge:
 *       0.5s telegraph pause (flash) → bursts toward player at high speed.
 *   - Contact damage persists.
 *
 * Relics interact naturally:
 *   - Reflect: bounces projectiles back at the Boss.
 *   - Half-Damage: halves contact and projectile damage received.
 *   - Intangible: dodges the lunge burst.
 *
 * On death: stubs a CutscenePlayer trigger (class does not exist yet).
 *
 * Person 3 — Combat & Enemies
 */
import acm.graphics.*;
import java.util.List;
import java.util.ArrayList;

public class Boss extends Enemy {

    // ==========================================================
    // CONSTANTS
    // ==========================================================

    /** Phase 1 walk speed in pixels/second. */
    private static final double PHASE1_SPEED = 60.0;

    /** Phase 2 walk speed in pixels/second — noticeably faster. */
    private static final double PHASE2_SPEED = 110.0;

    /** Phase 1 shot interval in ticks (~3s at 60fps). */
    private static final int PHASE1_FIRE_RATE = 180;

    /** Phase 2 shot interval in ticks (~1.5s at 60fps). */
    private static final int PHASE2_FIRE_RATE = 90;

    /** Ticks the lunge telegraph (pause + flash) lasts (~0.5s). */
    private static final int LUNGE_WINDUP = 30;

    /** Speed of the lunge burst in pixels/second. */
    private static final double LUNGE_SPEED = 400.0;

    /** How many ticks the lunge burst travels before stopping. */
    private static final int LUNGE_DURATION = 15;

    /** Ticks between lunge attempts (~5s cooldown). */
    private static final int LUNGE_COOLDOWN = 300;

    /** Melee contact cooldown in ticks (~0.5s). */
    private static final int MELEE_COOLDOWN = 30;

    /**
     * Aggro range covers the entire room (1280px = full screen width).
     * Boss immediately aggros when the player enters Room 3.
     */
    private static final double BOSS_AGGRO_RANGE = 1280.0;

    // ==========================================================
    // FIELDS
    // ==========================================================

    // ── Phase ──────────────────────────────────────────────────

    /** Current phase: 1 or 2. Flips at half health. */
    private int phase;

    /** Health threshold at which phase 2 activates (maxHealth / 2). */
    private int phaseFlipThreshold;

    // ── Ranged attack ──────────────────────────────────────────

    /**
     * Active projectile list. Set by Room via setProjectileList() at spawn time.
     * Null until wired — tryAttack() guards against null so compilation is safe.
     *
     * TODO [Room]: call boss.setProjectileList(roomProjectiles) when spawning Boss.
     */
    private List<Projectile> projectiles;

    /** Ticks between projectile shots. Halved when phase 2 activates. */
    private int fireRate;

    // ── Melee contact ──────────────────────────────────────────

    /** Damage dealt on hitbox overlap. Separate cooldown from ranged. */
    private int contactDamage;

    /** Separate cooldown for melee contact so it doesn't share with ranged. */
    private int meleeCooldownTicks;

    // ── Lunge (phase 2) ────────────────────────────────────────

    /** True while the lunge sequence (windup or burst) is in progress. */
    private boolean isLunging;

    /** Counts down during the telegraph pause before the burst fires. */
    private int lungeWindupTimer;

    /** Counts down during the lunge burst itself. */
    private int lungeDurationTimer;

    /** Ticks remaining before the next lunge is allowed. */
    private int lungeCooldownTicks;

    // ==========================================================
    // CONSTRUCTOR
    // ==========================================================

    /**
     * Creates the Boss centered at (x, y).
     *
     * Stats:
     *   Health      10  — requires sustained combat to defeat
     *   Phase speed  60 → 110 px/s on phase flip
     *   Aggro      1280 px — covers the entire room; aggros on entry
     *
     * @param x       Spawn center X in world pixels
     * @param y       Spawn center Y in world pixels
     * @param tileMap Tile map for collision and line-of-sight
     */
    public Boss(double x, double y, TileMap tileMap) {
        super(x, y, "assets/boss.png", tileMap,
              10,               // maxHealth
              PHASE1_SPEED,     // patrolSpeed  (unused — Boss doesn't patrol)
              PHASE1_SPEED,     // chaseSpeed   (updated on phase flip)
              BOSS_AGGRO_RANGE  // aggroRange   (full-room detection)
        );

        this.phase             = 1;
        this.phaseFlipThreshold = maxHealth / 2; // 5
        this.fireRate          = PHASE1_FIRE_RATE;
        this.contactDamage     = 1;
        this.isPushable        = false;
        this.meleeCooldownTicks = 0;
        this.isLunging         = false;
        this.lungeWindupTimer  = 0;
        this.lungeDurationTimer = 0;
        this.lungeCooldownTicks = 0;
        this.projectiles       = null; // wired by Room via setProjectileList()
    }

    // ==========================================================
    // PROJECTILE LIST WIRING
    // ==========================================================

    /**
     * Wires in the Room's active projectile list so Boss can spawn Projectiles.
     * Call this immediately after constructing the Boss in the Room.
     *
     * @param list The Room's shared active projectile list
     */
    public void setProjectileList(List<Projectile> list) {
        this.projectiles = list;
    }

    // ==========================================================
    // CORE UPDATE
    // ==========================================================

    /**
     * Main per-tick update. Boss does NOT patrol — it immediately pursues
     * the player and uses all attack types each tick.
     *
     * Flow each tick:
     *   1. Decrement cooldown timers.
     *   2. Check phase flip.
     *   3. If lunging: handle lunge sequence (windup or burst).
     *      Else: chase player + try all attacks.
     *   4. Always face the player (same as ArmorEnemy pattern).
     *
     * Parameter is Entity (not Player) because Player does not extend
     * Entity yet. Will Change the parameter type to Player once Player is refactored —
     * no other logic changes needed.
     *
     * @param dt     Delta-time in seconds (~0.016 at 60fps)
     * @param target The player entity to fight
     */
    @Override
    public void update(double dt, Entity target) {
        // 1. Tick down all cooldowns
        if (attackCooldownTicks > 0) attackCooldownTicks--;
        if (meleeCooldownTicks  > 0) meleeCooldownTicks--;
        if (lungeCooldownTicks  > 0) lungeCooldownTicks--;

        // 2. Check for phase transition
        checkPhaseFlip();

        if (target != null && isAlive()) {
            if (isLunging) {
                // 3a. Lunge sequence takes priority over normal attacks
                doLunge(dt, target);
            } else {
                // 3b. Normal AI: chase + all attack types
                chase(dt, target);
                tryAttack(target);
            }

            // 4. Always face the player (overwrite movement-derived facing)
            double dx = target.getX() - x;
            double dy = target.getY() - y;
            Direction toward = Direction.fromDelta(dx, dy);
            if (toward != null) setFacing(toward);
        }
    }

    // ==========================================================
    // PHASE MANAGEMENT
    // ==========================================================

    /**
     * Flips to phase 2 when health drops to or below half.
     * Increases movement speed and doubles fire rate.
     * Only fires once — guarded by phase == 1 check.
     */
    private void checkPhaseFlip() {
        if (phase == 1 && health <= phaseFlipThreshold) {
            phase      = 2;
            chaseSpeed = PHASE2_SPEED;
            fireRate   = PHASE2_FIRE_RATE;
            // TODO [GRAPHICS]: flash sprite to signal phase transition to the player
            // # rig — play GameSFX.SFX.BOSS_PHASE_FLIP here once a phase-transition sound is added to the catalog
        }
    }

    // ==========================================================
    // COMBINED ATTACK
    // ==========================================================

    /**
     * Runs all attack types each tick — Boss combines melee AND ranged.
     *
     * Melee: contact damage whenever hitboxes overlap (own cooldown).
     * Ranged: fires 1 projectile (phase 1) or 3-spread (phase 2) on fireRate cooldown.
     * Lunge:  initiates windup if phase 2 and lunge cooldown is ready.
     *
     * @param target Entity to attack (typically the Player)
     */
    @Override
    protected void tryAttack(Entity target) {
        if (target == null) return;

        // ── Melee contact (own cooldown, always checked) ───────
        if (meleeCooldownTicks <= 0 && hitbox.overlapsHurtbox(target.getHurtbox())) {
            target.takeDamage(contactDamage);
            GameSFX.play(GameSFX.SFX.ENEMY_ATTACK);
            meleeCooldownTicks = MELEE_COOLDOWN;
        }

        // ── Ranged projectile fire (shared attackCooldownTicks) ─
        if (attackCooldownTicks <= 0) {
            if (phase == 1) {
                fireProjectile(target);
            } else {
                fireSpread(target); // 3-shot spread in phase 2
            }
            attackCooldownTicks = fireRate;
        }

        // ── Lunge trigger (phase 2 only, own cooldown) ─────────
        if (phase == 2 && lungeCooldownTicks <= 0 && !isLunging) {
            startLungeWindup();
        }
    }

    // ==========================================================
    // RANGED — single shot
    // ==========================================================

    /**
     * Fires one Projectile directly toward the target.
     * No-ops if the projectile list hasn't been wired by Room yet.
     *
     * @param target Entity to aim at
     */
    private void fireProjectile(Entity target) {
        if (projectiles == null) return;
        projectiles.add(new Projectile(x, y, target.getX(), target.getY(), tileMap, this));
    }

    // ==========================================================
    // RANGED — 3-shot spread (phase 2)
    // ==========================================================

    /**
     * Fires 3 Projectiles: one aimed directly at the target, and two offset
     * perpendicular to the aim vector to create a spread pattern.
     *
     * The perpendicular offset is half the distance to the target, giving a
     * spread angle that widens as the player gets farther away. Because
     * Projectile snaps direction to the nearest cardinal, two shots may share
     * a direction when close — still creates a "burst" feel.
     *
     * @param target Entity to aim the spread at
     */
    private void fireSpread(Entity target) {
        if (projectiles == null) return;

        double dx   = target.getX() - x;
        double dy   = target.getY() - y;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < 1.0) {
            // Degenerate case: just fire one straight shot
            fireProjectile(target);
            return;
        }

        // Perpendicular unit vector (rotate 90°): (-dy/dist, dx/dist)
        // Scale by half the distance for a reasonable spread width
        double perpX = (-dy / dist) * (dist * 0.5);
        double perpY = ( dx / dist) * (dist * 0.5);

        // Center shot — aimed directly at target
        projectiles.add(new Projectile(x, y,
            target.getX(), target.getY(), tileMap, this));

        // Left spread shot
        projectiles.add(new Projectile(x, y,
            target.getX() + perpX, target.getY() + perpY, tileMap, this));

        // Right spread shot
        projectiles.add(new Projectile(x, y,
            target.getX() - perpX, target.getY() - perpY, tileMap, this));
    }

    // ==========================================================
    // LUNGE — windup + burst
    // ==========================================================

    /**
     * Starts the lunge telegraph: freezes the Boss for LUNGE_WINDUP ticks
     * so the player has time to react.
     *
     * TODO [GRAPHICS]: flash the Boss sprite during windup to telegraph the lunge.
     */
    private void startLungeWindup() {
        isLunging        = true;
        lungeWindupTimer  = LUNGE_WINDUP;
        lungeDurationTimer = LUNGE_DURATION;
        // TODO [GRAPHICS]: begin sprite flash here
        // # rig — play GameSFX.SFX.BOSS_LUNGE_WINDUP here once a lunge-telegraph sound is added to the catalog
    }

    /**
     * Handles the lunge sequence each tick while isLunging is true.
     *
     * Two stages:
     *   Windup: stands still for LUNGE_WINDUP ticks (telegraph pause).
     *   Burst:  moves at LUNGE_SPEED toward the target for LUNGE_DURATION ticks.
     *
     * Lunge ends when the burst timer runs out or the Boss reaches the target.
     * After lunge: lungeCooldownTicks reset so it can't spam.
     *
     * @param dt     Delta-time in seconds
     * @param target Entity to lunge toward
     */
    private void doLunge(double dt, Entity target) {
        if (lungeWindupTimer > 0) {
            // Still in telegraph pause — stand still
            lungeWindupTimer--;
            return;
        }

        // Burst phase: move fast toward target
        double dx   = target.getX() - x;
        double dy   = target.getY() - y;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (lungeDurationTimer <= 0 || dist < 1.0) {
            // Lunge complete
            isLunging          = false;
            lungeCooldownTicks  = LUNGE_COOLDOWN;
            return;
        }

        // Move at lunge speed toward target
        double scale = LUNGE_SPEED * dt / dist;
        move(dx * scale, dy * scale);
        lungeDurationTimer--;
    }

    // ==========================================================
    // DEATH
    // ==========================================================

    /**
     * Called by Room when isAlive() first returns false.
     * Boss drops no coin — victory is its own reward.
     *
     * TODO [CutscenePlayer]: replace stub below with the real ending trigger:
     *   CutscenePlayer.play(endingFrames);
     * CutscenePlayer does not exist yet (Person 1, Task 19).
     *
     * @return false — Boss never drops a coin
     */
    @Override
    public boolean onDeath() {
        // TODO [Task 19 — CutscenePlayer]: trigger ending cutscene here:
        //   CutscenePlayer.play(endingFrames);
        System.out.println("TODO: Boss defeated — trigger CutscenePlayer ending sequence");
        // # rig — play GameSFX.SFX.BOSS_DEATH here once a boss-death sound is added to the catalog
        return false; // no coin drop
    }

    // ==========================================================
    // GETTERS
    // ==========================================================

    /** @return current phase (1 or 2) */
    public int getPhase() { return phase; }

    /** @return true while the Boss is in a lunge sequence */
    public boolean isLunging() { return isLunging; }
}
