import acm.graphics.*;
public class Tile {
    public enum TileType { FLOOR, WALL, HOLE, BRIDGE }


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


    /** FLOOR and BRIDGE are both walkable. WALL and HOLE block movement. */
    public boolean isPassable() {
        return type == TileType.FLOOR || type == TileType.BRIDGE;
    }


    public boolean isHole() {
        return type == TileType.HOLE;
    }

    public boolean isBridge() {
        return type == TileType.BRIDGE;
    }

    /** Returns the raw tile type — used by drawbridge / room logic. */
    public TileType getType() {
        return type;
    }


    public int getCol() { return col; }
    public int getRow() { return row; }
}

