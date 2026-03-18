
/**
 * Represents a single card in the game.
 * Each card will have a unique id, a display name, a description, and a type
 * that determines how it interacts with obstacles.
 */
public class Card {

    private String id;          // Unique identifier for the card
    private String name;        // Display name shown to the player
    private String description; // Describing the card
    private CardType type;      // The type of card, used to resolve obstacle outcomes

    /**
     * Creates a new Card with the given attributes.
     * @param id          unique identifier
     * @param name        display name
     * @param description flavour text
     * @param type        card type
     */
    public Card(String id, String name, String description, CardType type) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
    }

    /** Returns the card's unique identifier. */
    public String getId() {
        return id;
    }

    /** Returns the card's display name. */
    public String getName() {
        return name;
    }

    /** Returns the card's flavour text description. */
    public String getDescription() {
        return description;
    }

    /** Returns the card's type. */
    public CardType getType() {
        return type;
    }

}
