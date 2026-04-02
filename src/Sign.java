/*
Person 4: Sign — a readable sign that opens the DialogueBox when the player interacts with it
Who RIGs it: Room — holds Sign instances in its WorldObject list.
               When the J key fires: Room finds the WorldObject the player is facing and calls onInteract(player).
               Room must also pass a Dialogue reference so Sign can open it.

Extends: WorldObject

===============
PLAN OF ACTION
===============

- CLASS ROLE
- Sign is a stationary, non-blocking world decoration with text.
- Pressing J while facing a Sign opens the Dialogue overlay with the sign's stored lines.
- Sign does NOT have enemies, combat, or any per-tick logic — it is completely passive.
- Sign IS passable — it does not block the player's movement (hitbox is used for interact range only).

- FIELDS
- String[] dialogueLines  — the lines of text shown when read. Set at construction.
- Dialogue dialogue       — reference to the shared Dialogue overlay.
                            Passed in at construction so Sign can call dialogue.open() directly.

- onInteract() BEHAVIOR
  1. Check that dialogue is not already open (prevent double-trigger).
  2. Call dialogue.open(dialogueLines) to show the text.
  3. GamePlayState is set to DIALOGUE inside Dialogue.open() — Sign does not set it.
  4. Player reads through lines. When dialogue closes, GamePlayState returns to PLAYING automatically.

- NOTE ON DIALOGUE REFERENCE
- Sign holds a reference to the shared Dialogue instance.
- Room passes Dialogue into the Sign constructor (or via a setter) when building room content.
- This keeps Sign simple and avoids Sign needing to know about the full game state.

- HITBOX NOTE
- The hitbox here is used as an INTERACT ZONE (slightly larger than the visual).
- It does NOT block the player from walking through — Sign is a decoration, not a wall.
- Room's passability check should skip WorldObject hitboxes unless the object explicitly
  sets itself as impassable (PathBlocker does this; Sign does not).
*/

import acm.graphics.GCanvas;
import acm.graphics.GRect;

import java.awt.Color;

/**
 * A readable sign. Opens Dialogue with stored text lines when the player presses J while facing it.
 * See PLAN OF ACTION above before implementing.
 */
public class Sign extends WorldObject {

    // =========================================================
    // CONSTANTS
    // =========================================================

    /** Placeholder sign color until real sprite is wired. */
    private static final Color SIGN_COLOR = new Color(160, 110, 60);

    // =========================================================
    // FIELDS
    // =========================================================

    /** The lines of text shown when the player reads this sign. */
    private final String[] dialogueLines;

    /** The shared Dialogue overlay. Set at construction or via setDialogue(). */
    private Dialogue dialogue;

    /** Placeholder visual until real sign sprite is ready. */
    private GRect placeholder;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    /**
     * @param x             top-left world pixel X
     * @param y             top-left world pixel Y
     * @param dialogueLines the lines to show when read
     * @param dialogue      the shared Dialogue overlay
     */
    public Sign(double x, double y, String[] dialogueLines, Dialogue dialogue) {
        super(x, y, 48, 48);
        this.dialogueLines = dialogueLines;
        this.dialogue      = dialogue;

        this.placeholder = new GRect(x, y, 48, 48);
        this.placeholder.setFilled(true);
        this.placeholder.setFillColor(SIGN_COLOR);
    }

    // =========================================================
    // WORLD OBJECT OVERRIDES
    // =========================================================

    @Override
    public void draw(GCanvas canvas) {
        if (visible) canvas.add(placeholder);
    }

    @Override
    public void removeFrom(GCanvas canvas) {
        canvas.remove(placeholder);
    }

    /**
     * Opens the Dialogue overlay with this sign's text.
     * Called by Room when the player presses J while facing this sign.
     *
     * @param p the Player interacting (not used here, but required by WorldObject signature)
     */
    @Override
    public void onInteract(Player p) {
        if (dialogue == null) return;
        // TODO: if (dialogue.isOpen()) return; — prevent double-trigger
        // TODO: dialogue.open(dialogueLines)
        // GamePlayState → DIALOGUE is handled inside Dialogue.open()
    }

    // =========================================================
    // SETTER (for late-wiring dialogue reference)
    // =========================================================

    public void setDialogue(Dialogue d) { this.dialogue = d; }
}
