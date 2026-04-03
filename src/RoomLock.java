/*
Person 2: RoomLock — prevents a room's north exit until all enemies are defeated
Who RIGs it: Room — calls roomLock.update(entities) each tick during PLAYING state.
             WorldMap — attaches one to Dungeon Room D1 during initRooms().
               When RoomLock unlocks, it calls WorldMap.openExit("D1", Direction.UP) via a callback.

Extends: nothing (small helper, not a WorldObject — it has no visual and is not placed in the world)

===============
PLAN OF ACTION
===============

- CLASS ROLE
- RoomLock is a pure logic helper — it has no sprite, no hitbox, no visual presence.
- It watches a list of enemies each tick and unlocks when they are all dead.
- Used ONLY in Dungeon Room D1 (combat room). No other room in the game uses this.
- When unlocked, it fires a callback so WorldMap can open the north exit.

- FIELDS
- boolean locked              — true while enemies remain; false when all are dead
- Runnable onUnlockCallback   — called once when the lock first opens (WorldMap wires this)

- LOCK / UNLOCK BEHAVIOR
- Lock starts LOCKED when D1 is entered.
- Each tick, update(entities) counts how many enemies in the list are still alive.
- When count reaches 0 AND locked == true: set locked = false, fire onUnlockCallback.
- The callback fires ONCE — guard with the locked flag so it does not re-fire.
- On room re-entry (D1.reset()), Room calls roomLock.reset() to re-lock it since enemies re-spawn.

- WHAT ROOMLOCK DOES NOT DO
- Does not draw anything — it is invisible.
- Does not block the player physically — Room.getExitAt(NORTH) returns false while locked,
  which prevents the exit detection in Room.update() from triggering a transition.
- Does not know about the canvas or tiles.
*/

import java.util.List;

/**
 * Prevents a room's north exit until all enemies are dead.
 * Attached to Dungeon Room D1 only.
 * See PLAN OF ACTION above before implementing.
 */
public class RoomLock {

    // =========================================================
    // FIELDS
    // =========================================================

    /** True while at least one enemy is alive. False once all are dead. */
    private boolean locked;

    /**
     * Called once when the lock transitions from locked to unlocked.
     * WorldMap sets this to open D1's north exit.
     */
    private Runnable onUnlockCallback;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    /**
     * Creates a RoomLock in the locked state.
     *
     * @param onUnlockCallback called once when all enemies are dead
     */
    public RoomLock(Runnable onUnlockCallback) {
        this.locked           = true;
        this.onUnlockCallback = onUnlockCallback;
    }

    // =========================================================
    // UPDATE — called by Room each tick during PLAYING
    // =========================================================

    /**
     * Checks the enemy list. If all are dead and the lock was still locked, unlocks and fires callback.
     * Call this from Room.update() each tick while this room's RoomLock is not null.
     *
     * @param entities the room's current entity list (enemies only — Player is not in this list)
     */
    public void update(List<Entity> entities) {
        if (!locked) return; // already unlocked — nothing to check

        if (entities != null) {
            for (Entity entity : entities) {
                if (entity != null && entity.isAlive()) {
                    return;
                }
            }
        }

        locked = false;
        if (onUnlockCallback != null) {
            onUnlockCallback.run();
        }
    }

    // =========================================================
    // RESET — called by Room when D1 is re-entered
    // =========================================================

    /**
     * Re-locks this lock for room re-entry (enemies have re-spawned).
     * Called by Room.reset() before enemies are re-added to the entity list.
     */
    public void reset() {
        locked = true;
    }

    // =========================================================
    // GETTERS
    // =========================================================

    /** Returns true while enemies are still alive (exit is blocked). */
    public boolean isLocked() { return locked; }
}
