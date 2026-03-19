import java.awt.Color;
import java.awt.event.MouseEvent;

import acm.graphics.GLabel;
import acm.graphics.GObject;

public class RestingScene2Pane extends GraphicsPane{
	private GLabel nextButton;
	
	public RestingScene2Pane(MainApplication mainScreen) {
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
		GLabel text = new GLabel("Resting Scene 2", 100, 70);
		text.setColor(Color.BLACK);
		text.setFont("DialogInput-PLAIN-30");
		text.setLocation((mainScreen.getWidth() - text.getWidth())/ 2, 70);
		
		contents.add(text);
		mainScreen.add(text);
	}
	
	private void addNextButton() {
		nextButton = new GLabel("Next", 0, 0);
		nextButton.setColor(Color.BLUE);
		nextButton.setFont("DialogInput-PLAIN-24");
		nextButton.setLocation((mainScreen.getWidth() - nextButton.getWidth())/ 2, 400);
		
		contents.add(nextButton);
		mainScreen.add(nextButton);
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		if (mainScreen.getElementAtLocation(e.getX(), e.getY()) == nextButton) {
			mainScreen.switchToScene5Screen();
		}
	}
}

