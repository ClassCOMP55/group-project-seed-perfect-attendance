import acm.graphics.*;
import java.awt.Color;

public class Tile {
    public enum TileType { FLOOR, WALL, HOLE, BRIDGE }


    private TileType type;
    private GRect sprite;
    private int col, row;

    // Placeholder colors — replace with GImage once tile sprites are ready
    private static final Color COLOR_FLOOR  = new Color(90, 110, 80);
    private static final Color COLOR_WALL   = new Color(55, 50, 45);
    private static final Color COLOR_HOLE   = new Color(15, 15, 25);
    private static final Color COLOR_BRIDGE = new Color(140, 100, 60);

    public Tile(TileType type, int col, int row, String spritePath) {
        this.type = type;
        this.col = col;
        this.row = row;
        this.sprite = new GRect(col * 48 + TileMap.MAP_OFFSET_X, row * 48, 48, 48);
        this.sprite.setFilled(true);
        switch (type) {
            case WALL:   this.sprite.setFillColor(COLOR_WALL);   break;
            case HOLE:   this.sprite.setFillColor(COLOR_HOLE);   break;
            case BRIDGE: this.sprite.setFillColor(COLOR_BRIDGE); break;
            default:     this.sprite.setFillColor(COLOR_FLOOR);  break;
        }
        this.sprite.setColor(new Color(0, 0, 0, 40));
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

