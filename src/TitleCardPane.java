import java.awt.Color;
import java.awt.event.MouseEvent;

import acm.graphics.GLabel;
import acm.graphics.GObject;
import acm.graphics.GRect;

/**
 * Intro “title card” splash: black screen, handwritten-style copy, art placeholder,
 * Continue → main menu.
 */
public class TitleCardPane extends GraphicsPane {

    private GRect fullscreenBg;
    private GLabel[] introLines;
    private GLabel titleCardTag;
    private GRect artPlaceholder;
    private GLabel artHint;
    private GRect continueFrame;
    private GLabel continueLabel;

    public TitleCardPane(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
    }

    @Override
    public void showContent() {
        fullscreenBg = new GRect(originX(), originY(), mainScreen.getLayoutWidth(), mainScreen.getLayoutHeight());
        fullscreenBg.setFilled(true);
        fullscreenBg.setColor(Color.BLACK);
        fullscreenBg.setFillColor(Color.BLACK);
        addBoth(fullscreenBg);

        Color ink = Color.WHITE;
        String[] lines = {
            "So there's a Wizard Who's a Goat now..."
        };
        introLines = new GLabel[lines.length];
        double y = 52;
        for (int i = 0; i < lines.length; i++) {
            GLabel line = new GLabel(lines[i], 0, 0);
            line.setFont(sketchFont(17));
            line.setColor(ink);
            line.setLocation(scaleX(28), scaleY(y));
            introLines[i] = line;
            addBoth(line);
            y += 28;
        }

        titleCardTag = new GLabel("* Title Card *", 0, 0);
        titleCardTag.setFont(sketchFont(22));
        titleCardTag.setColor(ink);
        placeCenterX(titleCardTag, scaleY(400));
        addBoth(titleCardTag);

        double art = 72 * uniformScale();
        double pad = 28 * uniformScale();
        double ax = originX() + mainScreen.getLayoutWidth() - pad - art;
        double ay = originY() + mainScreen.getLayoutHeight() - pad - art;
        artPlaceholder = new GRect(ax, ay, art, art);
        artPlaceholder.setFilled(false);
        artPlaceholder.setColor(ink);
        addBoth(artPlaceholder);

        artHint = new GLabel("ART", 0, 0);
        artHint.setFont(sketchFont(12));
        artHint.setColor(new Color(180, 180, 180));
        artHint.setLocation(ax + art / 2 - artHint.getWidth() / 2, ay + art / 2 + artHint.getAscent() / 2);
        addBoth(artHint);

        double bw = 200 * uniformScale();
        double bh = 44 * uniformScale();
        double bx = leftEdgeForCenteredWidth(bw);
        continueFrame = new GRect(bx, scaleY(310), bw, bh);
        continueFrame.setFilled(false);
        continueFrame.setColor(ink);
        addBoth(continueFrame);

        continueLabel = new GLabel("Continue", 0, 0);
        continueLabel.setFont(sketchFont(18));
        continueLabel.setColor(ink);
        centerLabelInRect(continueLabel, continueFrame);
        addBoth(continueLabel);

        addSettingsCornerButton();
    }

    private void centerLabelInRect(GLabel g, GRect r) {
        double x = r.getX() + (r.getWidth() - g.getWidth()) / 2;
        double y = r.getY() + (r.getHeight() + g.getAscent()) / 2;
        g.setLocation(x, y);
    }

    private void placeCenterX(GLabel g, double baselineY) {
        g.setLocation(centeredX(g), baselineY);
    }

    private double leftEdgeForCenteredWidth(double width) {
        return originX() + (mainScreen.getLayoutWidth() - width) / 2;
    }

    private String sketchFont(int base) {
        return "Courier New-BOLD-" + Math.max(10, scaleFontSize(base));
    }

    private void addBoth(GObject g) {
        contents.add(g);
        mainScreen.add(g);
    }

    @Override
    public void hideContent() {
        for (GObject item : contents) {
            mainScreen.remove(item);
        }
        contents.clear();
        introLines = null;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        GObject hit = mainScreen.getElementAtLocation(e.getX(), e.getY());
        if (hit == continueLabel || hit == continueFrame) {
            mainScreen.switchToStartMenuScreen();
        }
    }
}
