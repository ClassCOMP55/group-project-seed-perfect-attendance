import acm.graphics.GObject;
import acm.program.*;


import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

public class MainApplication extends GraphicsProgram{
	//Settings
	public static final int WINDOW_WIDTH = 700; 
	public static final int WINDOW_HEIGHT = 500;
	
	private Player player;

	//List of all the full screen panes
	private TitleCardPane titleCardPane;
	private StartMenuPane startMenuPane;
	private CharacterCreationPane characterCreationPane;
	private Scene1Pane scene1Pane;
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
	private GraphicsPane currentScreen;
	private int lastKnownWidth;
	private int lastKnownHeight;
	/** Virtual canvas size used for layout (animates toward window size on resize). */
	private double layoutWidth;
	private double layoutHeight;

	private static final int RESIZE_ANIM_STEP_MS = 20;
	private static final int RESIZE_ANIM_DURATION_MS = 280;


	public MainApplication() {
		super();
	}
	
	protected void setupInteractions() {
		requestFocus();
		addKeyListeners();
		addMouseListeners();
	}
	
	public void init() {
		setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
	}
	
	public void run() {
		System.out.println("Lets' Begin!");
		setupInteractions();
		player = new Player();

		//Initialize all Panes
		titleCardPane = new TitleCardPane(this);
		startMenuPane = new StartMenuPane(this);
		
		characterCreationPane = new CharacterCreationPane(this);
		scene1Pane = new Scene1Pane(this);
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

		//TheDefaultPane
		switchToScreen(titleCardPane);
		lastKnownWidth = (int) getWidth();
		lastKnownHeight = (int) getHeight();
		monitorResizeAndRefresh();
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
		switchToTitleCardScreen();
	}
	
	public void switchToTitleCardScreen() {
		switchToScreen(titleCardPane);
	}
	
	public void switchToStartMenuScreen() {
		switchToScreen(startMenuPane);
	}
	
	public void switchToCharacterCreationScreen() {
		switchToScreen(characterCreationPane);
	}
	
	public void switchToScene1Screen() {
		switchToScreen(scene1Pane);
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
		if(currentScreen != null) {
			currentScreen.hideContent();
		}
		newScreen.showContent();
		currentScreen = newScreen;
	}

	private void refreshCurrentScreen() {
		if (currentScreen == null) {
			return;
		}
		currentScreen.hideContent();
		currentScreen.showContent();
	}

	private void monitorResizeAndRefresh() {
		while (true) {
			pause(100);
			int currentWidth = (int) getWidth();
			int currentHeight = (int) getHeight();
			if (currentWidth != lastKnownWidth || currentHeight != lastKnownHeight) {
				lastKnownWidth = currentWidth;
				lastKnownHeight = currentHeight;
				animateLayoutToTarget(currentWidth, currentHeight);
			}
		}
	}

	/**
	 * Smoothly interpolates layout size from current to target so UI scales in
	 * rather than jumping (ease-out cubic).
	 */
	private void animateLayoutToTarget(double targetW, double targetH) {
		double startW = layoutWidth;
		double startH = layoutHeight;
		int steps = Math.max(1, RESIZE_ANIM_DURATION_MS / RESIZE_ANIM_STEP_MS);
		for (int i = 1; i <= steps; i++) {
			double t = (double) i / steps;
			// ease-out cubic: fast start, gentle finish
			t = 1 - (1 - t) * (1 - t) * (1 - t);
			layoutWidth = startW + (targetW - startW) * t;
			layoutHeight = startH + (targetH - startH) * t;
			refreshCurrentScreen();
			pause(RESIZE_ANIM_STEP_MS);
		}
		layoutWidth = targetW;
		layoutHeight = targetH;
		refreshCurrentScreen();
	}
	
	public Player getPlayer() {
		return player;
	}

	public GObject getElementAtLocation(double x, double y) {
		return getElementAt(x, y);
	}
	
	@Override
	public void mousePressed(MouseEvent e) {
		if(currentScreen != null) {
			currentScreen.mousePressed(e);
		}
	}
	
	@Override
	public void mouseReleased(MouseEvent e) {
		if(currentScreen != null) {
			currentScreen.mouseReleased(e);
		}
	}
	
	@Override
	public void mouseClicked(MouseEvent e) {
		if(currentScreen != null) {
			currentScreen.mouseClicked(e);
		}
	}
	
	@Override
	public void mouseDragged(MouseEvent e) {
		if(currentScreen != null) {
			currentScreen.mouseDragged(e);
		}
	}
	
	@Override
	public void mouseMoved(MouseEvent e) {
		if(currentScreen != null) {
			currentScreen.mouseMoved(e);
		}
	}
	
	@Override
	public void keyPressed(KeyEvent e) {
		if(currentScreen != null) {
			currentScreen.keyPressed(e);
		}
	}
	
	@Override
	public void keyReleased(KeyEvent e) {
		if(currentScreen != null) {
			currentScreen.keyReleased(e);
		}
	}
	
	@Override
	public void keyTyped(KeyEvent e) {
		if(currentScreen != null) {
			currentScreen.keyTyped(e);
		}
	}

}
