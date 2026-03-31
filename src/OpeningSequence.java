import acm.graphics.*;
import java.util.ArrayList;
import java.util.List;

/**
 * OpeningSequence.java
 *
 * State machine that drives the scripted events in room A1 at game start.
 * Tracks progress through a multi-phase sequence:
 *
 *   1. FREE_ROAM      — Player explores, talks to NPCs. Counter tracks unique NPCs talked to.
 *   2. GOAT_APPROACH   — Threshold met: Goat Wizard auto-paths toward the player.
 *   3. GOAT_DIALOGUE_1 — Force-open dialogue with the Goat Wizard's first lines.
 *   4. MONSTER_CUTSCENE — CutscenePlayer shows monsters bursting in.
 *   5. TUTORIAL_FIGHT   — Player fights TutorialMonster. Detect death to advance.
 *   6. GOAT_DIALOGUE_2 — Force-open dialogue with the Goat Wizard's second round.
 *   7. COMPLETE         — Set hasMarkOfHero, place PathBlocker, free play begins.
 *
 * Usage:
 *   OpeningSequence seq = new OpeningSequence(player, cutscenePlayer, canvas, tileMap);
 *   // Each tick: seq.update(dt);
 *   // When NPC dialogue finishes: seq.onNPCTalkedTo(npcId);
 *   // When dialogue box exhausted: seq.onDialogueExhausted();
 *
 * Person 1 — Engine & Sequences (Task 18)
 */
public class OpeningSequence {

    // ==========================================================
    // PHASES
    // ==========================================================

    public enum Phase {
        FREE_ROAM,
        GOAT_APPROACH,
        GOAT_DIALOGUE_1,
        MONSTER_CUTSCENE,
        TUTORIAL_FIGHT,
        GOAT_DIALOGUE_2,
        COMPLETE
    }

    // ==========================================================
    // CONSTANTS
    // ==========================================================

    /** Number of unique NPCs the player must talk to before the Goat Wizard appears. */
    private static final int NPC_THRESHOLD = 3;

    /** Speed at which the Goat Wizard approaches the player (px/s). */
    private static final double GOAT_APPROACH_SPEED = 80.0;

    /** Distance at which the Goat Wizard stops approaching and starts dialogue. */
    private static final double GOAT_INTERACT_RANGE = 60.0;

    /** Forced dialogue lines (placeholder copy — replace with script / DialogueNode later). */
    private static final String[] GOAT_DIALOGUE_1_LINES = {
        "Caelomund (Goat Wizard): Hoof there! You look like someone who can hold a line.",
        "The market's about to get lively — stay close.",
        "I'll explain more once things settle. Ready?"
    };
    private static final String[] GOAT_DIALOGUE_2_LINES = {
        "Caelomund: Not bad! That thing had too many teeth anyway.",
        "Take this — the thicket gate will know you now. You're marked."
    };

    // ==========================================================
    // FIELDS
    // ==========================================================

    private Phase currentPhase;

    /** Player reference for setting flags and position checks. */
    private final Player player;

    /** Cutscene player for the monster-burst scene. */
    private final CutscenePlayer cutscenePlayer;

    /** Canvas for drawing. */
    private final GCanvas canvas;

    /** Tile map for spawning the tutorial monster. */
    private final TileMap tileMap;

    /** Set of unique NPC ids the player has talked to. */
    private final java.util.Set<String> talkedToNPCs = new java.util.HashSet<>();

    /** The Goat Wizard's position during approach phase. */
    private double goatX, goatY;
    private GImage goatSprite;

    /** The tutorial monster spawned after the cutscene. */
    private TutorialMonster tutorialMonster;

    /** Index of the line currently shown in {@link #GOAT_DIALOGUE_1_LINES} / {@link #GOAT_DIALOGUE_2_LINES}. */
    private int goatDialogueLineIndex;

    /** Callback for when dialogue should be force-opened (Person 4 integration). */
    private Runnable onForceDialogue1;
    private Runnable onForceDialogue2;

    /** Callback for when the sequence fully completes. */
    private Runnable onSequenceComplete;

    /** Callback for spawning a PathBlocker at A1's south exit. */
    private Runnable onPlacePathBlocker;

    // ==========================================================
    // CONSTRUCTOR
    // ==========================================================

    /**
     * Creates the opening sequence manager.
     *
     * @param player         the player entity
     * @param cutscenePlayer the cutscene player for cinematic frames
     * @param canvas         the game canvas
     * @param tileMap        the current room's tile map
     */
    public OpeningSequence(Player player, CutscenePlayer cutscenePlayer,
                           GCanvas canvas, TileMap tileMap) {
        this.player = player;
        this.cutscenePlayer = cutscenePlayer;
        this.canvas = canvas;
        this.tileMap = tileMap;
        this.currentPhase = Phase.FREE_ROAM;

        // Goat wizard starts near top center of this room's walkable area
        this.goatX = tileMap.getWidthPixels() * 0.5;
        this.goatY = tileMap.getTileSize() * 1.5;
    }

    // ==========================================================
    // CALLBACK SETTERS — for wiring Person 4's dialogue system
    // ==========================================================

    /** Sets the callback to force-open the Goat Wizard's first dialogue. */
    public void setOnForceDialogue1(Runnable r) { this.onForceDialogue1 = r; }

    /** Sets the callback to force-open the Goat Wizard's second dialogue. */
    public void setOnForceDialogue2(Runnable r) { this.onForceDialogue2 = r; }

    /** Sets the callback for when the entire opening sequence completes. */
    public void setOnSequenceComplete(Runnable r) { this.onSequenceComplete = r; }

    /** Sets the callback to place a PathBlocker at A1's south exit. */
    public void setOnPlacePathBlocker(Runnable r) { this.onPlacePathBlocker = r; }

    // ==========================================================
    // EXTERNAL EVENTS — called by other systems
    // ==========================================================

    /**
     * Called by the NPC/dialogue system when the player finishes talking
     * to a unique NPC. Increments the counter toward NPC_THRESHOLD.
     *
     * @param npcId unique identifier for the NPC (e.g. "blacksmith", "elder")
     */
    public void onNPCTalkedTo(String npcId) {
        if (currentPhase != Phase.FREE_ROAM) return;
        if (npcId == null) return;
        talkedToNPCs.add(npcId);

        if (talkedToNPCs.size() >= NPC_THRESHOLD) {
            currentPhase = Phase.GOAT_APPROACH;
            initGoatSprite();
        }
    }

    /**
     * Called by the dialogue system when the current forced dialogue
     * is fully exhausted (player has seen every line).
     * <p>
     * Prefer {@link #advanceDialogue()} for multi-line boxes; this skips straight to
     * the next phase (useful for tests).
     */
    public void onDialogueExhausted() {
        if (currentPhase == Phase.GOAT_DIALOGUE_1) {
            currentPhase = Phase.MONSTER_CUTSCENE;
            startMonsterCutscene();
        } else if (currentPhase == Phase.GOAT_DIALOGUE_2) {
            currentPhase = Phase.COMPLETE;
            completeSequence();
        }
    }

    /**
     * Text for the dialogue box while in {@link Phase#GOAT_DIALOGUE_1} or {@link Phase#GOAT_DIALOGUE_2}.
     * Press E to call {@link #advanceDialogue()} after each line.
     */
    public String getActiveDialogueLine() {
        if (currentPhase == Phase.GOAT_DIALOGUE_1) {
            if (goatDialogueLineIndex >= 0 && goatDialogueLineIndex < GOAT_DIALOGUE_1_LINES.length) {
                return GOAT_DIALOGUE_1_LINES[goatDialogueLineIndex];
            }
        } else if (currentPhase == Phase.GOAT_DIALOGUE_2) {
            if (goatDialogueLineIndex >= 0 && goatDialogueLineIndex < GOAT_DIALOGUE_2_LINES.length) {
                return GOAT_DIALOGUE_2_LINES[goatDialogueLineIndex];
            }
        }
        return null;
    }

    /**
     * Advances the Goat dialogue by one line. When the last line is dismissed, starts the
     * monster cutscene (dialogue 1) or completes the sequence (dialogue 2).
     */
    public void advanceDialogue() {
        if (currentPhase == Phase.GOAT_DIALOGUE_1) {
            goatDialogueLineIndex++;
            if (goatDialogueLineIndex >= GOAT_DIALOGUE_1_LINES.length) {
                currentPhase = Phase.MONSTER_CUTSCENE;
                startMonsterCutscene();
            }
            return;
        }
        if (currentPhase == Phase.GOAT_DIALOGUE_2) {
            goatDialogueLineIndex++;
            if (goatDialogueLineIndex >= GOAT_DIALOGUE_2_LINES.length) {
                currentPhase = Phase.COMPLETE;
                completeSequence();
            }
        }
    }

    // ==========================================================
    // UPDATE — called each tick
    // ==========================================================

    /**
     * Advances the opening sequence by one tick.
     * Call this from the room's update loop.
     *
     * @param dt delta-time in seconds
     */
    public void update(double dt) {
        switch (currentPhase) {
            case GOAT_APPROACH:
                updateGoatApproach(dt);
                break;

            case TUTORIAL_FIGHT:
                updateTutorialFight();
                break;

            case MONSTER_CUTSCENE:
                cutscenePlayer.update();
                break;

            default:
                break;
        }
    }

    // ==========================================================
    // PHASE: GOAT_APPROACH
    // ==========================================================

    private void initGoatSprite() {
        goatSprite = new GImage("assets/visuals/characters/player-1-idle-front.gif", goatX - 24, goatY - 24);
        goatSprite.setSize(48, 48);
        canvas.add(goatSprite);
    }

    /**
     * Moves the Goat Wizard toward the player's position each tick.
     * When within interact range, transitions to GOAT_DIALOGUE_1.
     */
    private void updateGoatApproach(double dt) {
        double dx = player.getX() - goatX;
        double dy = player.getY() - goatY;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist <= GOAT_INTERACT_RANGE) {
            // Close enough — start first dialogue
            currentPhase = Phase.GOAT_DIALOGUE_1;
            goatDialogueLineIndex = 0;
            GamePlayState.setCurrent(GamePlayState.DIALOGUE);
            if (onForceDialogue1 != null) {
                onForceDialogue1.run();
            }
            return;
        }

        // Move toward player
        double scale = GOAT_APPROACH_SPEED * dt / dist;
        goatX += dx * scale;
        goatY += dy * scale;

        if (goatSprite != null) {
            goatSprite.setLocation(goatX - 24, goatY - 24);
        }
    }

    // ==========================================================
    // PHASE: MONSTER_CUTSCENE
    // ==========================================================

    private void startMonsterCutscene() {
        List<CutsceneFrame> frames = new ArrayList<>();

        // Frame 1: Monsters burst in
        frames.add(new CutsceneFrame(
            null, // No image yet — text-only placeholder
            "Suddenly, monsters burst through the gates!",
            2000, // hold 2 seconds
            500   // 0.5s fade
        ));

        // Frame 2: Chaos
        frames.add(new CutsceneFrame(
            null,
            "The villagers scatter in fear...",
            1500,
            500
        ));

        // Frame 3: A monster remains
        frames.add(new CutsceneFrame(
            null,
            "One creature stands before you, snarling.",
            1500,
            500
        ));

        cutscenePlayer.play(frames, () -> {
            // Cutscene complete — spawn tutorial monster
            currentPhase = Phase.TUTORIAL_FIGHT;
            GamePlayState.setCurrent(GamePlayState.PLAYING);
            spawnTutorialMonster();
        });
    }

    // ==========================================================
    // PHASE: TUTORIAL_FIGHT
    // ==========================================================

    private void spawnTutorialMonster() {
        tutorialMonster = new TutorialMonster(
            tileMap.getWidthPixels() * 0.5,
            tileMap.getHeightPixels() * 0.45,
            tileMap
        );
    }

    /**
     * Checks if the tutorial monster has been killed.
     * When dead, transitions to the second Goat dialogue.
     */
    private void updateTutorialFight() {
        if (tutorialMonster != null && !tutorialMonster.isAlive()) {
            tutorialMonster = null;
            currentPhase = Phase.GOAT_DIALOGUE_2;
            goatDialogueLineIndex = 0;
            GamePlayState.setCurrent(GamePlayState.DIALOGUE);
            if (onForceDialogue2 != null) {
                onForceDialogue2.run();
            }
        }
    }

    // ==========================================================
    // PHASE: COMPLETE
    // ==========================================================

    private void completeSequence() {
        // Set the Mark of Hero flag
        player.setHasMarkOfHero(true);

        // Place PathBlocker at A1's south exit
        if (onPlacePathBlocker != null) {
            onPlacePathBlocker.run();
        }

        // Clean up goat sprite
        if (goatSprite != null) {
            canvas.remove(goatSprite);
            goatSprite = null;
        }

        GamePlayState.setCurrent(GamePlayState.PLAYING);

        // Notify the room/scene that the sequence is done
        if (onSequenceComplete != null) {
            onSequenceComplete.run();
        }
    }

    // ==========================================================
    // DRAW — call each tick from the room's draw loop
    // ==========================================================

    /**
     * Renders opening-sequence visuals (goat sprite, cutscene overlay).
     * Call from the room's draw loop after drawing the tile map and entities.
     */
    public void draw() {
        if (currentPhase == Phase.MONSTER_CUTSCENE) {
            cutscenePlayer.draw();
        }
    }

    // ==========================================================
    // GETTERS
    // ==========================================================

    /** @return the current phase of the opening sequence */
    public Phase getCurrentPhase() { return currentPhase; }

    /** @return the tutorial monster (null before spawn or after death) */
    public TutorialMonster getTutorialMonster() { return tutorialMonster; }

    /** @return true if the sequence has fully completed */
    public boolean isComplete() { return currentPhase == Phase.COMPLETE; }

    /** @return number of unique NPCs talked to so far */
    public int getNPCsTalkedTo() { return talkedToNPCs.size(); }

    /** @return the threshold count needed to trigger the Goat Wizard */
    public int getNPCThreshold() { return NPC_THRESHOLD; }

    /** @return the goat wizard's sprite for rendering (null before approach phase) */
    public GImage getGoatSprite() { return goatSprite; }

    /** @return true if the tutorial fight is active and the monster needs updating */
    public boolean isTutorialFightActive() {
        return currentPhase == Phase.TUTORIAL_FIGHT && tutorialMonster != null && tutorialMonster.isAlive();
    }
}
