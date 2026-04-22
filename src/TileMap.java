import acm.graphics.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
Person 2: TileMap — 2D grid of Tile objects for one Room.
Who RIGs it: Room — creates one TileMap per room, calls draw() and removeFrom().
             WorldMap — rooms are initialized with TileMaps on construction.
             Collision: Player and Entity movement queries isPassable() / isHole() each tick.

===============
TILE SIZE DECISION (locked 2026-04-01)
===============
- Tile size: 48px.
- Room grid: 26 cols × 15 rows = 1248 × 720 px.
- Window: 1280 × 720. The 32px horizontal gap is split evenly: MAP_OFFSET_X = 16px left margin.
- MAP_OFFSET_X is applied when drawing tiles and when converting pixel coords to tile coords.
- DO NOT change tileSize, cols, rows, or MAP_OFFSET_X without updating Tile.java,
  Enemy.java, ThicketGate.java, and all pixel-position constants across the project.

===============
PLAN OF ACTION
===============

- CLASS ROLE
- TileMap holds a 2D array of Tile objects for a single Room.
- TileMap does not know which room it belongs to — that is Room's job.
- TileMap answers collision questions: isPassable(), isHole().
- TileMap draws and removes all its tiles from the canvas.

- TILE COORDINATE SYSTEM
- Columns: 0 (leftmost) to 25 (rightmost). Pixel x = col * 48 + MAP_OFFSET_X.
- Rows: 0 (topmost) to 14 (bottommost). Pixel y = row * 48.
- getTileAtPixel(px, py) must subtract MAP_OFFSET_X before dividing: col = (px - MAP_OFFSET_X) / tileSize.

- ROOM LAYOUTS
- Each named room layout is a private generateXxx() method.
- All room methods call fillBorderWalls() first (border = WALL, interior = FLOOR).
- Room-specific walls, holes, bridges etc. are applied on top.
- Dummy / placeholder rooms: just fillBorderWalls() with no extra tiles.
- Real room layouts will be built out during Person 2's implementation sprint.

- SPECIAL FACTORY
- createOpeningRoom() is a TRANSITIONAL method used by P1GameplayPane.
  It will be removed once P1GameplayPane is replaced by the Room/WorldMap system.
*/

public class TileMap {
    private Tile[][] tiles;
    private int cols = 26;
    private int rows = 15;
    private int tileSize = 48;
    /** Player-only trigger strips (teleports, doors) that enemies must never enter. */
    private final List<Rectangle2D.Double> enemyBlockedZones = new ArrayList<>();
    /** Optional room-specific forbidden tiles for push blocks. Empty means unrestricted. */
    private final Set<Integer> pushBlockBlockedTiles = new HashSet<>();

    /**
     * Horizontal pixel offset that centers the 1248px-wide map inside the 1280px window.
     * Map starts at x = MAP_OFFSET_X, not x = 0.
     * All tile drawing and pixel-to-tile conversion must account for this.
     */
    public static final int MAP_OFFSET_X = (1280 - 26 * 48) / 2; // = 16

    public TileMap() {
        tiles = new Tile[rows][cols];
        generateMarket();
    }

    /**
     * Generates a named room layout.
     * Unknown IDs fall back to the market layout.
     */
    public TileMap(String roomId) {
        tiles = new Tile[rows][cols];
        switch (roomId) {
            case "dungeon1":
                generateDungeon1();
                break;
            case "dungeon2":
                generateDungeon2();
                break;
            case "dungeon3":
                generateDungeon3();
                break;
            case "forest":
                generateForest();
                break;
            case "ore":
                generateOre();
                break;
            default:
                generateMarket();
                break;
        }
    }

    /**
     * TRANSITIONAL — used only by P1GameplayPane for the opening sequence test.
     * Will be removed once P1GameplayPane is replaced by the Room/WorldMap system.
     * A1 (Market) will then be a full 26×15 room built by WorldMap.
     */
    public static TileMap createOpeningRoom() {
        TileMap m = new TileMap();
        m.cols = 11;
        m.rows = 8;
        m.tiles = new Tile[m.rows][m.cols];
        m.generateOpeningRoom();
        return m;
    }

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

    private void generateOpeningRoom() {
        fillBorderWalls();
    }

    private void generateMarket() {
        fillBorderWalls();
        tiles[5][5] = new Tile(Tile.TileType.HOLE, 5, 5, "assets/visuals/png's/hole.png");
        tiles[3][15] = new Tile(Tile.TileType.HOLE, 15, 3, "assets/visuals/png's/hole.png");
    }

    private void generateDungeon1() {
        fillBorderWalls();
        for (int r = 3; r <= 6; r++) {
            tiles[r][5] = new Tile(Tile.TileType.WALL, 5, r, "assets/tile_wall.png");
            tiles[r][14] = new Tile(Tile.TileType.WALL, 14, r, "assets/tile_wall.png");
        }
    }

    private void generateDungeon2() {
        fillBorderWalls();
        for (int r = 3; r <= 6; r++) {
            for (int c = 8; c <= 12; c++) {
                tiles[r][c] = new Tile(Tile.TileType.HOLE, c, r, "assets/visuals/png's/hole.png");
            }
        }
        for (int c = 8; c <= 12; c++) {
            tiles[4][c] = new Tile(Tile.TileType.BRIDGE, c, 4, "assets/tile_floor.png");
        }
    }

    private void generateDungeon3() {
        fillBorderWalls();
    }

    private void generateForest() {
        fillBorderWalls();
        int[][] pits = { {2, 3}, {2, 16}, {5, 7}, {5, 12}, {7, 2}, {7, 17} };
        for (int[] p : pits) {
            tiles[p[0]][p[1]] = new Tile(Tile.TileType.HOLE, p[1], p[0], "assets/visuals/png's/hole.png");
        }
    }

    private void generateOre() {
        fillBorderWalls();
        for (int r = 2; r <= 4; r++) {
            tiles[r][10] = new Tile(Tile.TileType.WALL, 10, r, "assets/tile_wall.png");
        }
        for (int r = 6; r <= 8; r++) {
            tiles[r][10] = new Tile(Tile.TileType.WALL, 10, r, "assets/tile_wall.png");
        }
    }

    public void draw(GCanvas canvas) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (tiles[r][c] != null) {
                    tiles[r][c].draw(canvas);
                }
            }
        }
    }

    /** Removes all tile sprites from the canvas. */
    public void removeFrom(GCanvas canvas) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (tiles[r][c] != null) {
                    tiles[r][c].removeFrom(canvas);
                }
            }
        }
    }

    public Tile getTileAtPixel(double px, double py) {
        int col = (int) ((px - MAP_OFFSET_X) / tileSize);
        int row = (int) (py / tileSize);
        if (col >= 0 && col < cols && row >= 0 && row < rows) {
            return tiles[row][col];
        }
        return null;
    }

    public Tile getTileAt(int col, int row) {
        if (col >= 0 && col < cols && row >= 0 && row < rows) {
            return tiles[row][col];
        }
        return null;
    }

    /**
     * Returns true if the pixel position lands inside this room's tile grid.
     *
     * Use this when callers need to distinguish "inside the room but blocked"
     * from "outside the room entirely". Enemy movement uses this so only the
     * player can treat out-of-bounds space as an exit zone.
     */
    public boolean containsPixel(double px, double py) {
        return px >= MAP_OFFSET_X
            && px < MAP_OFFSET_X + getWidthPixels()
            && py >= 0
            && py < getHeightPixels();
    }

    /**
     * Returns true if the given pixel position is walkable.
     *
     * Out-of-bounds positions (no tile at that coordinate) return {@code true} so
     * the player can step past the tile grid's edge and trigger a room exit.
     * This is intentional for the tech demo: all tiles are open by default, and
     * the Room/WorldMap layer handles what happens at the boundary.
     *
     * // TECH DEMO: null → passable is correct because every room uses
     * //            TileMap.createDummyAllFloor() (no border walls).
     * // RIG POINT: When real room layouts replace buildDummy(), border WALL tiles
     * //            will block the player before any out-of-bounds probe can fire,
     * //            so this null-passable behavior stays safe. To block a tile later,
     * //            call TileMap.setTileType(col, row, TileType.WALL, path) — one line.
     */
    public boolean isPassable(double px, double py) {
        Tile tile = getTileAtPixel(px, py);
        if (tile == null) return true; // out-of-bounds = exit zone; let Room/WorldMap decide
        return tile.isPassable();
    }

    /**
     * Enemy-specific movement query.
     * Uses the normal tile passability plus extra player-only trigger strips that enemies
     * must treat as blocked even though the player can still walk onto them.
     */
    public boolean isEnemyPassable(double px, double py) {
        if (!isPassable(px, py)) {
            return false;
        }
        for (Rectangle2D.Double zone : enemyBlockedZones) {
            if (zone.contains(px, py)) {
                return false;
            }
        }
        return true;
    }

    public boolean isHole(double px, double py) {
        Tile tile = getTileAtPixel(px, py);
        return tile != null && tile.isHole();
    }

    /** Push-block-specific movement query with optional room-local safety barriers. */
    public boolean isPushBlockPassable(int col, int row) {
        Tile tile = getTileAt(col, row);
        if (tile == null || !tile.isPassable()) {
            return false;
        }
        return !pushBlockBlockedTiles.contains(encodeTile(col, row));
    }

    /**
     * Marks a tile-aligned rectangle as blocked for enemies only.
     * Use this for teleport/door trigger strips that stay walkable for the player.
     */
    public void addEnemyBlockedZoneByTiles(int startCol, int startRow, int widthTiles, int heightTiles) {
        if (widthTiles <= 0 || heightTiles <= 0) {
            return;
        }
        double x = MAP_OFFSET_X + startCol * tileSize;
        double y = startRow * tileSize;
        enemyBlockedZones.add(new Rectangle2D.Double(
            x,
            y,
            widthTiles * tileSize,
            heightTiles * tileSize
        ));
    }

    /** Blocks push blocks from stepping onto a curated set of tiles for puzzle guard rails. */
    public void blockPushBlocksOnTiles(int[][] blockedTiles) {
        pushBlockBlockedTiles.clear();
        if (blockedTiles == null) {
            return;
        }
        for (int[] tile : blockedTiles) {
            if (tile == null || tile.length < 2) {
                continue;
            }
            int col = tile[0];
            int row = tile[1];
            if (getTileAt(col, row) != null) {
                pushBlockBlockedTiles.add(encodeTile(col, row));
            }
        }
    }

    /** Replaces a tile in place. Used by drawbridge logic to toggle BRIDGE/Hole. */
    public void setTileType(int col, int row, Tile.TileType type, String spritePath) {
        if (col >= 0 && col < cols && row >= 0 && row < rows) {
            tiles[row][col] = new Tile(type, col, row, spritePath);
        }
    }

    // =========================================================
    // HELPERS — used by room-specific generate methods
    // =========================================================

    /** Fills an entire row with WALL tiles. */
    private void wallRow(int row) {
        for (int c = 0; c < cols; c++) {
            tiles[row][c] = new Tile(Tile.TileType.WALL, c, row, null);
        }
    }

    /**
     * Fills a row with WALL tiles, leaving a floor gap from gapC1 to gapC2 (inclusive).
     * Use this for a border row that has an exit passage through it.
     */
    private void wallRowGap(int row, int gapC1, int gapC2) {
        for (int c = 0; c < cols; c++) {
            if (c < gapC1 || c > gapC2) {
                tiles[row][c] = new Tile(Tile.TileType.WALL, c, row, null);
            }
        }
    }

    /** Fills an entire column with WALL tiles. */
    private void wallCol(int col) {
        for (int r = 0; r < rows; r++) {
            tiles[r][col] = new Tile(Tile.TileType.WALL, col, r, null);
        }
    }

    /**
     * Fills a column with WALL tiles, leaving a floor gap from gapR1 to gapR2 (inclusive).
     * Use this for a border column that has an exit passage through it.
     */
    private void wallColGap(int col, int gapR1, int gapR2) {
        for (int r = 0; r < rows; r++) {
            if (r < gapR1 || r > gapR2) {
                tiles[r][col] = new Tile(Tile.TileType.WALL, col, r, null);
            }
        }
    }

    /** Fills a rectangular region with WALL tiles. Used for solid obstacles (buildings, trees, rocks). */
    private void wallRect(int rowStart, int rowEnd, int colStart, int colEnd) {
        for (int r = rowStart; r <= rowEnd; r++) {
            for (int c = colStart; c <= colEnd; c++) {
                if (r >= 0 && r < rows && c >= 0 && c < cols) {
                    tiles[r][c] = new Tile(Tile.TileType.WALL, c, r, null);
                }
            }
        }
    }

    /** Fills a rectangular region with FLOOR tiles. Used to reopen selected tiles after bulk wall placement. */
    private void floorRect(int rowStart, int rowEnd, int colStart, int colEnd) {
        for (int r = rowStart; r <= rowEnd; r++) {
            for (int c = colStart; c <= colEnd; c++) {
                if (r >= 0 && r < rows && c >= 0 && c < cols) {
                    tiles[r][c] = new Tile(Tile.TileType.FLOOR, c, r, "assets/tile_floor.png");
                }
            }
        }
    }

    // =========================================================
    // OVERWORLD ROOM LAYOUTS — one method per room
    // Convention: start with generateAllFloor(), then apply border walls
    // (full wall on closed sides, wallXxxGap on exit sides) and interior obstacles.
    //
    // Exit gap positions (kept consistent across all rooms so entry/exit align):
    //   NORTH / SOUTH edges: cols 11–14  (4-tile gap, 192 px wide)
    //   EAST  / WEST  edges: rows  6– 8  (3-tile gap, 144 px tall)
    // =========================================================

    private void generateA1() {
        // =====================================================================
        // MARKET ROOM — wall/floor layout
        // =====================================================================
        // Exits: NORTH at cols 19-20 (row 0 gap) | SOUTH visuals align at cols 19-20, but blocked here
        //        EAST  at rows  4-5  (col 25 gap)
        // =====================================================================
        generateAllFloor();

        wallCol(0);
        wallColGap(25, 3, 13);
        wallRowGap(0, 19, 20);

        // Row 1: hedge strip — only cols 19-20 remain walkable (corridor to north exit)
        wallRect(1, 1, 0, 18);
        wallRect(1, 1, 21, 25);

        wallRowGap(14, 19, 20);
        wallRect(13, 14, 19, 20);       // barrier tiles at the south exit

        wallRect(2, 2, 1, 18);
        wallRect(2, 2, 21, 24);
        wallRect(2, 3, 10, 13);
        wallRect(3, 3, 1, 2);
        wallRect(4, 8, 20, 25);
        wallRect(4, 4, 1, 4);
        wallRect(5, 5, 1, 5);
        wallRect(6, 6, 1, 1);
        wallRect(6, 6, 6, 6);
        wallRect(7, 7, 1, 6);
        wallRect(8, 8, 1, 3);
        wallRect(9, 9, 1, 2);
        wallRect(11, 11, 2, 3);
        wallRect(11, 11, 6, 6);
        wallRect(11, 11, 22, 25);
        wallRect(12, 12, 6, 7);
        wallRect(12, 12, 22, 24);
        wallRect(13, 13, 2, 7);

        // --- requested A1 barrier adjustments ---
        wallRect(3, 3, 3, 4);
        wallRect(6, 6, 2, 5);
        wallRect(12, 12, 2, 2);
        wallRect(13, 13, 1, 1);

        floorRect(8, 8, 3, 3);
        floorRect(11, 11, 2, 3);
        floorRect(11, 11, 6, 6);
        floorRect(12, 12, 6, 7);
        floorRect(4, 4, 20, 20);
        floorRect(11, 11, 25, 25);
        floorRect(12, 12, 2, 2);

        // --- requested A1 barrier removals ---
        floorRect(12, 12, 2, 2);
        floorRect(11, 11, 25, 25);
        floorRect(4, 4, 20, 20);
    }

    private void generateB1() {
        // Inn — exits NORTH (top), EAST (right), WEST (left)
        generateAllFloor();
        wallRow(14);                  // south: no exit
        wallRowGap(0, 9, 17);         // north: wide exit gap
        wallColGap(25, 4, 10);        // east:  wide exit gap
        wallColGap(0, 3, 13);         // west:  exit gap rows 3-13 (aligns with A1 east)

        // --- inn walls / furniture footprint ---
        wallRect(1, 1, 0, 13);
        wallRect(2, 2, 1, 13);
        wallRect(0, 0, 9, 13);
        wallRect(0, 0, 17, 17);
        wallRect(1, 2, 17, 24);
        wallRect(3, 3, 16, 24);
        wallRect(4, 6, 16, 25);
        wallRect(7, 7, 16, 18);
        wallRect(7, 7, 21, 25);
        wallRect(11, 13, 21, 24);
        wallRect(12, 12, 10, 12);
        wallRect(13, 13, 10, 12);
        wallRect(12, 13, 6, 6);
        wallRect(11, 13, 0, 2);
        wallRect(5, 6, 9, 10);        // user list included "10.5"; interpreted as (10,5)
        wallRect(3, 3, 1, 5);
        wallRect(4, 4, 1, 6);
        wallRect(5, 6, 1, 5);
        wallRect(7, 7, 3, 5);

        floorRect(11, 13, 0, 0);
        floorRect(11, 11, 2, 2);
        floorRect(4, 4, 6, 6);
        floorRect(5, 6, 5, 5);
        floorRect(6, 6, 19, 20);
        floorRect(12, 12, 6, 6);
        floorRect(12, 12, 10, 12);
        floorRect(5, 6, 10, 10);
        floorRect(5, 5, 2, 4);
        floorRect(6, 6, 2, 4);
        floorRect(6, 6, 9, 9);

        // --- requested B1 barrier removals ---
        floorRect(6, 6, 9, 9);
        floorRect(6, 6, 1, 4);
        floorRect(5, 5, 2, 4);
        floorRect(6, 6, 2, 4);
        floorRect(5, 5, 9, 9);
        floorRect(6, 6, 1, 1);
        floorRect(7, 7, 16, 18);      // remove requested barriers at (16-18,7)
    }

    private void generateC1() {
        // Bridge / River — exits WEST (left) and NORTH (top)
        // River cuts diagonally from upper-left to lower-right.
        // The center bridge is broken until the lever repairs the blocked bridge strip.
        generateAllFloor();
        wallRow(14);                  // south: no exit
        wallCol(25);                  // east:  no exit
        wallRowGap(0, 9, 17);         // north: wide exit gap (reachable via bridge)
        wallColGap(0, 5, 11);         // west:  wide exit gap (rows 5-11, below river)
        floorRect(0, 0, 19, 20);      // reopen requested north-edge tiles

        // --- river / broken bridge ---
        wallRect(1, 1, 1, 6);         // upper-left river mouth
        wallRect(2, 3, 2, 8);         // upper-left diagonal run
        wallRect(4, 4, 5, 10);        // channel approaching the bridge
        wallRect(5, 6, 9, 11);        // left bank beside the bridge
        wallRect(4, 7, 12, 13);       // broken bridge gap (repaired by DrawbridgeLever)
        wallRect(4, 5, 14, 18);       // right bank beside the bridge
        wallRect(6, 7, 18, 23);       // right-hand river channel
        wallRect(8, 9, 21, 24);       // lower-right bend toward the east wall

        floorRect(2, 3, 1, 4);        // reopen the upper-left approach
        floorRect(3, 3, 2, 7);        // narrow row-3 river coverage
        floorRect(4, 4, 5, 8);        // narrow the left bank near the bridge
        floorRect(5, 6, 9, 9);        // remove extra left-bank tiles
        floorRect(6, 6, 10, 10);      // remove the single extra river tile
        floorRect(6, 7, 12, 13);      // lower half of the broken bridge stays open
        floorRect(4, 4, 15, 18);      // narrow the right bank at row 4
        floorRect(5, 5, 16, 18);      // narrow the right bank at row 5
        floorRect(6, 6, 23, 23);      // reopen requested east-side river tile
        floorRect(7, 7, 18, 18);      // remove requested barrier at (18,7)
        floorRect(7, 9, 21, 21);      // remove requested barriers at (21,7-9)
        floorRect(8, 9, 22, 22);      // remove requested barriers at (22,8-9)
        floorRect(9, 9, 23, 23);      // remove requested barrier at (23,9)
        floorRect(4, 4, 14, 14);      // remove requested barrier at (14,4)

        wallRect(0, 0, 9, 17);
        wallRect(6, 6, 15, 17);
        wallRect(5, 7, 0, 0);
        wallRect(11, 11, 0, 0);
        wallRect(1, 1, 7, 7);         // add requested barrier at (7,1)
        wallRect(3, 3, 9, 9);         // add requested barrier at (9,3)
        wallRect(0, 0, 21, 21);       // add requested barrier at (21,0)

        floorRect(0, 0, 18, 21);
        floorRect(2, 2, 5, 6);
        floorRect(6, 6, 11, 11);
        wallRect(0, 0, 18, 18);       // add requested barrier at (18,0)
    }

    private void generateA2() {
        // Forest path — exits SOUTH (bottom), NORTH (top), EAST (right)
        // South exit aligns with A1's north exit: cols 19-20
        // North exit also at cols 19-20 to align with A3's south exit
        generateAllFloor();
        wallCol(0);                   // west: no exit
        wallRowGap(14, 19, 20);       // south: exit gap cols 19-20 (aligns with A1 north)
        wallRowGap(0, 19, 20);        // north: exit gap cols 19-20 (aligns with A3 south)
        wallColGap(25, 4, 5);         // east:  exit gap rows 4-5

        // --- trees ---
        wallRect(1, 9, 11, 13);       // cols 11-13, rows 1-9
        wallRect(1, 8, 10, 10);       // col 10, rows 1-8
        wallRect(2, 7, 9, 9);         // col 9, rows 2-7
        wallRect(10, 10, 11, 13);     // barrier row beneath the central tree cluster

        // --- bushes ---
        wallRect(11, 13, 13, 13);     // col 13, rows 11-13 (row 10 walkable)
        wallRect(11, 13, 11, 12);     // cols 11-12, rows 11-13
        wallRect(12, 13, 10, 10);     // col 10, rows 12-13

        floorRect(0, 0, 15, 18);      // reopen the top edge near the northeast path
        floorRect(0, 0, 21, 25);      // reopen the rest of the top-right edge
        floorRect(0, 3, 25, 25);      // reopen the upper east edge
        floorRect(6, 13, 25, 25);     // reopen the lower east edge

        wallRect(13, 13, 1, 9);
        wallRect(13, 13, 14, 18);
        wallRect(13, 13, 21, 25);

        // --- added grass ---
        floorRect(10, 10, 1, 3);
        floorRect(11, 11, 1, 3);
        floorRect(8, 8, 3, 6);
        floorRect(9, 9, 8, 9);
        floorRect(11, 11, 7, 8);
        floorRect(12, 12, 7, 9);
        floorRect(7, 7, 1, 1);
        floorRect(6, 6, 1, 1);
        floorRect(6, 6, 2, 2);
        floorRect(7, 7, 3, 3);
        floorRect(7, 7, 4, 4);
        floorRect(6, 6, 6, 8);
        floorRect(4, 4, 1, 5);
        floorRect(3, 3, 6, 6);
        floorRect(3, 3, 7, 7);

        // Match the A2 purple debug barrier layout: rocks cannot enter these tiles.
        blockPushBlocksOnTiles(
            new int[][]{
                {1,1}, {2,1}, {3,1}, {4,1}, {6,1}, {7,1}, {8,1}, {9,1},
                {1,2}, {1,3}, {1,4}, {1,5}, {1,6}, {1,7}, {1,8}, {1,9}, {1,10}, {1,11}, {1,12},
                {2,12}, {3,12}, {4,12}, {5,12}, {6,12}, {7,12}, {8,12}, {9,12},
                {8,2}, {8,3}, {8,4}, {8,5}, {8,6}, {8,7}, {9,8},
                {10,9}, {10,10}, {10,11}
            }
        );
    }

    private void generateA3() {
        // Timed Gauntlet — exits SOUTH (bottom), EAST (right)
        generateAllFloor();
        wallRow(0);                   // north: no exit
        wallCol(0);                   // west:  no exit
        wallRowGap(14, 19, 20);       // south: exit gap cols 19-20 (aligns with A2 north)
        wallColGap(25, 4, 5);         // east:  exit gap rows 4-5

        // --- trees / foliage ---
        // Approximate the diagonal tree line without sealing the east or south approach lanes.
        wallRect(1, 2, 18, 21);       // top-right treetops
        wallRect(3, 4, 14, 21);       // upper canopy band
        wallRect(5, 6, 11, 19);       // middle canopy band
        wallRect(7, 9, 10, 16);       // lower-left canopy band
        wallRect(10, 13, 10, 13);     // lower-left shrubs / trees near the south wall

        floorRect(9, 12, 13, 13);     // remove the vertical barrier column
        floorRect(9, 9, 13, 16);      // remove the horizontal row-9 barrier run
        floorRect(1, 3, 25, 25);      // reopen the upper east edge
        floorRect(6, 14, 25, 25);     // reopen the lower east edge
        floorRect(14, 14, 15, 18);    // reopen the left side of the south edge
        floorRect(14, 14, 21, 25);    // reopen the right side of the south edge

        wallRect(13, 13, 14, 14);     // add the lower-right tree barrier
        wallRect(7, 7, 17, 17);       // extend the mid tree line
        wallRect(5, 5, 20, 20);       // extend the east-side canopy downward
        wallRect(1, 2, 17, 17);       // extend the top canopy leftward
        wallRect(4, 4, 12, 13);       // fill the row-4 canopy gap
        wallRect(5, 6, 10, 10);       // add the far-left canopy edge
        wallRect(7, 7, 9, 9);         // add the lower-left edge tile
        wallRect(8, 8, 9, 9);         // add the lower-left corner barrier
    }

    private void generateB2() {
        // Ore location — exits SOUTH (bottom), NORTH (top), WEST (left)
        generateAllFloor();
        wallCol(25);                  // east: no exit
        wallRowGap(14, 9, 17);        // south: wide exit gap
        wallRowGap(0, 9, 17);         // north: wide exit gap
        wallColGap(0, 4, 10);         // west:  wide exit gap

        // --- rocks ---
        wallRect(9, 10, 2, 3);        // upper-left rock cluster
        wallRect(11, 11, 5, 6);       // lower-right rock cluster

        floorRect(0, 3, 0, 0);        // reopen the upper west edge
        floorRect(0, 0, 0, 8);        // reopen the northwest top edge
        floorRect(0, 0, 18, 24);      // reopen the northeast top edge
        floorRect(11, 13, 0, 0);      // reopen the lower west edge

        // --- ore-route barriers ---
        wallRect(8, 8, 2, 3);
        wallRect(9, 10, 1, 1);
        wallRect(10, 10, 5, 6);
        wallRect(1, 2, 22, 23);
        wallRect(5, 6, 21, 22);
        wallRect(8, 9, 23, 24);
        wallRect(9, 10, 21, 21);
        wallRect(13, 13, 0, 13);
        wallRect(13, 13, 17, 24);
        wallRect(14, 14, 9, 13);
        wallRect(14, 14, 17, 17);
    }

    private void generateB3() {
        // Riddle forest — exits SOUTH (bottom), WEST (left)
        generateAllFloor();
        wallRow(0);                   // north: no exit
        wallCol(25);                  // east:  no exit
        wallRowGap(14, 9, 17);        // south: wide exit gap
        wallColGap(0, 4, 10);         // west:  wide exit gap

        // --- trees / hedges ---
        wallRect(1, 4, 11, 14);       // top-left grove
        wallRect(5, 7, 13, 14);       // small center-left shrub column
        wallRect(1, 8, 22, 24);       // right-side tree wall
        wallRect(2, 6, 21, 21);       // left edge of the right-side canopy
        wallRect(8, 10, 12, 24);      // bottom tree line

        floorRect(1, 3, 0, 0);        // reopen the upper west edge
        floorRect(11, 14, 0, 0);      // reopen the lower west edge
        floorRect(14, 14, 0, 8);      // reopen the southwest edge
        floorRect(14, 14, 18, 24);    // reopen the southeast edge

        // --- extra hedge blockers ---
        wallRect(1, 3, 10, 10);
        wallRect(1, 1, 21, 21);
        wallRect(7, 7, 21, 21);
    }

    private void generateC2() {
        // Forest corridor — exits SOUTH (bottom), NORTH (top)
        generateAllFloor();
        wallCol(0);                   // west: no exit
        wallCol(25);                  // east: no exit
        wallRowGap(14, 9, 17);        // south: wide exit gap
        wallRowGap(0, 9, 17);         // north: wide exit gap
        floorRect(0, 0, 5, 6);        // reopen requested north-edge tiles
        floorRect(4, 4, 19, 19);      // explicit requested interior tile
        floorRect(14, 14, 19, 20);    // reopen requested south-edge tiles

        // --- trees ---
        // Match the painted tree trunks / lower canopy so actors cannot walk through them.
        wallRect(1, 3, 9, 10);        // upper-left pine by the north path
        wallRect(1, 2, 12, 14);       // upper-middle pine tucked into the top edge
        wallRect(1, 3, 15, 16);       // upper-right pine beside the rocks
        wallRect(5, 8, 1, 3);         // west-side tree cluster
        wallRect(9, 13, 9, 11);       // large lower-middle pine
        wallRect(12, 14, 1, 2);       // southwest pine near the corner

        wallRect(11, 11, 1, 3);
        wallRect(12, 13, 3, 3);
        wallRect(13, 13, 3, 8);
        wallRect(14, 14, 9, 17);
        wallRect(13, 13, 21, 21);
        wallRect(4, 4, 16, 17);
        wallRect(0, 3, 17, 17);
        wallRect(0, 0, 9, 17);
        wallRect(1, 3, 11, 11);
        wallRect(2, 3, 8, 8);
        wallRect(4, 4, 9, 9);
        wallRect(5, 8, 4, 4);
        wallRect(5, 7, 5, 5);
        wallRect(9, 9, 2, 3);

        floorRect(2, 2, 12, 14);
        floorRect(2, 3, 17, 17);
        floorRect(13, 13, 21, 21);
        floorRect(14, 14, 18, 18);
    }

    private void generateC3() {
        // Dungeon entrance — exits SOUTH (bottom) only
        generateAllFloor();
        wallRow(0);                   // north: no exit
        wallCol(0);                   // west:  no exit
        wallCol(25);                  // east:  no exit
        wallRowGap(14, 9, 17);        // south: wide exit gap (back to C2)
        floorRect(14, 14, 5, 6);      // reopen extra south-edge tiles

        // --- trees / castle footprint ---
        // Match the painted trunks and the castle facade so actors cannot walk through them.
        wallRect(1, 4, 1, 2);         // west-edge pine at the top-left border
        wallRect(1, 4, 3, 5);         // left-center pine cluster
        wallRect(1, 4, 6, 7);         // right side of the top-left grove
        wallRect(1, 2, 14, 15);       // lone pine along the north path
        wallRect(2, 5, 16, 17);       // pine beside the castle
        wallRect(13, 14, 1, 2);       // southwest corner pine

        wallRect(0, 4, 17, 24);       // castle body / ruin facade
        floorRect(3, 4, 20, 21);      // keep the dungeon doorway walkable

        wallRect(1, 4, 8, 8);
        wallRect(2, 3, 9, 9);
        wallRect(1, 1, 12, 13);
        wallRect(2, 2, 13, 13);
        wallRect(3, 4, 15, 15);
        wallRect(1, 1, 16, 16);
        wallRect(14, 14, 9, 17);
        wallRect(13, 13, 7, 24);
        wallRect(12, 12, 1, 1);
        wallRect(13, 13, 3, 4);

        floorRect(4, 4, 3, 4);
        floorRect(13, 13, 7, 24);
    }

    // =========================================================
    // OVERWORLD ROOM FACTORIES — public static; one per room
    // =========================================================

    public static TileMap createA1() { TileMap m = new TileMap(); m.generateA1(); return m; }
    public static TileMap createB1() { TileMap m = new TileMap(); m.generateB1(); return m; }
    public static TileMap createC1() { TileMap m = new TileMap(); m.generateC1(); return m; }
    public static TileMap createA2() { TileMap m = new TileMap(); m.generateA2(); return m; }
    public static TileMap createB2() { TileMap m = new TileMap(); m.generateB2(); return m; }
    public static TileMap createC2() { TileMap m = new TileMap(); m.generateC2(); return m; }
    public static TileMap createA3() { TileMap m = new TileMap(); m.generateA3(); return m; }
    public static TileMap createB3() { TileMap m = new TileMap(); m.generateB3(); return m; }
    public static TileMap createC3() { TileMap m = new TileMap(); m.generateC3(); return m; }
    public static TileMap createD1() { TileMap m = new TileMap(); m.generateD1(); return m; }
    public static TileMap createD2() { TileMap m = new TileMap(); m.generateD2(); return m; }
    public static TileMap createD3() { TileMap m = new TileMap(); m.generateD3(); return m; }

    // =========================================================
    // TECH-DEMO FACTORY — all-floor dummy layout
    // =========================================================

    /**
     * Creates a 26×15 TileMap where every tile is a walkable FLOOR tile.
     * Used by Room.buildDummy() so that exit-detection works at all four edges
     * without border walls blocking the player.
     *
     * // TECH DEMO: This layout is only for placeholder/dummy rooms.
     * // RIG POINT: Replace the buildDummy() call in each Room with a call to
     * //            new TileMap(roomId) once that room's real layout is designed.
     *
     * @return a fresh all-floor TileMap ready for a dummy room
     */
    public static TileMap createDummyAllFloor() {
        TileMap m = new TileMap(); // initialises the grid via generateMarket(); overwritten below
        m.generateAllFloor();
        return m;
    }

    private void generateD1() {
        generateAllFloor();
        // --- border walls ---
        wallRow(0);                    // top row, cols 0-25
        wallRow(1);                    // second row, cols 0-25
        wallColGap(0, 6, 8);           // left col — walls everything except rows 6-8 (exit)
        wallColGap(25, 6, 8);          // right col — walls everything except rows 6-8 (east exit)
        wallRow(13);                   // second-to-last row, cols 0-25
        wallRow(14);                   // bottom row, cols 0-25
        // --- interior walls (obstacles/furniture) ---
        for (int c = 7; c <= 18; c++) tiles[7][c] = new Tile(Tile.TileType.WALL, c, 7, null); // row 7, cols 7-18
        tiles[8][8]  = new Tile(Tile.TileType.WALL, 8,  8, null);
        tiles[8][10] = new Tile(Tile.TileType.WALL, 10, 8, null);
        tiles[8][12] = new Tile(Tile.TileType.WALL, 12, 8, null);
        tiles[8][14] = new Tile(Tile.TileType.WALL, 14, 8, null);
        tiles[8][16] = new Tile(Tile.TileType.WALL, 16, 8, null);
        tiles[8][18] = new Tile(Tile.TileType.WALL, 18, 8, null);
        tiles[6][8]  = new Tile(Tile.TileType.WALL, 8,  6, null);
        tiles[6][10] = new Tile(Tile.TileType.WALL, 10, 6, null);
        tiles[6][12] = new Tile(Tile.TileType.WALL, 12, 6, null);
        tiles[6][14] = new Tile(Tile.TileType.WALL, 14, 6, null);
        tiles[6][16] = new Tile(Tile.TileType.WALL, 16, 6, null);
        tiles[6][18] = new Tile(Tile.TileType.WALL, 18, 6, null);
        tiles[2][4]  = new Tile(Tile.TileType.WALL, 4,  2, null);
        tiles[2][9]  = new Tile(Tile.TileType.WALL, 9,  2, null);
        tiles[2][12] = new Tile(Tile.TileType.WALL, 12, 2, null);
        tiles[2][16] = new Tile(Tile.TileType.WALL, 16, 2, null);
        tiles[2][20] = new Tile(Tile.TileType.WALL, 20, 2, null);
        tiles[12][4] = new Tile(Tile.TileType.WALL, 4,  12, null);
        tiles[12][17]= new Tile(Tile.TileType.WALL, 17, 12, null);
        // --- holes ---
        tiles[5][4]  = new Tile(Tile.TileType.HOLE, 4,  5, "assets/visuals/png's/hole.png");
        tiles[10][5] = new Tile(Tile.TileType.HOLE, 5,  10, "assets/visuals/png's/hole.png");
        tiles[11][18]= new Tile(Tile.TileType.HOLE, 18, 11, "assets/visuals/png's/hole.png");
        tiles[6][23] = new Tile(Tile.TileType.HOLE, 23, 6, "assets/visuals/png's/hole.png");
    }

    private void generateD2() {
        generateAllFloor();
        // --- border walls ---
        wallRow(0);                    // top row
        wallRow(1);                    // second row
        wallRow(13);                   // second-to-last row
        wallRow(14);                   // bottom row
        wallColGap(0, 6, 8);           // left col — walls everything except rows 6-8 (west exit)
        wallColGap(25, 6, 8);          // right col — walls everything except rows 6-8 (east exit)
        // --- interior walls ---
        tiles[2][4]  = new Tile(Tile.TileType.WALL, 4,  2, null);
        tiles[3][4]  = new Tile(Tile.TileType.WALL, 4,  3, null);
        tiles[4][4]  = new Tile(Tile.TileType.WALL, 4,  4, null);
        tiles[4][3]  = new Tile(Tile.TileType.WALL, 3,  4, null);
        tiles[2][10] = new Tile(Tile.TileType.WALL, 10, 2, null);
        tiles[2][13] = new Tile(Tile.TileType.WALL, 13, 2, null);
        tiles[2][14] = new Tile(Tile.TileType.WALL, 14, 2, null);
        tiles[3][13] = new Tile(Tile.TileType.WALL, 13, 3, null);
        tiles[3][14] = new Tile(Tile.TileType.WALL, 14, 3, null);
        tiles[2][16] = new Tile(Tile.TileType.WALL, 16, 2, null);
        tiles[3][16] = new Tile(Tile.TileType.WALL, 16, 3, null);
        tiles[3][17] = new Tile(Tile.TileType.WALL, 17, 3, null);
        for (int r = 3; r <= 5; r++) tiles[r][19] = new Tile(Tile.TileType.WALL, 19, r, null); // col 19, rows 3-5
        tiles[5][16] = new Tile(Tile.TileType.WALL, 16, 5, null);
        tiles[5][17] = new Tile(Tile.TileType.WALL, 17, 5, null);
        tiles[6][16] = new Tile(Tile.TileType.WALL, 16, 6, null);
        tiles[6][17] = new Tile(Tile.TileType.WALL, 17, 6, null);
        for (int c = 15; c <= 18; c++) tiles[8][c] = new Tile(Tile.TileType.WALL, c, 8, null); // row 8, cols 15-18
        for (int c = 10; c <= 13; c++) tiles[7][c] = new Tile(Tile.TileType.WALL, c, 7, null); // row 7, cols 10-13
        for (int c = 8;  c <= 14; c++) tiles[6][c] = new Tile(Tile.TileType.WALL, c, 6, null); // row 6, cols 8-14
        for (int c = 11; c <= 13; c++) tiles[5][c] = new Tile(Tile.TileType.WALL, c, 5, null); // row 5, cols 11-13
        tiles[4][8]  = new Tile(Tile.TileType.WALL, 8,  4, null);
        tiles[5][8]  = new Tile(Tile.TileType.WALL, 8,  5, null);
        tiles[6][5]  = new Tile(Tile.TileType.WALL, 5,  6, null);
        tiles[6][6]  = new Tile(Tile.TileType.WALL, 6,  6, null);
        for (int c = 4; c <= 6; c++) tiles[7][c] = new Tile(Tile.TileType.WALL, c, 7, null); // row 7, cols 4-6
        for (int c = 1; c <= 3; c++) tiles[9][c] = new Tile(Tile.TileType.WALL, c, 9, null); // row 9, cols 1-3
        tiles[9][4]  = new Tile(Tile.TileType.WALL, 4,  9, null);
        tiles[10][4] = new Tile(Tile.TileType.WALL, 4,  10, null);
        tiles[10][5] = new Tile(Tile.TileType.WALL, 5,  10, null);
        for (int c = 6; c <= 8; c++)  tiles[10][c] = new Tile(Tile.TileType.WALL, c, 10, null); // row 10, cols 6-8
        for (int c = 8; c <= 13; c++) tiles[9][c]  = new Tile(Tile.TileType.WALL, c, 9, null);  // row 9, cols 8-13
        for (int c = 11; c <= 16; c++) tiles[10][c] = new Tile(Tile.TileType.WALL, c, 10, null); // row 10, cols 11-16
        tiles[12][1] = new Tile(Tile.TileType.WALL, 1,  12, null);
        tiles[12][2] = new Tile(Tile.TileType.WALL, 2,  12, null);
        tiles[12][7] = new Tile(Tile.TileType.WALL, 7,  12, null);
        tiles[12][8] = new Tile(Tile.TileType.WALL, 8,  12, null);
        for (int c = 11; c <= 14; c++) tiles[12][c] = new Tile(Tile.TileType.WALL, c, 12, null); // row 12, cols 11-14
        // --- holes (cols 21-22, rows 2-6 and rows 8-12) ---
        for (int r = 2; r <= 6; r++) {
            tiles[r][21] = new Tile(Tile.TileType.HOLE, 21, r, "assets/visuals/png's/hole.png");
            tiles[r][22] = new Tile(Tile.TileType.HOLE, 22, r, "assets/visuals/png's/hole.png");
        }
        for (int r = 8; r <= 12; r++) {
            tiles[r][21] = new Tile(Tile.TileType.HOLE, 21, r, "assets/visuals/png's/hole.png");
            tiles[r][22] = new Tile(Tile.TileType.HOLE, 22, r, "assets/visuals/png's/hole.png");
        }
        // chokepoint walls at row 7 — flipped to floor when puzzle is solved
        tiles[7][21] = new Tile(Tile.TileType.WALL, 21, 7, null);
        tiles[7][22] = new Tile(Tile.TileType.WALL, 22, 7, null);
    }

    private void generateD3() {
        generateAllFloor();
        // --- border walls ---
        wallRow(0);                    // top row
        wallRow(1);                    // second row
        wallCol(0);                    // left col fully walled — no exit from D3
        wallRow(13);                   // second-to-last row
        wallRow(14);                   // bottom row
        wallCol(25);                   // right col, fully solid
        // --- interior walls ---
        tiles[2][1]  = new Tile(Tile.TileType.WALL, 1,  2, null);
        tiles[3][1]  = new Tile(Tile.TileType.WALL, 1,  3, null);
        tiles[12][1] = new Tile(Tile.TileType.WALL, 1,  12, null);
        tiles[12][2] = new Tile(Tile.TileType.WALL, 2,  12, null);
        tiles[2][13] = new Tile(Tile.TileType.WALL, 13, 2, null);
        tiles[2][14] = new Tile(Tile.TileType.WALL, 14, 2, null);
        for (int c = 20; c <= 24; c++) tiles[2][c] = new Tile(Tile.TileType.WALL, c, 2, null); // row 2, cols 20-24
        tiles[3][23] = new Tile(Tile.TileType.WALL, 23, 3, null);
        tiles[3][24] = new Tile(Tile.TileType.WALL, 24, 3, null);
        tiles[4][24] = new Tile(Tile.TileType.WALL, 24, 4, null);
        tiles[12][24]= new Tile(Tile.TileType.WALL, 24, 12, null);
        tiles[12][23]= new Tile(Tile.TileType.WALL, 23, 12, null);
        for (int c = 7; c <= 10; c++) tiles[2][c] = new Tile(Tile.TileType.WALL, c, 2, null); // row 2, cols 7-10
        // --- holes ---
        for (int r = 4; r <= 6; r++)  tiles[r][1]  = new Tile(Tile.TileType.HOLE, 1,  r, "assets/visuals/png's/hole.png");
        tiles[7][1] = new Tile(Tile.TileType.WALL, 1, 7, null);
        for (int r = 8; r <= 11; r++) tiles[r][1]  = new Tile(Tile.TileType.HOLE, 1,  r, "assets/visuals/png's/hole.png");
        for (int c = 2; c <= 6; c++)  tiles[2][c]  = new Tile(Tile.TileType.HOLE, c,  2, "assets/visuals/png's/hole.png");
        tiles[2][11] = new Tile(Tile.TileType.HOLE, 11, 2, "assets/visuals/png's/hole.png");
        tiles[2][12] = new Tile(Tile.TileType.HOLE, 12, 2, "assets/visuals/png's/hole.png");
        for (int c = 15; c <= 19; c++) tiles[2][c] = new Tile(Tile.TileType.HOLE, c,  2, "assets/visuals/png's/hole.png");
        for (int r = 5; r <= 11; r++) tiles[r][24] = new Tile(Tile.TileType.HOLE, 24, r, "assets/visuals/png's/hole.png");
        for (int c = 3; c <= 23; c++) tiles[12][c] = new Tile(Tile.TileType.HOLE, c,  12, "assets/visuals/png's/hole.png");
        tiles[5][4]  = new Tile(Tile.TileType.HOLE, 4,  5, "assets/visuals/png's/hole.png");
        tiles[5][11] = new Tile(Tile.TileType.HOLE, 11, 5, "assets/visuals/png's/hole.png");
        tiles[6][8]  = new Tile(Tile.TileType.HOLE, 8,  6, "assets/visuals/png's/hole.png");
        tiles[6][16] = new Tile(Tile.TileType.HOLE, 16, 6, "assets/visuals/png's/hole.png");
        tiles[5][21] = new Tile(Tile.TileType.HOLE, 21, 5, "assets/visuals/png's/hole.png");
        tiles[9][22] = new Tile(Tile.TileType.HOLE, 22, 9, "assets/visuals/png's/hole.png");
        tiles[10][8] = new Tile(Tile.TileType.HOLE, 8,  10, "assets/visuals/png's/hole.png");
        tiles[9][13] = new Tile(Tile.TileType.HOLE, 13, 9, "assets/visuals/png's/hole.png");
        tiles[11][16]= new Tile(Tile.TileType.HOLE, 16, 11, "assets/visuals/png's/hole.png");
    }

    /** Fills every cell in the tile grid with a FLOOR tile. No walls, no holes. */
    private void generateAllFloor() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                tiles[r][c] = new Tile(Tile.TileType.FLOOR, c, r, "assets/tile_floor.png");
            }
        }
    }

    // =========================================================
    // ROOM TRANSITION — pan support
    // =========================================================

    /**
     * Shifts every tile's on-screen sprite by (panX, panY) pixels.
     * Used by RoomTransition each animation tick to slide a room across the screen.
     * Tile grid coordinates (col, row) are not changed — only the visual position moves.
     *
     * @param panX horizontal pixels to shift (negative = left, positive = right)
     * @param panY vertical pixels to shift (negative = up, positive = down)
     */
    public void panAll(double panX, double panY) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (tiles[r][c] != null) {
                    tiles[r][c].pan(panX, panY);
                }
            }
        }
    }

    /**
     * Resets every tile's visual sprite back to its canonical grid position.
     * Call this at the start of {@link Room#addTo(GCanvas)} before drawing tiles, so that
     * any pan offset accumulated during a room-transition animation is undone before
     * the sprites are re-added to the canvas.
     *
     * Without this reset, a room that was panned off-screen during a transition will redraw
     * its tiles at the shifted (off-screen) positions, making the room appear blank on re-entry.
     *
     * // RIG POINT: When real room tile layouts replace the all-floor dummy, this method still
     * //            applies — each Tile knows its own (col, row) and resets correctly regardless
     * //            of tile type or sprite path.
     */
    public void resetAllPositions() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (tiles[r][c] != null) {
                    tiles[r][c].resetVisualPosition();
                }
            }
        }
    }

    public int getCols() {
        return cols;
    }

    public int getRows() {
        return rows;
    }

    public int getTileSize() {
        return tileSize;
    }

    /** Total width of the map in pixels (cols × tile size). */
    public double getWidthPixels() {
        return cols * (double) tileSize;
    }

    /** Total height of the map in pixels (rows × tile size). */
    public double getHeightPixels() {
        return rows * (double) tileSize;
    }

    private int encodeTile(int col, int row) {
        return row * cols + col;
    }
}


