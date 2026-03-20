import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import acm.graphics.*;

public class GraphicsPane {
	protected MainApplication mainScreen;
	protected ArrayList<GObject> contents;
	
	public GraphicsPane() {
		contents = new ArrayList<GObject>();
	}

	
	public void showContent() {
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
}
