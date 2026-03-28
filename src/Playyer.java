public class Playyer extends SimpleEntity { // Player class has two y's just in case we still need player
    private static final double SPEED = 100.0; // Pixels per second

    public Playyer(double x, double y, TileMap tileMap) {
        super(x, y, "assets/player.png", tileMap);
    } // This extends SimpleEntity

    public void updateInput(boolean up, boolean down, boolean left, boolean right, double dt) {
        vx = 0; vy = 0;
        if (up) vy -= SPEED;
        if (down) vy += SPEED;
        if (left) vx -= SPEED;
        if (right) vx += SPEED;
        update(dt);
    }
}
