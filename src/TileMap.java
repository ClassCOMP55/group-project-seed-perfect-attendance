import acm.graphics.*;

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
        tiles[5][5] = new Tile(Tile.TileType.HOLE, 5, 5, "assets/tile_hole.png");
        tiles[3][15] = new Tile(Tile.TileType.HOLE, 15, 3, "assets/tile_hole.png");
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
                tiles[r][c] = new Tile(Tile.TileType.HOLE, c, r, "assets/tile_hole.png");
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
            tiles[p[0]][p[1]] = new Tile(Tile.TileType.HOLE, p[1], p[0], "assets/tile_hole.png");
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

    public boolean isPassable(double px, double py) {
        Tile tile = getTileAtPixel(px, py);
        return tile != null && tile.isPassable();
    }

    public boolean isHole(double px, double py) {
        Tile tile = getTileAtPixel(px, py);
        return tile != null && tile.isHole();
    }

    /** Replaces a tile in place. Used by drawbridge logic to toggle BRIDGE/Hole. */
    public void setTileType(int col, int row, Tile.TileType type, String spritePath) {
        if (col >= 0 && col < cols && row >= 0 && row < rows) {
            tiles[row][col] = new Tile(type, col, row, spritePath);
        }
    }

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
}


