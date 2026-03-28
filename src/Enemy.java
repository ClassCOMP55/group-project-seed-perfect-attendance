import acm.util.RandomGenerator;
public class Enemy extends SimpleEntity {
    private double idleTimer = 0;
    private RandomGenerator rgen = RandomGenerator.getInstance();
// Extends SimpleEntity
    public Enemy(double x, double y, TileMap tileMap) {
        super(x, y, "assets/enemy.png", tileMap);
    }

    public void update(double dt) {
        idleTimer += dt;
        if (idleTimer > 2.0) { // Change direction every 2 seconds
            vx = (rgen.nextBoolean() ? 1 : -1) * 50;
            vy = (rgen.nextBoolean() ? 1 : -1) * 50;
            idleTimer = 0;
        }
        update(dt); // Call parent
    }
}
