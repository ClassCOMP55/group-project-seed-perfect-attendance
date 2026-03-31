import acm.graphics.*;
public class Tile {
    public enum TileType { FLOOR, WALL, HOLE }


    private TileType type;
    private GImage sprite;
    private int col, row;


    public Tile(TileType type, int col, int row, String spritePath) {
        this.type = type;
        this.col = col;
        this.row = row;
        this.sprite = new GImage(spritePath, col * 64, row * 64);
        this.sprite.setSize(64, 64);
    }


    public void draw(GCanvas canvas) {
        canvas.add(sprite);
    }

    /** Removes this tile's sprite from the canvas (pair with {@link #draw}). */
    public void removeFrom(GCanvas canvas) {
        canvas.remove(sprite);
    }


    public boolean isPassable() {
        return type == TileType.FLOOR;
    }


    public boolean isHole() {
        return type == TileType.HOLE;
    }


    public int getCol() { return col; }
    public int getRow() { return row; }
}

