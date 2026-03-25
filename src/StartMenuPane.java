import java.awt.event.MouseEvent;

import acm.graphics.GLabel;
import acm.graphics.GObject;
import acm.graphics.GRoundRect;

/**
 * Main menu — same night scene and title banner as {@link LandingPane}, with
 * Start / Options / Quit.
 */
public class StartMenuPane extends NightScenePane {

    private GLabel wizardSprite;
    private GLabel goatSprite;

    private GRoundRect startFrame;
    private GLabel startLabel;
    private GRoundRect optionsFrame;
    private GLabel optionsLabel;
    private GRoundRect quitFrame;
    private GLabel quitLabel;

    public StartMenuPane(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
    }

    @Override
    public void showContent() {
        double ox = originX();
        double lw = mainScreen.getLayoutWidth();
        double bw = nightButtonWidth();
        double bh = nightButtonHeight();
        double[] tops = threeMenuButtonTops();

        paintNightSky();
        addTitleBanner();

        wizardSprite = new GLabel("\uD83E\uDDD9", 0, 0);
        wizardSprite.setFont(displayFont(72));
        wizardSprite.setColor(NIGHT_CREAM);
        wizardSprite.setLocation(scaleX(48), scaleY(228));
        addGraphic(wizardSprite);

        goatSprite = new GLabel("\uD83D\uDC10", 0, 0);
        goatSprite.setFont(displayFont(72));
        goatSprite.setColor(NIGHT_CREAM);
        goatSprite.setLocation(scaleX(560), scaleY(228));
        addGraphic(goatSprite);

        startFrame = addNightButton(ox, lw, tops[0], bw, bh);
        startLabel = new GLabel("Start Game", 0, 0);
        startLabel.setFont(displayFont(18));
        startLabel.setColor(NIGHT_GOLD);
        centerLabelInRect(startLabel, startFrame);
        addGraphic(startLabel);

        optionsFrame = addNightButton(ox, lw, tops[1], bw, bh);
        optionsLabel = new GLabel("Options", 0, 0);
        optionsLabel.setFont(displayFont(18));
        optionsLabel.setColor(NIGHT_GOLD);
        centerLabelInRect(optionsLabel, optionsFrame);
        addGraphic(optionsLabel);

        quitFrame = addNightButton(ox, lw, tops[2], bw, bh);
        quitLabel = new GLabel("Quit", 0, 0);
        quitLabel.setFont(displayFont(18));
        quitLabel.setColor(NIGHT_GOLD);
        centerLabelInRect(quitLabel, quitFrame);
        addGraphic(quitLabel);
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
        GObject hit = mainScreen.getElementAtLocation(e.getX(), e.getY());
        if (hit == startLabel || hit == startFrame) {
            mainScreen.switchToGameSavesScreen();
            return;
        }
        if (hit == optionsLabel || hit == optionsFrame) {
            mainScreen.switchToSettingsScreen();
            return;
        }
        if (hit == quitLabel || hit == quitFrame) {
            System.exit(0);
        }
    }
}
