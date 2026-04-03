import acm.graphics.GObject;
import acm.program.*;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class MainApplication extends GraphicsProgram{
	// Settings — fixed 1280x720, no resize support
	public static final int WINDOW_WIDTH = 1280;
	public static final int WINDOW_HEIGHT = 720;
	/** Shown in the OS window title bar (replaces default "Graphics Window"). */
	public static final String GAME_TITLE = "So There's This Wizard That's a Goat";

	/**
	 * If true, the first {@link Player} created in {@link #switchToGameplayScreen()} starts with the intangible
	 * relic so {@code K} works without opening a chest. Set {@code false} when the relic is only awarded in-world.
	 * Save loads override this in {@link GameSavesPane} via {@link SaveData#isHasIntangible()}.
	 */
	public static final boolean DEV_GRANT_INTANGIBLE_RELIC_ON_NEW_GAME = true;

	private PauseModal pauseModal;
	private Dialogue dialogue;
	private GameLoop gameLoop;
	private InputHandler inputHandler;
	private Player player;

	// Full-screen panes
	private TitleCardPane titleCardPane;
	private LandingPane landingPane;
	private StartMenuPane startMenuPane;
	private SettingsPane settingsPane;
	private GameSavesPane gameSavesPane;
	private MarketCharacterDebug marketDebugPane;
	private P1GameplayPane gameplayPane;
	/** Room-based gameplay screen — drives WorldMap, Room transitions, and player movement. */
	private GameplayPane worldMapGameplayPane;
	private GraphicsPane currentScreen;

	/** Virtual canvas size — set once on startup, fixed for 1280x720. */
	private double layoutWidth;
	private double layoutHeight;


	public MainApplication() {
		super();
	}

	protected void setupInteractions() {
		requestFocus();
		addKeyListeners();
		addMouseListeners();
		if (inputHandler != null) {
			getGCanvas().addKeyListener(inputHandler);
		}
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
		inputHandler = new InputHandler();
		setupInteractions();
		try {
			SettingsIO.loadOrCreate();
		} catch (IOException e) {
			System.err.println("Settings load: " + e.getMessage());
		}
		SwingUtilities.invokeLater(() -> {
			java.awt.Window w = SwingUtilities.getWindowAncestor(getGCanvas());
			if (w instanceof JFrame) {
				JFrame frame = (JFrame) w;
				frame.setTitle(GAME_TITLE);
				// Remove ACM's menu bar — it steals vertical space and creates whitespace
				frame.setJMenuBar(null);
				// Repack so the canvas fills exactly 1280x720 with no extra chrome
				getGCanvas().setPreferredSize(new java.awt.Dimension(WINDOW_WIDTH, WINDOW_HEIGHT));
				frame.pack();
			}
		});
		pauseModal = new PauseModal(this);
		dialogue = new Dialogue(this);
		gameLoop = new GameLoop(getGCanvas());

		// Initialize panes
		titleCardPane = new TitleCardPane(this);
		landingPane = new LandingPane(this);
		startMenuPane = new StartMenuPane(this);
		settingsPane = new SettingsPane(this);
		gameSavesPane = new GameSavesPane(this);
		marketDebugPane = new MarketCharacterDebug(this);
		gameplayPane = new P1GameplayPane(this);
		worldMapGameplayPane = new GameplayPane(this);

		syncLayoutToWindow();
		switchToScreen(landingPane);
		gameLoop.setUpdatable(dt -> {
			if (dialogue != null) dialogue.update(dt);
			if (currentScreen != null) {
				currentScreen.onTick(dt);
			}
		});
	}

	/** Width used for layout (fixed at 1280). */
	public double getLayoutWidth() {
		return layoutWidth;
	}

	/** Height used for layout (fixed at 720). */
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

	public void switchToDescriptionScreen() {
		// Teacher's DescriptionPane entry point (kept only so the project compiles).
		switchToStartMenuScreen();
	}

	public void switchToWelcomeScreen() {
		// Teacher's WelcomePane entry point (kept only so the project compiles).
		switchToLandingScreen();
	}

	/** Cinematic intro screen — click / Enter / Space to open the main menu. */
	public void switchToLandingScreen() {
		switchToScreen(landingPane);
	}

	public void switchToTitleCardScreen() {
		switchToScreen(titleCardPane);
	}

	public void switchToStartMenuScreen() {
		switchToScreen(startMenuPane);
	}

	public void switchToGameSavesScreen() {
		switchToScreen(gameSavesPane);
	}

	public void switchToSettingsScreen() {
		switchToScreen(settingsPane);
	}

	/**
	 * Launches the room-based gameplay (WorldMap, all 12 rooms, transitions).
	 * Creates a fresh Player if one does not already exist.
	 *
	 * // RIG POINT: When loading a saved game, call setPlayer(savedPlayer) and
	 * //            worldMapGameplayPane.getWorldMap().getRoomById(savedRoomId)
	 * //            BEFORE calling this method so the player starts at their saved location.
	 */
	public void switchToGameplayScreen() {
		if (player == null) {
			Player p = new Player();
			if (DEV_GRANT_INTANGIBLE_RELIC_ON_NEW_GAME) {
				p.setHasIntangible(true);
			}
			setPlayer(p);
		}
		switchToScreen(worldMapGameplayPane);
	}

	/**
	 * Handles game-over: resets the player so a fresh game can start,
	 * then routes to the landing screen.
	 */
	public void switchToGameOverScreen() {
		worldMapGameplayPane.resetForNewGame();
		player = null; // next switchToGameplayScreen() will create a fresh Player
		switchToScreen(landingPane);
	}

	protected void switchToScreen(GraphicsPane newScreen) {
		if (pauseModal != null) {
			pauseModal.hideContent();
		}
		if (currentScreen != null) {
			currentScreen.hideContent();
		}
		newScreen.showContent();
		currentScreen = newScreen;
		updateMenuMusicForScreen(newScreen);

		if (newScreen.needsGameLoop()) {
			if (gameLoop != null && !gameLoop.isRunning()) {
				gameLoop.start();
			}
		} else {
			if (gameLoop != null && gameLoop.isRunning()) {
				gameLoop.stop();
			}
		}
	}

	public InputHandler getInputHandler() {
		return inputHandler;
	}

	public boolean isPauseModalOpen() {
		return pauseModal != null && !pauseModal.contents.isEmpty();
	}

	/** Debug: opens the walkable market character test scene. */
	public void switchToMarketDebug() {
		switchToScreen(marketDebugPane);
	}

	/**
	 * Returns the shared Dialogue overlay.
	 * Call {@code getDialogue().open(...)} from any pane to show dialogue.
	 */
	public Dialogue getDialogue() {
		return dialogue;
	}

	/** Shows pause overlay. Driven by ESC in gameplay. */
	public void showPauseModal() {
		if (pauseModal != null) {
			pauseModal.showPause();
		}
	}

	/** Returns the active Player instance. */
	public Player getPlayer() {
		return player;
	}

	/** Sets the active Player (called when a new game starts or a save is loaded). */
	public void setPlayer(Player p) {
		this.player = p;
	}

	public GObject getElementAtLocation(double x, double y) {
		return getElementAt(x, y);
	}

	/**
	 * Main menu theme on landing, start menu, and settings; stop music otherwise.
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
		if (dialogue != null && dialogue.isOpen()) {
			dialogue.mouseClicked(e);
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
	 * splash, settings, saves). Any other {@link #currentScreen} may open pause.
	 */
	private boolean escPauseMenuDeniedForCurrentScreen() {
		if (currentScreen == null) 
		{
			return true;
		}
		return currentScreen instanceof LandingPane
				|| currentScreen instanceof StartMenuPane
				|| currentScreen instanceof SettingsPane
				|| currentScreen instanceof TitleCardPane
				|| currentScreen instanceof GameSavesPane;
	}

	@Override
	public void keyPressed(KeyEvent e) 
	{
		if (pauseModal != null && !pauseModal.contents.isEmpty()) 
		{
			pauseModal.keyPressed(e);
			return;
		}
		// F1 anywhere: open market-character-debug scene
		if (e.getKeyCode() == KeyEvent.VK_F1) {
			switchToMarketDebug();
			return;
		}
		// Pressing ESC opens/shows the pause menu when not on a denied (menu/splash) screen.
		if (pauseModal != null && e.getKeyCode() == KeyEvent.VK_ESCAPE && !escPauseMenuDeniedForCurrentScreen())
		{
			showPauseModal();
			return;
		}
		// Dialogue captures all non-ESC keys while open (ESC already handled above for pause).
		if (dialogue != null && dialogue.isOpen()
				&& e.getKeyCode() != KeyEvent.VK_ESCAPE)
		{
			dialogue.keyPressed(e);
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

}
