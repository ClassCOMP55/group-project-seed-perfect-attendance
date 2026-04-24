/*
Person 4: Sign — a readable sign that opens the DialogueBox when the player interacts with it
Who RIGs it: Room — holds Sign instances in its WorldObject list.
               When the E key fires: Room finds the WorldObject the player is facing and calls onInteract(player).
               Room must also pass a Dialogue reference so Sign can open it.

Extends: WorldObject

===============
PLAN OF ACTION
===============

- CLASS ROLE
- Sign is a stationary, non-blocking world decoration with text.
- Pressing E while facing a Sign opens the Dialogue overlay with the sign's stored lines.
- Sign does NOT have enemies, combat, or any per-tick logic — it is completely passive.
- Sign IS passable — it does not block the player's movement (hitbox is used for interact range only).

- FIELDS
- String[] dialogueLines  — the lines of text shown when read. Set at construction.
- Dialogue dialogue       — reference to the shared Dialogue overlay.
                            Passed in at construction so Sign can call dialogue.open() directly.

- onInteract() BEHAVIOR
  1. Check that dialogue is not already open (prevent double-trigger).
  2. Call dialogue.open(dialogueLines) to show the text.
  3. Sign sets GamePlayState to DIALOGUE before opening the overlay.
  4. When the final line closes, Sign restores GamePlayState to PLAYING in the callback.

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
import acm.graphics.GImage;
import acm.graphics.GLabel;
import acm.graphics.GRect;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * A readable sign. Opens Dialogue with stored text lines when the player presses E while facing it.
 * See PLAN OF ACTION above before implementing.
 */
public class Sign extends WorldObject {
    private static final double TITLE_BASELINE_Y = -10.0;
    private static final double HINT_BASELINE_Y = 64.0;

    // =========================================================
    // CONSTANTS
    // =========================================================

    /** Placeholder sign color until real sprite is wired. */
    private static final Color SIGN_COLOR = new Color(160, 110, 60);
    private static final Color SIGN_EDGE_COLOR = new Color(104, 70, 34);
    private static final Color SIGN_LABEL_COLOR = new Color(245, 231, 192);
    private static final Color SIGN_HINT_COLOR = new Color(222, 241, 184);

    // =========================================================
    // FIELDS
    // =========================================================

    /** The lines of text shown when the player reads this sign. */
    private final String[] dialogueLines;

    /** The shared Dialogue overlay. Set at construction or via setDialogue(). */
    private Dialogue dialogue;

    /** Sign sprite loaded from assets; null if load fails. */
    private GImage signSprite;

    /** Placeholder visual shown when sprite is unavailable. */
    private GRect placeholder;

    /** Floating helper text above and below the sign for quick testing. */
    private final GLabel titleLabel;
    private final GLabel hintLabel;

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
        this.dialogueLines = dialogueLines == null ? new String[0] : dialogueLines.clone();
        this.dialogue      = dialogue;

        this.signSprite = loadSprite("assets/visuals/png's/sign.png");

        this.placeholder = new GRect(x, y, 48, 48);
        this.placeholder.setFilled(true);
        this.placeholder.setFillColor(SIGN_COLOR);
        this.placeholder.setColor(SIGN_EDGE_COLOR);

        this.titleLabel = new GLabel("Sign", x, y);
        this.titleLabel.setFont("Courier New-BOLD-14");
        this.titleLabel.setColor(SIGN_LABEL_COLOR);

        this.hintLabel = new GLabel("e to interact", x, y);
        this.hintLabel.setFont("Courier New-BOLD-12");
        this.hintLabel.setColor(SIGN_HINT_COLOR);

        resetVisualPosition();
    }

    // =========================================================
    // WORLD OBJECT OVERRIDES
    // =========================================================

    @Override
    public void draw(GCanvas canvas) {
        if (!visible) return;
        resetVisualPosition();
        if (signSprite != null) {
            canvas.add(signSprite);
        } else {
            canvas.add(placeholder);
        }
        canvas.add(titleLabel);
        canvas.add(hintLabel);
        positionLabels();
    }

    @Override
    public void removeFrom(GCanvas canvas) {
        if (signSprite != null) canvas.remove(signSprite);
        canvas.remove(placeholder);
        canvas.remove(titleLabel);
        canvas.remove(hintLabel);
    }

    @Override
    public boolean isInteractable() {
        return true;
    }

    @Override
    public void panVisual(double panX, double panY) {
        if (signSprite != null) signSprite.move(panX, panY);
        placeholder.move(panX, panY);
        titleLabel.move(panX, panY);
        hintLabel.move(panX, panY);
    }

    @Override
    public void resetVisualPosition() {
        if (signSprite != null) signSprite.setLocation(x, y);
        placeholder.setLocation(x, y);
        positionLabels();
    }

    private void positionLabels() {
        double centerX = x + placeholder.getWidth() / 2.0;
        titleLabel.setLocation(centerX - titleLabel.getWidth() / 2.0, y + TITLE_BASELINE_Y);
        hintLabel.setLocation(centerX - hintLabel.getWidth() / 2.0, y + HINT_BASELINE_Y);
    }

    /**
     * Opens the Dialogue overlay with this sign's text.
     * Called by Room when the player presses E while facing this sign.
     *
     * @param p the Player interacting (not used here, but required by WorldObject signature)
     */
    @Override
    public void onInteract(Player p) {
        if (dialogue == null || dialogue.isOpen()) return;
        if (dialogueLines == null || dialogueLines.length == 0) return;

        GamePlayState.setCurrent(GamePlayState.DIALOGUE);
        dialogue.open(
            dialogueLines,
            "Sign",
            false,
            () -> GamePlayState.setCurrent(GamePlayState.PLAYING)
        );
    }

    // =========================================================
    // SETTER (for late-wiring dialogue reference)
    // =========================================================

    public void setDialogue(Dialogue d) { this.dialogue = d; }

    private GImage loadSprite(String path) {
        try {
            BufferedImage source = ImageIO.read(new File(path));
            if (source == null) return null;
            BufferedImage trimmed = trimTransparentBounds(source);
            GImage image = new GImage(trimmed);
            image.setSize(48, 48);
            image.setLocation(x, y);
            return image;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private BufferedImage trimTransparentBounds(BufferedImage source) {
        int w = source.getWidth(), h = source.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                if (((source.getRGB(px, py) >>> 24) & 0xFF) == 0) continue;
                if (px < minX) minX = px;
                if (py < minY) minY = py;
                if (px > maxX) maxX = px;
                if (py > maxY) maxY = py;
            }
        }
        return (maxX < minX || maxY < minY) ? source
            : source.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }
}
