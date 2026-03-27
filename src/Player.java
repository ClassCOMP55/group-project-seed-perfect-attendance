/**
 * Represents the player in the game.
 * Holds the player's hand of cards and their current health.
 */
public class Player {

    private Hand hand;    // The cards currently held by the player
    private int health;   // The player's current hearts (0–3)

    /** Display name used as [NAME] in dialogue. Set during character creation. */
    private String name = "Adventurer";

    /** Profession label used as [PROFESSION] in dialogue. Set during character creation. */
    private String profession = "Wanderer";

    /**
     * Creates a new Player with a full hand and 3 hearts.
     */
    public Player() {
        hand = new Hand();
        health = 3;
    }

    /**
     * Returns the player's hand of cards.
     * @return the player's Hand
     */
    public Hand getHand() {
        return hand;
    }

    /**
     * Returns the player's current health.
     * @return current HP
     */
    public int getHP() {
        return health;
    }

    /** Sets current hearts (clamped 0–3, e.g. when loading a save). */
    public void setHP(int hp) {
        health = Math.max(0, Math.min(3, hp));
    }

    /**
     * Reduces the player's health by the given amount.
     * @param amount the damage to deal
     */
    public void dealDamage(int amount) {
        health -= amount;
    }

    /** Returns the player's display name. */
    public String getName() {
        return name;
    }

    /** Sets the player's display name (used as [NAME] in dialogue). */
    public void setName(String name) {
        if (name != null && !name.trim().isEmpty()) {
            this.name = name.trim();
        }
    }

    /** Returns the player's profession. */
    public String getProfession() {
        return profession;
    }

    /** Sets the player's profession (used as [PROFESSION] in dialogue). */
    public void setProfession(String profession) {
        if (profession != null && !profession.trim().isEmpty()) {
            this.profession = profession.trim();
        }
    }

}
