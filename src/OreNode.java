/*
Person 2: OreNode — the mineable ore deposit in B2 that gives Ore and BrokenLever
Who RIGs it: Room (B2) — holds it in WorldObject list, routes J key to onInteract().

Extends: WorldObject

===============
PLAN OF ACTION
===============

- CLASS ROLE
- OreNode is the visually distinct interactable object in B2.
- It signals to the player (by its appearance) that it can be used.
- Mining requires the Pickaxe in inventory. Without it, show a hint dialogue.
- On successful mining: give the player Ore + BrokenLever simultaneously, remove self from room.
- One-time use. isMined stays true even on room re-entry (like Chest — permanent state).

- FIELDS
- boolean isMined           — true after player has mined this node; never resets
- Dialogue dialogue          — optional hint reference for "you need a pickaxe" message

- onInteract() BEHAVIOR
  1. If isMined, return immediately.
  2. Check player inventory for item with itemId == "pickaxe".
     If not found: dialogue hint "You'll need a pickaxe to mine this." → return.
  3. Set isMined = true.
  4. Hide this object (call hide() from WorldObject — removes hitbox and visual).
  5. Give player BOTH items simultaneously:
       p.collectItem(new Item("ore",          "Ore",           false))
       p.collectItem(new Item("broken_lever", "Broken Lever",  false))
  6. TODO: play a short mining animation (flash/particles) before hiding — TBD.

- DESIGN NOTE: Pickaxe Location
- The design doc marks Pickaxe location as TBD (Goat Wizard gift, chest, or NPC).
- OreNode only checks for "pickaxe" itemId — it does not care how the player got it.

- SAVE STATE
- isMined is persistent — once mined, OreNode is gone for the session.
- SaveData.collectedItemIds should include "ore_node_b2" as a flag once mined.
  Room checks this on load and calls oreNode.forceMined() to hide it immediately.
*/

import acm.graphics.GCanvas;

import java.util.function.Consumer;

/**
 * The ore deposit in B2. Requires Pickaxe to mine. Gives Ore + BrokenLever on success.
 * One-time use — permanently removed after mining.
 * See PLAN OF ACTION above before implementing.
 */
public class OreNode extends WorldObject {

    // =========================================================
    // CONSTANTS
    // =========================================================

    public static final String PICKAXE_ID      = "pickaxe";
    public static final String ORE_ID          = "ore";
    public static final String BROKEN_LEVER_ID = "broken_lever";
    public static final String SAVE_FLAG_ID    = "ore_node_b2";

    // =========================================================
    // FIELDS
    // =========================================================

    /** True after the player has mined this node. Never resets. */
    private boolean isMined;

    /** Optional Dialogue reference for "you need a pickaxe" hint. */
    private Dialogue dialogue;

    /** B2 tile map used to block/unblock the ore vein tiles. */
    private final TileMap tileMap;

    /** Tile coordinates covered by this ore vein. Each row is {col, row}. */
    private final int[][] oreTiles;

    /** Optional save hook used to remember that this ore node has already been mined. */
    private Consumer<String> collectedItemRecorder;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    public OreNode(TileMap tileMap, int[][] oreTiles) {
        super(computeWorldX(oreTiles), computeWorldY(oreTiles), computeWidth(oreTiles), computeHeight(oreTiles));
        this.tileMap = tileMap;
        this.oreTiles = copyTiles(oreTiles);
        this.isMined = false;

        // The ore vein itself is blocked tiles, so interaction must be reachable from nearby walkable tiles.
        // Expand the interact hitbox outward by half a tile on all sides.
        this.hitbox = new Hitbox(x - 24.0, y - 24.0, hitbox.width + 48.0, hitbox.height + 48.0);
        applyBlockedTiles();
    }

    // =========================================================
    // WORLD OBJECT OVERRIDES
    // =========================================================

    @Override
    public void draw(GCanvas canvas) {
        // Ore art is baked into the B2 background image. No overlay sprite is needed.
    }

    @Override
    public void removeFrom(GCanvas canvas) {
        // No sprite to remove.
    }

    /**
     * Called by Room when the player presses J while facing this node.
     * Requires Pickaxe in inventory. Gives Ore and BrokenLever on success.
     *
     * @param p the Player interacting
     */
    @Override
    public boolean isInteractable() {
        return !isMined;
    }

    @Override
    public void onInteract(Player p) {
        if (isMined) return;

        if (p.findInventoryItem(PICKAXE_ID) == null) {
            if (dialogue != null && !dialogue.isOpen()) {
                GamePlayState.setCurrent(GamePlayState.DIALOGUE);
                dialogue.open(
                    new String[]{"This ore vein looks mineable, but you'll need a pickaxe."},
                    "Ore Vein",
                    false,
                    () -> GamePlayState.setCurrent(GamePlayState.PLAYING)
                );
            }
            return;
        }

        isMined = true;
        applyMinedTiles();
        hide();

        if (collectedItemRecorder != null) {
            collectedItemRecorder.accept(SAVE_FLAG_ID);
        }

        p.collectItem(new Item(ORE_ID, "Ore", false) {
            @Override
            public String getDescription() {
                return "A hefty chunk of metal pulled from the earth, rough and unrefined. Someone who knows what they're doing could make something useful out of this.";
            }
        });

        if (dialogue != null && !dialogue.isOpen()) {
            GamePlayState.setCurrent(GamePlayState.DIALOGUE);
            dialogue.open(
                new String[]{
                    "You mined the ore vein!",
                    "Obtained Ore."
                },
                "Ore Vein",
                false,
                () -> GamePlayState.setCurrent(GamePlayState.PLAYING)
            );
        }
    }

    @Override
    public void panVisual(double panX, double panY) {
        // No sprite to pan.
    }

    @Override
    public void resetVisualPosition() {
        // No sprite to reset.
    }

    // =========================================================
    // FORCE MINED — called on load when SaveData shows this was already mined
    // =========================================================

    /** Silently mines this node without giving items. Called during Room setup on save load. */
    public void forceMined() {
        isMined = true;
        applyMinedTiles();
        hide();
    }

    // =========================================================
    // SETTERS / GETTERS
    // =========================================================

    public void    setDialogue(Dialogue d) { this.dialogue = d; }
    public void    setCollectedItemRecorder(Consumer<String> recorder) { this.collectedItemRecorder = recorder; }
    public boolean isMined()               { return isMined; }

    /** Applies the ore-vein blocking footprint so those tiles are not walkable before mining. */
    private void applyBlockedTiles() {
        if (tileMap == null) return;
        for (int[] tile : oreTiles) {
            tileMap.setTileType(tile[0], tile[1], Tile.TileType.WALL, "assets/tile_wall.png");
        }
    }

    /** Clears the ore-vein footprint after mining so the path becomes walkable. */
    private void applyMinedTiles() {
        if (tileMap == null) return;
        for (int[] tile : oreTiles) {
            tileMap.setTileType(tile[0], tile[1], Tile.TileType.FLOOR, "assets/tile_floor.png");
        }
    }

    private static int[][] copyTiles(int[][] source) {
        if (source == null) return new int[0][0];
        int[][] copy = new int[source.length][2];
        for (int i = 0; i < source.length; i++) {
            copy[i][0] = source[i][0];
            copy[i][1] = source[i][1];
        }
        return copy;
    }

    private static double computeWorldX(int[][] tiles) {
        int minCol = Integer.MAX_VALUE;
        for (int[] tile : tiles) {
            if (tile[0] < minCol) minCol = tile[0];
        }
        return TileMap.MAP_OFFSET_X + minCol * 48.0;
    }

    private static double computeWorldY(int[][] tiles) {
        int minRow = Integer.MAX_VALUE;
        for (int[] tile : tiles) {
            if (tile[1] < minRow) minRow = tile[1];
        }
        return minRow * 48.0;
    }

    private static double computeWidth(int[][] tiles) {
        int minCol = Integer.MAX_VALUE;
        int maxCol = Integer.MIN_VALUE;
        for (int[] tile : tiles) {
            if (tile[0] < minCol) minCol = tile[0];
            if (tile[0] > maxCol) maxCol = tile[0];
        }
        return (maxCol - minCol + 1) * 48.0;
    }

    private static double computeHeight(int[][] tiles) {
        int minRow = Integer.MAX_VALUE;
        int maxRow = Integer.MIN_VALUE;
        for (int[] tile : tiles) {
            if (tile[1] < minRow) minRow = tile[1];
            if (tile[1] > maxRow) maxRow = tile[1];
        }
        return (maxRow - minRow + 1) * 48.0;
    }
}
