import acm.graphics.*;

/**
 * ThicketGate.java
 *
 * A world object placed in rooms A2, A3, and B3 that blocks the inner puzzle
 * area with an impassable hitbox. Automatically opens (removes collision) on
 * contact if the player has the Mark of Hero.
 *
 * The gate check happens automatically on contact — the player doesn't press
 * an interact button. Check this in the per-tick collision/contact loop.
 *
 * Person 1 — Engine & Sequences (Task 24)
 */
public class ThicketGate {

    // ==========================================================
    // FIELDS
    // ==========================================================

    /** Unique identifier for this gate (e.g. "gate_a2", "gate_a3", "gate_b3"). */
    private final String gateId;

    /** Whether this gate has been opened. */
    private boolean isOpen;

    /** Collision hitbox — blocks player movement when closed. */
    private final Hitbox hitbox;

    /** Visual sprite for the gate. */
    private GImage sprite;

    /** Position (top-left of sprite/hitbox). */
    private final double x, y;

    /** Size of the gate in pixels. */
    private static final int GATE_WIDTH = 64;
    private static final int GATE_HEIGHT = 64;

    // ==========================================================
    // CONSTRUCTOR
    // ==========================================================

    /**
     * Creates a ThicketGate at the given position.
     *
     * @param x      top-left X in world pixels
     * @param y      top-left Y in world pixels
     * @param gateId unique identifier for save/load tracking
     */
    public ThicketGate(double x, double y, String gateId) {
        this.x = x;
        this.y = y;
        this.gateId = gateId;
        this.isOpen = false;
        this.hitbox = new Hitbox(x, y, GATE_WIDTH, GATE_HEIGHT);

        this.sprite = new GImage("assets/visuals/characters/player-1-idle-front.gif", x, y);
        this.sprite.setSize(GATE_WIDTH, GATE_HEIGHT);
    }

    // ==========================================================
    // CONTACT CHECK — called each tick from the room's update loop
    // ==========================================================

    /**
     * Checks if the player is in contact with this gate and should open it.
     * Opens automatically if the player has the Mark of Hero.
     *
     * @param player the player entity
     * @return true if the gate just opened this tick (for triggering effects)
     */
    /**
     * Checks if the player is in contact with this gate and should open it.
     * Opens automatically if the player has the Mark of Hero.
     *
     * @param player the player entity
     * @return true if the gate just opened this tick (for triggering effects)
     */
    public boolean onContact(Player player) {
        if (isOpen) return false;
        if (player == null) return false;

        if (!hitbox.overlaps(player.getHitbox())) return false;

        if (player.hasMarkOfHero()) {
            open();
            return true;
        }

        return false;
    }

    /**
     * Returns a message to display when the player contacts a closed gate
     * without the Mark of Hero. Returns null if the gate is open or no
     * contact is occurring.
     *
     * @param player the player entity
     * @return hint message string, or null
     */
    public String getBlockedMessage(Player player) {
        if (isOpen) return null;
        if (player == null) return null;
        if (!hitbox.overlaps(player.getHitbox())) return null;
        if (player.hasMarkOfHero()) return null;
        return "A strange force blocks your path.";
    }

    /**
     * Opens the gate — removes collision and hides the sprite.
     * Call this when the player has MarkOfHero and touches the gate,
     * or when loading a save where this gate was already opened.
     */
    public void open() {
        isOpen = true;
        // Zero-out the hitbox so it no longer blocks movement
        hitbox.updatePosition(-9999, -9999);
    }

    // ==========================================================
    // DRAW / REMOVE
    // ==========================================================

    /**
     * Draws the gate sprite if it is still closed.
     * @param canvas the GCanvas to draw onto
     */
    public void draw(GCanvas canvas) {
        if (!isOpen && sprite != null) {
            canvas.add(sprite);
        }
    }

    /**
     * Removes the gate sprite from the canvas.
     * @param canvas the GCanvas to remove from
     */
    public void removeFrom(GCanvas canvas) {
        if (sprite != null) {
            canvas.remove(sprite);
        }
    }

    // ==========================================================
    // TILE COLLISION — for use in the room's passability check
    // ==========================================================

    /**
     * Returns true if the given point is blocked by this gate.
     * Use in the room's custom isPassable() check.
     *
     * @param px point X in world pixels
     * @param py point Y in world pixels
     * @return true if the gate is closed and the point is inside it
     */
    public boolean blocksPoint(double px, double py) {
        if (isOpen) return false;
        return hitbox.contains(px, py);
    }

    // ==========================================================
    // GETTERS
    // ==========================================================

    public String getGateId() { return gateId; }
    public boolean isOpen()   { return isOpen; }
    public Hitbox getHitbox() { return hitbox; }
}
