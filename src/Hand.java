import java.util.ArrayList;
import java.util.List;

/**
 * Represents the player's hand of cards.
 * Manages adding, removing, and querying the cards currently held by the player.
 */
public class Hand {

    private List<Card> cards; // The cards currently in the player's hand

    /**
     * Creates a new empty Hand.
     */
    public Hand() {
        cards = new ArrayList<Card>();
    }

    /**
     * Adds a card to the hand.
     * @param card the card to add
     */
    public void addCard(Card card) {
        cards.add(card);
    }

    /**
     * Removes and returns the card at the given index.
     * @param index position of the card to remove
     * @return the removed card
     */
    public Card removeCard(int index) {
        return cards.remove(index);
    }

    /** Returns the list of all cards in the hand. */
    public List<Card> getCards() {
        return cards;
    }

    /** Returns true if the hand contains at least one card. */
    public boolean hasCards() {
        return !cards.isEmpty();
    }

    /** Returns true if the hand is empty. */
    public boolean isEmpty() {
        return cards.isEmpty();
    }

    /** Returns the number of cards in the hand. */
    public int size() {
        return cards.size();
    }

    /** Removes all cards (e.g. when loading a save). */
    public void clear() {
        cards.clear();
    }

}
