/*
Roberto: SavePoint — interactive world object (Inn Door or Save Crystal) that triggers a save
Who RIGs it: The room / pane that contains it — calls update() each tick, draw() each frame,
             and registers the interact key via inputHandler.onPress(VK_J, () -> savePoint.tryInteract(...))
No extends (not an Entity — has no health, movement, or AI)

===============
PLAN OF ACTION
===============

- CLASS ROLE
- SavePoint is a static interactive object placed in a room (Inn or dungeon).
- On interact, SavePoint:
    1. Opens a Yes / No save prompt via Dialogue.openSavePrompt().
    2. If Yes: heals player to maxHp, snapshots state into SaveData, calls SaveManager.writeSave().
    3. If No: closes dialogue, does nothing.
- SavePoint does NOT own the Dialogue instance; it receives one as a parameter.
- SavePoint does NOT own the save slot number; it reads it from GameState.

- TYPES
- SavePointType enum (defined inside this file): INN_DOOR, SAVE_CRYSTAL.
- Both types behave identically in code. Type is stored for future visual / audio differentiation.

- INTERACTION DETECTION
- SavePoint holds a Hitbox (interactZone) slightly larger than the visual sprite.
- The room registers the interact key (J) via InputHandler.onPress().
- tryInteract() checks if the player's hitbox overlaps interactZone before opening the prompt.
- This prevents the prompt from firing when the player is nowhere near the SavePoint.

- FEEDBACK
- After a confirmed save, display a "Game Saved!" label above the SavePoint for ~120 ticks (~2 seconds).
- Sound on save is handled externally by SoundManager (also Person 4); SavePoint does not call it directly.

- RESPAWN POSITION
- SavePoint stores double spawnX, spawnY — where the player will be placed when this save is loaded.
  Set this to the center of the SavePoint when placing the object in a room.
*/

import acm.graphics.GCanvas;
import acm.graphics.GLabel;
import acm.graphics.GRect;

import java.awt.Color;
import java.io.IOException;

/**
 * A static interactive world object that lets the player save the game.
 * Place one in a room, register its interact key, and call update() + draw() each tick.
 */
public class SavePoint {

	// =========================================================
	// INNER ENUM
	// =========================================================

	/** The two kinds of save object in the game world. */
	public enum SavePointType {
		INN_DOOR,
		SAVE_CRYSTAL
	}

	// =========================================================
	// CONSTANTS
	// =========================================================

	/** Visual placeholder size in pixels (centered on x, y). */
	private static final int VISUAL_SIZE = 48;

	/** Interaction zone is larger than the visual so the player doesn't need pixel-perfect positioning. */
	private static final int INTERACT_SIZE = 80;

	/** How many ticks to show the "Game Saved!" label (~2 seconds at 60fps). */
	private static final int SAVED_DISPLAY_TICKS = 120;

	// =========================================================
	// COLORS  (placeholder visuals until real sprites are added)
	// =========================================================

	private static final Color INN_COLOR      = new Color(180, 140, 80);
	private static final Color CRYSTAL_COLOR  = new Color(100, 180, 255);
	private static final Color SAVED_COLOR    = new Color(255, 215, 120);

	// =========================================================
	// FIELDS
	// =========================================================

	/** Center of this object in world pixels. */
	private final double x;
	private final double y;

	/** Room ID passed into SaveData so the player respawns here on load. */
	private final String roomId;

	/** Which kind of save object this is (for future visual / sound differentiation). */
	private final SavePointType type;

	/** Position to place the player when this save is loaded (usually same as x, y). */
	private final double spawnX;
	private final double spawnY;

	/** Collision zone — player must overlap this to trigger interact. */
	private final Hitbox interactZone;

	// Visual elements
	private final GRect  visual;
	private final GLabel savedLabel;

	/** Counts down from SAVED_DISPLAY_TICKS to 0 after a confirmed save. */
	private int savedFeedbackTicks;

	/** True while the save prompt is open (prevents double-triggering). */
	private boolean promptOpen;

	// =========================================================
	// CONSTRUCTOR
	// =========================================================

	/**
	 * Creates a SavePoint centered at (x, y).
	 *
	 * @param x      center X in world pixels
	 * @param y      center Y in world pixels
	 * @param roomId room identifier written into SaveData for respawn routing
	 * @param type   INN_DOOR or SAVE_CRYSTAL
	 * @param spawnX player X position on load (usually same as x)
	 * @param spawnY player Y position on load (usually same as y)
	 */
	public SavePoint(double x, double y, String roomId,
	                 SavePointType type, double spawnX, double spawnY) {
		this.x      = x;
		this.y      = y;
		this.roomId = roomId;
		this.type   = type;
		this.spawnX = spawnX;
		this.spawnY = spawnY;

		double half = VISUAL_SIZE / 2.0;
		double iHalf = INTERACT_SIZE / 2.0;

		// Interaction zone: centered on (x, y), slightly larger than visual
		this.interactZone = new Hitbox(x - iHalf, y - iHalf, INTERACT_SIZE, INTERACT_SIZE);

		// Visual placeholder rectangle
		Color fill = (type == SavePointType.INN_DOOR) ? INN_COLOR : CRYSTAL_COLOR;
		this.visual = new GRect(x - half, y - half, VISUAL_SIZE, VISUAL_SIZE);
		this.visual.setFilled(true);
		this.visual.setFillColor(fill);
		this.visual.setColor(fill.darker());

		// "Game Saved!" label — shown above the object after a confirmed save
		this.savedLabel = new GLabel("Game Saved!", x - 36, y - half - 12);
		this.savedLabel.setFont("Monospaced-BOLD-14");
		this.savedLabel.setColor(SAVED_COLOR);
		this.savedLabel.setVisible(false);
	}

	// =========================================================
	// LIFECYCLE — called by the containing room
	// =========================================================

	/**
	 * Adds this SavePoint's graphics to the canvas.
	 * Call once when the room is set up.
	 *
	 * @param canvas the game canvas
	 */
	public void addTo(GCanvas canvas) {
		canvas.add(visual);
		canvas.add(savedLabel);
	}

	/**
	 * Removes this SavePoint's graphics from the canvas.
	 * Call during room teardown / transition.
	 *
	 * @param canvas the game canvas
	 */
	public void removeFrom(GCanvas canvas) {
		canvas.remove(visual);
		canvas.remove(savedLabel);
	}

	/**
	 * Per-tick update. Ticks down the "Game Saved!" display timer.
	 * Call this from the room's update loop every frame.
	 *
	 * @param dt delta-time in seconds (not used for timing here — tick-based)
	 */
	public void update(double dt) {
		if (savedFeedbackTicks > 0) {
			savedFeedbackTicks--;
			if (savedFeedbackTicks <= 0) {
				savedLabel.setVisible(false);
			}
		}
	}

	// =========================================================
	// INTERACT
	// =========================================================

	/**
	 * Called by the room when the player presses the interact key (J).
	 * Opens a Yes / No save prompt if the player is close enough.
	 *
	 * Typical wiring in the room setup:
	 * <pre>
	 *   inputHandler.onPress(KeyEvent.VK_J,
	 *       () -> savePoint.tryInteract(player, dialogue, gameState));
	 * </pre>
	 *
	 * @param player    the live Player
	 * @param dialogue  the shared Dialogue overlay
	 * @param gameState the live GameState (provides the active save slot)
	 */
	public void tryInteract(Player player, Dialogue dialogue, GameState gameState) {
		if (promptOpen) return;
		if (!interactZone.overlaps(player.getHitbox())) return;

		promptOpen = true;
		dialogue.openSavePrompt(() -> {
			promptOpen = false;
			if (dialogue.getSelectedOption() == 0) {
				// Player chose Yes
				performSave(player, gameState);
			}
			// Player chose No — nothing happens
		});
	}

	// =========================================================
	// SAVE EXECUTION
	// =========================================================

	/**
	 * Heals the player to full, snapshots game state, and writes the save file.
	 * Called only after the player confirms Yes in the save prompt.
	 */
	private void performSave(Player player, GameState gameState) {
		// Heal to max HP first
		player.setHP(player.getMaxHealth());

		int slot = gameState.getActiveSaveSlot();
		if (slot < 1 || slot > SaveManager.SAVE_COUNT) {
			slot = 1;
			gameState.setActiveSaveSlot(slot);
		}

		SaveData data = SaveData.from(slot, player, gameState, roomId, spawnX, spawnY);

		try {
			SaveManager.writeSave(slot, data);
			savedFeedbackTicks = SAVED_DISPLAY_TICKS;
			savedLabel.setVisible(true);
		} catch (IOException e) {
			System.err.println("[SavePoint] Save failed: " + e.getMessage());
		}
	}

	// =========================================================
	// GETTERS  (for room / debug use)
	// =========================================================

	/** @return center X in world pixels */
	public double getX()             { return x; }

	/** @return center Y in world pixels */
	public double getY()             { return y; }

	/** @return the room ID this SavePoint is associated with */
	public String getRoomId()        { return roomId; }

	/** @return the SavePoint type (INN_DOOR or SAVE_CRYSTAL) */
	public SavePointType getType()   { return type; }

	/** @return the interaction zone hitbox */
	public Hitbox getInteractZone()  { return interactZone; }
}
