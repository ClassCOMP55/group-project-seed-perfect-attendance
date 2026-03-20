import java.util.EnumMap;
import java.util.Map;

/**
 * Represents an obstacle the player must overcome using a card.
 * Each obstacle has a title, description, and a table of outcomes —
 * one per CardType — so each card produces a unique narrative result.
 * A default outcome handles the case where no matching card is played.
 */
public class ObstacleScene {

    private String title;                      // Short name of the obstacle
    private String description;               // Flavour text describing the obstacle
    private String prompt;                    // Action prompt shown above the card hand
    private Map<CardType, Outcome> outcomeTable; // Maps each CardType to its unique outcome
    private Outcome defaultOutcome;           // Outcome used when player has no cards

    /**
     * Creates a new ObstacleScene with an empty outcome table.
     * Use addOutcome() to register outcomes for each CardType.
     *
     * @param title          short obstacle name
     * @param description    flavour text shown on the modal
     * @param prompt         prompt shown above the player's cards
     * @param defaultOutcome outcome applied when the player has no cards
     */
    public ObstacleScene(String title, String description, String prompt, Outcome defaultOutcome) {
        this.title = title;
        this.description = description;
        this.prompt = prompt;
        this.defaultOutcome = defaultOutcome;
        this.outcomeTable = new EnumMap<>(CardType.class);
    }

    /**
     * Registers an outcome for a specific card type.
     * @param type    the CardType this outcome applies to
     * @param outcome the Outcome produced when that card type is played
     */
    public void addOutcome(CardType type, Outcome outcome) {
        outcomeTable.put(type, outcome);
    }

    /**
     * Resolves the obstacle using the given card.
     * Returns the outcome mapped to the card's type, or the default
     * outcome if no mapping exists.
     *
     * @param card the card being played
     * @return the resulting Outcome
     */
    public Outcome resolveCard(Card card) {
        Outcome result = outcomeTable.get(card.getType());
        return result != null ? result : defaultOutcome;
    }

    /**
     * Returns the obstacle's title.
     * @return title string
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the obstacle's flavour description.
     * @return description string
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the action prompt shown above the player's hand.
     * @return prompt string
     */
    public String getPrompt() {
        return prompt;
    }

    /**
     * Returns the default outcome (used when no cards are available).
     * @return default Outcome
     */
    public Outcome getDefaultOutcome() {
        return defaultOutcome;
    }

}
