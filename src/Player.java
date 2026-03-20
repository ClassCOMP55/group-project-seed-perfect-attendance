/**
 * Represents the player in the game.
 * Holds the player's hand of cards and their current health.
 */
public class Player {

    private Hand hand;    // The cards currently held by the player
    private int health;   // The player's current health points

    /**
     * Creates a new Player with a full hand and 100 health.
     */
    public Player() {
        hand = new Hand();
        health = 100;
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

    /**
     * Reduces the player's health by the given amount.
     * @param amount the damage to deal
     */
    public void dealDamage(int amount) {
        health -= amount;
    }

}
