import java.awt.event.MouseEvent;

import acm.graphics.GImage;
import acm.graphics.GObject;

public class WelcomePane extends GraphicsPane{
	public WelcomePane(MainApplication mainScreen) {
		this.mainScreen = mainScreen;
	}
	
	@Override
	public void showContent() {
		addPicture();
		addDescriptionButton();
	}

	@Override
	public void hideContent() {
		for(GObject item : contents) {
			mainScreen.remove(item);
		}
		contents.clear();
	}
	
	private void addPicture(){
		GImage startImage = new GImage("start.png", 200, 100);
		double imageScale = 0.5 * uniformScale();
		startImage.scale(imageScale, imageScale);
		startImage.setLocation(centeredX(startImage), scaleY(70));
		
		contents.add(startImage);
		mainScreen.add(startImage);
	}
	
	private void addDescriptionButton() {
		GImage moreButton = new GImage("more.jpeg", 200, 400);
		double imageScale = 0.3 * uniformScale();
		moreButton.scale(imageScale, imageScale);
		moreButton.setLocation(centeredX(moreButton), scaleY(400));
		
		contents.add(moreButton);
		mainScreen.add(moreButton);

	}
	
	@Override
	public void mouseClicked(MouseEvent e) {
		if (mainScreen.getElementAtLocation(e.getX(), e.getY()) == contents.get(1)) {
			mainScreen.switchToDescriptionScreen();
		}
	}

}
