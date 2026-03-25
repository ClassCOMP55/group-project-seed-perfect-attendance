import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Global narrative state tracker — follows the same static-singleton
 * pattern as {@link GameSettings}.
 *
 * Tracks two categories of data that persist across scenes:
 *
 * <ul>
 *   <li><b>Archetype scores</b> — running totals per {@link CardType},
 *       seeded from the character-creation quiz and incremented by
 *       dialogue choices throughout the game.</li>
 *   <li><b>Narrative flags</b> — string-based flags set by specific
 *       player decisions (e.g. "GOAT_TRUST", "SCROLL_LOST"). Later
 *       scenes can check these to branch dialogue or unlock content.</li>
 * </ul>
 *
 * Call {@link #reset()} at the start of each new game to clear all
 * state from a previous playthrough.
 */
public final class GameState {

    // =========================================================
    // ARCHETYPE SCORES
    // =========================================================

    /**
     * Running archetype score per card type. Starts at 0 for each type
     * and is seeded by {@link CharacterCreationPane} after the quiz,
     * then incremented by dialogue choices in each scene.
     */
    private static Map<CardType, Integer> archetypeScores = new EnumMap<>(CardType.class);

    // =========================================================
    // NARRATIVE FLAGS
    // =========================================================

    /**
     * Set of narrative flags raised by player choices.
     * Known flags (Scene 1):
     *   GOAT_TRUST         — Caelomund trusts the player more
     *   GOAT_RESPECT        — Caelomund respects the player's ability
     *   PLAYER_DOUBT        — Player questions Caelomund's story
     *   APPRENTICE_EMPATHY  — Player showed empathy toward Bastian's construct
     *   SCROLL_LOST         — The scroll was left behind in the market
     */
    private static Set<String> flags = new HashSet<>();

    // =========================================================
    // PLAYER IDENTITY
    // =========================================================

    /** Player's chosen name — shown as [NAME] in dialogue. */
    private static String playerName = "Adventurer";

    /** Player's chosen profession — shown as [PROFESSION] in dialogue. */
    private static String playerProfession = "Wanderer";

    // =========================================================
    // PRIVATE CONSTRUCTOR (utility class — no instances)
    // =========================================================

    private GameState() {
    }

    // =========================================================
    // RESET — call at the start of each new game
    // =========================================================

    /**
     * Clears all state for a fresh playthrough.
     * Should be called in {@link MainApplication#run()} before
     * any panes are initialized.
     */
    public static void reset() {
        archetypeScores.clear();
        for (CardType type : CardType.values()) {
            archetypeScores.put(type, 0);
        }
        flags.clear();
        playerName = "Adventurer";
        playerProfession = "Wanderer";
    }

    // =========================================================
    // ARCHETYPE METHODS
    // =========================================================

    /**
     * Adds points to the given archetype's running score.
     *
     * @param type   the card type / archetype to increment
     * @param points number of points to add (typically +1 or +2)
     */
    public static void addArchetypePoints(CardType type, int points) {
        archetypeScores.put(type, archetypeScores.getOrDefault(type, 0) + points);
    }

    /**
     * Returns the current archetype score for the given card type.
     *
     * @param type the card type to query
     * @return accumulated score (0 if never incremented)
     */
    public static int getArchetypeScore(CardType type) {
        return archetypeScores.getOrDefault(type, 0);
    }

    // =========================================================
    // FLAG METHODS
    // =========================================================

    /**
     * Raises a narrative flag. Duplicate calls are harmless.
     *
     * @param flag the flag name (e.g. "GOAT_TRUST")
     */
    public static void setFlag(String flag) {
        if (flag != null) {
            flags.add(flag);
        }
    }

    /**
     * Checks whether a narrative flag has been raised.
     *
     * @param flag the flag name to check
     * @return true if the flag has been set
     */
    public static boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    // =========================================================
    // PLAYER IDENTITY
    // =========================================================

    /** Returns the player's chosen name. */
    public static String getPlayerName() {
        return playerName;
    }

    /** Sets the player's chosen name. */
    public static void setPlayerName(String name) {
        if (name != null && !name.trim().isEmpty()) {
            playerName = name.trim();
        }
    }

    /** Returns the player's chosen profession. */
    public static String getPlayerProfession() {
        return playerProfession;
    }

    /** Sets the player's chosen profession. */
    public static void setPlayerProfession(String profession) {
        if (profession != null && !profession.trim().isEmpty()) {
            playerProfession = profession.trim();
        }
    }

    // =========================================================
    // DEBUG
    // =========================================================

    /**
     * Prints a summary of all state to stdout — useful for verifying
     * that choices are being tracked correctly during development.
     */
    public static void printDebugSummary() {
        System.out.println("=== GameState Debug ===");
        System.out.println("Player: " + playerName + " (" + playerProfession + ")");
        System.out.println("Archetype scores:");
        for (CardType type : CardType.values()) {
            System.out.println("  " + type + ": " + getArchetypeScore(type));
        }
        System.out.println("Flags: " + flags);
        System.out.println("=======================");
    }
}
