import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single session state owned by {@link MainApplication}: one {@link Player},
 * quiz and save metadata, current scene, plus narrative data (archetype scores
 * and story flags) updated by dialogue.
 */
public class GameState {

    private Player player = new Player();
    private boolean personalityQuizCompleted;
    private GameSceneId currentScene = GameSceneId.LANDING;
    /**
     * Last in-world scene for save/load (menus do not update this). The JSON {@code scene} field
     * stores this so returning to the main menu does not overwrite progress with {@code START_MENU}.
     */
    private GameSceneId resumeScene = GameSceneId.LANDING;
    /** 1–3 when a slot is selected; 0 = none. */
    private int activeSaveSlot;

    /**
     * Next question index during an in-progress personality quiz (0 = first question;
     * equals question count when all answers are recorded but completion has not run yet).
     * Meaningful only while {@link #personalityQuizCompleted} is false.
     */
    private int personalityQuizQuestionIndex;
    /** Scores per {@link CardType#ordinal()} while the quiz is in progress. */
    private final int[] personalityQuizScores = new int[CardType.values().length];

    /**
     * Running archetype score per card type — seeded from the character quiz and
     * incremented by dialogue choices.
     */
    private final Map<CardType, Integer> archetypeScores = new EnumMap<>(CardType.class);

    /**
     * Narrative flags (e.g. GOAT_TRUST, SCROLL_LOST). Duplicate {@link #setFlag} calls are harmless.
     */
    private final Set<String> flags = new HashSet<>();

    /**
     * Scene 1 dialogue resume (only used when {@link #currentScene} is {@link GameSceneId#SCENE_1}).
     * Null {@code scene1NodeId} means start from the opening node.
     */
    private String scene1NodeId;
    private int scene1LineIndex;
    private boolean scene1ShowingChoice;
    private String scene1RejoinId;

    /**
     * Scene 2 dialogue resume (only used when {@link #currentScene} is {@link GameSceneId#SCENE_2}).
     * Null {@code scene2NodeId} means start from the opening node.
     */
    private String scene2NodeId;
    private int scene2LineIndex;
    private boolean scene2ShowingChoice;
    private String scene2RejoinId;

    public GameState() {
        for (CardType t : CardType.values()) {
            archetypeScores.put(t, 0);
        }
    }

    public Player getPlayer() {
        return player;
    }

    public boolean isPersonalityQuizCompleted() {
        return personalityQuizCompleted;
    }

    public void setPersonalityQuizCompleted(boolean personalityQuizCompleted) {
        this.personalityQuizCompleted = personalityQuizCompleted;
    }

    public GameSceneId getCurrentScene() {
        return currentScene;
    }

    public void setCurrentScene(GameSceneId currentScene) {
        if (currentScene != null) {
            this.currentScene = currentScene;
        }
    }

    /** Scene id written to saves and used when loading — not updated on shell menus. */
    public GameSceneId getResumeScene() {
        return resumeScene;
    }

    public void setResumeScene(GameSceneId resumeScene) {
        if (resumeScene != null) {
            this.resumeScene = resumeScene;
        }
    }

    /** Menus / pickers that should not overwrite {@link #resumeScene} on autosave. */
    public static boolean isShellMenuScene(GameSceneId s) {
        if (s == null) {
            return false;
        }
        return s == GameSceneId.LANDING || s == GameSceneId.START_MENU
            || s == GameSceneId.GAME_SAVES || s == GameSceneId.SETTINGS;
    }

    public int getActiveSaveSlot() {
        return activeSaveSlot;
    }

    public void setActiveSaveSlot(int slot) {
        if (slot >= 1 && slot <= 3) {
            this.activeSaveSlot = slot;
        } else {
            this.activeSaveSlot = 0;
        }
    }

    public int getPersonalityQuizQuestionIndex() {
        return personalityQuizQuestionIndex;
    }

    public int getPersonalityQuizScore(CardType type) {
        if (type == null) {
            return 0;
        }
        int o = type.ordinal();
        return o >= 0 && o < personalityQuizScores.length ? personalityQuizScores[o] : 0;
    }

    /** Clears in-progress quiz fields (call when the quiz is finished or starting a new run). */
    public void resetPersonalityQuizProgress() {
        personalityQuizQuestionIndex = 0;
        Arrays.fill(personalityQuizScores, 0);
    }

    /**
     * Persists the current quiz position for autosave (question index = next question to show,
     * or question count after the last answer).
     */
    public void setPersonalityQuizProgress(int questionIndex, Map<CardType, Integer> scores) {
        personalityQuizQuestionIndex = Math.max(0, questionIndex);
        Arrays.fill(personalityQuizScores, 0);
        if (scores != null) {
            for (CardType t : CardType.values()) {
                Integer v = scores.get(t);
                personalityQuizScores[t.ordinal()] = v != null ? Math.max(0, v) : 0;
            }
        }
    }

    private void applyLoadedQuizProgress(int questionIndex, int[] scoresByOrdinal) {
        resetPersonalityQuizProgress();
        personalityQuizQuestionIndex = Math.max(0, questionIndex);
        if (scoresByOrdinal != null) {
            int n = Math.min(scoresByOrdinal.length, personalityQuizScores.length);
            for (int i = 0; i < n; i++) {
                personalityQuizScores[i] = Math.max(0, scoresByOrdinal[i]);
            }
        }
    }

    /**
     * New run for the chosen slot: fresh player, quiz not done, scene will be set when entering character creation.
     */
    public void beginNewRunInSlot(int slot) {
        player = new Player();
        personalityQuizCompleted = false;
        resetPersonalityQuizProgress();
        clearNarrativeAndScene1Progress();
        currentScene = GameSceneId.CHARACTER_CREATION;
        resumeScene = GameSceneId.CHARACTER_CREATION;
        activeSaveSlot = slot;
    }

    /**
     * Apply data from a loaded save (player snapshot already built by I/O).
     */
    public void applyLoadedSave(int slot, Player loadedPlayer, boolean quizDone, GameSceneId scene,
            int quizQuestionIndex, int[] quizScoresByOrdinal,
            int[] archetypeScoresByOrdinal, String[] narrativeFlags,
            String s1NodeId, int s1LineIndex, boolean s1ShowingChoice, String s1RejoinId,
            String s2NodeId, int s2LineIndex, boolean s2ShowingChoice, String s2RejoinId) {
        this.player = loadedPlayer;
        this.personalityQuizCompleted = quizDone;
        this.activeSaveSlot = slot;

        GameSceneId rs = scene != null ? scene : GameSceneId.SCENE_1;
        // Older saves autosaved while on the main menu — scene was START_MENU etc.; restore in-world play.
        if (quizDone && isShellMenuScene(rs)) {
            rs = GameSceneId.SCENE_1;
        }
        this.resumeScene = rs;
        this.currentScene = rs;

        applyArchetypeScoresFromOrdinals(archetypeScoresByOrdinal);
        flags.clear();
        if (narrativeFlags != null) {
            for (String f : narrativeFlags) {
                if (f != null && !f.isEmpty()) {
                    flags.add(f);
                }
            }
        }

        if (rs != GameSceneId.SCENE_1) {
            clearScene1Checkpoint();
        } else {
            if (s1NodeId != null && !s1NodeId.isEmpty()) {
                this.scene1NodeId = s1NodeId;
                this.scene1LineIndex = Math.max(0, s1LineIndex);
                this.scene1ShowingChoice = s1ShowingChoice;
                this.scene1RejoinId = (s1RejoinId != null && !s1RejoinId.isEmpty()) ? s1RejoinId : null;
            } else {
                clearScene1Checkpoint();
            }
        }

        if (rs != GameSceneId.SCENE_2) {
            clearScene2Checkpoint();
        } else {
            if (s2NodeId != null && !s2NodeId.isEmpty()) {
                this.scene2NodeId = s2NodeId;
                this.scene2LineIndex = Math.max(0, s2LineIndex);
                this.scene2ShowingChoice = s2ShowingChoice;
                this.scene2RejoinId = (s2RejoinId != null && !s2RejoinId.isEmpty()) ? s2RejoinId : null;
            } else {
                clearScene2Checkpoint();
            }
        }

        if (quizDone) {
            resetPersonalityQuizProgress();
        } else {
            applyLoadedQuizProgress(quizQuestionIndex, quizScoresByOrdinal);
        }
    }

    private void applyArchetypeScoresFromOrdinals(int[] byOrdinal) {
        if (byOrdinal == null) {
            for (CardType t : CardType.values()) {
                archetypeScores.put(t, 0);
            }
            return;
        }
        int n = Math.min(byOrdinal.length, CardType.values().length);
        for (int i = 0; i < CardType.values().length; i++) {
            CardType t = CardType.values()[i];
            int v = i < n ? Math.max(0, byOrdinal[i]) : 0;
            archetypeScores.put(t, v);
        }
    }

    /** Fresh narrative + scene checkpoints (new game). */
    private void clearNarrativeAndScene1Progress() {
        for (CardType t : CardType.values()) {
            archetypeScores.put(t, 0);
        }
        flags.clear();
        clearScene1Checkpoint();
        clearScene2Checkpoint();
    }

    public void updateScene1Checkpoint(String nodeId, int lineIndex, boolean showingChoice, String rejoinId) {
        if (nodeId == null || nodeId.isEmpty()) {
            clearScene1Checkpoint();
            return;
        }
        this.scene1NodeId = nodeId;
        this.scene1LineIndex = Math.max(0, lineIndex);
        this.scene1ShowingChoice = showingChoice;
        this.scene1RejoinId = (rejoinId == null || rejoinId.isEmpty()) ? null : rejoinId;
    }

    public void clearScene1Checkpoint() {
        scene1NodeId = null;
        scene1LineIndex = 0;
        scene1ShowingChoice = false;
        scene1RejoinId = null;
    }

    public String getScene1NodeId() {
        return scene1NodeId;
    }

    public int getScene1LineIndex() {
        return scene1LineIndex;
    }

    public boolean isScene1ShowingChoice() {
        return scene1ShowingChoice;
    }

    public String getScene1RejoinId() {
        return scene1RejoinId;
    }

    // --- Scene 2 checkpoint ---

    public void updateScene2Checkpoint(String nodeId, int lineIndex, boolean showingChoice, String rejoinId) {
        if (nodeId == null || nodeId.isEmpty()) {
            clearScene2Checkpoint();
            return;
        }
        this.scene2NodeId = nodeId;
        this.scene2LineIndex = Math.max(0, lineIndex);
        this.scene2ShowingChoice = showingChoice;
        this.scene2RejoinId = (rejoinId == null || rejoinId.isEmpty()) ? null : rejoinId;
    }

    public void clearScene2Checkpoint() {
        scene2NodeId = null;
        scene2LineIndex = 0;
        scene2ShowingChoice = false;
        scene2RejoinId = null;
    }

    public String getScene2NodeId() {
        return scene2NodeId;
    }

    public int getScene2LineIndex() {
        return scene2LineIndex;
    }

    public boolean isScene2ShowingChoice() {
        return scene2ShowingChoice;
    }

    public String getScene2RejoinId() {
        return scene2RejoinId;
    }

    /**
     * Appends format-v2 fields (archetype scores, narrative flags, Scene 1 dialogue checkpoint)
     * to a save JSON builder. Kept on {@link GameState} so save I/O stays in sync with one class file.
     */
    public void appendFormatV2SaveJson(StringBuilder json) {
        json.append("  \"archetypeScores\": [");
        CardType[] ct = CardType.values();
        for (int i = 0; i < ct.length; i++) {
            json.append(getArchetypeScore(ct[i]));
            if (i < ct.length - 1) {
                json.append(", ");
            }
        }
        json.append("],\n");
        json.append("  \"flags\": [\n");
        List<String> flagList = new ArrayList<>(flags);
        Collections.sort(flagList);
        for (int i = 0; i < flagList.size(); i++) {
            json.append("    \"").append(jsonEscape(flagList.get(i))).append("\"");
            if (i < flagList.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  ],\n");
        json.append("  \"scene1NodeId\": \"").append(jsonEscape(scene1NodeId != null ? scene1NodeId : "")).append("\",\n");
        json.append("  \"scene1LineIndex\": ").append(scene1LineIndex).append(",\n");
        json.append("  \"scene1ShowingChoice\": ").append(scene1ShowingChoice).append(",\n");
        json.append("  \"scene1RejoinId\": \"").append(jsonEscape(scene1RejoinId != null ? scene1RejoinId : "")).append("\",\n");
        json.append("  \"scene2NodeId\": \"").append(jsonEscape(scene2NodeId != null ? scene2NodeId : "")).append("\",\n");
        json.append("  \"scene2LineIndex\": ").append(scene2LineIndex).append(",\n");
        json.append("  \"scene2ShowingChoice\": ").append(scene2ShowingChoice).append(",\n");
        json.append("  \"scene2RejoinId\": \"").append(jsonEscape(scene2RejoinId != null ? scene2RejoinId : "")).append("\",\n");
    }

    private static String jsonEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // --- Narrative: archetypes (post–quiz running totals) ---

    public void addArchetypePoints(CardType type, int points) {
        if (type == null) {
            return;
        }
        archetypeScores.put(type, archetypeScores.getOrDefault(type, 0) + points);
    }

    public int getArchetypeScore(CardType type) {
        return archetypeScores.getOrDefault(type, 0);
    }

    public void setFlag(String flag) {
        if (flag != null) {
            flags.add(flag);
        }
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    /** Delegates to {@link Player#getName()} for dialogue tokens. */
    public String getPlayerName() {
        return player.getName();
    }

    public void setPlayerName(String name) {
        player.setName(name);
    }

    /** Delegates to {@link Player#getProfession()}. */
    public String getPlayerProfession() {
        return player.getProfession();
    }

    public void setPlayerProfession(String profession) {
        player.setProfession(profession);
    }

    public void printDebugSummary() {
        System.out.println("=== GameState Debug ===");
        System.out.println("Player: " + getPlayerName() + " (" + getPlayerProfession() + ")");
        System.out.println("Archetype scores:");
        for (CardType type : CardType.values()) {
            System.out.println("  " + type + ": " + getArchetypeScore(type));
        }
        System.out.println("Flags: " + flags);
        System.out.println("=======================");
    }
}
