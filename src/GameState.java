import java.util.Arrays;
import java.util.Map;

/**
 * Single session state: one {@link Player}, quiz progress, active save slot, and last scene for saves.
 * Owned by {@link MainApplication}; not tied to UI panes except through the app.
 */
public class GameState {

    private Player player = new Player();
    private boolean personalityQuizCompleted;
    private GameSceneId currentScene = GameSceneId.LANDING;
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
        currentScene = GameSceneId.CHARACTER_CREATION;
        activeSaveSlot = slot;
    }

    /**
     * Apply data from a loaded save (player snapshot already built by I/O).
     */
    public void applyLoadedSave(int slot, Player loadedPlayer, boolean quizDone, GameSceneId scene,
            int quizQuestionIndex, int[] quizScoresByOrdinal) {
        this.player = loadedPlayer;
        this.personalityQuizCompleted = quizDone;
        this.currentScene = scene != null ? scene : GameSceneId.SCENE_1;
        this.activeSaveSlot = slot;
        if (quizDone) {
            resetPersonalityQuizProgress();
        } else {
            applyLoadedQuizProgress(quizQuestionIndex, quizScoresByOrdinal);
        }
    }
}
