/*
Person 2+4: Coin — a currency drop that is auto-collected when the player walks over it
Who RIGs it: Room — holds dropped Coins in its List<Item> droppedItems.
               Each tick: Room checks if any dropped Coin's world position overlaps the player.
               If yes: calls coin.onCollect(player), then removes the coin from droppedItems and canvas.
             Grass.onHit() — creates new Coin instances via dropCallback and gives them to Room.
             MeleeEnemy / Enemy.onDeath() — creates Coins and gives them to Room's droppedItems list.

Extends: Item

===============
PLAN OF ACTION
===============

- CLASS ROLE
- Coin is the game's currency drop.
- It does NOT go into the player's inventory — it directly increments player.coins on collect.
- Coin is auto-collected on player contact (no button press needed).
- Coin.onCollect() is called by Room's per-tick overlap check, NOT by J key.

- FIELDS
- int value   — how many coins this drop is worth. Default: 1. (TBD per design doc economy)
- GRect worldSprite — placeholder visual shown when the coin is lying on the ground

- onCollect() BEHAVIOR
  1. player.coins += value
  2. Room removes this coin from droppedItems and calls coin.removeFrom(canvas).
  3. TODO: play collect SFX (SoundManager.play("coin_collect") — wire later)

- WORLD POSITION
- Coin inherits worldX, worldY from Item.
- Room sets these when a coin is created as a drop (from Grass or enemy death).
- Room checks: if player hitbox overlaps a circle/rect at (worldX, worldY) → collect.

- COIN ECONOMY (TBD per design doc)
- Default drop value: 1 coin.
- Grass drop: 1 coin (if roll succeeds).
- Enemy drops: 1–3 coins random. TBD — the exact amounts are "Still To Decide" in the design doc.
- Bread cost at BreadMerchant: 5 coins. TBD.
- Do not hardcode economy values into this class — keep value as a constructor parameter.
*/

import acm.graphics.GCanvas;
import acm.graphics.GImage;
import acm.graphics.GRect;

import java.awt.image.BufferedImage;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * A dropped coin that auto-collects when the player walks over it.
 * Extends Item but bypasses inventory — directly increments player.coins.
 * See PLAN OF ACTION above before implementing.
 */
public class Coin extends Item {

    // =========================================================
    // CONSTANTS
    // =========================================================

    private static final Color COIN_COLOR = new Color(240, 200, 40);
    private static final double DROP_SIZE = 26.0;

    /** Default coin value. Economy amounts TBD per design doc. */
    private static final int DEFAULT_VALUE = 1;
    private static final double DROP_HALF = DROP_SIZE / 2.0;

    // =========================================================
    // FIELDS
    // =========================================================

    /** How many coins this drop is worth. */
    private final int value;

    /** Placeholder visual shown on the ground until a real coin sprite is wired. */
    private final GRect worldSprite;

    /** Optional coin sprite loaded from assets. */
    private final GImage worldSpriteImage;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    /**
     * Creates a Coin drop at the given world position with default value (1).
     *
     * @param worldX world pixel X of the drop
     * @param worldY world pixel Y of the drop
     */
    public Coin(double worldX, double worldY) {
        this(worldX, worldY, DEFAULT_VALUE);
    }

    /**
     * Creates a Coin drop at the given world position with a specific value.
     *
     * @param worldX world pixel X of the drop
     * @param worldY world pixel Y of the drop
     * @param value  how many coins this drop is worth
     */
    public Coin(double worldX, double worldY, int value) {
        super("coin", "Coin", true); // stackable = true but coins don't go into inventory
        this.value = value;
        double topLeftX = worldX - DROP_SIZE / 2.0;
        double topLeftY = worldY - DROP_SIZE / 2.0;
        setWorldPosition(topLeftX, topLeftY);
        this.worldSpriteImage = loadSprite("assets/visuals/png's/coin.png");

        this.worldSprite = new GRect(topLeftX, topLeftY, DROP_SIZE, DROP_SIZE);
        this.worldSprite.setFilled(true);
        this.worldSprite.setFillColor(COIN_COLOR);
        this.worldSprite.setColor(Color.BLACK);
    }

    // =========================================================
    // ITEM OVERRIDES
    // =========================================================

    /**
     * Auto-collect: directly increments player.coins.
     * Does NOT add this coin to the player's inventory.
     * Called by Room when the player overlaps this coin on the ground.
     *
     * @param p the Player who collected this coin
     */
    @Override
    public void onCollect(Player p) {
        if (p != null) {
            p.addCoins(value);
        }
        GameSFX.play(GameSFX.SFX.COIN_PICKUP);
        inWorld = false;
        // Room removes this from droppedItems and calls removeFrom(canvas) after onCollect returns
    }

    @Override
    public void draw(GCanvas canvas) {
        if (!inWorld) return;
        resetVisualPosition();
        if (worldSpriteImage != null) {
            canvas.add(worldSpriteImage);
        } else if (worldSprite != null) {
            canvas.add(worldSprite);
        }
    }

    @Override
    public void removeFrom(GCanvas canvas) {
        if (worldSpriteImage != null) canvas.remove(worldSpriteImage);
        if (worldSprite != null) canvas.remove(worldSprite);
    }

    /** Coins are not usable from inventory. */
    @Override
    public boolean isUsable() { return false; }

    @Override
    public void panVisual(double panX, double panY) {
        if (worldSpriteImage != null) {
            worldSpriteImage.move(panX, panY);
        }
        if (worldSprite != null) {
            worldSprite.move(panX, panY);
        }
    }

    @Override
    public void resetVisualPosition() {
        if (worldSpriteImage != null) {
            worldSpriteImage.setLocation(worldX, worldY);
        }
        if (worldSprite != null) {
            worldSprite.setLocation(worldX, worldY);
        }
    }

    // =========================================================
    // GETTERS
    // =========================================================

    public int getValue() { return value; }

    /**
     * Ensures this dropped coin starts on a walkable tile.
     * If spawn is invalid, place it on the tile in front of that invalid tile.
     */
    public void snapToValidSpawn(TileMap map) {
        if (!inWorld || map == null) return;
        double centerX = worldX + DROP_HALF;
        double centerY = worldY + DROP_HALF;
        if (canOccupy(map, centerX, centerY)) return;

        int tileSize = map.getTileSize();
        int blockedCol = (int) ((centerX - TileMap.MAP_OFFSET_X) / tileSize);
        int blockedRow = (int) (centerY / tileSize);

        double blockedCenterX = TileMap.MAP_OFFSET_X + blockedCol * tileSize + tileSize / 2.0;
        double blockedCenterY = blockedRow * tileSize + tileSize / 2.0;
        double dx = centerX - blockedCenterX;
        double dy = centerY - blockedCenterY;

        int[][] directionPriority = buildDirectionPriority(dx, dy);
        for (int[] dir : directionPriority) {
            int col = blockedCol + dir[0];
            int row = blockedRow + dir[1];
            double candidateCenterX = TileMap.MAP_OFFSET_X + col * tileSize + tileSize / 2.0;
            double candidateCenterY = row * tileSize + tileSize / 2.0;
            if (canOccupy(map, candidateCenterX, candidateCenterY)) {
                worldX = candidateCenterX - DROP_HALF;
                worldY = candidateCenterY - DROP_HALF;
                resetVisualPosition();
                return;
            }
        }
    }

    private int[][] buildDirectionPriority(double dx, double dy) {
        int[] primary;
        if (Math.abs(dx) >= Math.abs(dy)) {
            primary = dx >= 0 ? new int[] {1, 0} : new int[] {-1, 0};
        } else {
            primary = dy >= 0 ? new int[] {0, 1} : new int[] {0, -1};
        }
        if (primary[0] != 0) {
            return new int[][] { primary, {0, -1}, {0, 1}, {-primary[0], 0} };
        }
        return new int[][] { primary, {-1, 0}, {1, 0}, {0, -primary[1]} };
    }

    private boolean canOccupy(TileMap map, double centerX, double centerY) {
        double probe = DROP_HALF - 2.0;
        return isPassableProbe(map, centerX, centerY)
            && isPassableProbe(map, centerX - probe, centerY - probe)
            && isPassableProbe(map, centerX + probe, centerY - probe)
            && isPassableProbe(map, centerX - probe, centerY + probe)
            && isPassableProbe(map, centerX + probe, centerY + probe);
    }

    private boolean isPassableProbe(TileMap map, double px, double py) {
        return map.containsPixel(px, py) && map.isPassable(px, py);
    }

    private GImage loadSprite(String path) {
        try {
            BufferedImage source = ImageIO.read(new File(path));
            if (source == null) return null;
            BufferedImage trimmed = trimTransparentBounds(source);
            GImage image = new GImage(trimmed);
            image.setSize(DROP_SIZE, DROP_SIZE);
            image.setLocation(worldX, worldY);
            return image;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private BufferedImage trimTransparentBounds(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;

        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                int alpha = (source.getRGB(px, py) >>> 24) & 0xFF;
                if (alpha == 0) continue;
                if (px < minX) minX = px;
                if (py < minY) minY = py;
                if (px > maxX) maxX = px;
                if (py > maxY) maxY = py;
            }
        }

        if (maxX < minX || maxY < minY) {
            return source;
        }
        return source.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }
}
