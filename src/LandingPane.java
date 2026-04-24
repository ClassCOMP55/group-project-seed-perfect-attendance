import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import acm.graphics.GImage;
import acm.graphics.GObject;
import acm.graphics.GRect;

/**
 * Landing splash — displays title.png full-screen.
 * One hit zone over the "Enter the Realm" button; E / Enter / Space also advance.
 */
public class LandingPane extends NightScenePane {

    private static final String BG = "assets/visuals/start screen/title.png";

    // "Enter the Realm" button hit zone — tune these numbers after first run
    private static final int ENTER_X = 407;
    private static final int ENTER_Y = 428;
    private static final int ENTER_W = 447;
    private static final int ENTER_H = 57;

    private GRect enterZone;

    public LandingPane(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
    }

    @Override
    public void showContent() {
        GImage bg = new GImage(BG, 0, 0);
        bg.setSize(mainScreen.getWidth(), mainScreen.getHeight());
        addGraphic(bg);

        enterZone = new GRect(ENTER_X, ENTER_Y, ENTER_W, ENTER_H);
        enterZone.setFilled(false);
        enterZone.setColor(new Color(0, 0, 0, 0));
        addGraphic(enterZone);
    }

    @Override
    public void hideContent() {
        for (GObject item : contents) {
            mainScreen.remove(item);
        }
        contents.clear();
        enterZone = null;
    }

    private void goToMenu() {
        mainScreen.switchToStartMenuScreen();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        int mx = e.getX(), my = e.getY();
        if (mx >= ENTER_X && mx <= ENTER_X + ENTER_W
                && my >= ENTER_Y && my <= ENTER_Y + ENTER_H) {
            goToMenu();
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE || k == KeyEvent.VK_E) {
            goToMenu();
        }
    }
}
