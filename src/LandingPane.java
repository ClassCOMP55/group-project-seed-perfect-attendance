import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import acm.graphics.GLabel;
import acm.graphics.GObject;
import acm.graphics.GRoundRect;

/**
 * Landing splash — same night scene as the main menu, plus Enter CTA.
 */
public class LandingPane extends NightScenePane {

    private GLabel ctaLabel;
    private GLabel hintLabel;
    private GLabel wizardEmoji;
    private GLabel goatEmoji;
    private GRoundRect enterFrame;
    private GLabel enterLabel;

    public LandingPane(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
    }

    @Override
    public void showContent() {
        double ox = originX();
        double lw = mainScreen.getLayoutWidth();
        double bw = nightButtonWidth();
        double bh = nightButtonHeight();
        double enterTop = scaleY(350);

        paintNightSky();
        addTitleBanner();

        wizardEmoji = new GLabel("\uD83E\uDDD9", 0, 0);
        wizardEmoji.setFont(displayFont(72));
        wizardEmoji.setColor(NIGHT_CREAM);
        wizardEmoji.setLocation(scaleX(48), scaleY(255));
        addGraphic(wizardEmoji);

        goatEmoji = new GLabel("\uD83D\uDC10", 0, 0);
        goatEmoji.setFont(displayFont(72));
        goatEmoji.setColor(NIGHT_CREAM);
        goatEmoji.setLocation(scaleX(560), scaleY(255));
        addGraphic(goatEmoji);

        enterFrame = addNightButton(ox, lw, enterTop, bw, bh);

        enterLabel = new GLabel("Enter the Realm", 0, 0);
        enterLabel.setFont(displayFont(18));
        enterLabel.setColor(NIGHT_GOLD);
        centerLabelInRect(enterLabel, enterFrame);
        addGraphic(enterLabel);

        ctaLabel = new GLabel("Press Enter  ·  Space  ·  or Click", 0, 0);
        ctaLabel.setFont(displayFont(12));
        ctaLabel.setColor(new Color(160, 170, 210));
        ctaLabel.setLocation(centeredX(ctaLabel), scaleY(410));
        addGraphic(ctaLabel);

        hintLabel = new GLabel("\u2728", 0, 0);
        hintLabel.setFont(displayFont(20));
        hintLabel.setColor(new Color(255, 230, 150));
        hintLabel.setLocation(centeredX(hintLabel), scaleY(434));
        addGraphic(hintLabel);
    }

    private void goToMenu() {
        mainScreen.switchToStartMenuScreen();
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
        goToMenu();
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE) {
            goToMenu();
        }
    }
}
