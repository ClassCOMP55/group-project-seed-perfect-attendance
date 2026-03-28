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
