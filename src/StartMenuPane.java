import java.awt.Color;
import java.awt.event.MouseEvent;

import acm.graphics.GLabel;
import acm.graphics.GObject;

public class StartMenuPane extends GraphicsPane{
	private GLabel nextButton;
	
	public StartMenuPane(MainApplication mainScreen) {
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
				"Start Menu",
				"Click Next to begin walkthrough"
		};
		
		int baseY = 60;
		int lineHeight = 30;
		
		for (int i = 0; i < lines.length; i++) {
			GLabel lineLabel = new GLabel(lines[i], 100, 70);
			lineLabel.setColor(Color.BLACK);
			if (i == 0) {
				lineLabel.setFont(scaledFont(30));
			} else {
				lineLabel.setFont(scaledFont(24));
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
			mainScreen.switchToCharacterCreationScreen();
		}
	}
}

