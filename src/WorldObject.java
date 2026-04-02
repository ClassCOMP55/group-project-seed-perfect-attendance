/*
Person 2: WorldObject — abstract base class for every static interactive object placed in a Room
Who RIGs it: Room — holds a List<WorldObject>, calls draw(), update(dt), and routes player interaction:
               - J key press → room calls onInteract(player) on nearby objects
               - Per-tick contact check → room calls onContact(player) for objects that react on touch
               - SwordSwing hit check → room calls onHit() for objects that react to sword (Grass, TrainingDummy)

Extends: nothing (base class)
Extended by: Grass, Sign, PathBlocker, DrawbridgeLever, Chest, PushBlock, PressureButton,
             OreNode, TrainingDummy
TO BE REFACTORED to extend this: ThicketGate, SavePoint (see TODO in those files)

===============
PLAN OF ACTION
===============

- CLASS ROLE
- WorldObject is the shared base for anything in a Room that is NOT a tile and NOT an entity.
- "Static" means it does not move under its own AI — though PushBlock can be moved by the player.
- Every WorldObject has: a world position (x, y), a visual sprite, and a Hitbox.
- WorldObject does NOT extend Entity — it has no health, speed, or AI. Do not mix these trees.

- ABSTRACT vs CONCRETE METHODS
- draw(GCanvas) is abstract — every subclass must implement its own visual.
- update(double dt) has a default no-op body — subclasses override only if they need per-tick logic.
- onInteract(Player) has a default no-op body — subclasses override if they respond to the J key.
- onContact(Player) has a default no-op body — subclasses override if they respond to player touch.
- onHit() has a default no-op body — subclasses override if they respond to a SwordSwing hit.
- removeFrom(GCanvas) has a default implementation using the sprite field.

- FIELDS
- double x, y          — top-left world pixel position of this object
- GImage sprite        — visual representation (null until sprites are wired; use GRect placeholder)
- Hitbox hitbox        — collision zone; used by Room for interact range and by player movement blocking
- boolean visible      — if false, draw() renders nothing and hitbox is considered inactive

- INTERACTION RANGE
- Room checks "is the player close enough to interact?" before calling onInteract().
- The interaction range is the player's hitbox overlapping this object's hitbox (or a slightly
  larger interact zone, depending on the subclass — subclasses may define their own interactZone Hitbox).
- onContact() is for objects that trigger automatically on player overlap (no button press needed).
  Examples: ThicketGate (opens on contact), Coin world drops (auto-collect on contact).

- WHAT WORLDOBJECT DOES NOT DO
- Does not move on its own (PushBlock moves only when the player pushes it).
- Does not read InputHandler — Room handles key routing and passes the player reference in.
- Does not write to SaveData — Room or the specific subclass decides what state to persist.
- Does not call GamePlayState — Room is responsible for checking state before calling update().
*/

import acm.graphics.GCanvas;
import acm.graphics.GImage;

/**
 * Abstract base for every static interactive object placed inside a Room.
 * Subclasses: Grass, Sign, PathBlocker, DrawbridgeLever, Chest, PushBlock,
 * PressureButton, OreNode, TrainingDummy.
 * ThicketGate and SavePoint will be refactored to extend this once it exists.
 * See PLAN OF ACTION above before implementing.
 */
public abstract class WorldObject {

    // =========================================================
    // FIELDS
    // =========================================================

    /** Top-left world pixel X position. */
    protected double x;

    /** Top-left world pixel Y position. */
    protected double y;

    /**
     * Visual sprite. Null until real sprites are wired in.
     * Use a colored GRect placeholder during development.
     */
    protected GImage sprite;

    /** Collision zone for movement blocking and interact-range checks. */
    protected Hitbox hitbox;

    /**
     * When false, draw() renders nothing and the hitbox is treated as inactive.
     * Use this to hide/disable an object without removing it from the Room's list.
     */
    protected boolean visible;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    /**
     * Creates a WorldObject at the given position with the given hitbox size.
     *
     * @param x      top-left world pixel X
     * @param y      top-left world pixel Y
     * @param width  hitbox width in pixels
     * @param height hitbox height in pixels
     */
    public WorldObject(double x, double y, double width, double height) {
        this.x       = x;
        this.y       = y;
        this.hitbox  = new Hitbox(x, y, width, height);
        this.visible = true;
    }

    // =========================================================
    // ABSTRACT — every subclass must implement
    // =========================================================

    /**
     * Draws this object onto the canvas.
     * Called by Room every frame if visible = true.
     *
     * @param canvas the game canvas
     */
    public abstract void draw(GCanvas canvas);

    // =========================================================
    // DEFAULT NO-OP HOOKS — override in subclasses that need them
    // =========================================================

    /**
     * Per-tick update. Default: no-op.
     * Room calls this each tick ONLY when GamePlayState == PLAYING.
     *
     * @param dt delta-time in seconds
     */
    public void update(double dt) {
        // no-op by default — override in subclasses that animate or change state over time
    }

    /**
     * Called by Room when the player presses the interact key (J) while facing and close to this object.
     * Default: no-op. Override in Sign, Chest, DrawbridgeLever, OreNode, SavePoint.
     *
     * @param p the Player interacting
     */
    public void onInteract(Player p) {
        // no-op by default
    }

    /**
     * Called by Room each tick when the player's hitbox overlaps this object's hitbox.
     * Default: no-op. Override in ThicketGate (auto-open on contact), Coin (auto-collect).
     *
     * @param p the Player in contact
     */
    public void onContact(Player p) {
        // no-op by default
    }

    /**
     * Called by Room when a SwordSwing hitbox overlaps this object.
     * Default: no-op. Override in Grass (cut + coin drop) and TrainingDummy (react animation).
     */
    public void onHit() {
        // no-op by default
    }

    /**
     * Shifts this object's sprite by (panX, panY) pixels on the canvas.
     * Only the visual moves — the internal position (x, y) is NOT changed.
     * Subclasses that have multiple visual elements (e.g. Chest lid) should
     * override this method to pan all of them.
     * Used by RoomTransition during the room pan animation.
     *
     * @param panX horizontal pixels to shift (negative = left, positive = right)
     * @param panY vertical pixels to shift (negative = up, positive = down)
     */
    public void panVisual(double panX, double panY) {
        if (sprite != null) {
            sprite.move(panX, panY);
        }
    }

    /**
     * Removes this object's sprite from the canvas.
     * Default implementation removes the sprite GImage if it is not null.
     * Subclasses with multiple visuals (e.g. Chest lid) should override.
     *
     * @param canvas the game canvas
     */
    public void removeFrom(GCanvas canvas) {
        if (sprite != null) {
            canvas.remove(sprite);
        }
    }

    // =========================================================
    // GETTERS / SETTERS
    // =========================================================

    public double  getX()       { return x; }
    public double  getY()       { return y; }
    public Hitbox  getHitbox()  { return hitbox; }
    public boolean isVisible()  { return visible; }

    /** Hides this object and deactivates its hitbox (moves it off-screen). */
    public void hide() {
        visible = false;
        hitbox.updatePosition(-99999, -99999);
    }

    /** Re-shows this object and restores its hitbox to its original position. */
    public void show() {
        visible = true;
        hitbox.updatePosition(x, y);
    }
}
