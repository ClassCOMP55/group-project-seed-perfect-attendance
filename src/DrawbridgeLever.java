/*
Person 2: DrawbridgeLever — the broken lever fixture in C1 that lowers the bridge
Who RIGs it: Room (C1) — holds it in WorldObject list, routes J key to onInteract().
             WorldMap — DrawbridgeLever calls worldMap.openExit("C1", Direction.UP) after the
               bridge animation completes.
             TileMap (C1) — DrawbridgeLever calls tileMap.setTileType() to convert WALL tiles
               on the bridge to FLOOR tiles, making them passable.

Extends: WorldObject

===============
PLAN OF ACTION
===============

- CLASS ROLE
- DrawbridgeLever is the interactable fixture the player uses to lower the bridge in C1.
- It checks that the player has FixedLever in inventory before doing anything.
- On successful interact: plays a bridge-lowering animation, updates the C1 TileMap bridge tiles
  to FLOOR (passable), removes FixedLever from the player's inventory, tells WorldMap to open the
  C1 NORTH exit.
- After the bridge is fixed, the lever can no longer be interacted with (isFixed = true).

- FIELDS
- boolean isFixed             — true after the bridge has been lowered (one-time use)
- TileMap roomTileMap         — reference to C1's TileMap (needed to update bridge tiles)
- WorldMap worldMap           — reference to WorldMap (needed to open the C1 NORTH exit)

- onInteract() BEHAVIOR
  1. If isFixed, return immediately (already done).
  2. Check player's inventory for an item with itemId == "fixed_lever".
     If not found: show a short hint via Dialogue ("The lever is broken. Maybe a blacksmith could fix it?")
     and return.
  3. Remove FixedLever from player inventory.
  4. Set isFixed = true.
  5. TODO: play bridge-lowering animation (tile color changes, or a short frame sequence).
  6. Update bridge tiles in roomTileMap: call tileMap.setTileType(col, row, FLOOR, "assets/tile_floor.png")
     for each tile in the bridge row/column (exact tile positions TBD during room layout design).
  7. Call worldMap.openExit("C1", Direction.UP) to allow transition to C2.

- BRIDGE TILE POSITIONS (TBD)
- The exact columns/rows of the bridge tiles in C1 are TBD until the C1 room layout is designed.
- They will be a horizontal or vertical strip of Tile.TileType.WALL tiles that become FLOOR.
- Mark them with a comment here when the layout is finalized.

- HINT ON FAILED INTERACT
- If player interacts without FixedLever: brief dialogue hint ("The lever is broken...")
  This requires a Dialogue reference. Pass it via constructor or setter like Sign does.

- WHAT DRAWBRIDGELEVER DOES NOT DO
- Does not handle the BrokenLever → Blacksmith → FixedLever craft chain — that is Blacksmith NPC (Person 4).
- Does not re-open or re-close — bridge fix is permanent for the session.
*/

import acm.graphics.GCanvas;
import acm.graphics.GImage;
import acm.graphics.GRect;

import java.awt.Color;

/**
 * The broken lever fixture in C1. Lowers the bridge when the player has FixedLever.
 * One-time use — permanently opens C1's north exit.
 * See PLAN OF ACTION above before implementing.
 */
public class DrawbridgeLever extends WorldObject {

    // =========================================================
    // CONSTANTS
    // =========================================================

    private static final Color LEVER_COLOR       = new Color(120, 80, 40);
    private static final Color LEVER_FIXED_COLOR = new Color(80, 180, 80);

    /** itemId that must be in the player's inventory to use this lever. */
    public static final String FIXED_LEVER_ID = "fixed_lever";

    /** Bridge tile strip: top-left column in the C1 TileMap (centered horizontal span). */
    private static final int BRIDGE_START_COL = 10;
    /** Bridge tile strip: top row. */
    private static final int BRIDGE_START_ROW = 0;
    /** Bridge tile strip: width in tiles. */
    private static final int BRIDGE_WIDTH = 6;
    /** Bridge tile strip: height in tiles. */
    private static final int BRIDGE_HEIGHT = 2;

    // =========================================================
    // FIELDS
    // =========================================================

    /** True after the bridge has been lowered. Prevents re-interaction. */
    private boolean isFixed;

    /** C1's TileMap — bridge tiles are updated here on fix. */
    private final TileMap roomTileMap;

    /** WorldMap reference — openExit("C1", UP) is called here on fix. */
    private final WorldMap worldMap;

    /** Optional Dialogue reference for "broken lever" hint on failed interact. */
    private Dialogue dialogue;

    /** Optional sprite used when lever art is available. */
    private final GImage leverSprite;

    /** Placeholder visual until real lever sprite is ready. */
    private final GRect placeholder;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    /**
     * @param x           top-left world pixel X
     * @param y           top-left world pixel Y
     * @param roomTileMap C1's TileMap (bridge tiles will be updated here)
     * @param worldMap    the WorldMap (openExit will be called here)
     */
    public DrawbridgeLever(double x, double y, TileMap roomTileMap, WorldMap worldMap) {
        super(x, y, 48, 48);
        this.roomTileMap = roomTileMap;
        this.worldMap    = worldMap;
        this.isFixed     = false;
        this.leverSprite = loadSprite("assets/visuals/png's/button.png");

        this.placeholder = new GRect(x, y, 48, 48);
        this.placeholder.setFilled(true);
        this.placeholder.setFillColor(LEVER_COLOR);
    }

    // =========================================================
    // WORLD OBJECT OVERRIDES
    // =========================================================

    @Override
    public void draw(GCanvas canvas) {
        if (!visible) return;
        if (leverSprite != null) {
            canvas.add(leverSprite);
        } else {
            canvas.add(placeholder);
        }
    }

    @Override
    public void removeFrom(GCanvas canvas) {
        if (leverSprite != null) {
            canvas.remove(leverSprite);
        }
        canvas.remove(placeholder);
    }

    /**
     * Called by Room when the player presses J while facing this lever.
     * Checks for FixedLever in inventory, fixes the bridge if found.
     *
     * @param p the Player interacting
     */
    @Override
    public boolean isInteractable() {
        return !isFixed;
    }

    @Override
    public void onInteract(Player p) {
        if (isFixed) return;

        Item lever = p.findInventoryItem(FIXED_LEVER_ID);
        if (lever == null) {
            if (dialogue != null && !dialogue.isOpen()) {
                GamePlayState.setCurrent(GamePlayState.DIALOGUE);
                dialogue.open(
                    new String[]{"The lever is broken. A blacksmith might be able to fix it."},
                    "Drawbridge Lever",
                    false,
                    () -> GamePlayState.setCurrent(GamePlayState.PLAYING)
                );
            }
            return;
        }

        p.consumeInventoryItem(lever);
        isFixed = true;
        if (leverSprite == null) {
            placeholder.setFillColor(LEVER_FIXED_COLOR);
        }

        for (int col = BRIDGE_START_COL; col < BRIDGE_START_COL + BRIDGE_WIDTH; col++) {
            for (int row = BRIDGE_START_ROW; row < BRIDGE_START_ROW + BRIDGE_HEIGHT; row++) {
                roomTileMap.setTileType(col, row, Tile.TileType.BRIDGE, "assets/tile_floor.png");
            }
        }

        worldMap.openExit("C1", Direction.UP);

        if (dialogue != null && !dialogue.isOpen()) {
            GamePlayState.setCurrent(GamePlayState.DIALOGUE);
            dialogue.open(
                new String[]{
                    "You inserted the repaired lever and pulled it.",
                    "The drawbridge lowers with a heavy groan. The path north is now open!"
                },
                "Drawbridge",
                false,
                () -> GamePlayState.setCurrent(GamePlayState.PLAYING)
            );
        }
    }

    @Override
    public void panVisual(double panX, double panY) {
        if (leverSprite != null) {
            leverSprite.move(panX, panY);
        }
        placeholder.move(panX, panY);
    }

    @Override
    public void resetVisualPosition() {
        if (leverSprite != null) {
            leverSprite.setLocation(x, y);
        }
        placeholder.setLocation(x, y);
    }

    // =========================================================
    // SETTERS
    // =========================================================

    public void setDialogue(Dialogue d) { this.dialogue = d; }

    // =========================================================
    // GETTERS
    // =========================================================

    public boolean isFixed() { return isFixed; }

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
