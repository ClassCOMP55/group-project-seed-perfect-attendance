/*
Person 2: PushBlock — a movable block used in push-block puzzles (A2, D2)
Who RIGs it: Room — holds PushBlock instances in WorldObject list.
               Each tick during PLAYING: Room detects player walking into a PushBlock (player hitbox
               + facing direction) and calls tryPush(direction, tileMap, allBlocksInRoom).
               Also calls update(pressureButtons) each tick to check if this block is on a button.

Extends: WorldObject

===============
PLAN OF ACTION
===============

- CLASS ROLE
- PushBlock is a solid block the player pushes by walking into it.
- It can only be pushed one tile at a time (48px per push).
- It cannot be pushed into a WALL tile or into another PushBlock.
- It can be pushed into a FLOOR or BRIDGE tile.
- After each push, PushBlock checks if it now overlaps any PressureButton.

- FIELDS
- int tileCol, tileRow     — current tile position (kept in sync with pixel x, y)
- int startCol, startRow   — original tile position (for reset on room re-entry)

- tryPush() BEHAVIOR
  1. Determine the destination tile based on current tile + direction delta.
  2. Check destination tile in TileMap — if WALL or out of bounds: do nothing (block cannot move).
  3. Check if another PushBlock is in the destination tile — if so: do nothing.
  4. Move: tileCol/tileRow += delta, update x/y = tileCol*48 + MAP_OFFSET_X, tileRow*48.
  5. Update hitbox position to new x, y.
  6. TODO: visual slide animation (smooth pixel movement over a few frames vs. instant snap — TBD).

- PRESSURE BUTTON CHECK
- After each push, Room (or this block's update()) calls update(List<PressureButton> buttons).
- For each button, check if this block's tile position matches the button's tile position.
- If yes: button.setPressed(true).
- If no: button.setPressed(false) — only if this block was the one pressing it.
- Room collects all button states after all blocks update to check if puzzle is solved.

- RESET
- Room.reset() calls pushBlock.reset() on each block.
- reset() moves the block back to startCol, startRow and re-draws at the start position.

- PUZZLE ROOMS
- A2 (overworld): 2 blocks, 3 pressure buttons. Player must stand on the 3rd button.
  Puzzle resets if player leaves the room (Room.reset() is called on re-entry).
- D2 (dungeon): same mechanic. Puzzle does NOT reset on re-entry to D2 (TBD — design doc says
  it resets "from Room 1". Clarify with team: does D2 reset only from D1, or always?).
*/

import acm.graphics.GCanvas;
import acm.graphics.GImage;
import acm.graphics.GRect;

import java.awt.Color;
import java.util.List;

/**
 * A pushable block for puzzle rooms.
 * Player pushes it by walking into it. Cannot be pushed into walls or other blocks.
 * See PLAN OF ACTION above before implementing.
 */
public class PushBlock extends WorldObject {

    // =========================================================
    // CONSTANTS
    // =========================================================

    private static final Color BLOCK_COLOR = new Color(120, 100, 80);

    // =========================================================
    // FIELDS
    // =========================================================

    /** Current tile column (0-based). Kept in sync with pixel x. */
    private int tileCol;

    /** Current tile row (0-based). Kept in sync with pixel y. */
    private int tileRow;

    /** Starting tile column — used by reset(). */
    private final int startCol;

    /** Starting tile row — used by reset(). */
    private final int startRow;

    /** Ticks remaining before another push is allowed (~0.2s cooldown). */
    private int pushCooldownTicks = 0;

    /** True when this block has fallen into a hole and is gone for this room visit. */
    private boolean fallen = false;

    /** Sprite image; non-null when push_block.png loads successfully. */
    private GImage blockSprite;
    /** Fallback colored rectangle when the sprite asset is unavailable. */
    private GRect placeholder;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    /**
     * @param tileCol starting tile column
     * @param tileRow starting tile row
     */
    public PushBlock(int tileCol, int tileRow) {
        super(
            tileCol * 48 + TileMap.MAP_OFFSET_X,
            tileRow * 48,
            48, 48
        );
        this.tileCol  = tileCol;
        this.tileRow  = tileRow;
        this.startCol = tileCol;
        this.startRow = tileRow;

        this.blockSprite = loadSprite("assets/visuals/png's/push_block.png");
        this.placeholder = new GRect(x, y, 48, 48);
        this.placeholder.setFilled(true);
        this.placeholder.setFillColor(BLOCK_COLOR);
    }

    private GImage loadSprite(String path) {
        try {
            GImage img = new GImage(path);
            img.setSize(48, 48);
            img.setLocation(x, y);
            return img;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    // =========================================================
    // WORLD OBJECT OVERRIDES
    // =========================================================

    @Override
    public void draw(GCanvas canvas) {
        if (!visible) return;
        if (blockSprite != null) canvas.add(blockSprite);
        else canvas.add(placeholder);
    }

    @Override
    public void removeFrom(GCanvas canvas) {
        if (blockSprite != null) canvas.remove(blockSprite);
        canvas.remove(placeholder);
    }

    // =========================================================
    // PUSH LOGIC
    // =========================================================

    /**
     * Attempts to push this block one tile in the given direction.
     * Called by Room when the player walks into this block's hitbox.
     *
     * @param dir        the direction of the push (same as the player's facing direction)
     * @param tileMap    this room's TileMap (to check destination tile passability)
     * @param allBlocks  all PushBlocks in this room (to prevent block-into-block pushing)
     * @return true if the block moved, false if it was blocked
     */
    /** Decrements the push cooldown each tick. Call from Room.update(). */
    public void tickCooldown() {
        if (pushCooldownTicks > 0) pushCooldownTicks--;
    }

    public boolean tryPush(Direction dir, TileMap tileMap, List<PushBlock> allBlocks) {
        if (dir == null || tileMap == null) return false;
        if (pushCooldownTicks > 0) return false;

        int dCol = 0;
        int dRow = 0;
        switch (dir) {
            case LEFT:  dCol = -1; break;
            case RIGHT: dCol = 1;  break;
            case UP:    dRow = -1; break;
            case DOWN:  dRow = 1;  break;
        }

        int destCol = tileCol + dCol;
        int destRow = tileRow + dRow;

        Tile destination = tileMap.getTileAt(destCol, destRow);
        if (destination == null || !tileMap.isPushBlockPassable(destCol, destRow)) {
            return false;
        }

        if (allBlocks != null) {
            for (PushBlock block : allBlocks) {
                if (block == null || block == this) continue;
                if (block.tileCol == destCol && block.tileRow == destRow) {
                    return false;
                }
            }
        }

        tileCol = destCol;
        tileRow = destRow;
        x = tileCol * 48 + TileMap.MAP_OFFSET_X;
        y = tileRow * 48;
        syncVisualPosition();
        hitbox.updatePosition(x, y);
        pushCooldownTicks = 12;

        // If the block landed on a hole, mark it as fallen — Room will remove it from canvas
        if (destination.isHole()) {
            fallen = true;
            this.hide();
        }

        return true;
    }

    /** Returns true if this block has fallen into a hole this room visit. */
    public boolean isFallen() { return fallen; }

    /**
     * Checks if this block is resting on any PressureButton and updates their pressed state.
     * Called by Room each tick after tryPush() and during normal update.
     *
     * @param buttons all PressureButtons in this room
     */
    public void updateButtonOverlap(List<PressureButton> buttons) {
        if (buttons == null || fallen) return;
        for (PressureButton button : buttons) {
            if (button == null) continue;
            if (button.getTileCol() == tileCol && button.getTileRow() == tileRow) {
                button.setPressedByBlock(true);
            }
        }
    }

    // =========================================================
    // RESET
    // =========================================================

    /** Returns this block to its starting position. Called by Room.reset(). */
    public void reset() {
        fallen = false;
        this.show();   // restores visible=true and hitbox to current position
        tileCol = startCol;
        tileRow = startRow;
        x = tileCol * 48 + TileMap.MAP_OFFSET_X;
        y = tileRow * 48;
        syncVisualPosition();
        hitbox.updatePosition(x, y);
        pushCooldownTicks = 0;
    }

    @Override
    public void panVisual(double panX, double panY) {
        if (blockSprite != null) blockSprite.move(panX, panY);
        placeholder.move(panX, panY);
    }

    @Override
    public void resetVisualPosition() {
        syncVisualPosition();
    }

    private void syncVisualPosition() {
        if (blockSprite != null) blockSprite.setLocation(x, y);
        placeholder.setLocation(x, y);
    }

    // =========================================================
    // GETTERS
    // =========================================================

    public int getTileCol() { return tileCol; }
    public int getTileRow() { return tileRow; }
}
