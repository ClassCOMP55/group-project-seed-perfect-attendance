/*
Person 2: Grass — cuttable grass tile that may drop a Coin when hit by a sword
Who RIGs it: Room — holds Grass instances in its WorldObject list.
               Each tick: Room checks SwordSwing hitbox overlap with each Grass and calls onHit().
               Grass objects are placed in C2 (Forest) and potentially other outdoor areas.

Extends: WorldObject

===============
PLAN OF ACTION
===============

- CLASS ROLE
- Grass is a cuttable decoration placed on the floor.
- It is NOT triggered by the interact key (J) — it reacts to SwordSwing hits only.
- When cut: plays a short cut animation, then has a random chance to drop a Coin into the room.
- Once cut, Grass stays cut for the rest of the room visit. It resets when the player re-enters
  the room (Room.reset() creates fresh Grass objects or calls reset() on existing ones).

- FIELDS
- boolean isCut              — true once the sword has hit this grass
- float coinDropChance       — 0.0 to 1.0; suggested default: 0.5 (50% chance per design doc TBD)

- onHit() BEHAVIOR
  1. If already cut, return immediately.
  2. Set isCut = true.
  3. Hide the sprite (grass is now gone visually).
  4. Roll Math.random() < coinDropChance — if true, create a new Coin at this position and
     add it to the Room's droppedItems list.
     Room needs to be accessible here — either pass it in via constructor or use a callback.
     Recommended: constructor takes a Runnable or Consumer<Item> dropCallback so Grass
     does not need a direct reference to Room.
  5. TODO: play a short cut animation (sprite flicker or quick fade) before hiding.

- COIN DROP CALLBACK
- Grass should NOT hold a reference to Room directly — that creates tight coupling.
- Instead, Room passes a Consumer<Item> dropCallback when constructing Grass:
    new Grass(x, y, canvas, item -> room.addDroppedItem(item))
- When onHit() decides to drop a coin, it calls dropCallback.accept(new Coin(x, y)).
- This keeps Grass decoupled from Room.

- RESET
- Room.reset() either re-constructs all Grass objects fresh or calls grass.reset() on each one.
- grass.reset() sets isCut = false and re-shows the sprite.
*/

import acm.graphics.GCanvas;
import acm.graphics.GRect;

import java.awt.Color;
import java.util.function.Consumer;

/**
 * Cuttable grass. Reacts to SwordSwing hits via onHit().
 * May drop a Coin on cut. Resets on room re-entry.
 * See PLAN OF ACTION above before implementing.
 */
public class Grass extends WorldObject {

    // =========================================================
    // CONSTANTS
    // =========================================================

    /** Default coin drop probability (50%). TBD per design doc. */
    private static final float DEFAULT_COIN_DROP_CHANCE = 0.5f;

    /** Placeholder grass color until real sprite is wired. */
    private static final Color GRASS_COLOR = new Color(60, 160, 60);

    // =========================================================
    // FIELDS
    // =========================================================

    /** True once this grass has been cut by the player's sword. */
    private boolean isCut;

    /** Probability (0.0–1.0) that cutting this grass drops a Coin. */
    private final float coinDropChance;

    /**
     * Called when this grass drops a coin.
     * Receives the new Coin item — passes it to Room.addDroppedItem().
     * Keeps Grass decoupled from Room.
     */
    private final Consumer<Item> dropCallback;

    /** Placeholder visual until real grass sprite is ready. */
    private GRect placeholder;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    /**
     * @param x            top-left world pixel X
     * @param y            top-left world pixel Y
     * @param dropCallback called with a new Coin if the coin-drop roll succeeds; may be null
     */
    public Grass(double x, double y, Consumer<Item> dropCallback) {
        super(x, y, 48, 48);
        this.isCut          = false;
        this.coinDropChance = DEFAULT_COIN_DROP_CHANCE;
        this.dropCallback   = dropCallback;

        this.placeholder = new GRect(x, y, 48, 48);
        this.placeholder.setFilled(true);
        this.placeholder.setFillColor(GRASS_COLOR);
    }

    // =========================================================
    // WORLD OBJECT OVERRIDES
    // =========================================================

    @Override
    public void draw(GCanvas canvas) {
        if (!isCut && visible) {
            canvas.add(placeholder);
        }
    }

    @Override
    public void removeFrom(GCanvas canvas) {
        canvas.remove(placeholder);
    }

    /**
     * Called by Room when a SwordSwing hitbox overlaps this grass.
     * Cuts the grass and optionally drops a Coin.
     */
    @Override
    public void onHit() {
        if (isCut) return;

        isCut = true;
        // TODO: hide placeholder (canvas.remove) or play cut animation
        // TODO: if (Math.random() < coinDropChance && dropCallback != null)
        //           dropCallback.accept(new Coin(x + 16, y + 16))
    }

    // =========================================================
    // RESET
    // =========================================================

    /** Restores this grass to its uncut state for room re-entry. */
    public void reset() {
        isCut = false;
        // TODO: re-add placeholder to canvas (or let Room.addTo re-draw)
    }

    // =========================================================
    // GETTERS
    // =========================================================

    public boolean isCut() { return isCut; }
}
