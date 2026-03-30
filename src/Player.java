import acm.graphics.*;

/**
 * Represents the player in the game.
 *
 * Extends Entity for combat (position, hitbox, tile-aware movement, facing,
 * health) and also carries card-game state (hand, name, profession).
 *
 * Two constructors:
 *   Player()                    — card-game / save-load use (no TileMap needed).
 *                                 Do NOT call move() on a Player created this way.
 *   Player(x, y, tileMap)      — combat / action-scene use.
 *
 * Person 3 — Combat & Enemies
 */
public class Player extends Entity {

    private static final double PLAYER_SPEED = 100.0; // pixels per second

    private Hand   hand;
    private String name       = "Adventurer";
    private String profession = "Wanderer";

    // ==========================================================
    // CONSTRUCTORS
    // ==========================================================

    /**
     * Creates a Player for the card-game / dialogue layer.
     * Health = 3, position = (0,0), tileMap = null.
     * Do NOT call move() on a Player created this way.
     */
    public Player() {
        super(0, 0, "assets/Visuals/Characters/Player 1 Idle Front.gif", null, 3, PLAYER_SPEED);
        hand = new Hand();
    }

    /**
     * Creates a Player for the combat / action layer.
     *
     * @param x       Starting center X in world pixels
     * @param y       Starting center Y in world pixels
     * @param tileMap Tile map for collision checks
     */
    public Player(double x, double y, TileMap tileMap) {
        super(x, y, "assets/Visuals/Characters/Player 1 Idle Front.gif", tileMap, 3, PLAYER_SPEED);
        hand = new Hand();
    }

    // ==========================================================
    // INPUT / MOVEMENT
    // ==========================================================

    /**
     * Processes directional input and moves the player this frame.
     *
     * @param up    true if the up key is held
     * @param down  true if the down key is held
     * @param left  true if the left key is held
     * @param right true if the right key is held
     * @param dt    delta-time in seconds
     */
    public void updateInput(boolean up, boolean down, boolean left, boolean right, double dt) {
        double dx = 0, dy = 0;
        if (up)    dy -= speed * dt;
        if (down)  dy += speed * dt;
        if (left)  dx -= speed * dt;
        if (right) dx += speed * dt;
        if (dx != 0 || dy != 0) {
            move(dx, dy);
        }
    }

    // ==========================================================
    // CARD-GAME / DIALOGUE ACCESSORS
    // ==========================================================

    /** Returns the player's hand of cards. */
    public Hand getHand() {
        return hand;
    }

    /** Returns the player's current health. Alias for Entity.getHealth(). */
    public int getHP() {
        return health;
    }

    /** Sets health, clamped to [0, maxHealth]. Used when loading a save. */
    public void setHP(int hp) {
        health = Math.max(0, Math.min(maxHealth, hp));
    }

    /**
     * Adjusts health by the given amount (positive = damage, negative = heal).
     * Kept for card-game compatibility — does not clamp at maxHealth on heal.
     *
     * @param amount damage to deal (pass negative to heal)
     */
    public void dealDamage(int amount) {
        health -= amount;
    }

    /** Returns the player's display name (used as [NAME] in dialogue). */
    public String getName() {
        return name;
    }

    /** Sets the player's display name. */
    public void setName(String name) {
        if (name != null && !name.trim().isEmpty()) {
            this.name = name.trim();
        }
    }

    /** Returns the player's profession label (used as [PROFESSION] in dialogue). */
    public String getProfession() {
        return profession;
    }

    /** Sets the player's profession label. */
    public void setProfession(String profession) {
        if (profession != null && !profession.trim().isEmpty()) {
            this.profession = profession.trim();
        }
    }
}
