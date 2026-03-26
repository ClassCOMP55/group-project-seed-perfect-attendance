import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;

import acm.graphics.GLabel;
import acm.graphics.GObject;
import acm.graphics.GOval;
import acm.graphics.GRect;
import acm.graphics.GRoundRect;

public class GraphicsPane {
	protected MainApplication mainScreen;
	protected ArrayList<GObject> contents;

	/** Top-right "×" control — opens the pause menu when clicked (see {@link #addSettingsCornerButton()}). */
	protected GRoundRect settingsCornerFrame;
	protected GLabel settingsCornerLabel;

	public GraphicsPane() {
		contents = new ArrayList<GObject>();
	}


	public void showContent() {
	}

	/**
	 * Re-layout at the current window size without switching screens.
	 * Default rebuilds the pane (stateless menus). Panes with progress override.
	 */
	public void refreshLayout() {
		hideContent();
		showContent();
	}

	public void hideContent() {
	}

	public void mousePressed(MouseEvent e) {
	}

	public void mouseReleased(MouseEvent e) {
	}

	public void mouseClicked(MouseEvent e) {
	}

	public void mouseDragged(MouseEvent e) {
	}

	public void mouseMoved(MouseEvent e) {
	}

	public void mouseWheelMoved(MouseWheelEvent e) {
	}

	public void keyPressed(KeyEvent e) {
	}

	public void keyReleased(KeyEvent e) {
	}

	public void keyTyped(KeyEvent e) {
	}

	/** Horizontal offset so the layout canvas stays centered in the real window while animating. */
	protected double originX() {
		double lw = mainScreen.getLayoutWidth();
		return (mainScreen.getWidth() - lw) / 2.0;
	}

	/** Vertical offset so the layout canvas stays centered in the real window while animating. */
	protected double originY() {
		double lh = mainScreen.getLayoutHeight();
		return (mainScreen.getHeight() - lh) / 2.0;
	}

	protected double scaleX(double logicalX) {
		return originX() + logicalX * mainScreen.getLayoutWidth() / MainApplication.WINDOW_WIDTH;
	}

	protected double scaleY(double logicalY) {
		return originY() + logicalY * mainScreen.getLayoutHeight() / MainApplication.WINDOW_HEIGHT;
	}

	protected int scaleFontSize(int baseSize) {
		double widthRatio = mainScreen.getLayoutWidth() / MainApplication.WINDOW_WIDTH;
		double heightRatio = mainScreen.getLayoutHeight() / MainApplication.WINDOW_HEIGHT;
		double ratio = Math.min(widthRatio, heightRatio);
		return Math.max(12, (int) Math.round(baseSize * ratio));
	}

	protected String scaledFont(int baseSize) {
		return "DialogInput-PLAIN-" + scaleFontSize(baseSize);
	}

	protected double centeredX(GObject object) {
		return originX() + (mainScreen.getLayoutWidth() - object.getWidth()) / 2.0;
	}

	protected double uniformScale() {
		double widthRatio = mainScreen.getLayoutWidth() / MainApplication.WINDOW_WIDTH;
		double heightRatio = mainScreen.getLayoutHeight() / MainApplication.WINDOW_HEIGHT;
		return Math.min(widthRatio, heightRatio);
	}

	/**
	 * Adds a small gold-outlined × button in the top-right (layout coordinates) that opens the pause menu.
	 * Call at the end of {@link #showContent()} on full-screen panes except {@link SettingsPane},
	 * {@link StartMenuPane} (main menu — use Options), {@link LandingPane} (splash), and
	 * {@link SkyTransitionPane} (camera pans every object in {@code contents}).
	 */
	protected void addSettingsCornerButton() {
		double ox = originX();
		double lw = mainScreen.getLayoutWidth();
		double side = Math.max(scaleY(28) - scaleY(0), 24);
		double bx = ox + lw - side - scaleX(22);
		double by = scaleY(24);
		double arc = Math.min(scaleX(8) - scaleX(0), scaleY(8) - scaleY(0));
		settingsCornerFrame = new GRoundRect(bx, by, side, side, arc, arc);
		settingsCornerFrame.setFilled(true);
		settingsCornerFrame.setFillColor(new Color(40, 45, 75));
		settingsCornerFrame.setColor(new Color(255, 215, 120));
		settingsCornerLabel = new GLabel("\u00D7", 0, 0);
		settingsCornerLabel.setFont("SansSerif-BOLD-" + Math.max(11, scaleFontSize(18)));
		settingsCornerLabel.setColor(new Color(255, 215, 120));
		GLabel lab = settingsCornerLabel;
		double cx = bx + (side - lab.getWidth()) / 2;
		double cy = by + (side + lab.getAscent() - lab.getDescent()) / 2;
		lab.setLocation(cx, cy);
		contents.add(settingsCornerFrame);
		contents.add(settingsCornerLabel);
		mainScreen.add(settingsCornerFrame);
		mainScreen.add(settingsCornerLabel);
		settingsCornerFrame.sendToFront();
		settingsCornerLabel.sendToFront();
	}

	// =========================================================
	// SHARED HELPERS — available to all panes
	// =========================================================

	/**
	 * Adds a GObject to both the {@link #contents} tracking list and the
	 * main application canvas. Every visual element should use this method
	 * so that {@link #hideContent()} can remove it later.
	 *
	 * @param obj the graphics object to display
	 */
	protected void place(GObject obj) {
		contents.add(obj);
		mainScreen.add(obj);
	}

	/**
	 * Creates a filled {@link GRect} using <b>raw pixel</b> coordinates.
	 * Use {@link #srect} for logical (700×500) coordinates that auto-scale.
	 *
	 * @param x      pixel X of the left edge
	 * @param y      pixel Y of the top edge
	 * @param w      pixel width
	 * @param h      pixel height
	 * @param fill   fill colour
	 * @param border border / outline colour
	 * @return configured GRect (not yet added to canvas — call {@link #place})
	 */
	protected GRect rect(double x, double y, double w, double h, Color fill, Color border) {
		GRect r = new GRect(x, y, w, h);
		r.setFilled(true);
		r.setFillColor(fill);
		r.setColor(border);
		return r;
	}

	/**
	 * Creates a filled {@link GRect} using <b>logical</b> (700×500 design space)
	 * coordinates that are automatically scaled to pixel coordinates via
	 * {@link #scaleX} / {@link #scaleY}.
	 *
	 * @param lx     logical X of the left edge
	 * @param ly     logical Y of the top edge
	 * @param lw     logical width
	 * @param lh     logical height
	 * @param fill   fill colour
	 * @param border border / outline colour
	 * @return configured GRect (not yet added to canvas — call {@link #place})
	 */
	protected GRect srect(double lx, double ly, double lw, double lh, Color fill, Color border) {
		return rect(
			scaleX(lx),
			scaleY(ly),
			scaleX(lx + lw) - scaleX(lx),
			scaleY(ly + lh) - scaleY(ly),
			fill,
			border
		);
	}

	/**
	 * Creates a {@link GLabel} in the pixel-art monospaced bold font.
	 * The font size is automatically scaled for the current window size.
	 *
	 * @param text  label string
	 * @param size  logical font size (scaled automatically)
	 * @param color text colour
	 * @return configured GLabel at (0, 0) — caller sets location
	 */
	protected GLabel pixelLabel(String text, int size, Color color) {
		GLabel lbl = new GLabel(text, 0, 0);
		lbl.setFont("Monospaced-BOLD-" + scaleFontSize(size));
		lbl.setColor(color);
		return lbl;
	}

	/**
	 * Word-wraps a string into lines of at most {@code maxChars} characters,
	 * breaking only at spaces. Respects embedded {@code \n} newlines.
	 *
	 * @param text     the string to wrap
	 * @param maxChars maximum characters per line
	 * @return list of wrapped lines (never empty)
	 */
	protected List<String> wrapText(String text, int maxChars) {
		List<String> lines   = new ArrayList<>();
		String[]     words   = text.split(" ");
		StringBuilder current = new StringBuilder();

		for (String word : words) {
			if (current.length() + word.length() + 1 > maxChars && current.length() > 0) {
				lines.add(current.toString().trim());
				current = new StringBuilder();
			}
			current.append(word).append(" ");
		}
		if (current.length() > 0) {
			lines.add(current.toString().trim());
		}
		if (lines.isEmpty()) {
			lines.add("");
		}
		return lines;
	}

	// =========================================================
	// PLAYER HUD — top-left health bar, name, and icon
	// =========================================================

	/** HUD background panel. */
	private GRect hudPanel;
	/** Circle avatar placeholder. */
	private GOval hudIcon;
	/** First-initial label centred in the avatar circle. */
	private GLabel hudIconLabel;
	/** Player name label. */
	private GLabel hudNameLabel;
	/** Health bar background. */
	private GRect hudHealthBg;
	/** Health bar fill (width updated by updatePlayerHUD). */
	private GRect hudHealthFill;
	/** HP numeric label. */
	private GLabel hudHpLabel;

	// Logical layout constants for the HUD
	private static final double HUD_X      = 8;
	private static final double HUD_Y      = 8;
	private static final double HUD_W      = 165;
	private static final double HUD_H      = 62;
	private static final double HUD_ICON_X = 14;
	private static final double HUD_ICON_Y = 12;
	private static final double HUD_ICON_D = 44;   // diameter (circle)
	private static final double HUD_TEXT_X = 66;
	private static final double HUD_BAR_X  = 66;
	private static final double HUD_BAR_Y  = 40;
	private static final double HUD_BAR_W  = 98;
	private static final double HUD_BAR_H  = 10;

	private static final Color HUD_BG_COLOR     = new Color(10, 8, 20, 200);
	private static final Color HUD_BORDER_COLOR = new Color(255, 215, 120);
	private static final Color HUD_ICON_COLOR   = new Color(90, 100, 130);
	private static final Color HUD_NAME_COLOR   = new Color(220, 220, 235);
	private static final Color HUD_BAR_BG_COLOR = new Color(35, 35, 52);
	private static final Color HUD_BAR_FULL     = new Color(80, 200, 100);
	private static final Color HUD_BAR_MID      = new Color(230, 180, 50);
	private static final Color HUD_BAR_LOW      = new Color(220, 70, 70);
	private static final Color HUD_HP_COLOR     = new Color(180, 180, 200);

	/**
	 * Draws the player HUD in the top-left corner.
	 * Shows a circle avatar placeholder, the player's name, and a health bar.
	 * Call at the end of {@link #showContent()} in gameplay panes.
	 *
	 * @param player the Player whose data to display
	 */
	protected void showPlayerHUD(Player player) {
		hidePlayerHUD();

		// Background panel
		hudPanel = new GRect(scaleX(HUD_X), scaleY(HUD_Y),
			scaleX(HUD_X + HUD_W) - scaleX(HUD_X),
			scaleY(HUD_Y + HUD_H) - scaleY(HUD_Y));
		hudPanel.setFilled(true);
		hudPanel.setFillColor(HUD_BG_COLOR);
		hudPanel.setColor(HUD_BORDER_COLOR);
		contents.add(hudPanel);
		mainScreen.add(hudPanel);

		// Circle avatar icon
		hudIcon = new GOval(
			scaleX(HUD_ICON_X), scaleY(HUD_ICON_Y),
			scaleX(HUD_ICON_X + HUD_ICON_D) - scaleX(HUD_ICON_X),
			scaleY(HUD_ICON_Y + HUD_ICON_D) - scaleY(HUD_ICON_Y));
		hudIcon.setFilled(true);
		hudIcon.setFillColor(HUD_ICON_COLOR);
		hudIcon.setColor(HUD_BORDER_COLOR);
		contents.add(hudIcon);
		mainScreen.add(hudIcon);

		// First-initial label centred in the avatar circle
		String initials = player.getName().isEmpty() ? "?" :
			String.valueOf(player.getName().charAt(0)).toUpperCase();
		hudIconLabel = pixelLabel(initials, 16, HUD_NAME_COLOR);
		double iconCx = scaleX(HUD_ICON_X) + (scaleX(HUD_ICON_X + HUD_ICON_D) - scaleX(HUD_ICON_X)
			- hudIconLabel.getWidth()) / 2.0;
		double iconCy = scaleY(HUD_ICON_Y) + (scaleY(HUD_ICON_Y + HUD_ICON_D) - scaleY(HUD_ICON_Y)
			+ hudIconLabel.getAscent()) / 2.0 - hudIconLabel.getDescent() / 2.0;
		hudIconLabel.setLocation(iconCx, iconCy);
		contents.add(hudIconLabel);
		mainScreen.add(hudIconLabel);

		// Player name label
		String displayName = player.getName();
		if (displayName.length() > 14) {
			displayName = displayName.substring(0, 13) + ".";
		}
		hudNameLabel = pixelLabel(displayName, 11, HUD_NAME_COLOR);
		hudNameLabel.setLocation(scaleX(HUD_TEXT_X), scaleY(HUD_ICON_Y + 16));
		contents.add(hudNameLabel);
		mainScreen.add(hudNameLabel);

		// Health bar background
		hudHealthBg = new GRect(
			scaleX(HUD_BAR_X), scaleY(HUD_BAR_Y),
			scaleX(HUD_BAR_X + HUD_BAR_W) - scaleX(HUD_BAR_X),
			scaleY(HUD_BAR_Y + HUD_BAR_H) - scaleY(HUD_BAR_Y));
		hudHealthBg.setFilled(true);
		hudHealthBg.setFillColor(HUD_BAR_BG_COLOR);
		hudHealthBg.setColor(HUD_BORDER_COLOR);
		contents.add(hudHealthBg);
		mainScreen.add(hudHealthBg);

		// Health bar fill
		int hp = player.getHP();
		double fillFraction = Math.max(0, Math.min(1, hp / 100.0));
		double barMaxW = scaleX(HUD_BAR_X + HUD_BAR_W) - scaleX(HUD_BAR_X);
		Color barColor = hp > 60 ? HUD_BAR_FULL : hp > 30 ? HUD_BAR_MID : HUD_BAR_LOW;
		hudHealthFill = new GRect(
			scaleX(HUD_BAR_X), scaleY(HUD_BAR_Y),
			barMaxW * fillFraction,
			scaleY(HUD_BAR_Y + HUD_BAR_H) - scaleY(HUD_BAR_Y));
		hudHealthFill.setFilled(true);
		hudHealthFill.setFillColor(barColor);
		hudHealthFill.setColor(barColor);
		contents.add(hudHealthFill);
		mainScreen.add(hudHealthFill);

		// HP label
		hudHpLabel = pixelLabel("HP: " + hp, 9, HUD_HP_COLOR);
		hudHpLabel.setLocation(scaleX(HUD_BAR_X), scaleY(HUD_BAR_Y + HUD_BAR_H + 8));
		contents.add(hudHpLabel);
		mainScreen.add(hudHpLabel);

		// Send HUD to front so it isn't obscured
		hudPanel.sendToFront();
		hudIcon.sendToFront();
		hudIconLabel.sendToFront();
		hudNameLabel.sendToFront();
		hudHealthBg.sendToFront();
		hudHealthFill.sendToFront();
		hudHpLabel.sendToFront();
	}

	/**
	 * Updates the health bar and HP label without fully re-rendering the HUD.
	 * Call this after any health change in the scene.
	 *
	 * @param player the Player with current HP
	 */
	protected void updatePlayerHUD(Player player) {
		if (hudHealthFill == null || hudHealthBg == null || hudHpLabel == null) {
			return;
		}
		int hp = player.getHP();
		double fillFraction = Math.max(0, Math.min(1, hp / 100.0));
		double barMaxW = scaleX(HUD_BAR_X + HUD_BAR_W) - scaleX(HUD_BAR_X);
		Color barColor = hp > 60 ? HUD_BAR_FULL : hp > 30 ? HUD_BAR_MID : HUD_BAR_LOW;
		hudHealthFill.setSize(barMaxW * fillFraction,
			scaleY(HUD_BAR_Y + HUD_BAR_H) - scaleY(HUD_BAR_Y));
		hudHealthFill.setFillColor(barColor);
		hudHealthFill.setColor(barColor);
		hudHpLabel.setLabel("HP: " + hp);
	}

	/**
	 * Removes all HUD elements from the canvas and tracking list.
	 */
	protected void hidePlayerHUD() {
		GObject[] hudObjects = {hudPanel, hudIcon, hudIconLabel, hudHealthBg, hudHealthFill,
			hudNameLabel, hudHpLabel};
		for (GObject obj : hudObjects) {
			if (obj != null) {
				mainScreen.remove(obj);
				contents.remove(obj);
			}
		}
		hudPanel = null;
		hudIcon = null;
		hudIconLabel = null;
		hudHealthBg = null;
		hudHealthFill = null;
		hudNameLabel = null;
		hudHpLabel = null;
	}

	/**
	 * @return true if the click was on the settings × button (caller should not forward the event)
	 */
	public boolean tryHandleSettingsCornerClick(MouseEvent e) {
		if (settingsCornerFrame == null) {
			return false;
		}
		double x = e.getX();
		double y = e.getY();
		if (settingsCornerFrame.contains(x, y)) {
			mainScreen.showPauseModal();
			return true;
		}
		if (settingsCornerLabel != null && settingsCornerLabel.contains(x, y)) {
			mainScreen.showPauseModal();
			return true;
		}
		return false;
	}
}
