import acm.graphics.GObject;
import acm.program.*;


import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class MainApplication extends GraphicsProgram{
	//Settings
	public static final int WINDOW_WIDTH = 700;
	public static final int WINDOW_HEIGHT = 500;
	/** Shown in the OS window title bar (replaces default "Graphics Window"). */
	public static final String GAME_TITLE = "So There's This Wizard That's a Goat";

	/**
	 * Pause overlay (inventory + settings tabs). This class is the place to own {@code isPaused}
	 * (or equivalent): open/close pause from ESC, block gameplay updates while open, show/hide
	 * {@link PauseModal}. See plan comments in {@link PauseModal}.
	 */
	private PauseModal pauseModal;

	//List of all the full screen panes
	private LandingPane landingPane;
	private StartMenuPane startMenuPane;
	private SettingsPane settingsPane;
	private GraphicsPane currentScreen;
	private int lastKnownWidth;
	private int lastKnownHeight;
	/** Virtual canvas size tracks the window (single step on resize — no animation loop). */
	private double layoutWidth;
	private double layoutHeight;

	/** Fires once after resize events go quiet so we do not rebuild the scene on every drag tick. */
	private Timer resizeDebounceTimer;
	private static final int RESIZE_DEBOUNCE_MS = 120;


	public MainApplication() {
		super();
	}

	protected void setupInteractions() {
		requestFocus();
		addKeyListeners();
		addMouseListeners();
		getGCanvas().addMouseWheelListener(e -> {
			if (pauseModal != null && !pauseModal.contents.isEmpty()) {
				return;
			}
			if (currentScreen != null) {
				currentScreen.mouseWheelMoved(e);
			}
		});
	}

	public void init() {
		setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
	}

	public void run() {
		System.out.println("Lets' Begin!");
		setupInteractions();
		try {
			SettingsIO.loadOrCreate();
		} catch (IOException e) {
			System.err.println("Settings load: " + e.getMessage());
		}
		SwingUtilities.invokeLater(() -> {
			java.awt.Window w = SwingUtilities.getWindowAncestor(getGCanvas());
			if (w instanceof JFrame) {
				((JFrame) w).setTitle(GAME_TITLE);
			}
		});
		pauseModal = new PauseModal(this);

		//Initialize all Panes
		landingPane = new LandingPane(this);
		startMenuPane = new StartMenuPane(this);
		settingsPane = new SettingsPane(this);

		// Landing splash → then main menu (Start / Options / Quit)
		switchToScreen(landingPane);
		lastKnownWidth = (int) getWidth();
		lastKnownHeight = (int) getHeight();
		installResizeHandler();
	}

	/** Width used for scaling layout (may animate during resize). */
	public double getLayoutWidth() {
		return layoutWidth;
	}

	/** Height used for scaling layout (may animate during resize). */
	public double getLayoutHeight() {
		return layoutHeight;
	}

	private void syncLayoutToWindow() {
		layoutWidth = getWidth();
		layoutHeight = getHeight();
	}

	public static void main(String[] args) {
		new MainApplication().start();
	}

	/** Cinematic intro screen — click / Enter / Space to open the main menu. */
	public void switchToLandingScreen() {
		switchToScreen(landingPane);
	}

	public void switchToStartMenuScreen() {
		switchToScreen(startMenuPane);
	}

	public void switchToSettingsScreen() {
		switchToScreen(settingsPane);
	}

	/** Placeholder — routes to landing until a real game-over screen is built. */
	public void switchToGameOverScreen() {
		switchToScreen(landingPane);
	}

	protected void switchToScreen(GraphicsPane newScreen) {
		syncLayoutToWindow();
		if (pauseModal != null) {
			pauseModal.hideContent();
		}
		if (currentScreen != null) {
			currentScreen.hideContent();
		}
		newScreen.showContent();
		currentScreen = newScreen;
		updateMenuMusicForScreen(newScreen);
	}

	/**
	 * Main menu theme on landing, start menu, and settings;
	 * otherwise stop music during gameplay.
	 */
	private void updateMenuMusicForScreen(GraphicsPane newScreen) {
		if (newScreen == landingPane || newScreen == startMenuPane || newScreen == settingsPane) {
			GameMusic.stopJourneyBeginsMusic();
			GameMusic.startMainMenuMusic();
		} else {
			GameMusic.stopMainMenuMusic();
			GameMusic.stopJourneyBeginsMusic();
		}
	}

	/** Shows pause overlay. Driven by ESC during gameplay. */
	public void showPauseModal() {
		if (pauseModal != null) {
			pauseModal.showPause();
		}
	}

	public GObject getElementAtLocation(double x, double y) {
		return getElementAt(x, y);
	}

	@Override
	public void mousePressed(MouseEvent e) {
		if (pauseModal != null && !pauseModal.contents.isEmpty()) {
			return;
		}
		if (currentScreen != null) {
			currentScreen.mousePressed(e);
		}
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		if (pauseModal != null && !pauseModal.contents.isEmpty()) {
			return;
		}
		if (currentScreen != null && currentScreen.tryHandleSettingsCornerClick(e)) {
			return;
		}
		if (currentScreen != null) {
			currentScreen.mouseReleased(e);
		}
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		if (pauseModal != null && !pauseModal.contents.isEmpty()) {
			return;
		}
		if (currentScreen != null && currentScreen.tryHandleSettingsCornerClick(e)) {
			return;
		}
		if (currentScreen != null) {
			currentScreen.mouseClicked(e);
		}
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		if (pauseModal != null && !pauseModal.contents.isEmpty()) {
			return;
		}
		if (currentScreen != null) {
			currentScreen.mouseDragged(e);
		}
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		if (pauseModal != null && !pauseModal.contents.isEmpty()) {
			return;
		}
		if (currentScreen != null) {
			currentScreen.mouseMoved(e);
		}
	}

	/**
	 * Deny-list guard: ESC must not open {@link PauseModal} on these full-screen shells (menus,
	 * splash, settings). Any other {@link #currentScreen} may open pause.
	 * Add new menu-style panes here when you create them.
	 */
	private boolean escPauseMenuDeniedForCurrentScreen() {
		if (currentScreen == null) 
		{
			return true;
		}
		return currentScreen instanceof LandingPane
				|| currentScreen instanceof StartMenuPane
				|| currentScreen instanceof SettingsPane;
	}

	@Override
	public void keyPressed(KeyEvent e) 
	{
		if (pauseModal != null && !pauseModal.contents.isEmpty()) 
		{
			pauseModal.keyPressed(e);
			return;
		}
		// Pressing ESC opens/shows the pause menu when not on a denied (menu/splash) screen.
		if (pauseModal != null && e.getKeyCode() == KeyEvent.VK_ESCAPE && !escPauseMenuDeniedForCurrentScreen())
		{
			showPauseModal();
			return;
		}
		if (currentScreen != null) 
		{
			currentScreen.keyPressed(e);
		}
	}

	@Override
	public void keyReleased(KeyEvent e) 
	{
		if (pauseModal != null && !pauseModal.contents.isEmpty()) {
			return;
		}
		if (currentScreen != null) {
			currentScreen.keyReleased(e);
		}
	}

	@Override
	public void keyTyped(KeyEvent e) {
		if (pauseModal != null && !pauseModal.contents.isEmpty()) {
			return;
		}
		if (currentScreen != null) {
			currentScreen.keyTyped(e);
		}
	}

	private void installResizeHandler() {
		resizeDebounceTimer = new Timer(RESIZE_DEBOUNCE_MS, e -> {
			resizeDebounceTimer.stop();
			applyLayoutToCanvasSize();
		});
		resizeDebounceTimer.setRepeats(false);
		getGCanvas().addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				resizeDebounceTimer.restart();
			}
		});
	}

	/** One layout pass: sync logical size to the window, re-scale the pane, then modals. */
	private void applyLayoutToCanvasSize() {
		int w = (int) getWidth();
		int h = (int) getHeight();
		if (w <= 0 || h <= 0) {
			return;
		}
		if (w == lastKnownWidth && h == lastKnownHeight) {
			return;
		}
		lastKnownWidth = w;
		lastKnownHeight = h;
		syncLayoutToWindow();
		if (currentScreen != null) {
			currentScreen.refreshLayout();
		}
		refreshModalsOnResize();
	}

	private void refreshModalsOnResize() {
		if (pauseModal != null && !pauseModal.contents.isEmpty()) {
			pauseModal.refreshForResize();
		}
	}

}
