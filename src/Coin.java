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
import acm.graphics.GRect;

import java.awt.Color;

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

    /** Default coin value. Economy amounts TBD per design doc. */
    private static final int DEFAULT_VALUE = 1;

    // =========================================================
    // FIELDS
    // =========================================================

    /** How many coins this drop is worth. */
    private final int value;

    /** Placeholder visual shown on the ground until a real coin sprite is wired. */
    private GRect worldSprite;

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
        setWorldPosition(worldX, worldY);

        this.worldSprite = new GRect(worldX + 12, worldY + 12, 24, 24); // smaller than a tile
        this.worldSprite.setFilled(true);
        this.worldSprite.setFillColor(COIN_COLOR);
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
        // TODO: p.coins += value  (or p.addCoins(value) if Player has that method)
        inWorld = false;
        // Room removes this from droppedItems and calls removeFrom(canvas) after onCollect returns
    }

    @Override
    public void draw(GCanvas canvas) {
        if (inWorld && worldSprite != null) {
            canvas.add(worldSprite);
        }
    }

    @Override
    public void removeFrom(GCanvas canvas) {
        if (worldSprite != null) canvas.remove(worldSprite);
    }

    /** Coins are not usable from inventory. */
    @Override
    public boolean isUsable() { return false; }

    // =========================================================
    // GETTERS
    // =========================================================

    public int getValue() { return value; }
}
