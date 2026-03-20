/**
 * Represents the type of outcome when a card is played against an obstacle.
 * Used by Outcome to categorise the result as positive, negative, or neutral.
 */
public enum OutcomeType {
    POSITIVE,  // Obstacle cleared successfully with a good result
    NEGATIVE,  // Obstacle cleared but at a cost (e.g. health damage)
    NEUTRAL    // Passed through without meaningful benefit or loss
}
