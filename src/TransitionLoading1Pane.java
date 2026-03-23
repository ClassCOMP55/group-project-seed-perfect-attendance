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
		addSettingsCornerButton();
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
				lineLabel.setFont(scaledFont(titleFontSize));
			} else {
				lineLabel.setFont(scaledFont(bodyFontSize));
			}
			lineLabel.setLocation(centeredX(lineLabel), scaleY(baseY + i * lineHeight));
			
			contents.add(lineLabel);
			mainScreen.add(lineLabel);
		}
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
			mainScreen.switchToRestingScene1Screen();
		}
	}
}

