/**
 * Represents a single block of dialogue in a scene's narrative.
 *
 * A DialogueNode is one "beat" in the story — a speaker delivers one
 * or more lines of text, and then the game either:
 * <ul>
 *   <li>Advances to the next node (via {@link #nextNodeId}), or</li>
 *   <li>Presents a {@link DialogueChoice} to the player (when
 *       {@code nextNodeId} is {@code null} and a choice is registered
 *       for this node's id).</li>
 * </ul>
 *
 * This is a pure data class — it holds script content but contains
 * no rendering or game logic. Scene panes read these objects and
 * drive the UI accordingly.
 *
 * @see DialogueChoice
 * @see Scene1Pane
 */
public class DialogueNode {

    // =========================================================
    // SPEAKER CONSTANTS — use these for the speaker parameter
    // =========================================================

    /** Narrator voice — scene descriptions, stage directions. */
    public static final String NARRATOR   = "NARRATOR";

    /** The vendor in the market (Scene 1, Part 1). */
    public static final String ORET       = "ORET";

    /** The goat wizard (Scene 1, Parts 2–4). */
    public static final String CAELOMUND  = "CAELOMUND";

    /** The player character — replaced with the player's chosen name at render time. */
    public static final String PLAYER     = "PLAYER";

    /** Maret — road trader at the caravan camp (Scene 2, Location A). */
    public static final String MARET      = "MARET";

    /** Drev — back-alley information broker (Scene 2, Location B). */
    public static final String DREV       = "DREV";

    /** Innkeeper — city inn (Scene 2, Inn outcome). */
    public static final String INNKEEPER  = "INNKEEPER";

    /** Generic vendor — market vendors (Scene 2, Location D). */
    public static final String VENDOR     = "VENDOR";

    // =========================================================
    // FIELDS
    // =========================================================

    /** Unique identifier for this node (e.g. "p1_intro", "p2_goat_enters"). */
    private final String id;

    /**
     * Who is speaking. Use the constants above, or {@code null} for
     * continued narration that doesn't need a new speaker label.
     */
    private final String speaker;

    /**
     * The text lines delivered in this node, shown one at a time.
     * The player clicks to advance through them. Supports [NAME]
     * and [PROFESSION] tokens that are replaced at render time.
     */
    private final String[] lines;

    /**
     * The id of the next DialogueNode to show after all lines are
     * exhausted. If {@code null}, the scene controller checks for
     * a {@link DialogueChoice} registered after this node.
     */
    private final String nextNodeId;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    /**
     * Creates a new DialogueNode.
     *
     * @param id         unique identifier for lookup
     * @param speaker    who is speaking (use constants, or null)
     * @param lines      text lines shown one at a time
     * @param nextNodeId id of the next node, or null if a choice follows
     */
    public DialogueNode(String id, String speaker, String[] lines, String nextNodeId) {
        this.id = id;
        this.speaker = speaker;
        this.lines = lines;
        this.nextNodeId = nextNodeId;
    }

    // =========================================================
    // GETTERS
    // =========================================================

    /** Returns this node's unique id. */
    public String getId() {
        return id;
    }

    /** Returns the speaker name (or null for continued narration). */
    public String getSpeaker() {
        return speaker;
    }

    /** Returns the array of text lines to display. */
    public String[] getLines() {
        return lines;
    }

    /** Returns the next node id, or null if a choice follows this node. */
    public String getNextNodeId() {
        return nextNodeId;
    }
}
