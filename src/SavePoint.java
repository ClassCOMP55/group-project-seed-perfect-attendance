/*
Roberto: SavePoint — interactive world object (Inn Door or Save Crystal) that triggers a save
Who RIGs it: Room — holds it as a room-level object, calls update(dt) each tick, draw() each frame;
             GameplayPane — routes the interact key to the active room's SavePoint;

TODO: extends WorldObject — refactor this class to extend WorldObject once WorldObject.java is built.
      SavePoint has x, y, Hitbox (interactZone), a visual sprite, draw(), update(), onInteract() — it IS a WorldObject.
      When refactored: remove the duplicate x, y, visual, and hitbox fields and inherit them from WorldObject.
No extends (not an Entity — has no health, movement, or AI)

===============
PLAN OF ACTION
===============

- CLASS ROLE
- SavePoint is a static interactive object placed in a room (Inn or dungeon).
- On interact, SavePoint:
    1. Opens a Yes / No save prompt via Dialogue.openSavePrompt().
    2. If Yes: delegates the actual save to the owning gameplay/session code.
    3. If save succeeds: shows a short "Game Saved!" label.
    4. If No: closes dialogue, does nothing.
- SavePoint does NOT own the Dialogue instance; it receives one as a parameter.
- SavePoint does NOT own the save slot number; the active gameplay/session decides that.

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
import acm.graphics.GImage;
import acm.graphics.GLabel;
import acm.graphics.GRect;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
/**
 * A static interactive world object that lets the player save the game.
 * Place one in a room, register its interact key, and call update() + draw() each tick.
 */
public class SavePoint {

	/** Callback used by the active gameplay session to perform the actual save. */
	@FunctionalInterface
	public interface SaveAction {
		boolean save();
	}

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

	/** Placeholder save-object size in pixels (centered on x, y). */
	private static final double PLACEHOLDER_VISUAL_SIZE = 48.0;

	/** Save crystal occupies a 6x6 tile footprint. */
	private static final double CRYSTAL_VISUAL_SIZE = 288.0;

	/** Crystal art used for overworld save points. */
	private static final String SAVE_CRYSTAL_SPRITE_PATH = "assets/visuals/png's/save_crystal.png";

	/** When true, the crystal PNG is drawn instead of the placeholder rectangle. Set via enableSprite(). */
	private boolean showSprite = false;

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
	private static final Color MARKER_BG      = new Color(12, 18, 28, 215);
	private static final Color MARKER_BORDER  = new Color(150, 200, 255);
	private static final Color MARKER_TEXT    = new Color(235, 245, 255);

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
	private final GRect  visualPlaceholder;
	private final GImage crystalSprite;
	private final GRect  markerPlate;
	private final GLabel markerLabel;
	private final GLabel savedLabel;
	private double visualWidth;
	private double visualHeight;

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

		double iHalf = INTERACT_SIZE / 2.0;

		// Interaction zone: centered on (x, y), slightly larger than visual
		this.interactZone = new Hitbox(x - iHalf, y - iHalf, INTERACT_SIZE, INTERACT_SIZE);

		double targetVisualSize =
			(type == SavePointType.SAVE_CRYSTAL) ? CRYSTAL_VISUAL_SIZE : PLACEHOLDER_VISUAL_SIZE;
		this.crystalSprite =
			(type == SavePointType.SAVE_CRYSTAL) ? loadCrystalSprite(targetVisualSize) : null;
		this.visualWidth = (crystalSprite != null) ? crystalSprite.getWidth() : targetVisualSize;
		this.visualHeight = (crystalSprite != null) ? crystalSprite.getHeight() : targetVisualSize;

		Color fill = (type == SavePointType.INN_DOOR) ? INN_COLOR : CRYSTAL_COLOR;
		this.visualPlaceholder =
			(crystalSprite == null) ? createPlaceholderVisual(fill, targetVisualSize) : null;

		this.markerPlate = new GRect(x - 43, y + visualHeight / 2.0 - 64, 86, 20);
		this.markerPlate.setFilled(true);
		this.markerPlate.setFillColor(MARKER_BG);
		this.markerPlate.setColor(MARKER_BORDER);

		this.markerLabel = new GLabel("[E] SAVE", x - 24, y + visualHeight / 2.0 - 49);
		this.markerLabel.setFont("Courier New-BOLD-11");
		this.markerLabel.setColor(MARKER_TEXT);

		// "Game Saved!" label — shown above the object after a confirmed save
		this.savedLabel = new GLabel("Game Saved!", x - 36, y - visualHeight / 2.0 - 12);
		this.savedLabel.setFont("Courier New-BOLD-14");
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
	/** Call once after constructing to show the crystal PNG instead of the placeholder rectangle. */
	public void enableSprite() { this.showSprite = true; }

	public void addTo(GCanvas canvas) {
		resetVisualPosition();
		if (showSprite && crystalSprite != null) {
			canvas.add(crystalSprite);
		}
		if (visualPlaceholder != null) {
			canvas.add(visualPlaceholder);
		}
		canvas.add(markerPlate);
		canvas.add(markerLabel);
		centerMarkerLabel();
		canvas.add(savedLabel);
		centerSavedLabel();
	}

	/**
	 * Removes this SavePoint's graphics from the canvas.
	 * Call during room teardown / transition.
	 *
	 * @param canvas the game canvas
	 */
	public void removeFrom(GCanvas canvas) {
		if (crystalSprite != null) {
			canvas.remove(crystalSprite);
		}
		if (visualPlaceholder != null) {
			canvas.remove(visualPlaceholder);
		}
		canvas.remove(markerPlate);
		canvas.remove(markerLabel);
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
	 * Called by gameplay when the player presses the interact key near this save point.
	 * Opens a Yes / No save prompt if the player is close enough.
	 *
	 * Typical wiring in gameplay:
	 * <pre>
	 *   inputHandler.onPress(KeyEvent.VK_E,
	 *       () -> savePoint.tryInteract(player, dialogue, saveAction));
	 * </pre>
	 *
	 * @param player     the live Player
	 * @param dialogue   the shared Dialogue overlay
	 * @param saveAction callback that performs the active-slot save
	 */
	public void tryInteract(Player player, Dialogue dialogue, SaveAction saveAction) {
		if (player == null || dialogue == null) return;
		if (promptOpen) return;
		if (!interactZone.overlaps(player.getHitbox())) return;

		promptOpen = true;
		GamePlayState.setCurrent(GamePlayState.DIALOGUE);
		dialogue.openSavePrompt(() -> {
			boolean saved = false;
			boolean confirmed = dialogue.getSelectedOption() == 0;
			promptOpen = false;
			if (confirmed && saveAction != null) {
				saved = saveAction.save();
			}
			if (saved) {
				savedFeedbackTicks = SAVED_DISPLAY_TICKS;
				savedLabel.setVisible(true);
				GameSFX.play(GameSFX.SFX.SAVE_POINT);
			}
			GamePlayState.setCurrent(GamePlayState.PLAYING);
		});
	}

	// =========================================================
	// VISUAL HELPERS
	// =========================================================

	/** Resets the placeholder rectangle + saved label to this SavePoint's canonical room position. */
	private void resetVisualPosition() {
		double halfWidth = visualWidth / 2.0;
		double halfHeight = visualHeight / 2.0;
		if (crystalSprite != null) {
			crystalSprite.setLocation(x - halfWidth, y - halfHeight);
		}
		if (visualPlaceholder != null) {
			visualPlaceholder.setLocation(x - halfWidth, y - halfHeight);
		}
		markerPlate.setLocation(x - markerPlate.getWidth() / 2.0, y + halfHeight - 64);
		centerMarkerLabel();
		centerSavedLabel();
	}

	/** Centers the static SAVE label over its plate once ACM knows the rendered label width. */
	private void centerMarkerLabel() {
		markerLabel.setLocation(x - markerLabel.getWidth() / 2.0, y + visualHeight / 2.0 - 49);
	}

	/** Keeps the save-confirm label centered above the save point. */
	private void centerSavedLabel() {
		savedLabel.setLocation(x - savedLabel.getWidth() / 2.0, y - visualHeight / 2.0 - 12);
	}

	/** Pans both placeholder visuals during a room transition. */
	public void panVisual(double panX, double panY) {
		if (crystalSprite != null) {
			crystalSprite.move(panX, panY);
		}
		if (visualPlaceholder != null) {
			visualPlaceholder.move(panX, panY);
		}
		markerPlate.move(panX, panY);
		markerLabel.move(panX, panY);
		savedLabel.move(panX, panY);
	}

	private GRect createPlaceholderVisual(Color fill, double size) {
		double half = size / 2.0;
		GRect placeholder = new GRect(x - half, y - half, size, size);
		placeholder.setFilled(true);
		placeholder.setFillColor(fill);
		placeholder.setColor(fill.darker());
		return placeholder;
	}

	private GImage loadCrystalSprite(double targetSize) {
		try {
			BufferedImage source = ImageIO.read(new File(SAVE_CRYSTAL_SPRITE_PATH));
			if (source == null) {
				return null;
			}
			BufferedImage trimmed = trimTransparentBounds(source);
			GImage image = new GImage(trimmed);
			double nativeWidth = Math.max(1.0, image.getWidth());
			double nativeHeight = Math.max(1.0, image.getHeight());
			double scale = Math.min(targetSize / nativeWidth, targetSize / nativeHeight);
			image.setSize(nativeWidth * scale, nativeHeight * scale);
			image.setLocation(x - image.getWidth() / 2.0, y - image.getHeight() / 2.0);
			return image;
		} catch (IOException | RuntimeException ignored) {
			return null;
		}
	}

	private BufferedImage trimTransparentBounds(BufferedImage source) {
		int width = source.getWidth();
		int height = source.getHeight();
		int minX = width;
		int minY = height;
		int maxX = -1;
		int maxY = -1;

		for (int py = 0; py < height; py++) {
			for (int px = 0; px < width; px++) {
				int alpha = (source.getRGB(px, py) >>> 24) & 0xFF;
				if (alpha == 0) {
					continue;
				}
				if (px < minX) minX = px;
				if (py < minY) minY = py;
				if (px > maxX) maxX = px;
				if (py > maxY) maxY = py;
			}
		}

		if (maxX < minX || maxY < minY) {
			return source;
		}
		return source.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
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

	/** @return the player X position to save as this SavePoint's respawn target */
	public double getSpawnX()        { return spawnX; }

	/** @return the player Y position to save as this SavePoint's respawn target */
	public double getSpawnY()        { return spawnY; }

	/** @return the interaction zone hitbox */
	public Hitbox getInteractZone()  { return interactZone; }
}
