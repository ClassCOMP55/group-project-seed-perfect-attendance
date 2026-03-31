import acm.graphics.*;

public class TileMap {
    private Tile[][] tiles;
    private int cols = 20;
    private int rows = 10;
    private int tileSize = 64;

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
     * Compact walkable room for the opening sequence (~704×512 px), fits the default window.
     * Border walls, floor inside, no holes so the tutorial flow is not interrupted by pits.
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
        int col = (int) (px / tileSize);
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


