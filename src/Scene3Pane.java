import java.awt.Color;
import java.awt.event.MouseEvent;

import acm.graphics.GLabel;
import acm.graphics.GObject;

public class Scene3Pane extends GraphicsPane{
	private GLabel nextButton;
	
	public Scene3Pane(MainApplication mainScreen) {
		this.mainScreen = mainScreen;
	}

	@Override
	public void showContent() {
		addPlaceholderText();
		addNextButton();
	}

	@Override
	public void hideContent() {
		for(GObject item : contents) {
			mainScreen.remove(item);
		}
		contents.clear();
	}
	
	private void addPlaceholderText() {
		GLabel text = new GLabel("Scene 3", 100, 70);
		text.setColor(Color.BLACK);
		text.setFont(scaledFont(30));
		text.setLocation(centeredX(text), scaleY(70));
		
		contents.add(text);
		mainScreen.add(text);
	}
	
	private void addNextButton() {
		nextButton = new GLabel("Next", 0, 0);
		nextButton.setColor(Color.BLUE);
		nextButton.setFont(scaledFont(24));
		nextButton.setLocation(centeredX(nextButton), scaleY(400));
		
		contents.add(nextButton);
		mainScreen.add(nextButton);
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		if (mainScreen.getElementAtLocation(e.getX(), e.getY()) == nextButton) {
			mainScreen.switchToScene4Screen();
		}
	}
}

