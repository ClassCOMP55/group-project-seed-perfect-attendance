/**
 * TileMap.java
 *
 * A 20x10 grid of Tiles representing one room's floor plan (1280x640 px at 64px tiles).
 *
 * Construction:
 *   new TileMap()           — defaults to market layout (backwards-compatible)
 *   new TileMap("roomId")   — picks a named layout; unknown IDs fall back to market
 *
 * Room IDs: "market", "dungeon1", "dungeon2", "dungeon3" (boss), "forest", "ore"
 *
 * Drawbridge support:
 *   setTileType(col, row, type, sprite) — mutates a tile in place; used by DrawbridgeLever
 *   getTileAt(col, row)                 — grid-index lookup; used by DrawbridgeLever
 *
 * Person 2 — World & Objects
 */
import acm.graphics.*;

public class TileMap {

    private Tile[][] tiles;
    private int cols     = 20;
    private int rows     = 10;
    private int tileSize = 64;

    // ==========================================================
    // CONSTRUCTORS
    // ==========================================================

    /** Backwards-compatible default: generates the market layout. */
    public TileMap() {
        tiles = new Tile[rows][cols];
        generateMarket();
    }

    /**
     * Generates a named room layout.
     * Unknown IDs fall back to the market layout.
     *
     * @param roomId One of: "market", "dungeon1", "dungeon2", "dungeon3", "forest", "ore"
     */
    public TileMap(String roomId) {
        tiles = new Tile[rows][cols];
        switch (roomId) {
            case "dungeon1": generateDungeon1(); break;
            case "dungeon2": generateDungeon2(); break;
            case "dungeon3": generateDungeon3(); break;
            case "forest":   generateForest();   break;
            case "ore":      generateOre();      break;
            default:         generateMarket();   break;
        }
    }

    // ==========================================================
    // ROOM GENERATORS
    // ==========================================================

    /**
     * Shared helper: fills the grid with border walls and interior floors.
     * Holes and special tiles are added by callers on top of this base.
     */
    private void fillBorderWalls() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (r == 0 || r == rows - 1 || c == 0 || c == cols - 1) {
                    tiles[r][c] = new Tile(Tile.TileType.WALL, c, r, "assets/tile_wall.png");
                } else {
                    tiles[r][c] = new Tile(Tile.TileType.FLOOR, c, r, "assets/tile_floor.png");
                }
            }
        }
    }

    private void generateMarket() {
        fillBorderWalls();
        // Two decorative holes from the original layout
        tiles[5][5]  = new Tile(Tile.TileType.HOLE, 5,  5,  "assets/tile_hole.png");
        tiles[3][15] = new Tile(Tile.TileType.HOLE, 15, 3,  "assets/tile_hole.png");
    }

    /**
     * Dungeon Room 1 — introductory combat room.
     * Interior walls form two pillars the player can use as cover.
     * TODO [P2]: refine layout with actual dungeon design.
     */
    private void generateDungeon1() {
        fillBorderWalls();
        // Two interior wall pillars for cover
        for (int r = 3; r <= 6; r++) {
            tiles[r][5]  = new Tile(Tile.TileType.WALL, 5,  r, "assets/tile_wall.png");
            tiles[r][14] = new Tile(Tile.TileType.WALL, 14, r, "assets/tile_wall.png");
        }
    }

    /**
     * Dungeon Room 2 — armor enemy room with a bridge puzzle.
     * A 3-tile BRIDGE spans a hole gap; DrawbridgeLever can retract it.
     * TODO [P2]: refine layout and place bridge + lever positions.
     */
    private void generateDungeon2() {
        fillBorderWalls();
        // Hole gap (cols 8–12, rows 3–6)
        for (int r = 3; r <= 6; r++) {
            for (int c = 8; c <= 12; c++) {
                tiles[r][c] = new Tile(Tile.TileType.HOLE, c, r, "assets/tile_hole.png");
            }
        }
        // Bridge across the middle of the gap (row 4, cols 8–12)
        for (int c = 8; c <= 12; c++) {
            tiles[4][c] = new Tile(Tile.TileType.BRIDGE, c, 4, "assets/tile_floor.png");
        }
    }

    /**
     * Dungeon Room 3 — Boss arena. Open floor, no obstacles, so the Boss has room to lunge.
     * TODO [P2]: add decorative wall detail around the edges.
     */
    private void generateDungeon3() {
        fillBorderWalls();
        // Intentionally open — Boss needs space to move
    }

    /**
     * Forest room — outdoor feel, scattered holes as pits between tree roots.
     * TODO [P2]: refine with actual forest layout.
     */
    private void generateForest() {
        fillBorderWalls();
        // Scattered pits
        int[][] pits = { {2,3},{2,16},{5,7},{5,12},{7,2},{7,17} };
        for (int[] p : pits) {
            tiles[p[0]][p[1]] = new Tile(Tile.TileType.HOLE, p[1], p[0], "assets/tile_hole.png");
        }
    }

    /**
     * Ore room — wide open with OreNode placements (placed by Room, not TileMap).
     * Interior wall clusters break up the space.
     * TODO [P2]: refine layout.
     */
    private void generateOre() {
        fillBorderWalls();
        // Small wall clusters
        for (int r = 2; r <= 4; r++) {
            tiles[r][10] = new Tile(Tile.TileType.WALL, 10, r, "assets/tile_wall.png");
        }
        for (int r = 6; r <= 8; r++) {
            tiles[r][10] = new Tile(Tile.TileType.WALL, 10, r, "assets/tile_wall.png");
        }
    }

    // ==========================================================
    // DRAW
    // ==========================================================

    public void draw(GCanvas canvas) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (tiles[r][c] != null) tiles[r][c].draw(canvas);
            }
        }
    }

    // ==========================================================
    // TILE LOOKUPS
    // ==========================================================

    /** Pixel-coordinate lookup — used by Entity.move() and isPassable/isHole checks. */
    public Tile getTileAtPixel(double px, double py) {
        int col = (int)(px / tileSize);
        int row = (int)(py / tileSize);
        if (col >= 0 && col < cols && row >= 0 && row < rows) return tiles[row][col];
        return null;
    }

    /**
     * Grid-index lookup — used by DrawbridgeLever to find and inspect bridge tiles.
     *
     * @param col Column index (0–19)
     * @param row Row index (0–9)
     */
    public Tile getTileAt(int col, int row) {
        if (col >= 0 && col < cols && row >= 0 && row < rows) return tiles[row][col];
        return null;
    }

    public boolean isPassable(double px, double py) {
        Tile tile = getTileAtPixel(px, py);
        return tile != null && tile.isPassable();
    }

    public boolean isHole(double px, double py) {
        Tile tile = getTileAtPixel(px, py);
        return tile != null && tile.isHole();
    }

    // ==========================================================
    // TILE MUTATION — bridge / lever logic
    // ==========================================================

    /**
     * Replaces a tile in place. Used by DrawbridgeLever to toggle BRIDGE ↔ HOLE.
     *
     * @param col        Column index
     * @param row        Row index
     * @param type       New tile type
     * @param spritePath Sprite asset for the new tile
     */
    public void setTileType(int col, int row, Tile.TileType type, String spritePath) {
        if (col >= 0 && col < cols && row >= 0 && row < rows) {
            tiles[row][col] = new Tile(type, col, row, spritePath);
        }
    }

    // ==========================================================
    // GETTERS
    // ==========================================================

    public int getCols()     { return cols; }
    public int getRows()     { return rows; }
    public int getTileSize() { return tileSize; }
}
