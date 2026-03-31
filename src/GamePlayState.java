/**
 * GamePlayState.java
 *
 * Controls what input does, what gets drawn, and what updates each tick.
 * The game loop checks this every tick to determine behavior.
 *
 * Person 1 — Engine & Sequences
 */
public enum GamePlayState {

    /** Normal gameplay — player moves, enemies update, combat active. */
    PLAYING,

    /** Game is paused — nothing updates, pause overlay shown. */
    PAUSED,

    /** Inventory screen open — gameplay frozen, inventory UI shown. */
    INVENTORY,

    /** Room transition in progress — player slides, no input accepted. */
    TRANSITIONING,

    /** Dialogue box is open — only dialogue-advance input accepted. */
    DIALOGUE,

    /** Cutscene playing — no input except advance/skip, cinematic renders. */
    CUTSCENE;

    /** Singleton current state — accessed globally via get/set. */
    private static GamePlayState current = PLAYING;

    /** Returns the current gameplay state. */
    public static GamePlayState getCurrent() {
        return current;
    }

    /** Sets the current gameplay state. */
    public static void setCurrent(GamePlayState state) {
        if (state != null) {
            current = state;
        }
    }

    /** Convenience check: returns true if the current state matches this value. */
    public boolean is() {
        return current == this;
    }
}
