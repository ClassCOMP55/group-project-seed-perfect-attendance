import acm.graphics.*;
public class SimpleEntity {
    protected double x, y;
    protected double vx, vy;
    protected GImage sprite;
    protected TileMap tileMap;


    public SimpleEntity(double x, double y, String spritePath, TileMap tileMap) {
        this.x = x;
        this.y = y;
        this.sprite = new GImage(spritePath, x, y);
        this.sprite.setSize(48, 48);
        this.tileMap = tileMap;
    }
    //This is for Player + Enemy base
    public void update(double dt) {
        // Move
        double newX = x + vx * dt;
        double newY = y + vy * dt;


        // Collision
        if (tileMap.isPassable(newX + 24, y + 24) &&
                tileMap.isPassable(newX + 24, y + 48) &&
                tileMap.isPassable(newX - 24, y + 24) &&
                tileMap.isPassable(newX - 24, y + 48)) {
            x = newX;
        }
        if (tileMap.isPassable(x + 24, newY + 24) &&
                tileMap.isPassable(x + 24, newY + 48) &&
                tileMap.isPassable(x - 24, newY + 24) &&
                tileMap.isPassable(x - 24, newY + 48)) {
            y = newY;
        }


        // Hole check
        if (tileMap.isHole(x, y)) {
            x = 100; y = 100; // Respawn
        }


        sprite.setLocation(x - 24, y - 24);
    }


    public void draw(GCanvas canvas) {
        canvas.add(sprite);
    }


    // Getters
    public double getX() { return x; }
    public double getY() { return y; }
    public void setVelocity(double vx, double vy) { this.vx = vx; this.vy = vy; }
}

