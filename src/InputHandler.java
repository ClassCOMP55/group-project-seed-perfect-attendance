import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * InputHandler.java
 *
 * Implements KeyListener to track held keys for smooth movement polling
 * and one-shot key-press actions for discrete events (attack, pause, interact).
 *
 * Usage:
 *   - Attach to the game canvas via addKeyListener(inputHandler).
 *   - Each tick, poll isHeld(KeyEvent.VK_W) etc. for movement.
 *   - Register one-shot actions via onPress(KeyEvent.VK_SPACE, () -> attack()).
 *
 * Person 1 — Engine & Sequences
 */
public class InputHandler implements KeyListener {

    /** Set of key codes currently held down. */
    private final Set<Integer> heldKeys = new HashSet<>();

    /**
     * Map of key code → action to run once on key press.
     * Actions fire on keyPressed (not keyReleased) for responsive feel.
     * Each action fires at most once per press — the key must be released
     * and pressed again to fire again (guarded by heldKeys check).
     */
    private final Map<Integer, Runnable> onPressActions = new HashMap<>();

    /**
     * Returns true if the given key is currently held down.
     * Called every tick by the Player for smooth movement.
     *
     * @param keyCode a KeyEvent.VK_* constant
     * @return true if the key is currently pressed
     */
    public boolean isHeld(int keyCode) {
        return heldKeys.contains(keyCode);
    }

    /**
     * Registers a one-shot action that fires when the given key is pressed.
     * Replaces any previously registered action for that key.
     *
     * @param keyCode a KeyEvent.VK_* constant
     * @param action  the Runnable to execute on key press
     */
    public void onPress(int keyCode, Runnable action) {
        onPressActions.put(keyCode, action);
    }

    /**
     * Removes a registered one-shot action for the given key.
     *
     * @param keyCode a KeyEvent.VK_* constant
     */
    public void removeOnPress(int keyCode) {
        onPressActions.remove(keyCode);
    }

    /**
     * Marks a key as already consumed so its one-shot action will not fire
     * until the physical key is released and pressed again.
     * Useful when UI layers share keys with gameplay actions.
     *
     * @param keyCode a KeyEvent.VK_* constant
     */
    public void suppressUntilRelease(int keyCode) {
        heldKeys.add(keyCode);
    }

    /** Clears all held keys. Call on focus loss or screen transition. */
    public void clearAll() {
        heldKeys.clear();
    }

    // =========================================================
    // KeyListener implementation
    // =========================================================

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();

        // Guard: only fire one-shot actions on initial press, not on key repeat
        if (!heldKeys.contains(code)) {
            heldKeys.add(code);

            Runnable action = onPressActions.get(code);
            if (action != null) {
                action.run();
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        heldKeys.remove(e.getKeyCode());
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // Not used — keyPressed handles everything
    }
}
