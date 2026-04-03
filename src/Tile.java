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


    /** FLOOR, BRIDGE, and HOLE are enterable. WALL is the only blocking tile. */
    public boolean isPassable() {
        return type != TileType.WALL;
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

    /**
     * Shifts this tile's visual sprite by (panX, panY) pixels on the canvas.
     * Only the on-screen position changes — the tile's grid coordinates (col, row) are unchanged.
     * Called by TileMap.panAll() during room-to-room pan animations.
     *
     * @param panX horizontal pixels to shift (negative = left, positive = right)
     * @param panY vertical pixels to shift (negative = up, positive = down)
     */
    public void pan(double panX, double panY) {
        if (sprite != null) {
            sprite.move(panX, panY);
        }
    }

    /**
     * Snaps this tile's visual sprite back to its canonical grid position.
     * Must be called before re-adding a room to the canvas after it was panned off-screen,
     * otherwise the sprite stays at the shifted position from the last pan animation
     * and the room appears blank on re-entry.
     *
     * Canonical position:
     *   X = col * 48 + TileMap.MAP_OFFSET_X
     *   Y = row * 48
     *
     * // RIG POINT: When real tile art replaces the GRect placeholders, this method still applies —
     * //            sprite.setLocation() works on GImage as well as GRect.
     */
    public void resetVisualPosition() {
        if (sprite != null) {
            sprite.setLocation(col * 48 + TileMap.MAP_OFFSET_X, row * 48);
        }
    }
}

