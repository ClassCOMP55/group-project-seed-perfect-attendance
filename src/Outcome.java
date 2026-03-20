/**
 * Represents the result of playing a card against an obstacle.
 * Each Outcome has a type (positive/negative/neutral), a narrative
 * text shown to the player, and a health change applied to the player.
 */
public class Outcome {

    private OutcomeType type;  // Whether this outcome is good, bad, or neutral
    private String text;       // Narrative description shown to the player
    private int healthChange;  // Positive = heal, negative = damage, 0 = no change

    /**
     * Creates a new Outcome.
     * @param type         the category of this outcome
     * @param text         the narrative text displayed to the player
     * @param healthChange health delta applied to the player (negative = damage)
     */
    public Outcome(OutcomeType type, String text, int healthChange) {
        this.type = type;
        this.text = text;
        this.healthChange = healthChange;
    }

    /**
     * Returns the outcome type (POSITIVE, NEGATIVE, or NEUTRAL).
     * @return the OutcomeType
     */
    public OutcomeType getType() {
        return type;
    }

    /**
     * Returns the narrative text to display to the player.
     * @return outcome description string
     */
    public String getText() {
        return text;
    }

    /**
     * Returns the health change applied to the player.
     * Negative values deal damage; positive values heal.
     * @return health delta as an integer
     */
    public int getHealthDifference() {
        return healthChange;
    }

}
