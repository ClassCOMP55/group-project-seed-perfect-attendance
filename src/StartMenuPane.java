import java.awt.Color;
import java.awt.event.MouseEvent;

import acm.graphics.GImage;
import acm.graphics.GObject;
import acm.graphics.GRect;

/**
 * Main menu — displays Start Game.png full-screen.
 * Three hit zones over the baked-in Start Game / Options / Quit buttons.
 */
public class StartMenuPane extends NightScenePane {

    private static final String BG = "assets/visuals/start screen/Start Game.png";

    // All three buttons share the same X and width — tune after first run
    private static final int BTN_X = 465;
    private static final int BTN_W = 362;
    private static final int BTN_H = 45;
    private static final int BTN_START_Y   = 412;
    private static final int BTN_OPTIONS_Y = 470;
    private static final int BTN_QUIT_Y    = 528;

    public StartMenuPane(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
    }

    @Override
    public void showContent() {
        GImage bg = new GImage(BG, 0, 0);
        bg.setSize(mainScreen.getWidth(), mainScreen.getHeight());
        addGraphic(bg);

        makeZone(BTN_X, BTN_START_Y,   BTN_W, BTN_H);
        makeZone(BTN_X, BTN_OPTIONS_Y, BTN_W, BTN_H);
        makeZone(BTN_X, BTN_QUIT_Y,    BTN_W, BTN_H);
    }

    private GRect makeZone(int x, int y, int w, int h) {
        GRect r = new GRect(x, y, w, h);
        r.setFilled(false);
        r.setColor(new Color(0, 0, 0, 0));
        addGraphic(r);
        return r;
    }

    @Override
    public void hideContent() {
        for (GObject item : contents) {
            mainScreen.remove(item);
        }
        contents.clear();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        int mx = e.getX(), my = e.getY();
        if (inZone(mx, my, BTN_X, BTN_START_Y, BTN_W, BTN_H)) {
            mainScreen.switchToGameSavesScreen();
        } else if (inZone(mx, my, BTN_X, BTN_OPTIONS_Y, BTN_W, BTN_H)) {
            mainScreen.switchToSettingsScreen();
        } else if (inZone(mx, my, BTN_X, BTN_QUIT_Y, BTN_W, BTN_H)) {
            System.exit(0);
        }
    }

    private static boolean inZone(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
