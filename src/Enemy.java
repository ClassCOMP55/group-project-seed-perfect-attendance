/**
 * Enemy.java
 *
 * Base enemy class. Extends Entity. Handles patrol loop (waypoint following),
 * line-of-sight aggro detection (range + wall check), and stubs for chase/attack
 * that subclasses override.
 *
 * All three enemy subtypes (MeleeEnemy, RangedEnemy, ArmorEnemy) extend this class
 * and override tryAttack() with their specific combat logic.
 *
 * update() signature:
 *   update(double dt, Entity target) — not update(double dt, Player p) — because
 *   Player does not extend Entity yet. Once Player is refactored to extend Entity,
 *   change the parameter type to Player with no other logic changes needed.
 *
 * Aggro rule: enemy aggros if target is within aggroRange (default 192px = 3 tiles)
 * AND has an unobstructed line of sight (no wall tiles between enemy and target).
 * Both conditions must be true every tick to stay in chase mode, so hiding behind
 * a wall will cause the enemy to return to its patrol.
 *
 * Person 3 — Combat & Enemies
 */
import acm.graphics.*;
import acm.util.RandomGenerator;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class Enemy extends Entity {

    // ==========================================================
    // ANIMATION STATE
    // ==========================================================

    /** Visual animation states for enemies. */
    public enum AnimState { IDLE, ATTACK, DAMAGE, DEATH }

    /** Current animation state — drives which GIF set the SpriteAnimator uses. */
    protected AnimState animState = AnimState.IDLE;

    /**
     * Countdown timer (seconds) for one-shot animations (ATTACK, DAMAGE, DEATH).
     * When this reaches 0, the enemy transitions back to IDLE (or stays dead).
     */
    protected double animTimer = 0;

    /**
     * Per-state GImage lookup: animSprites[state][direction].
     * Populated by subclass constructors (e.g. MeleeEnemy.loadAllSprites()).
     * Null entries are fine — the animator falls back to whatever is loaded.
     */
    protected GImage[][] animSprites;

    /** Duration in seconds for each one-shot animation. Set by subclass. */
    protected double attackAnimDuration = 0.5;
    // Stunlock fix: keep this below the player's attack cooldown (~0.5s / 30 ticks).
    // Lower = enemy recovers faster and gets more time to fight back between hits.
    protected double damageAnimDuration = 0.3;
    protected double deathAnimDuration  = 1.40;

    /**
     * Manual death-animation frame paths by direction.
     * Layout: deathFramePathsByDirection[dirIndex][frameIndex].
     *
     * We intentionally avoid animated GIF playback for enemy death because ACM's
     * GIF rendering can loop/restart unpredictably at runtime. By stepping through
     * extracted PNG frames ourselves, the animation can only move forward and then
     * freeze on the final "on the floor" frame.
     */
    protected String[][] deathFramePathsByDirection;

    /** The death GIF or final-frame PNG currently on canvas — managed manually. */
    private GImage deathVisualOnCanvas;

    /** True once the death GIF has been added to the canvas. */
    private boolean deathSpriteAdded = false;

    /** True once we've swapped from GIF to static PNG (freeze on floor). */
    private boolean deathFrozen = false;

    /** Direction the enemy was facing when death started. Locks the death anim orientation. */
    private Direction deathFacing = Direction.DOWN;

    /** Current frame index inside the manual death animation. */
    private int deathFrameIndex = 0;

    /** Countdown until the next manual death frame swap. */
    private double deathFrameTimer = 0;

    /** Each skeleton death frame lasts 140ms in the source animation. */
    private static final double DEATH_FRAME_DURATION = 0.14;

    /** Extra time to keep the frozen floor pose visible before despawning. */
    private static final double DEATH_FROZEN_LINGER = 0.45;

    // ==========================================================
    // CONSTANTS
    // ==========================================================

    /** Distance threshold (pixels) at which a patrol waypoint is considered "reached". */
    private static final double WAYPOINT_THRESHOLD = 8.0;

    /** One tile in pixels — used to build the default patrol square. */
    private static final double TILE = 48.0; // matches TileMap.tileSize (locked 2026-04-01)

    /** Step size (pixels) for line-of-sight ray march — half a tile. */
    private static final double LOS_STEP = 32.0;

    /** Step size for straight-line patrol validation so trigger strips cannot be skipped. */
    private static final double PATROL_SEGMENT_STEP = 12.0;

    /** Matches Entity's 48px hitbox so patrol sampling checks the full enemy body. */
    private static final double COLLISION_HALF = 24.0;

    /** Default patrol speed in pixels per second. */
    private static final double DEFAULT_PATROL_SPEED = 60.0;

    /** Default chase speed in pixels per second. */
    private static final double DEFAULT_CHASE_SPEED = 100.0;

    /** Default aggro range in pixels (3 tiles = 192px). */
    private static final double DEFAULT_AGGRO_RANGE = 192.0;

    /** Probability of dropping a coin on death (0.0–1.0). */
    private static final double COIN_DROP_CHANCE = 0.5;
    /** Enemy overlay heart size in pixels per heart cell. */
    private static final double COMBAT_OVERLAY_HEART_CELL_SIZE = 3.0;
    /** Vertical gap between the name label and the hearts. */
    private static final double COMBAT_OVERLAY_NAME_GAP = 4.0;
    /** Padding above the enemy hitbox before the overlay starts. */
    private static final double COMBAT_OVERLAY_TOP_PAD = 10.0;
    /** Clamp overlays inside the room's top edge. */
    private static final double COMBAT_OVERLAY_SCREEN_PAD = 6.0;

    /** How many ticks the post-hit blink lasts (~0.5s at 60fps). */
    private static final int HIT_FLASH_DURATION = 30;

    private static final Color COMBAT_OVERLAY_NAME_COLOR = new Color(255, 244, 214);
    private static final Color COMBAT_OVERLAY_HEART_FULL = new Color(232, 45, 34);
    private static final Color COMBAT_OVERLAY_HEART_EMPTY = new Color(108, 33, 30);
    private static final Color COMBAT_OVERLAY_HEART_BORDER = new Color(70, 18, 17);
    private static final Color COMBAT_OVERLAY_HEART_SHADOW = new Color(38, 10, 10);

    // ==========================================================
    // FIELDS
    // ==========================================================

    // ── Patrol ─────────────────────────────────────────────────────────────

    /** Ordered list of world-pixel waypoints {x, y} forming the patrol loop. */
    protected List<double[]> patrolPath;

    /** Index of the next waypoint to walk toward. */
    protected int patrolIndex;

    /** Movement speed in pixels/second while patrolling. */
    protected double patrolSpeed;

    /** Distance in pixels at which this enemy detects the target and switches to chase. */
    protected double aggroRange;

    /** Movement speed in pixels/second while chasing the target. */
    protected double chaseSpeed;

    /** True when the target is within aggroRange AND visible this tick. Re-evaluated every tick. */
    protected boolean isAggro;

    /**
     * When true, this enemy is nudged away from the player on contact (player can slightly push it).
     * When false, the player is nudged away instead — the enemy acts as a solid immovable wall.
     * Defaults to true; set to false in subclasses that should feel immovable (e.g. ArmorEnemy, Boss).
     */
    protected boolean isPushable = true;

    // ── Combat ─────────────────────────────────────────────────────────────

    /**
     * Cooldown counter in game ticks. Decremented by 1 each update() call.
     * Subclasses reset this to their attack rate (e.g. 60 = ~1s at 60fps) after attacking.
     */
    protected int attackCooldownTicks;

    /** Countdown in ticks for the post-hit blink effect. 0 = no blink active. */
    private int hitFlashTicks = 0;

    /** Floating pixel-heart display shown above the enemy during gameplay. */
    private HeartDisplay combatHeartDisplay;
    /** Enemy name shown above the hearts. */
    private GLabel combatNameLabel;

    // ── Spawn tracking ──────────────────────────────────────────────────────

    /** World-pixel X at construction — used as respawn point if enemy falls in a hole. */
    protected final double spawnX;

    /** World-pixel Y at construction — used as respawn point if enemy falls in a hole. */
    protected final double spawnY;

    // ── Shared RNG ─────────────────────────────────────────────────────────

    private static final RandomGenerator rgen = RandomGenerator.getInstance();

    // ==========================================================
    // CONSTRUCTORS
    // ==========================================================

    /**
     * Creates a basic enemy at (x, y) with default patrol speed, chase speed, and aggro range.
     * Automatically builds a 4-point square patrol path around the spawn point.
     *
     * @param x       Spawn center X in world pixels
     * @param y       Spawn center Y in world pixels
     * @param tileMap Tile map for movement collision and line-of-sight
     */
    public Enemy(double x, double y, TileMap tileMap) {
        this(x, y, "assets/enemy.png", tileMap,
             2, DEFAULT_PATROL_SPEED, DEFAULT_CHASE_SPEED, DEFAULT_AGGRO_RANGE);
    }

    /**
     * Protected constructor for subclasses to customise sprite, health, and speeds
     * without duplicating patrol setup logic. Called via super() in MeleeEnemy, etc.
     *
     * @param x           Spawn center X in world pixels
     * @param y           Spawn center Y in world pixels
     * @param spritePath  Path to the sprite asset (e.g. "assets/enemy_armor.png")
     * @param tileMap     Tile map for collision and line-of-sight
     * @param maxHealth   Starting health
     * @param patrolSpeed Movement speed while patrolling (px/s)
     * @param chaseSpeed  Movement speed while chasing (px/s)
     * @param aggroRange  Detection radius in pixels
     */
    protected Enemy(double x, double y, String spritePath, TileMap tileMap,
                    int maxHealth, double patrolSpeed, double chaseSpeed, double aggroRange) {
        // Pass patrolSpeed as Entity.speed so the inherited field is meaningful
        super(x, y, spritePath, tileMap, maxHealth, patrolSpeed);

        this.spawnX      = x;
        this.spawnY      = y;
        this.patrolSpeed = patrolSpeed;
        this.chaseSpeed  = chaseSpeed;
        this.aggroRange  = aggroRange;
        this.isAggro     = false;
        this.attackCooldownTicks = 0;
        this.patrolIndex = 0;

        buildDefaultPatrolPath();
    }

    /**
     * Enemies obey the normal tile collision plus any player-only trigger strips that the
     * room marks as blocked for enemy navigation.
     */
    @Override
    protected boolean isPassableForMovement(double px, double py) {
        if (tileMap == null) {
            return true;
        }
        if (!tileMap.containsPixel(px, py)) {
            return false;
        }
        return tileMap.isEnemyPassable(px, py);
    }

    // ==========================================================
    // PATROL PATH SETUP
    // ==========================================================

    /**
     * Builds a randomized patrol path centered on the spawn point.
     * Each enemy gets 3-6 waypoints scattered within a 1-3 tile radius,
     * so no two enemies walk the same route.
     */
    private void buildDefaultPatrolPath() {
        patrolPath = new ArrayList<>();
        int numPoints = 4 + rgen.nextInt(4); // 4 to 7 waypoints
        double radius = TILE * (4 + rgen.nextDouble() * 4); // 4-8 tiles from spawn

        for (int attempt = 0; attempt < numPoints * 5 && patrolPath.size() < numPoints; attempt++) {
            double angle = 2 * Math.PI * rgen.nextDouble();
            double dist  = radius * (0.4 + rgen.nextDouble() * 0.6);
            double wx = spawnX + Math.cos(angle) * dist;
            double wy = spawnY + Math.sin(angle) * dist;

            // Keep patrol waypoints inside the room; only the player gets to use
            // out-of-bounds space as an exit zone.
            if (!canOccupyPatrolPosition(wx, wy)) continue;
            if (!isPatrolSegmentClear(spawnX, spawnY, wx, wy)) continue;

            patrolPath.add(new double[]{ wx, wy });
        }

        // Fallback: if all waypoints were culled, stay at spawn
        if (patrolPath.isEmpty()) {
            patrolPath.add(new double[]{ spawnX, spawnY });
        }
    }

    /**
     * Replaces the patrol path with a custom set of waypoints.
     * Resets the patrol index to 0 to prevent out-of-bounds on shorter paths.
     *
     * @param path List of {x, y} waypoints — must not be null or empty
     */
    public void setPatrolPath(List<double[]> path) {
        if (path != null && !path.isEmpty()) {
            this.patrolPath = path;
            this.patrolIndex = 0;
        }
    }

    // ==========================================================
    // CORE UPDATE
    // ==========================================================

    /**
     * Main per-tick update. Orchestrates patrol / aggro / chase / attack.
     *
     * Aggro is re-evaluated every tick using both distance and line of sight:
     *   isAggro = (dist <= aggroRange) && hasLineOfSight(target)
     * This means the player can break aggro by hiding behind a wall.
     *
     * Parameter is Entity so the combat/aggro layer can target any entity subtype
     * that exposes position, hitbox, and life-state through the shared base class.
     *
     * Passing null is safe — the enemy will patrol when there is no target.
     *
     * @param dt     Delta-time in seconds (e.g. 0.016 at ~60fps)
     * @param target The entity to chase/attack (typically the Player)
     */
    public void update(double dt, Entity target) {

        // 1. Tick down attack cooldown
        if (attackCooldownTicks > 0) {
            attackCooldownTicks--;
        }

        // Death is a terminal state: keep advancing the death visual / timer and
        // never fall back into patrol, chase, or normal animation logic.
        if (animState == AnimState.DEATH) {
            if (animTimer > 0) {
                animTimer -= dt;
            }
            tickDeathAnimation(dt);
            return;
        }

        // 2. Tick down hit-blink counter every tick (including during stagger)
        if (hitFlashTicks > 0) hitFlashTicks--;

        // 3. Tick down one-shot animation timers (DAMAGE, DEATH, ATTACK)
        if (animTimer > 0) {
            animTimer -= dt;
            if (animTimer <= 0) {
                setAnimState(AnimState.IDLE);
            } else if (animState == AnimState.DAMAGE) {
                applyAnimVisual(); // keep syncing the damage GIF to facing
                return; // stagger — skip movement while hurt
            }
        }

        // 4. Re-evaluate aggro every tick:
        //    Requires target in range AND unobstructed line of sight.
        if (target != null) {
            double dist = distanceTo(target);
            isAggro = (dist <= aggroRange) && hasLineOfSight(target);
        } else {
            isAggro = false;
        }

        // 5. Chase + attack if aggro; otherwise patrol
        boolean targetAlive = target != null && target.isAlive();
        if (isAggro && targetAlive) {
            chase(dt, target);
            tryAttack(target);
        } else {
            if (target != null && !targetAlive) {
                isAggro = false;
                if (animState == AnimState.ATTACK) {
                    setAnimState(AnimState.IDLE);
                    animTimer = 0;
                }
            }
            patrol(dt);
        }

        // 6. Hole-fall respawn: reset to spawn point, sync hitbox and sprite manually
        if (isOverHole()) {
            x = spawnX;
            y = spawnY;
            hitbox.updatePosition(x - 24, y - 24);
            sprite.setLocation(x - 24, y - 24);
        }

        // 7. Sync visual — pick the right GIF for (animState, facing) every tick
        applyAnimVisual();
    }

    // ==========================================================
    // PATROL
    // ==========================================================

    /**
     * Advances the enemy along its patrol path one tick.
     *
     * Each tick: get the vector toward patrolPath[patrolIndex]. If distance is within
     * WAYPOINT_THRESHOLD, advance the index (wrapping). Otherwise, normalize and
     * call move() with the patrol-speed-scaled delta.
     *
     * Entity.move() handles tile collision, hitbox/sprite sync, and facing update.
     *
     * @param dt Delta-time in seconds
     */
    protected void patrol(double dt) {
        if (patrolPath == null || patrolPath.isEmpty()) return;

        double[] wp   = patrolPath.get(patrolIndex);
        if (!isPatrolSegmentClear(x, y, wp[0], wp[1])) {
            pickReachablePatrolWaypoint(x, y);
            return;
        }

        double   dx   = wp[0] - x;
        double   dy   = wp[1] - y;
        double   dist = Math.sqrt(dx * dx + dy * dy);

        if (dist <= WAYPOINT_THRESHOLD) {
            pickReachablePatrolWaypoint(x, y);
            return;
        }

        // Normalize to unit vector, scale by patrolSpeed * dt
        double scale = patrolSpeed * dt / dist;
        move(dx * scale, dy * scale);
    }

    /** Returns true when the enemy's full 48x48 hitbox can stand at the given center point. */
    private boolean canOccupyPatrolPosition(double centerX, double centerY) {
        return isPassableForMovement(centerX - COLLISION_HALF + 1, centerY - COLLISION_HALF + 1)
            && isPassableForMovement(centerX + COLLISION_HALF - 1, centerY - COLLISION_HALF + 1)
            && isPassableForMovement(centerX - COLLISION_HALF + 1, centerY + COLLISION_HALF - 1)
            && isPassableForMovement(centerX + COLLISION_HALF - 1, centerY + COLLISION_HALF - 1);
    }

    /**
     * Samples the straight segment between two patrol points using the enemy hitbox so a
     * waypoint jump cannot cut through a blocked strip or wall tile.
     */
    private boolean isPatrolSegmentClear(double startX, double startY, double endX, double endY) {
        double dx = endX - startX;
        double dy = endY - startY;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < 1.0) {
            return canOccupyPatrolPosition(endX, endY);
        }

        int steps = Math.max(1, (int) Math.ceil(dist / PATROL_SEGMENT_STEP));
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            double px = startX + dx * t;
            double py = startY + dy * t;
            if (!canOccupyPatrolPosition(px, py)) {
                return false;
            }
        }
        return true;
    }

    /** Chooses a random patrol waypoint that is reachable by a clear straight segment. */
    private boolean pickReachablePatrolWaypoint(double startX, double startY) {
        if (patrolPath == null || patrolPath.isEmpty()) {
            return false;
        }

        List<Integer> reachable = new ArrayList<>();
        for (int i = 0; i < patrolPath.size(); i++) {
            double[] candidate = patrolPath.get(i);
            double dx = candidate[0] - startX;
            double dy = candidate[1] - startY;
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist <= WAYPOINT_THRESHOLD) {
                continue;
            }
            if (isPatrolSegmentClear(startX, startY, candidate[0], candidate[1])) {
                reachable.add(i);
            }
        }

        if (reachable.isEmpty()) {
            return false;
        }

        patrolIndex = reachable.get(rgen.nextInt(reachable.size()));
        return true;
    }

    // ==========================================================
    // CHASE
    // ==========================================================

    /**
     * Moves directly toward the target's center at chaseSpeed.
     *
     * @param dt     Delta-time in seconds
     * @param target Entity to pursue
     */
    protected void chase(double dt, Entity target) {
        double dx   = target.getX() - x;
        double dy   = target.getY() - y;
        double dist = Math.sqrt(dx * dx + dy * dy);

        // Guard: prevent divide-by-zero if centers overlap (can occur in testing)
        if (dist < 1.0) return;

        double scale = chaseSpeed * dt / dist;
        move(dx * scale, dy * scale);
    }

    // ==========================================================
    // ATTACK
    // ==========================================================

    /**
     * Attempts to attack the target. Base implementation is a no-op.
     * Subclasses override with their specific combat behavior.
     *
     * Typical override pattern:
     *   if (attackCooldownTicks <= 0 && target.getHitbox().overlaps(hitbox)) {
     *       target.takeDamage(damage);
     *       attackCooldownTicks = 60;   // ~1s cooldown at 60fps
     *   }
     *
     * TODO: MeleeEnemy — contact damage when hitbox overlaps player
     * TODO: RangedEnemy — spawn Projectile toward player on cooldown
     * TODO: ArmorEnemy  — contact damage; only vulnerable to hits from behind
     *
     * @param target Entity to attack
     */
    protected void tryAttack(Entity target) {
        // no-op in base Enemy — subclasses override
    }

    // ==========================================================
    // SHARED VISUAL HELPERS
    // ==========================================================

    /**
     * Loads the normalized skeleton sprite sheet used by the current enemy variants.
     * This keeps prototype enemies visible even before bespoke art lands.
     */
    protected void loadSkeletonAnimations(String spriteDir, String spritePrefix) {
        String[][] names = {
            { "-idle-front.gif",  "-idle-back.gif",  "-idle-left.gif",  "-idle-right.gif"  },
            { "-attack-front.gif","-attack-back.gif","-attack-left.gif","-attack-right.gif" },
            { "-damage-front.gif","-damage-back.gif","-damage-left.gif","-damage-right.gif" },
            { "-death-front.gif", "-death-back.gif", "-death-left.gif", "-death-right.gif"  },
        };

        animSprites = new GImage[4][4];
        for (int state = 0; state < names.length; state++) {
            for (int dir = 0; dir < names[state].length; dir++) {
                animSprites[state][dir] = new GImage(spriteDir + spritePrefix + names[state][dir]);
            }
        }

        SpriteAnimator anim = getAnimator();
        Direction[] dirs = { Direction.DOWN, Direction.UP, Direction.LEFT, Direction.RIGHT };
        for (int i = 0; i < dirs.length; i++) {
            anim.addFrames(dirs[i], java.util.Collections.singletonList(animSprites[0][i]));
        }
        anim.setFallbackFrame(animSprites[0][0]);

        String[] deathDirs = { "front", "back", "left", "right" };
        deathFramePathsByDirection = new String[4][10];
        for (int dir = 0; dir < deathDirs.length; dir++) {
            for (int frame = 0; frame < 10; frame++) {
                deathFramePathsByDirection[dir][frame] =
                    spriteDir + spritePrefix + "-death-" + deathDirs[dir] + "-frame-" + frame + ".png";
            }
        }
    }

    // ==========================================================
    // ANIMATION STATE MANAGEMENT
    // ==========================================================

    /**
     * Transitions to a new animation state. The actual GIF swap happens in
     * applyAnimVisual(), which runs every tick — same pattern as Player's
     * applyDirectionalVisual().
     */
    protected void setAnimState(AnimState state) {
        if (state == animState) return;
        animState = state;
    }

    /**
     * Resolves the current (animState, facing) to a GImage and pushes it into
     * both the animator's frame list AND fallback frame. Called every tick at the
     * end of update() to seamlessly blend between idle/walk/attack/damage/death.
     *
     * Must update the frame list (not just the fallback) because getCurrentFrame()
     * checks framesByDirection first and only falls back when the list is empty.
     */
    protected void applyAnimVisual() {
        if (animSprites == null) return;
        int stateIdx = animState.ordinal();
        int dirIdx = dirToIndex(facing);
        GImage visual = animSprites[stateIdx][dirIdx];
        if (visual == null) {
            visual = animSprites[stateIdx][0]; // fallback to DOWN
        }
        if (visual != null) {
            SpriteAnimator anim = getAnimator();
            anim.addFrames(facing, java.util.Collections.singletonList(visual));
            anim.setFallbackFrame(visual);
            syncVisualPosition();
        }
    }

    /** Maps Direction enum to the animSprites column index. */
    private int dirToIndex(Direction d) {
        if (d == null) return 0;
        switch (d) {
            case DOWN:  return 0;
            case UP:    return 1;
            case LEFT:  return 2;
            case RIGHT: return 3;
            default:    return 0;
        }
    }

    /**
     * Override takeDamage to trigger the DAMAGE animation.
     * The enemy staggers (skips movement) for the duration of the hurt GIF.
     */
    @Override
    public void takeDamage(int amount) {
        // Dying enemies linger briefly for their death animation, but they should
        // stop accepting hits immediately so repeated attacks cannot restart despawn.
        if (animState == AnimState.DEATH || health <= 0) {
            return;
        }

        super.takeDamage(amount);
        if (health <= 0) {
            setAnimState(AnimState.DEATH);
            animTimer = deathAnimDuration + DEATH_FROZEN_LINGER;
            triggerDeathAnimation();
        } else if (animState != AnimState.DEATH) {
            // Stunlock fix: only enter/reset the hurt animation if not already in it.
            // This prevents rapid hits from extending the freeze time indefinitely.
            // The enemy still takes full damage — they just recover on their original schedule.
            if (animState != AnimState.DAMAGE) {
                setAnimState(AnimState.DAMAGE);
                animTimer = damageAnimDuration;
            }
            hitFlashTicks = HIT_FLASH_DURATION;
        }
    }

    /**
     * Enemy stays "alive" for room cleanup purposes while its death sequence is
     * still playing or lingering on the floor. Once the death timer expires,
     * Room removes it and triggers any drop logic.
     */
    @Override
    public boolean isAlive() {
        if (health > 0) return true;
        return animState == AnimState.DEATH && animTimer > 0;
    }

    // ==========================================================
    // DEATH — manual sprite management (bypasses animator to avoid GIF looping)
    // ==========================================================

    /**
     * Sets up the manual death animation. Creates a fresh GImage from the
     * death GIF path and clears the animator so Entity.draw() won't interfere.
     */
    private void triggerDeathAnimation() {
        deathFrozen = false;
        deathSpriteAdded = false;
        deathFacing = (facing != null) ? facing : Direction.DOWN;
        deathFrameIndex = 0;
        deathFrameTimer = DEATH_FRAME_DURATION;
        deathVisualOnCanvas = buildDeathVisual(0);
        getAnimator().clearFrames();
    }

    /**
     * Called every tick while dying. Counts down the GIF play time,
     * then swaps to the static final-frame PNG.
     */
    protected void tickDeathAnimation(double dt) {
        if (deathFrozen) return;
        deathFrameTimer -= dt;
        while (deathFrameTimer <= 0 && !deathFrozen) {
            deathFrameTimer += DEATH_FRAME_DURATION;
            advanceDeathFrame();
        }
    }

    /**
     * Advances to the next extracted death frame. Once the final frame is reached,
     * the enemy stays frozen on the floor pose until despawn.
     */
    private void advanceDeathFrame() {
        String[] framePaths = getDeathFramePaths();
        if (framePaths == null || framePaths.length == 0) {
            deathFrozen = true;
            return;
        }
        if (deathFrameIndex >= framePaths.length - 1) {
            deathFrozen = true;
            return;
        }

        deathFrameIndex++;
        GImage previousVisual = deathVisualOnCanvas;
        deathVisualOnCanvas = buildDeathVisual(deathFrameIndex);
        if (previousVisual != null && previousVisual.getParent() != null) {
            ((GCanvas) previousVisual.getParent()).remove(previousVisual);
        }

        if (deathFrameIndex >= framePaths.length - 1) {
            deathFrozen = true;
        }
    }

    /**
     * Returns the extracted death-frame path list for the direction locked at death time.
     */
    private String[] getDeathFramePaths() {
        if (deathFramePathsByDirection == null || deathFramePathsByDirection.length == 0) {
            return null;
        }
        int dirIdx = dirToIndex(deathFacing);
        String[] paths = deathFramePathsByDirection[dirIdx];
        if (paths == null || paths.length == 0) {
            paths = deathFramePathsByDirection[0];
        }
        return paths;
    }

    /**
     * Builds a fresh death visual for a specific extracted frame and centers it.
     */
    private GImage buildDeathVisual(int frameIndex) {
        String[] paths = getDeathFramePaths();
        if (paths == null || paths.length == 0) return null;
        int clampedIndex = Math.max(0, Math.min(frameIndex, paths.length - 1));
        String path = paths[clampedIndex];
        if (path == null) return null;

        GImage visual = new GImage(path);
        visual.setLocation(x - visual.getWidth() / 2.0, y - visual.getHeight() / 2.0);
        return visual;
    }

    /**
     * Keeps the death visual centered without calling setSize() on the GIF.
     */
    private void syncDeathVisualPosition() {
        if (deathVisualOnCanvas == null) return;
        deathVisualOnCanvas.setLocation(
            x - deathVisualOnCanvas.getWidth() / 2.0,
            y - deathVisualOnCanvas.getHeight() / 2.0
        );
    }

    /**
     * During death: completely bypasses Entity.draw() to manage the GIF/PNG
     * manually on the canvas. This prevents the animator and syncVisualPosition
     * from calling setSize() on the GIF (which restarts/corrupts it in ACM).
     */
    @Override
    public void draw(GCanvas canvas) {
        if (animState == AnimState.DEATH) {
            removeCombatOverlay();
            if (deathVisualOnCanvas == null) return;

            if (!deathSpriteAdded) {
                super.removeSpriteFromCanvas(canvas);
                deathSpriteAdded = true;
            }
            syncDeathVisualPosition();
            if (deathVisualOnCanvas.getParent() == null) {
                canvas.add(deathVisualOnCanvas);
            }
            return;
        }
        boolean showSprite = (hitFlashTicks == 0) || (hitFlashTicks % 4 >= 2);
        applyHitFlashVisibility(showSprite);
        super.draw(canvas);
        updateCombatOverlay(canvas);
    }

    private void applyHitFlashVisibility(boolean visible) {
        if (sprite != null) sprite.setVisible(visible);
        GImage frame = getAnimator().getCurrentFrame();
        if (frame != null) frame.setVisible(visible);
    }

    @Override
    public void panVisual(double panX, double panY) {
        super.panVisual(panX, panY);
        if (combatHeartDisplay != null) combatHeartDisplay.moveBy(panX, panY);
        if (combatNameLabel != null) combatNameLabel.move(panX, panY);
    }

    @Override
    public void removeSpriteFromCanvas(GCanvas canvas) {
        removeCombatOverlay();
        super.removeSpriteFromCanvas(canvas);
        if (deathVisualOnCanvas != null) {
            canvas.remove(deathVisualOnCanvas);
            deathVisualOnCanvas = null;
        }
        deathSpriteAdded = false;
        deathFrozen = false;
        deathFacing = Direction.DOWN;
        deathFrameIndex = 0;
        deathFrameTimer = 0;
    }

    /**
     * Called by the Room when isAlive() first returns false.
     * Returns true if a Coin should be spawned at this enemy's last position.
     *
     * @return true ~50% of the time (COIN_DROP_CHANCE)
     */
    public boolean onDeath() {
        return rgen.nextDouble() < COIN_DROP_CHANCE;
    }

    // ==========================================================
    // COMBAT OVERLAY
    // ==========================================================

    /**
     * Returns the label shown above this enemy in the combat overlay.
     * Defaults to a spaced version of the class name, e.g. "Melee Enemy".
     */
    public String getDisplayName() {
        String simpleName = getClass().getSimpleName();
        if (simpleName == null || simpleName.isEmpty()) {
            return "Enemy";
        }
        StringBuilder spaced = new StringBuilder();
        for (int i = 0; i < simpleName.length(); i++) {
            char ch = simpleName.charAt(i);
            if (i > 0 && Character.isUpperCase(ch) && Character.isLowerCase(simpleName.charAt(i - 1))) {
                spaced.append(' ');
            }
            spaced.append(ch);
        }
        return spaced.toString();
    }

    /** Converts half-heart max health into the number of visible hearts to render. */
    private int getCombatOverlayHeartCount() {
        int unitsPerHeart = Math.max(1, Player.HALF_HEARTS_PER_HEART);
        return Math.max(1, (maxHealth + unitsPerHeart - 1) / unitsPerHeart);
    }

    /** Creates, positions, and updates the name + heart cluster. */
    private void updateCombatOverlay(GCanvas canvas) {
        if (canvas == null || health <= 0) {
            removeCombatOverlay();
            return;
        }

        if (combatNameLabel == null) {
            combatNameLabel = new GLabel(getDisplayName());
            combatNameLabel.setFont("Courier New-BOLD-10");
            combatNameLabel.setColor(COMBAT_OVERLAY_NAME_COLOR);
        }
        if (combatHeartDisplay == null) {
            combatHeartDisplay = new HeartDisplay(getCombatOverlayHeartCount(), COMBAT_OVERLAY_HEART_CELL_SIZE);
            combatHeartDisplay.setColors(
                COMBAT_OVERLAY_HEART_FULL,
                COMBAT_OVERLAY_HEART_EMPTY,
                COMBAT_OVERLAY_HEART_BORDER
            );
            combatHeartDisplay.setShadowColor(COMBAT_OVERLAY_HEART_SHADOW);
            combatHeartDisplay.show(canvas, 0, 0);
        }

        if (combatNameLabel.getParent() == null) {
            canvas.add(combatNameLabel);
        }

        combatHeartDisplay.setFilledHalfHearts(health);

        double heartsWidth = combatHeartDisplay.getWidth();
        double heartsHeight = combatHeartDisplay.getHeight();
        double labelHeight = combatNameLabel.getAscent() + combatNameLabel.getDescent();
        double overlayWidth = Math.max(heartsWidth, combatNameLabel.getWidth());
        double overlayCenterX = x;

        if (tileMap != null) {
            double minCenterX = TileMap.MAP_OFFSET_X + overlayWidth / 2.0 + 2.0;
            double maxCenterX = TileMap.MAP_OFFSET_X + tileMap.getWidthPixels() - overlayWidth / 2.0 - 2.0;
            if (minCenterX <= maxCenterX) {
                overlayCenterX = Math.max(minCenterX, Math.min(maxCenterX, overlayCenterX));
            }
        }

        double totalHeight = labelHeight + COMBAT_OVERLAY_NAME_GAP + heartsHeight;
        double topY = Math.max(COMBAT_OVERLAY_SCREEN_PAD, hitbox.y - COMBAT_OVERLAY_TOP_PAD - totalHeight);
        double nameBaselineY = topY + combatNameLabel.getAscent();
        combatNameLabel.setLocation(overlayCenterX - combatNameLabel.getWidth() / 2.0, nameBaselineY);

        double heartsX = overlayCenterX - heartsWidth / 2.0;
        double heartsY = nameBaselineY + combatNameLabel.getDescent() + COMBAT_OVERLAY_NAME_GAP;
        combatHeartDisplay.setPosition(heartsX, heartsY);

        sendCombatOverlayToFront();
    }

    /** Removes every gameplay overlay object associated with this enemy. */
    private void removeCombatOverlay() {
        if (combatHeartDisplay != null) {
            combatHeartDisplay.remove();
            combatHeartDisplay = null;
        }
        removeFromParent(combatNameLabel);
        combatNameLabel = null;
    }

    /** Small helper so overlay teardown works without requiring a canvas parameter. */
    private void removeFromParent(GObject obj) {
        if (obj == null || obj.getParent() == null) {
            return;
        }
        if (obj.getParent() instanceof GCanvas) {
            ((GCanvas) obj.getParent()).remove(obj);
        }
    }

    /** Keeps the overlay above the enemy sprite after draw() swaps animation frames. */
    private void sendCombatOverlayToFront() {
        if (combatHeartDisplay != null) combatHeartDisplay.bringToFront();
        if (combatNameLabel != null) combatNameLabel.sendToFront();
    }

    // ==========================================================
    // LINE-OF-SIGHT
    // ==========================================================

    /**
     * Returns true if there are no wall tiles between this enemy and the target.
     *
     * Algorithm: march from enemy center toward target center in LOS_STEP (32px) increments.
     * At each step, call tileMap.isPassable(). If any point is not passable, a wall
     * is blocking the view → return false. If all points are passable → return true.
     *
     * 32px steps (half a tile) ensure the ray cannot skip over a single-tile-wide wall:
     * a 64px wall will always be hit by at least one step point.
     *
     * @param target The entity to test visibility toward
     * @return true if the path from this enemy's center to the target's center is clear
     */
    private boolean hasLineOfSight(Entity target) {
        double dx   = target.getX() - x;
        double dy   = target.getY() - y;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < 1.0) return true; // trivially visible if overlapping

        // Unit step vector scaled to LOS_STEP pixels
        double stepX = (dx / dist) * LOS_STEP;
        double stepY = (dy / dist) * LOS_STEP;

        // Number of full LOS_STEP increments that fit before reaching the target
        int steps = (int)(dist / LOS_STEP);

        for (int i = 1; i <= steps; i++) {
            double px = x + stepX * i;
            double py = y + stepY * i;
            if (!tileMap.isPassable(px, py)) {
                return false; // wall tile found — line of sight is blocked
            }
        }

        return true; // all intermediate points are passable
    }

    // ==========================================================
    // HELPERS
    // ==========================================================

    /**
     * Euclidean distance from this enemy's center to another entity's center.
     *
     * @param other Target entity
     * @return distance in pixels
     */
    private double distanceTo(Entity other) {
        double dx = other.getX() - this.x;
        double dy = other.getY() - this.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    // ==========================================================
    // GETTERS
    // ==========================================================

    /** @return true if this enemy is currently in chase/attack mode */
    public boolean isAggro() { return isAggro; }

    /** @return true if the player can push this enemy; false if this enemy acts as a solid wall */
    public boolean isPushable() { return isPushable; }

    /** @return ticks remaining on attack cooldown (0 = ready to attack) */
    public int getAttackCooldown() { return attackCooldownTicks; }

    /** @return current animation state (IDLE, ATTACK, DAMAGE, DEATH) */
    public AnimState getAnimState() { return animState; }

    /** @return aggro radius in pixels */
    public double getAggroRange() { return aggroRange; }

    /** @return patrol speed in pixels per second */
    public double getPatrolSpeed() { return patrolSpeed; }

    /** @return chase speed in pixels per second */
    public double getChaseSpeed() { return chaseSpeed; }

    /** @return the patrol waypoint list (for debug overlay) */
    public java.util.List<double[]> getPatrolPath() { return patrolPath; }

    /** @return index of the current target waypoint */
    public int getPatrolIndex() { return patrolIndex; }
}
