/*
Person 2: RoomTransition — sliding pan animation when the player moves between rooms
Who RIGs it: WorldMap — calls start() when triggerTransition() fires; calls update(dt) each tick
               while the transition is running; calls getToRoom() in finishTransition().
             GameLoop — still fires update() every tick during TRANSITIONING state.
               RoomTransition is the ONLY thing that updates during TRANSITIONING.

Extends: nothing
Owns: fromRoom reference, toRoom reference, pixel offset state

===============
PLAN OF ACTION
===============

- CLASS ROLE
- RoomTransition handles the visual sliding pan when the player walks off an exit edge.
- It is the ONLY class that updates during GamePlayState.TRANSITIONING.
  All enemies, projectiles, and WorldObjects are frozen during this time (Room.update() returns early).
- RoomTransition does NOT move the Player during the pan — the player is visually carried by the
  offset but their internal coordinates do not change until finishTransition() fires in WorldMap.

- THE ANIMATION
- When start() is called:
    1. GamePlayState is set to TRANSITIONING.
    2. fromRoom is already drawn on the canvas at its normal position.
    3. toRoom is drawn on the canvas at one full screen-width or screen-height away (off-screen).
    4. Each tick, both rooms slide toward their final positions: fromRoom slides out, toRoom slides in.
    5. The slide covers one full room width (1248px) or height (720px) over TOTAL_STEPS ticks.
    6. When step == TOTAL_STEPS: isComplete() returns true → WorldMap calls finishTransition().

- PIXEL OFFSETS
- Direction.RIGHT (player exits east): fromRoom slides left (-offsetX), toRoom enters from right (+offsetX).
- Direction.LEFT  (player exits west): fromRoom slides right, toRoom enters from left.
- Direction.UP    (player exits north): fromRoom slides up (-offsetY), toRoom enters from below.
- Direction.DOWN  (player exits south): fromRoom slides down, toRoom enters from above.
- The offset is applied each tick by repositioning the room's TileMap tiles and WorldObjects on canvas.
  (Implementation detail: simplest approach is to use a GCanvas translate or move all GObjects by delta.)

- TOTAL_STEPS / SPEED
- TOTAL_STEPS = 30 ticks (~0.5 seconds at 60fps). Gives a smooth, quick pan.
- Each tick advances offset by (room dimension / TOTAL_STEPS) pixels.
- Horizontal pan: step size = 1248 / 30 = 41.6px per tick.
- Vertical pan:   step size = 720  / 30 = 24.0px per tick.

- PLAYER REPOSITIONING (on complete)
- After finishTransition(), WorldMap moves the Player to the correct spawn position in toRoom:
    exited EAST  → player spawns at the WEST edge of toRoom (x = MAP_OFFSET_X + player width)
    exited WEST  → player spawns at the EAST edge of toRoom
    exited NORTH → player spawns at the SOUTH edge of toRoom
    exited SOUTH → player spawns at the NORTH edge of toRoom
- This is WorldMap's responsibility, not RoomTransition's.

- WHAT ROOMTRANSITION DOES NOT DO
- Does not reset the new room — WorldMap calls toRoom.reset() after the transition.
- Does not reposition the Player — WorldMap does that in finishTransition().
- Does not restore GamePlayState — WorldMap does that when finishTransition() completes.
*/

import acm.graphics.GCanvas;

/**
 * Handles the sliding pan animation when the player moves between rooms.
 * Only active during GamePlayState.TRANSITIONING.
 * See PLAN OF ACTION above before implementing.
 */
public class RoomTransition {

    // =========================================================
    // CONSTANTS
    // =========================================================

    /**
     * Number of ticks the slide animation lasts (~0.5 seconds at 60fps).
     * Horizontal step: 1248 / 30 ≈ 41.6px per tick.
     * Vertical step:   720  / 30 = 24.0px per tick.
     */
    private static final int TOTAL_STEPS = 30;

    /** Full room width in pixels (26 cols × 48px). Used for horizontal pan distance. */
    private static final double ROOM_WIDTH_PX = 26 * 48; // = 1248

    /** Full room height in pixels (15 rows × 48px). Used for vertical pan distance. */
    private static final double ROOM_HEIGHT_PX = 15 * 48; // = 720

    // =========================================================
    // FIELDS
    // =========================================================

    /** The room the player is leaving. */
    private Room fromRoom;

    /** The room the player is entering. */
    private Room toRoom;

    /** The direction the player exited (e.g. RIGHT means they walked off the east edge). */
    private Direction direction;

    /** Current animation step (0 = not started, TOTAL_STEPS = complete). */
    private int step;

    /** The game canvas — needed to reposition room graphics each tick. */
    private GCanvas canvas;

    // =========================================================
    // START
    // =========================================================

    /**
     * Begins the transition animation.
     * Call from WorldMap.triggerTransition() after confirming the exit is valid.
     * Sets GamePlayState to TRANSITIONING immediately.
     *
     * @param fromRoom  the room being left
     * @param toRoom    the room being entered
     * @param direction the exit direction the player used
     * @param canvas    the game canvas
     */
    public void start(Room fromRoom, Room toRoom, Direction direction, GCanvas canvas) {
        this.fromRoom  = fromRoom;
        this.toRoom    = toRoom;
        this.direction = direction;
        this.canvas    = canvas;
        this.step      = 0;

        GamePlayState.setCurrent(GamePlayState.TRANSITIONING);

        // TODO: call toRoom.addTo(canvas) positioned one full room away (off-screen)
        //       so it is ready to slide in
    }

    // =========================================================
    // UPDATE — called each tick by WorldMap during TRANSITIONING
    // =========================================================

    /**
     * Advances the animation by one step.
     * Moves fromRoom and toRoom graphics by their per-tick pixel offset.
     * Does nothing once the animation is complete.
     *
     * @param dt delta-time in seconds (not used for step-counting — tick-based)
     */
    public void update(double dt) {
        if (isComplete()) return;

        step++;

        // TODO: calculate how many pixels to move this tick based on direction and step
        // TODO: shift all fromRoom GObjects by (-dx, -dy)
        // TODO: shift all toRoom GObjects by the same delta (they start one room away, converge)
    }

    // =========================================================
    // COMPLETION CHECK
    // =========================================================

    /** Returns true when the animation has finished and WorldMap can call finishTransition(). */
    public boolean isComplete() {
        return step >= TOTAL_STEPS;
    }

    // =========================================================
    // GETTERS — used by WorldMap in finishTransition()
    // =========================================================

    public Room      getFromRoom()  { return fromRoom; }
    public Room      getToRoom()    { return toRoom; }
    public Direction getDirection() { return direction; }
}
