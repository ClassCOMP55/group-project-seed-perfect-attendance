import acm.graphics.*;
import java.util.*;
public class TileMap {
    private Tile[][] tiles;
    private int cols = 20, rows = 10; // Fits 1280x640 at 64px tiles but can change if wanted
    private int tileSize = 64;


    public TileMap() {
        tiles = new Tile[rows][cols];
        generateMarket();
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

    private void generateOpeningRoom() {
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
        // Floor everywhere except borders and holes :)
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (r == 0 || r == rows-1 || c == 0 || c == cols-1) {
                    tiles[r][c] = new Tile(Tile.TileType.WALL, c, r, "assets/tile_wall.png");
                } else if ((c == 5 && r == 5) || (c == 15 && r == 3)) { // 2 holes
                    tiles[r][c] = new Tile(Tile.TileType.HOLE, c, r, "assets/tile_hole.png");
                } else {
                    tiles[r][c] = new Tile(Tile.TileType.FLOOR, c, r, "assets/tile_floor.png");
                }
            }
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
        int col = (int)(px / tileSize);
        int row = (int)(py / tileSize);
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
}


