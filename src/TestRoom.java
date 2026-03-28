import acm.graphics.*;
import acm.program.*;
import java.awt.event.*;
import java.util.*;
public class TestRoom extends GraphicsProgram {
    private GCanvas canvas;
    private TileMap tileMap;
    private Playyer player;
    private List<Enemy> enemies;
    private List<SimpleEntity> npcs;
    private double lastTime;

    private Set<Integer> keysHeld = new HashSet<>();

    public void init() {
        canvas = getGCanvas();
        canvas.setSize(1280, 720);

        // Create world
        tileMap = new TileMap();
        player = new Playyer(200, 200, tileMap);
        enemies = new ArrayList<>();
        npcs = new ArrayList<>();

        // Spawn enemies and NPCs
        enemies.add(new Enemy(600, 300, tileMap));
        enemies.add(new Enemy(800, 400, tileMap));
        npcs.add(new SimpleEntity(400, 150, "assets/npc1.png", tileMap));
        npcs.add(new SimpleEntity(500, 200, "assets/npc2.png", tileMap));
        npcs.add(new SimpleEntity(300, 350, "assets/npc3.png", tileMap));

        tileMap.draw(canvas);
        lastTime = System.currentTimeMillis() / 1000.0;

        addKeyListeners();
        run();
    }

    public void run() {
        while (true) {
            double now = System.currentTimeMillis() / 1000.0;
            double dt = now - lastTime;
            lastTime = now;

            update(dt);
            pause(16); // ~60 FPS
        }
    }

    private void update(double dt) {
        // Input
        boolean up = isKeyHeld(KeyEvent.VK_UP);
        boolean down = isKeyHeld(KeyEvent.VK_DOWN);
        boolean left = isKeyHeld(KeyEvent.VK_LEFT);
        boolean right = isKeyHeld(KeyEvent.VK_RIGHT);
        player.updateInput(up, down, left, right, dt);

        // Update entities
        player.update(dt);
        for (Enemy e : enemies) e.update(dt);
        for (SimpleEntity npc : npcs) {} // NPCs static

        // Redraw (ACM Graphics handles this automatically for GObjects)
    }
    public void keyPressed(KeyEvent e) {
        keysHeld.add(e.getKeyCode());
    }

    public void keyReleased(KeyEvent e) {
        keysHeld.remove(e.getKeyCode());
    }

    private boolean isKeyHeld(int keyCode) {
        return keysHeld.contains(keyCode);
    }
}
