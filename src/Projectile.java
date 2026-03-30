/**
 * Projectile.java
 *
 * A moving projectile fired by RangedEnemy and Boss. Travels in a fixed
 * cardinal direction until it hits a wall or a target. Can be reflected
 * back at enemies by the player's SwordSwing if they have the Reflect relic.
 *
 * Extends Entity for position, hitbox, draw(), and tile-aware move().
 * The hitbox is overridden to 16x16 (smaller than the standard 48x48)
 * so dodging feels fair. Note: Entity.move() still probes using 48px
 * geometry internally — the wall-stop margin is slightly conservative
 * as a result, which is acceptable.
 *
 * Lifecycle:
 *   - Created by RangedEnemy (or Boss) at their position, aimed at the player.
 *   - Each tick: moves in direction, self-destructs if blocked by a wall.
 *   - Room calls checkHit(player, true) and checkHit(enemy, false) each tick.
 *   - If SwordSwing overlaps it while canReflect is true: reflect() reverses it.
 *   - isAlive() == false → Room removes from active list.
 *
 * NOTE: checkHit(target, targetIsPlayer=true) requires Player to extend Entity
 * and expose getHitbox(). Player.java does not yet extend Entity — that path
 * is a forward stub that will work once Player is refactored.
 *
 * Person 3 — Combat & Enemies
 */
import acm.graphics.*;

public class Projectile extends Entity {

    // ==========================================================
    // CONSTANTS
    // ==========================================================

    /** Projectile hitbox size in pixels — smaller than entities (48px) for fair dodging. */
    private static final int PROJ_HITBOX_SIZE = 16;
    private static final int PROJ_HITBOX_HALF = 8;  // 16 / 2

    /** Sprite size matches hitbox. Resize the GImage asset here if needed. */
    private static final int PROJ_SPRITE_SIZE = 16;
    private static final int PROJ_SPRITE_HALF = 8;  // 16 / 2

    // ==========================================================
    // FIELDS
    // ==========================================================

    /**
     * Cardinal direction this projectile is traveling.
     * Determined at construction via Direction.fromDelta(); reversed by reflect().
     */
    private Direction direction;

    /**
     * True once reflect() has been called.
     * Drives targeting in checkHit():
     *   false → hits Player only
     *   true  → hits Enemies only
     */
    private boolean isReflected;

    /**
     * The entity that fired this projectile. Stored for future use
     * (e.g. damage attribution, Boss phase logic). Not used in hit
     * detection — isReflected drives targeting instead.
     */
    private Entity owner;

    // ==========================================================
    // CONSTRUCTOR
    // ==========================================================

    /**
     * Creates a Projectile fired from (startX, startY) toward (toX, toY).
     *
     * The travel direction is snapped to the nearest cardinal direction
     * (the dominant axis of the start-to-target vector). Speed is 200 px/s.
     *
     * The hitbox is replaced from the default 48x48 to 16x16 after super()
     * so dodging is possible. The sprite is also resized to 16x16.
     *
     * @param startX  Spawn center X in world pixels (typically the enemy's x)
     * @param startY  Spawn center Y in world pixels (typically the enemy's y)
     * @param toX     Target center X to aim toward (typically the player's x)
     * @param toY     Target center Y to aim toward (typically the player's y)
     * @param tileMap Tile map for wall collision
     * @param owner   The entity that fired this projectile
     */
    public Projectile(double startX, double startY,
                      double toX, double toY,
                      TileMap tileMap, Entity owner) {
        // health=1 (alive = not yet hit), speed=200px/s
        super(startX, startY, "assets/projectile.png", tileMap, 1, 200.0);

        // Snap travel direction to dominant cardinal axis
        this.direction  = Direction.fromDelta(toX - startX, toY - startY);
        this.owner      = owner;
        this.isReflected = false;

        // Replace Entity's default 48x48 hitbox with a smaller 16x16 hitbox.
        // hitbox is protected in Entity — direct reassignment is valid here.
        this.hitbox = new Hitbox(
            startX - PROJ_HITBOX_HALF,
            startY - PROJ_HITBOX_HALF,
            PROJ_HITBOX_SIZE,
            PROJ_HITBOX_SIZE
        );

        // Resize the sprite to match the smaller hitbox visual.
        // sprite is protected in Entity — setSize/setLocation on the same GImage object.
        this.sprite.setSize(PROJ_SPRITE_SIZE, PROJ_SPRITE_SIZE);
        this.sprite.setLocation(startX - PROJ_SPRITE_HALF, startY - PROJ_SPRITE_HALF);
    }

    // ==========================================================
    // UPDATE — movement and wall detection
    // ==========================================================

    /**
     * Moves the projectile one tick in its travel direction.
     * Destroys the projectile if it is blocked by a wall.
     *
     * Entity.move() stops movement at walls without flagging anything.
     * Wall detection: if position is unchanged after a non-zero move
     * attempt, move() was fully blocked → self-destruct via takeDamage(health).
     *
     * Holes are intentionally ignored — projectiles fly over them.
     *
     * @param dt Delta-time in seconds (~0.016 at 60fps)
     */
    @Override
    public void update(double dt) {
        double[] delta = direction.toDelta();  // unit vector: e.g. RIGHT → {1, 0}
        double   dx    = delta[0] * speed * dt; // e.g. 200 * 0.016 = 3.2 px
        double   dy    = delta[1] * speed * dt;

        // Snapshot position before attempting move
        double prevX = x;
        double prevY = y;

        move(dx, dy); // Entity.move() handles tile collision; stops at walls silently

        // If position is completely unchanged despite a non-zero delta → wall hit
        if (x == prevX && y == prevY && (dx != 0 || dy != 0)) {
            takeDamage(health); // health=1 → sets health=0 → isAlive() = false
        }

        // Sync the small 16x16 hitbox to the new center position
        // (Entity.move() syncs the inherited 48px hitbox ref, but we replaced it)
        hitbox.updatePosition(x - PROJ_HITBOX_HALF, y - PROJ_HITBOX_HALF);
    }

    // ==========================================================
    // HIT DETECTION
    // ==========================================================

    /**
     * Tests whether this projectile has hit the given target and applies damage.
     *
     * Targeting rules based on isReflected:
     *   isReflected = false → only damages Player  (pass targetIsPlayer = true)
     *   isReflected = true  → only damages Enemies (pass targetIsPlayer = false)
     *
     * Returns true if a hit was registered so the caller (Room game loop) can
     * immediately remove the projectile from the active list:
     *   if (proj.checkHit(enemy, false)) iter.remove();
     *
     * NOTE: targetIsPlayer = true path requires Player to extend Entity and
     * expose getHitbox(). This is a forward stub — it will work once Player
     * is refactored to extend Entity. No changes to this method will be needed.
     *
     * @param target         Entity to test (Player or Enemy, passed as Entity)
     * @param targetIsPlayer true if target is the Player; false if Enemy
     * @return true if damage was dealt (projectile also self-destructs)
     */
    public boolean checkHit(Entity target, boolean targetIsPlayer) {
        if (target == null || !target.isAlive() || !this.isAlive()) return false;

        // Gate: unreflected → hits Player; reflected → hits Enemy
        boolean shouldCheck = (targetIsPlayer && !isReflected)
                           || (!targetIsPlayer && isReflected);
        if (!shouldCheck) return false;

        if (hitbox.overlaps(target.getHitbox())) {
            target.takeDamage(1);
            takeDamage(health); // self-destruct on contact
            return true;
        }
        return false;
    }

    // ==========================================================
    // REFLECT
    // ==========================================================

    /**
     * Reverses the projectile's travel direction and marks it as reflected.
     *
     * Called by SwordSwing.update() when the player has the Reflect relic
     * and the swing hitbox overlaps this projectile.
     *
     * After reflection:
     *   - Travels in the opposite cardinal direction
     *   - checkHit() now targets Enemies instead of the Player
     *
     * Calling reflect() a second time (e.g. an enemy swings back — future mechanic)
     * would re-reverse direction but isReflected stays true, so it still targets
     * enemies. Add isReflected = !isReflected if two-bounce targeting is ever needed.
     */
    public void reflect() {
        direction   = direction.opposite();
        isReflected = true;
    }

    // ==========================================================
    // GETTERS
    // ==========================================================

    /** @return current travel direction */
    public Direction getDirection() { return direction; }

    /** @return true if this projectile has been reflected by a SwordSwing */
    public boolean isReflected() { return isReflected; }

    /** @return the entity that originally fired this projectile */
    public Entity getOwner() { return owner; }
}
