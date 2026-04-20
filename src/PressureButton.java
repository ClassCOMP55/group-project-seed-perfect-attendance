/*
Person 2: PressureButton — a floor button that activates when a PushBlock or the Player stands on it
Who RIGs it: Room — holds PressureButton instances in WorldObject list.
               Each tick: Room calls button.update(allPushBlocks, player) to refresh pressed state.
               After updating all buttons, Room checks if ALL buttons are pressed to solve the puzzle.

Extends: WorldObject

===============
PLAN OF ACTION
===============

- CLASS ROLE
- PressureButton is a floor-level object that detects when something is standing on it.
- Two types depending on requiresPlayer:
    requiresPlayer = false → activated by a PushBlock resting on it (or the player too)
    requiresPlayer = true  → activated ONLY by the Player standing on it (the 3rd button in the puzzle)
- All buttons in the puzzle room must be pressed simultaneously to solve the puzzle.
- PressureButton does NOT solve the puzzle itself — Room checks all buttons each tick and fires
  the puzzle-complete callback when all return isPressed() == true.

- FIELDS
- int tileCol, tileRow    — tile position of this button (used to check block/player overlap)
- boolean isPressed       — true when this button is currently being pressed
- boolean requiresPlayer  — if true, only the Player (not a PushBlock) can press this button
- boolean pressedByBlock  — true if a PushBlock is currently on this tile
- boolean pressedByPlayer — true if the Player is currently on this tile

- update() BEHAVIOR (called each tick by Room)
  1. Reset pressedByBlock = false (PushBlock.updateButtonOverlap() will set it to true if applicable).
  2. Check if player's tile position matches this button's tileCol/tileRow → set pressedByPlayer.
  3. Combine: isPressed = pressedByPlayer || (!requiresPlayer && pressedByBlock)
  4. Update visual (depressed/raised placeholder color).

- PUZZLE SOLVE CHECK (done by Room, not here)
  Room keeps a list of all PressureButtons.
  After updating all of them, Room calls allButtons.stream().allMatch(PressureButton::isPressed).
  If all are pressed: puzzle complete → open the chest or fire the puzzle-complete callback.

- RESET
- When the player leaves the room, Room.reset() calls button.reset() on each button.
- reset() sets pressedByBlock = false, pressedByPlayer = false, isPressed = false.
- PushBlocks are also reset to their start positions, which naturally un-presses block-buttons.

- NOTE on the 3-button puzzle layout (A2, D2)
- 2 PushBlocks + 3 PressureButtons. requiresPlayer = true on exactly ONE button.
- The player must push both blocks onto their buttons AND stand on the third button simultaneously.
- This is the core puzzle mechanic — PressureButton's requiresPlayer flag is what makes it possible.
*/

import acm.graphics.GCanvas;
import acm.graphics.GImage;
import acm.graphics.GRect;

import java.awt.Color;

/**
 * A floor button activated by a PushBlock or the Player standing on it.
 * Part of the push-block puzzles in A2 and D2.
 * See PLAN OF ACTION above before implementing.
 */
public class PressureButton extends WorldObject {

    // =========================================================
    // CONSTANTS
    // =========================================================

    private static final Color BUTTON_RAISED   = new Color(160, 60, 60);
    private static final Color BUTTON_PRESSED  = new Color(60, 200, 60);

    // =========================================================
    // FIELDS
    // =========================================================

    /** Tile column of this button. Used for overlap checks with PushBlocks and Player. */
    private final int tileCol;

    /** Tile row of this button. Used for overlap checks with PushBlocks and Player. */
    private final int tileRow;

    /** True when this button is currently activated. */
    private boolean isPressed;

    /**
     * If true, only the Player (not a PushBlock) can activate this button.
     * Used for the 3rd button in the puzzle that requires the player to stand on it.
     */
    private final boolean requiresPlayer;

    /** True if a PushBlock is currently resting on this tile. Set by PushBlock.updateButtonOverlap(). */
    private boolean pressedByBlock;

    /** True if the Player is currently standing on this tile. Set by update(). */
    private boolean pressedByPlayer;

    /** Placeholder visual until real button sprite is ready. */
    private final GRect placeholder;

    /** Optional button sprite when art is available. */
    private final GImage buttonSprite;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    /**
     * @param tileCol       tile column of this button
     * @param tileRow       tile row of this button
     * @param requiresPlayer true if only the Player (not a block) can activate this button
     */
    public PressureButton(int tileCol, int tileRow, boolean requiresPlayer) {
        super(
            tileCol * 48 + TileMap.MAP_OFFSET_X,
            tileRow * 48,
            48, 48
        );
        this.tileCol       = tileCol;
        this.tileRow       = tileRow;
        this.requiresPlayer = requiresPlayer;
        this.isPressed      = false;
        this.pressedByBlock  = false;
        this.pressedByPlayer = false;
        this.buttonSprite = loadSprite("assets/visuals/png's/push_button.png");

        this.placeholder = new GRect(x, y, 48, 48);
        this.placeholder.setFilled(true);
        this.placeholder.setFillColor(BUTTON_RAISED);
    }

    // =========================================================
    // WORLD OBJECT OVERRIDES
    // =========================================================

    @Override
    public void draw(GCanvas canvas) {
        if (!visible) return;
        if (buttonSprite != null) {
            canvas.add(buttonSprite);
        } else {
            canvas.add(placeholder);
        }
    }

    @Override
    public void removeFrom(GCanvas canvas) {
        if (buttonSprite != null) {
            canvas.remove(buttonSprite);
        }
        canvas.remove(placeholder);
    }

    // =========================================================
    // UPDATE — called each tick by Room
    // =========================================================

    /**
     * Refreshes this button's pressed state based on Player position.
     * PushBlock overlap is set separately by PushBlock.updateButtonOverlap().
     * Call this AFTER all PushBlocks have called updateButtonOverlap().
     *
     * @param player the active Player
     */
    public void update(Player player) {
        if (player == null) {
            pressedByPlayer = false;
            isPressed = !requiresPlayer && pressedByBlock;
        } else {
            int pCol = (int) ((player.getX() - TileMap.MAP_OFFSET_X) / 48.0);
            int pRow = (int) (player.getY() / 48.0);
            pressedByPlayer = (pCol == tileCol && pRow == tileRow);
            isPressed = pressedByPlayer || (!requiresPlayer && pressedByBlock);
        }

        placeholder.setFillColor(isPressed ? BUTTON_PRESSED : BUTTON_RAISED);
    }

    // =========================================================
    // RESET
    // =========================================================

    /** Resets this button to unpressed. Called by Room.reset(). */
    public void reset() {
        pressedByBlock  = false;
        pressedByPlayer = false;
        isPressed       = false;
        if (placeholder != null) placeholder.setFillColor(BUTTON_RAISED);
    }

    @Override
    public void panVisual(double panX, double panY) {
        if (buttonSprite != null) {
            buttonSprite.move(panX, panY);
        }
        placeholder.move(panX, panY);
    }

    @Override
    public void resetVisualPosition() {
        if (buttonSprite != null) {
            buttonSprite.setLocation(x, y);
        }
        placeholder.setLocation(x, y);
    }

    // =========================================================
    // SETTERS / GETTERS
    // =========================================================

    /** Called by PushBlock.updateButtonOverlap() when a block moves onto or off this tile. */
    public void setPressedByBlock(boolean val) { this.pressedByBlock = val; }

    public boolean isPressed()        { return isPressed; }
    public boolean requiresPlayer()   { return requiresPlayer; }
    public int     getTileCol()       { return tileCol; }
    public int     getTileRow()       { return tileRow; }

    private GImage loadSprite(String path) {
        try {
            GImage image = new GImage(path);
            image.setSize(48, 48);
            image.setLocation(x, y);
            return image;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
