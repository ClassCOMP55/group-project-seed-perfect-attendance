import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;

import acm.graphics.GLabel;
import acm.graphics.GObject;
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
