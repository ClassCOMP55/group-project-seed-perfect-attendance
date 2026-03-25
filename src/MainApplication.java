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
	/** Shown in the OS window title bar (replaces default “Graphics Window”). */
	public static final String GAME_TITLE = "So There's This Wizard That's a Goat";

	private GameState gameState;     // One run: player, quiz flag, scene, save slot
	private CardPlayModal cardPlayModal; // Reusable modal overlay for obstacle encounters
	private PauseModal pauseModal; // Dim overlay + Paused Game menu (Settings / Main Menu / Exit)

	//List of all the full screen panes
	private TitleCardPane titleCardPane;
	private LandingPane landingPane;
	private StartMenuPane startMenuPane;
	private SettingsPane settingsPane;
	private CharacterCreationPane characterCreationPane;
	private SkyTransitionPane skyTransitionPane;
	private Scene1Pane scene1Pane;
	private Scene1To2TransitionPane scene1To2TransitionPane;
	private Scene2Pane scene2Pane;
	private TransitionLoading1Pane transitionLoading1Pane;
	private RestingScene1Pane restingScene1Pane;
	private Scene3Pane scene3Pane;
	private Scene4Pane scene4Pane;
	private RestingScene2Pane restingScene2Pane;
	private Scene5Pane scene5Pane;
	private FinalRestingScenePane finalRestingScenePane;
	private Scene6Pane scene6Pane;
	private EndingPane endingPane;
	private GameSavesPane gameSavesPane;
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
			if (cardPlayModal != null && !cardPlayModal.contents.isEmpty()) {
				return;
			}
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
		gameState = new GameState();
		cardPlayModal = new CardPlayModal(this);
		pauseModal = new PauseModal(this);

		//Initialize all Panes
		titleCardPane = new TitleCardPane(this);
		landingPane = new LandingPane(this);
		startMenuPane = new StartMenuPane(this);
		settingsPane = new SettingsPane(this);

		characterCreationPane = new CharacterCreationPane(this);
		skyTransitionPane = new SkyTransitionPane(this);
		scene1Pane = new Scene1Pane(this);
		scene1To2TransitionPane = new Scene1To2TransitionPane(this);
		scene2Pane = new Scene2Pane(this);
		transitionLoading1Pane = new TransitionLoading1Pane(this);
		restingScene1Pane = new RestingScene1Pane(this);
		scene3Pane = new Scene3Pane(this);
		scene4Pane = new Scene4Pane(this);
		restingScene2Pane = new RestingScene2Pane(this);
		scene5Pane = new Scene5Pane(this);
		finalRestingScenePane = new FinalRestingScenePane(this);
		scene6Pane = new Scene6Pane(this);
		endingPane = new EndingPane(this);
		gameSavesPane = new GameSavesPane(this);

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

	public void switchToCharacterCreationScreen() {
		switchToScreen(characterCreationPane);
	}

	public void switchToSkyTransitionScreen() {
		switchToScreen(skyTransitionPane);
	}

	public void switchToScene1Screen() {
		switchToScreen(scene1Pane);
	}

	public void switchToScene1To2TransitionScreen() {
		switchToScreen(scene1To2TransitionPane);
	}

	public void switchToScene2Screen() {
		switchToScreen(scene2Pane);
	}

	public void switchToTransitionLoading1Screen() {
		switchToScreen(transitionLoading1Pane);
	}

	public void switchToRestingScene1Screen() {
		switchToScreen(restingScene1Pane);
	}

	public void switchToScene3Screen() {
		switchToScreen(scene3Pane);
	}

	public void switchToScene4Screen() {
		switchToScreen(scene4Pane);
	}

	public void switchToRestingScene2Screen() {
		switchToScreen(restingScene2Pane);
	}

	public void switchToScene5Screen() {
		switchToScreen(scene5Pane);
	}

	public void switchToFinalRestingSceneScreen() {
		switchToScreen(finalRestingScenePane);
	}

	public void switchToScene6Screen() {
		switchToScreen(scene6Pane);
	}

	public void switchToEndingScreen() {
		switchToScreen(endingPane);
	}


	protected void switchToScreen(GraphicsPane newScreen) {
		syncLayoutToWindow();
		if (pauseModal != null) {
			pauseModal.hideContent();
		}
		if (currentScreen != null) {
			currentScreen.hideContent();
		}
		// Before showContent so panes that autosave (e.g. Scene 1 dialogue) persist the correct scene id.
		GameSceneId sid = sceneIdFor(newScreen);
		gameState.setCurrentScene(sid);
		if (!GameState.isShellMenuScene(sid)) {
			gameState.setResumeScene(sid);
		}
		newScreen.showContent();
		currentScreen = newScreen;
		updateMenuMusicForScreen(newScreen);
		autosaveIfSlotActive();
	}

	/** Writes JSON when a save slot is in use (no manual save button). */
	public void autosaveIfSlotActive() {
		int s = gameState.getActiveSaveSlot();
		if (s < 1 || s > GameSaveIO.SAVE_COUNT) {
			return;
		}
		try {
			GameSaveIO.ensureGameDirectory();
			GameSaveIO.writeSave(s, gameState);
		} catch (IOException e) {
			System.err.println("Autosave failed: " + e.getMessage());
		}
	}

	/**
	 * Resume play from a loaded save’s scene id.
	 * If the personality quiz is not finished, always open character creation — the stored
	 * {@code scene} may be START_MENU or another screen from leaving mid-quiz, which would
	 * otherwise strand the player away from the quiz on load.
	 */
	public void switchToSceneFromSave(GameSceneId id) {
		if (!gameState.isPersonalityQuizCompleted()) {
			switchToScreen(characterCreationPane);
			return;
		}
		if (id == null) {
			id = GameSceneId.SCENE_1;
		}
		switch (id) {
			case LANDING:
				switchToScreen(landingPane);
				break;
			case START_MENU:
				switchToScreen(startMenuPane);
				break;
			case GAME_SAVES:
				switchToScreen(gameSavesPane);
				break;
			case SETTINGS:
				switchToScreen(settingsPane);
				break;
			case CHARACTER_CREATION:
				switchToScreen(characterCreationPane);
				break;
			case SKY_TRANSITION:
				switchToScreen(skyTransitionPane);
				break;
			case SCENE_1:
				switchToScreen(scene1Pane);
				break;
			case SCENE_1_TO_2_TRANSITION:
				switchToScreen(scene1To2TransitionPane);
				break;
			case SCENE_2:
				switchToScreen(scene2Pane);
				break;
			case TRANSITION_LOADING_1:
				switchToScreen(transitionLoading1Pane);
				break;
			case RESTING_1:
				switchToScreen(restingScene1Pane);
				break;
			case SCENE_3:
				switchToScreen(scene3Pane);
				break;
			case SCENE_4:
				switchToScreen(scene4Pane);
				break;
			case RESTING_2:
				switchToScreen(restingScene2Pane);
				break;
			case SCENE_5:
				switchToScreen(scene5Pane);
				break;
			case FINAL_RESTING:
				switchToScreen(finalRestingScenePane);
				break;
			case SCENE_6:
				switchToScreen(scene6Pane);
				break;
			case ENDING:
				switchToScreen(endingPane);
				break;
			default:
				switchToScreen(scene1Pane);
				break;
		}
	}

	/**
	 * If the personality quiz was already finished, skip re-rolling cards — continue the stored journey.
	 */
	public void resumeAfterQuizIfAlreadyComplete() {
		GameSceneId s = gameState.getResumeScene();
		if (s == GameSceneId.CHARACTER_CREATION || s == GameSceneId.SKY_TRANSITION) {
			switchToSkyTransitionScreen();
			return;
		}
		switchToSceneFromSave(s);
	}

	public GameState getGameState() {
		return gameState;
	}

	/** Writes the active slot (defaults to 1 if unset). */
	public void saveGameNow() {
		int s = gameState.getActiveSaveSlot();
		if (s < 1 || s > GameSaveIO.SAVE_COUNT) {
			s = 1;
			gameState.setActiveSaveSlot(1);
		}
		try {
			GameSaveIO.writeSave(s, gameState);
		} catch (IOException e) {
			System.err.println("Save failed: " + e.getMessage());
		}
	}

	private GameSceneId sceneIdFor(GraphicsPane p) {
		if (p == landingPane) {
			return GameSceneId.LANDING;
		}
		if (p == startMenuPane) {
			return GameSceneId.START_MENU;
		}
		if (p == gameSavesPane) {
			return GameSceneId.GAME_SAVES;
		}
		if (p == settingsPane) {
			return GameSceneId.SETTINGS;
		}
		if (p == characterCreationPane) {
			return GameSceneId.CHARACTER_CREATION;
		}
		if (p == skyTransitionPane) {
			return GameSceneId.SKY_TRANSITION;
		}
		if (p == scene1Pane) {
			return GameSceneId.SCENE_1;
		}
		if (p == scene1To2TransitionPane) {
			return GameSceneId.SCENE_1_TO_2_TRANSITION;
		}
		if (p == scene2Pane) {
			return GameSceneId.SCENE_2;
		}
		if (p == transitionLoading1Pane) {
			return GameSceneId.TRANSITION_LOADING_1;
		}
		if (p == restingScene1Pane) {
			return GameSceneId.RESTING_1;
		}
		if (p == scene3Pane) {
			return GameSceneId.SCENE_3;
		}
		if (p == scene4Pane) {
			return GameSceneId.SCENE_4;
		}
		if (p == restingScene2Pane) {
			return GameSceneId.RESTING_2;
		}
		if (p == scene5Pane) {
			return GameSceneId.SCENE_5;
		}
		if (p == finalRestingScenePane) {
			return GameSceneId.FINAL_RESTING;
		}
		if (p == scene6Pane) {
			return GameSceneId.SCENE_6;
		}
		if (p == endingPane) {
			return GameSceneId.ENDING;
		}
		if (p == titleCardPane) {
			return GameSceneId.START_MENU;
		}
		return GameSceneId.SCENE_1;
	}

	/**
	 * Main menu theme on landing, start menu, and settings; journey theme on character quiz;
	 * otherwise stop music during gameplay.
	 */
	private void updateMenuMusicForScreen(GraphicsPane newScreen) {
		if (newScreen == landingPane || newScreen == startMenuPane || newScreen == settingsPane) {
			GameMusic.stopJourneyBeginsMusic();
			GameMusic.startMainMenuMusic();
		} else if (newScreen == characterCreationPane) {
			GameMusic.stopMainMenuMusic();
			GameMusic.startJourneyBeginsMusic();
		} else {
			GameMusic.stopMainMenuMusic();
			GameMusic.stopJourneyBeginsMusic();
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
		if (cardPlayModal != null && !cardPlayModal.contents.isEmpty()) {
			cardPlayModal.refreshLayout();
		}
	}

	/**
	 * Scene refresh re-adds objects on top of the canvas; the obstacle modal would
	 * otherwise sit underneath and getElementAt would hit the scene instead of buttons.
	 */
	public Player getPlayer() {
		return gameState.getPlayer();
	}

	/**
	 * Shows the card play modal overlay on top of the current screen.
	 * The modal lets the player choose a card to resolve the given obstacle.
	 * After the modal is dismissed, onComplete is called to advance the game.
	 *
	 * @param obstacle   the ObstacleScene to resolve
	 * @param onComplete Runnable called when the player clicks Continue
	 */
	public void showObstacle(ObstacleScene obstacle, Runnable onComplete) {
		cardPlayModal.showObstacle(obstacle, onComplete);
	}

	/**
	 * Shows the card play modal in tutorial mode — the played card is NOT consumed.
	 * Used for Scene 1 so the player keeps their card after the tutorial obstacle.
	 */
	public void showObstacleTutorial(ObstacleScene obstacle, Runnable onComplete) {
		cardPlayModal.showObstacle(obstacle, onComplete, true);
	}

	/** Dims the screen and shows Settings / Main Menu / Exit (opened from the × corner button). */
	public void showPauseModal() {
		if (pauseModal != null) {
			pauseModal.showPause();
		}
	}

	/**
	 * Switches to the game over screen.
	 * Currently routes to the ending pane as a placeholder.
	 */
	public void switchToGameOverScreen() {
		switchToScreen(endingPane);
	}

	public GObject getElementAtLocation(double x, double y) {
		return getElementAt(x, y);
	}

	@Override
	public void mousePressed(MouseEvent e) {
		if (cardPlayModal != null && !cardPlayModal.contents.isEmpty()) {
			return;
		}
		if (pauseModal != null && !pauseModal.contents.isEmpty()) {
			return;
		}
		if (currentScreen != null) {
			currentScreen.mousePressed(e);
		}
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		if (cardPlayModal != null && !cardPlayModal.contents.isEmpty()) {
			if (SwingUtilities.isLeftMouseButton(e)) {
				cardPlayModal.handlePointer(e.getX(), e.getY());
			}
			return;
		}
		if (pauseModal != null && !pauseModal.contents.isEmpty()) {
			if (SwingUtilities.isLeftMouseButton(e)) {
				pauseModal.handlePointer(e.getX(), e.getY());
			}
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
		if (cardPlayModal != null && !cardPlayModal.contents.isEmpty()) {
			return;
		}
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
		if (cardPlayModal != null && !cardPlayModal.contents.isEmpty()) {
			return;
		}
		if (pauseModal != null && !pauseModal.contents.isEmpty()) {
			return;
		}
		if (currentScreen != null) {
			currentScreen.mouseDragged(e);
		}
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		if (cardPlayModal != null && !cardPlayModal.contents.isEmpty()) {
			return;
		}
		if (pauseModal != null && !pauseModal.contents.isEmpty()) {
			return;
		}
		if (currentScreen != null) {
			currentScreen.mouseMoved(e);
		}
	}

	@Override
	public void keyPressed(KeyEvent e) {
		if (pauseModal != null && !pauseModal.contents.isEmpty()) {
			return;
		}
		if (currentScreen != null) {
			currentScreen.keyPressed(e);
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {
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
