/*
Person 2: RoomTransition — sliding pan animation when the player moves between rooms
Who RIGs it: WorldMap — calls start() when triggerTransition() fires; calls update(dt) each tick
               while the transition is running; calls getToRoom() / getFromRoom() in finishTransition().
             GameLoop — still fires update() every tick during TRANSITIONING state.
               RoomTransition is the ONLY thing that updates during TRANSITIONING.

Extends: nothing
Owns: fromRoom reference, toRoom reference, Player reference (visual only), pixel offset state

===============
PLAN OF ACTION
===============

- CLASS ROLE
- RoomTransition handles the visual sliding pan when the player walks off an exit edge.
- It is the ONLY class that updates during GamePlayState.TRANSITIONING.
  All enemies, projectiles, and WorldObjects are frozen during this time (Room.update() returns early).
- RoomTransition does NOT change the Player's internal coordinates during the pan —
  the player's sprite is visually carried by the same per-tick pan offset, but their x/y
  stay the same until WorldMap.finishTransition() syncs them to the sprite's final position.

- THE ANIMATION
- When start() is called:
    1. GamePlayState is set to TRANSITIONING.
    2. fromRoom is already drawn on canvas at its normal position.
    3. toRoom is added to canvas, then immediately panned one full room away (off-screen).
    4. Each tick, both rooms AND the player's sprite slide in the same direction and by the
       same per-tick amount — creating a seamless world-pan effect.
    5. The player visually straddles the boundary between the two rooms during the pan,
       as if passing through a doorway that moves with them.
    6. When ticksElapsed == TRANSITION_TICKS: isAnimationComplete() returns true →
       WorldMap calls finishTransition().

- PIXEL PAN DIRECTION (per exit direction)
  EXIT RIGHT → both rooms and player slide LEFT  (panX = -ROOM_WIDTH_PX  / TRANSITION_TICKS)
  EXIT LEFT  → both rooms and player slide RIGHT (panX = +ROOM_WIDTH_PX  / TRANSITION_TICKS)
  EXIT UP    → both rooms and player slide DOWN  (panY = +ROOM_HEIGHT_PX / TRANSITION_TICKS)
  EXIT DOWN  → both rooms and player slide UP    (panY = -ROOM_HEIGHT_PX / TRANSITION_TICKS)

- INITIAL OFF-SCREEN OFFSET FOR toRoom (applied once in start())
  EXIT RIGHT → toRoom offset: +ROOM_WIDTH_PX  in X  (starts one room to the right)
  EXIT LEFT  → toRoom offset: -ROOM_WIDTH_PX  in X  (starts one room to the left)
  EXIT UP    → toRoom offset: -ROOM_HEIGHT_PX in Y  (starts one room above)
  EXIT DOWN  → toRoom offset: +ROOM_HEIGHT_PX in Y  (starts one room below)

- PLAYER REPOSITIONING (on complete — done by WorldMap.finishTransition(), NOT here)
  After finishTransition(), WorldMap sets the player's internal coordinates to match
  where the sprite ended up. Expected final positions (for verification):
    Exited RIGHT → player.x = left  edge of new room, player.y unchanged
    Exited LEFT  → player.x = right edge of new room, player.y unchanged
    Exited UP    → player.y = bottom edge of new room, player.x unchanged
    Exited DOWN  → player.y = top   edge of new room, player.x unchanged

- WHAT ROOMTRANSITION DOES NOT DO
- Does not reset the new room — WorldMap calls toRoom.reset() after the transition.
- Does not reposition the Player internally — WorldMap does that in finishTransition().
- Does not restore GamePlayState — WorldMap does that when finishTransition() completes.
*/

import acm.graphics.GCanvas;

/**
 * Handles the sliding pan animation when the player moves between rooms.
 * Only active during GamePlayState.TRANSITIONING.
 * See the PLAN OF ACTION block above for full implementation details.
 */
public class RoomTransition {

    // =========================================================
    // CONSTANTS
    // =========================================================

    /*
     * =====================
     * Transition speed — adjust TRANSITION_TICKS to change how long the pan takes.
     * At 60fps, 30 ticks = 0.5 seconds. Raise this number to slow the pan; lower it to speed it up.
     * =====================
     */

    /**
     * Number of game ticks (frames) the pan animation runs for.
     * At 60fps: 30 ticks = 0.5 seconds.
     * Raise this value to slow the transition; lower it to make it faster.
     */
    private static final int TRANSITION_TICKS = 30;

    /*
     * =====================
     * End of adjustable transition speed.
     * =====================
     */

    /** Full room width in pixels (26 columns × 48px per tile). Horizontal pan distance. */
    private static final double ROOM_WIDTH_PX  = 26 * 48; // = 1248

    /** Full room height in pixels (15 rows × 48px per tile). Vertical pan distance. */
    private static final double ROOM_HEIGHT_PX = 15 * 48; // = 720

    // =========================================================
    // FIELDS
    // =========================================================

    /** The room the player is leaving. */
    private Room fromRoom;

    /** The room the player is entering. */
    private Room toRoom;

    /** The direction the player exited (e.g. RIGHT = walked off the east edge). */
    private Direction direction;

    /** Number of animation ticks completed so far (0 = just started, TRANSITION_TICKS = done). */
    private int ticksElapsed;

    /** The game canvas — needed to add toRoom graphics during start(). */
    private GCanvas canvas;

    /**
     * The active Player — sprite is panned visually each tick so the player
     * appears to ride the transition rather than vanish.
     * Internal x/y coordinates are NOT changed here; that is WorldMap's job.
     */
    private Player player;

    /** How many pixels to shift horizontally each tick (negative = left, 0 = vertical pan). */
    private double panXPerTick;

    /** How many pixels to shift vertically each tick (negative = up, 0 = horizontal pan). */
    private double panYPerTick;

    // =========================================================
    // START
    // =========================================================

    /**
     * Begins the transition animation.
     * Adds toRoom to canvas at its off-screen starting position, then pans everything
     * TRANSITION_TICKS times toward the final positions.
     * Sets GamePlayState to TRANSITIONING immediately so the game world freezes.
     *
     * @param fromRoom  the room being left (already on canvas at normal position)
     * @param toRoom    the room being entered (will be added here, positioned off-screen)
     * @param direction the exit direction the player used
     * @param canvas    the game canvas
     * @param player    the active Player (sprite will be panned with the rooms)
     */
    public void start(Room fromRoom, Room toRoom, Direction direction,
                      GCanvas canvas, Player player) {
        this.fromRoom    = fromRoom;
        this.toRoom      = toRoom;
        this.direction   = direction;
        this.canvas      = canvas;
        this.player      = player;
        this.ticksElapsed = 0;

        // Freeze all game logic for the duration of the animation
        GamePlayState.setCurrent(GamePlayState.TRANSITIONING);

        // --- calculate per-tick pan amounts ---
        // Both rooms and the player sprite move by the same amount each tick.
        // See PLAN OF ACTION above for the direction table.
        switch (direction) {
            case RIGHT:
                panXPerTick = -ROOM_WIDTH_PX  / TRANSITION_TICKS; // slide left
                panYPerTick = 0;
                break;
            case LEFT:
                panXPerTick = +ROOM_WIDTH_PX  / TRANSITION_TICKS; // slide right
                panYPerTick = 0;
                break;
            case UP:
                panXPerTick = 0;
                panYPerTick = +ROOM_HEIGHT_PX / TRANSITION_TICKS; // slide down
                break;
            case DOWN:
                panXPerTick = 0;
                panYPerTick = -ROOM_HEIGHT_PX / TRANSITION_TICKS; // slide up
                break;
            default:
                panXPerTick = 0;
                panYPerTick = 0;
                break;
        }

        // --- add toRoom to canvas, then pan it one full room away (off-screen) ---
        // All of this happens before repaint(), so there is no visible flicker.
        toRoom.addTo(canvas);

        double initialOffsetX = 0;
        double initialOffsetY = 0;

        switch (direction) {
            case RIGHT: initialOffsetX = +ROOM_WIDTH_PX;  break; // toRoom starts to the right
            case LEFT:  initialOffsetX = -ROOM_WIDTH_PX;  break; // toRoom starts to the left
            case UP:    initialOffsetY = -ROOM_HEIGHT_PX; break; // toRoom starts above
            case DOWN:  initialOffsetY = +ROOM_HEIGHT_PX; break; // toRoom starts below
        }

        toRoom.panAll(initialOffsetX, initialOffsetY);
    }

    // =========================================================
    // UPDATE — called each tick by WorldMap during TRANSITIONING
    // =========================================================

    /**
     * Advances the animation by one tick.
     * Moves fromRoom, toRoom, and the player sprite by one pan step toward their final positions.
     * Does nothing once the animation is complete — caller should check isAnimationComplete().
     *
     * @param dt delta-time in seconds (not used — this animation is tick-based, not time-based)
     */
    public void update(double dt) {
        if (isAnimationComplete()) return;

        ticksElapsed++;

        // --- slide both rooms and the player sprite by one step ---
        // All three move by the same amount so the world appears to scroll seamlessly.
        fromRoom.panAll(panXPerTick, panYPerTick);
        toRoom.panAll(panXPerTick, panYPerTick);

        // Move the player's visual sprite (internal coordinates unchanged until finishTransition)
        if (player != null) {
            player.panVisual(panXPerTick, panYPerTick);
        }
    }

    // =========================================================
    // COMPLETION CHECK
    // =========================================================

    /**
     * Returns true when all TRANSITION_TICKS have elapsed and the pan is finished.
     * WorldMap polls this each tick to know when to call finishTransition().
     *
     * @return true if the animation has fully completed
     */
    public boolean isAnimationComplete() {
        return ticksElapsed >= TRANSITION_TICKS;
    }

    // =========================================================
    // GETTERS — used by WorldMap in finishTransition()
    // =========================================================

    /** @return the room the player is leaving */
    public Room      getFromRoom()  { return fromRoom; }

    /** @return the room the player is entering */
    public Room      getToRoom()    { return toRoom; }

    /** @return the direction the player exited */
    public Direction getDirection() { return direction; }
}
