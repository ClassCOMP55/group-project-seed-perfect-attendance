import java.awt.Color;
import java.awt.event.MouseEvent;

import acm.graphics.GLabel;
import acm.graphics.GObject;

public class TransitionLoading1Pane extends GraphicsPane{
	private GLabel nextButton;
	
	public TransitionLoading1Pane(MainApplication mainScreen) {
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
		String[] lines = {
				"Transition / Loading 1",
				"Placeholder travel/",
				"dialogue pacing",
				"(different from gameplay",
				"scenes).",
				"Click Next to",
				"continue the walkthrough."
		};
		
		int baseY = 55;
		int lineHeight = 22;
		int titleFontSize = 30;
		int bodyFontSize = 24;
		
		for (int i = 0; i < lines.length; i++) {
			GLabel lineLabel = new GLabel(lines[i], 100, 70);
			lineLabel.setColor(Color.BLACK);
			if (i == 0) {
				lineLabel.setFont("DialogInput-PLAIN-" + titleFontSize);
			} else {
				lineLabel.setFont("DialogInput-PLAIN-" + bodyFontSize);
			}
			lineLabel.setLocation((mainScreen.getWidth() - lineLabel.getWidth())/ 2, baseY + i * lineHeight);
			
			contents.add(lineLabel);
			mainScreen.add(lineLabel);
		}
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
			mainScreen.switchToRestingScene1Screen();
		}
	}
}

