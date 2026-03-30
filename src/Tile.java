/**
 * Tile.java
 *
 * A single 64x64 pixel tile in a TileMap grid.
 *
 * TileType:
 *   FLOOR  — passable, solid ground
 *   WALL   — impassable, blocks movement and projectiles
 *   HOLE   — impassable pit; entities that step on one fall (handled by Entity)
 *   BRIDGE — passable by default; DrawbridgeLever can toggle it to HOLE
 *
 * World & Objects
 */
import acm.graphics.*;

public class Tile {

    public enum TileType { FLOOR, WALL, HOLE, BRIDGE }

    private TileType type;
    private GImage sprite;
    private int col, row;

    public Tile(TileType type, int col, int row, String spritePath) {
        this.type = type;
        this.col  = col;
        this.row  = row;
        this.sprite = new GImage(spritePath, col * 64, row * 64);
        this.sprite.setSize(64, 64);
    }

    public void draw(GCanvas canvas) {
        canvas.add(sprite);
    }

    /** FLOOR and BRIDGE are both walkable. WALL and HOLE block movement. */
    public boolean isPassable() {
        return type == TileType.FLOOR || type == TileType.BRIDGE;
    }

    public boolean isHole()    { return type == TileType.HOLE; }
    public boolean isBridge()  { return type == TileType.BRIDGE; }

    /** Returns the raw tile type — used by DrawbridgeLever and Room to inspect tile state. */
    public TileType getType()  { return type; }

    public int getCol() { return col; }
    public int getRow() { return row; }
}

