import java.awt.Color;

import acm.graphics.GLabel;
import acm.graphics.GObject;

public class EndingPane extends GraphicsPane{
	
	public EndingPane(MainApplication mainScreen) {
		this.mainScreen = mainScreen;
	}

	@Override
	public void showContent() {
		addCreditsText();
	}

	@Override
	public void hideContent() {
		for(GObject item : contents) {
			mainScreen.remove(item);
		}
		contents.clear();
	}
	
	private void addCreditsText() {
		String[] lines = {
				"Ending / Credits",
				"Final navigator stop",
				"(intentionally no Next",
				"button).",
				"Walkthrough ends here.",
				"Close and restart the",
				"game to run again."
		};
		
		int baseY = 45;
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
}

