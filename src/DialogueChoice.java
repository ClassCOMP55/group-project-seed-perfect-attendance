/**
 * Represents a branching choice point in a scene's dialogue.
 *
 * A DialogueChoice is shown to the player after a specific
 * {@link DialogueNode} completes. It presents four options — one
 * per {@link CardType} archetype — and each option:
 * <ul>
 *   <li>Awards archetype points to the relevant CardType</li>
 *   <li>May set narrative flags (e.g. "GOAT_TRUST")</li>
 *   <li>May spend a card from the player's hand</li>
 *   <li>Leads to a branch node with unique dialogue</li>
 * </ul>
 *
 * After the branch plays out, all paths converge at a common
 * rejoin node.
 *
 * This is a pure data class — no rendering or game logic.
 *
 * @see DialogueNode
 * @see Scene1Pane
 */
public class DialogueChoice {

    // =========================================================
    // FIELDS
    // =========================================================

    /** The id of the DialogueNode this choice appears after. */
    private final String afterNodeId;

    /** Short text labels for each of the 4 options (shown on buttons). */
    private final String[] optionTexts;

    /** The CardType each option corresponds to (always all 4 types, in order). */
    private final CardType[] optionTypes;

    /** The DialogueNode id each option leads to (branch entry point). */
    private final String[] branchNodeIds;

    /** The DialogueNode id where all branches converge after playing out. */
    private final String rejoinNodeId;

    /**
     * Whether this choice spends a card from the player's hand.
     * If true and {@link #isTutorial} is false, one card is consumed.
     * If true and {@link #isTutorial} is true, the card is "spent"
     * narratively but returned to the hand (tutorial mode).
     */
    private final boolean spendsCard;

    /**
     * If true, this is a tutorial choice — the card is not actually
     * removed from the player's hand even though {@link #spendsCard}
     * is true. Used for the first card interaction in Scene 1.
     */
    private final boolean isTutorial;

    /** Archetype points awarded per option (index matches optionTypes). */
    private final int[] archetypePoints;

    /**
     * Narrative flags to set per option (index matches optionTypes).
     * Each entry can be null (no flag), a single flag name, or
     * a comma-separated list of flags.
     */
    private final String[] flagChanges;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    /**
     * Creates a new DialogueChoice.
     *
     * @param afterNodeId     node id this choice follows
     * @param optionTexts     4 button labels
     * @param optionTypes     4 CardTypes (one per option)
     * @param branchNodeIds   4 branch entry node ids
     * @param rejoinNodeId    convergence node id
     * @param spendsCard      true if a card is spent
     * @param isTutorial      true if card is returned after spend
     * @param archetypePoints points per option
     * @param flagChanges     flags per option (nullable entries)
     */
    public DialogueChoice(
            String afterNodeId,
            String[] optionTexts,
            CardType[] optionTypes,
            String[] branchNodeIds,
            String rejoinNodeId,
            boolean spendsCard,
            boolean isTutorial,
            int[] archetypePoints,
            String[] flagChanges) {
        this.afterNodeId = afterNodeId;
        this.optionTexts = optionTexts;
        this.optionTypes = optionTypes;
        this.branchNodeIds = branchNodeIds;
        this.rejoinNodeId = rejoinNodeId;
        this.spendsCard = spendsCard;
        this.isTutorial = isTutorial;
        this.archetypePoints = archetypePoints;
        this.flagChanges = flagChanges;
    }

    // =========================================================
    // GETTERS
    // =========================================================

    /** Returns the id of the node this choice appears after. */
    public String getAfterNodeId() {
        return afterNodeId;
    }

    /** Returns the 4 option button labels. */
    public String[] getOptionTexts() {
        return optionTexts;
    }

    /** Returns the CardType for each option. */
    public CardType[] getOptionTypes() {
        return optionTypes;
    }

    /** Returns the branch node id for each option. */
    public String[] getBranchNodeIds() {
        return branchNodeIds;
    }

    /** Returns the rejoin node id where branches converge. */
    public String getRejoinNodeId() {
        return rejoinNodeId;
    }

    /** Returns true if this choice spends a card. */
    public boolean spendsCard() {
        return spendsCard;
    }

    /** Returns true if this is a tutorial choice (card returned). */
    public boolean isTutorial() {
        return isTutorial;
    }

    /** Returns the archetype points array (one per option). */
    public int[] getArchetypePoints() {
        return archetypePoints;
    }

    /** Returns the flag changes array (one per option, nullable). */
    public String[] getFlagChanges() {
        return flagChanges;
    }
}
