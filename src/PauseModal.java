import java.awt.Color;

import acm.graphics.GLabel;
import acm.graphics.GObject;
import acm.graphics.GRect;
import acm.graphics.GRoundRect;

/**
 * Full-screen dim overlay with a "Paused Game" panel: Settings, Return to Main Menu, Exit Game.
 */
public class PauseModal extends GraphicsPane {

    private GRect dimOverlay;
    private GRoundRect panel;
    private GLabel titleLabel;

    private GRoundRect settingsFrame;
    private GLabel settingsLabel;
    private GRoundRect mainMenuFrame;
    private GLabel mainMenuLabel;

    private GRoundRect exitFrame;
    private GLabel exitLabel;

    private static final Color PANEL_FILL = new Color(28, 32, 62);
    private static final Color PANEL_BORDER = new Color(255, 215, 120);
    private static final Color BTN_FILL = new Color(35, 40, 75);
    private static final Color TEXT_GOLD = new Color(255, 215, 120);
    private static final Color TITLE_CREAM = new Color(255, 248, 220);

    public PauseModal(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
    }

    /**
     * Shows dimmed background and the pause panel on top of the current scene.
     */
    public void showPause() {
        hideContent();

        double fw = mainScreen.getWidth();
        double fh = mainScreen.getHeight();
        dimOverlay = new GRect(0, 0, fw, fh);
        dimOverlay.setFilled(true);
        dimOverlay.setFillColor(new Color(0, 0, 0, 160));
        dimOverlay.setColor(new Color(0, 0, 0, 0));
        addBoth(dimOverlay);

        double lw = mainScreen.getLayoutWidth();
        double ox = originX();
        double panelW = Math.min(scaleX(380) - scaleX(0), lw * 0.82);
        double panelH = scaleY(260) - scaleY(0);
        double px = ox + (lw - panelW) / 2;
        double py = scaleY(100);
        double arc = Math.min(scaleX(14) - scaleX(0), scaleY(14) - scaleY(0));

        panel = new GRoundRect(px, py, panelW, panelH, arc, arc);
        panel.setFilled(true);
        panel.setFillColor(PANEL_FILL);
        panel.setColor(PANEL_BORDER);
        addBoth(panel);

        titleLabel = new GLabel("Paused Game", 0, 0);
        titleLabel.setFont("SansSerif-BOLD-" + Math.max(14, scaleFontSize(22)));
        titleLabel.setColor(TITLE_CREAM);
        titleLabel.setLocation(px + (panelW - titleLabel.getWidth()) / 2, py + scaleY(28));
        addBoth(titleLabel);

        double bw = Math.min(scaleX(280) - scaleX(0), panelW - scaleX(40));
        double bh = scaleY(36) - scaleY(0);
        double gap = scaleY(10) - scaleY(0);
        double btnLeft = px + (panelW - bw) / 2;
        double y0 = py + scaleY(58);

        settingsFrame = new GRoundRect(btnLeft, y0, bw, bh, arc, arc);
        settingsFrame.setFilled(true);
        settingsFrame.setFillColor(BTN_FILL);
        settingsFrame.setColor(PANEL_BORDER);
        addBoth(settingsFrame);
        settingsLabel = new GLabel("Settings", 0, 0);
        settingsLabel.setFont("SansSerif-BOLD-" + Math.max(12, scaleFontSize(16)));
        settingsLabel.setColor(TEXT_GOLD);
        centerLabelInButton(settingsLabel, settingsFrame);
        addBoth(settingsLabel);

        mainMenuFrame = new GRoundRect(btnLeft, y0 + bh + gap, bw, bh, arc, arc);
        mainMenuFrame.setFilled(true);
        mainMenuFrame.setFillColor(BTN_FILL);
        mainMenuFrame.setColor(PANEL_BORDER);
        addBoth(mainMenuFrame);
        mainMenuLabel = new GLabel("Return to Main Menu", 0, 0);
        mainMenuLabel.setFont("SansSerif-BOLD-" + Math.max(12, scaleFontSize(16)));
        mainMenuLabel.setColor(TEXT_GOLD);
        centerLabelInButton(mainMenuLabel, mainMenuFrame);
        addBoth(mainMenuLabel);

        exitFrame = new GRoundRect(btnLeft, y0 + 2 * (bh + gap), bw, bh, arc, arc);
        exitFrame.setFilled(true);
        exitFrame.setFillColor(BTN_FILL);
        exitFrame.setColor(PANEL_BORDER);
        addBoth(exitFrame);
        exitLabel = new GLabel("Exit Game", 0, 0);
        exitLabel.setFont("SansSerif-BOLD-" + Math.max(12, scaleFontSize(16)));
        exitLabel.setColor(TEXT_GOLD);
        centerLabelInButton(exitLabel, exitFrame);
        addBoth(exitLabel);

        restackOnTop();
    }

    /** Rebuild at the new window size while keeping the pause menu open. */
    public void refreshForResize() {
        if (contents.isEmpty()) {
            return;
        }
        showPause();
    }

    private void centerLabelInButton(GLabel g, GRoundRect r) {
        double x = r.getX() + (r.getWidth() - g.getWidth()) / 2;
        double y = r.getY() + (r.getHeight() + g.getAscent()) / 2;
        g.setLocation(x, y);
    }

    private void addBoth(GObject g) {
        contents.add(g);
        mainScreen.add(g);
    }

    /**
     * Re-adds all objects on top after a canvas refresh (resize).
     */
    public void restackOnTop() {
        java.util.ArrayList<GObject> snapshot = new java.util.ArrayList<>(contents);
        for (GObject g : snapshot) {
            mainScreen.remove(g);
            mainScreen.add(g);
        }
    }

    private static boolean containsPoint(GObject g, double x, double y) {
        return g != null && g.contains(x, y);
    }

    /**
     * Left-button release handler (same pattern as {@link CardPlayModal}).
     */
    public void handlePointer(double x, double y) {
        if (containsPoint(settingsFrame, x, y) || containsPoint(settingsLabel, x, y)) {
            hideContent();
            mainScreen.switchToSettingsScreen();
            return;
        }
        if (containsPoint(mainMenuFrame, x, y) || containsPoint(mainMenuLabel, x, y)) {
            hideContent();
            mainScreen.switchToStartMenuScreen();
            return;
        }
        if (containsPoint(exitFrame, x, y) || containsPoint(exitLabel, x, y)) {
            System.exit(0);
        }
    }

    @Override
    public void hideContent() {
        for (GObject item : contents) {
            mainScreen.remove(item);
        }
        contents.clear();
        dimOverlay = null;
        panel = null;
        titleLabel = null;
        settingsFrame = null;
        settingsLabel = null;
        mainMenuFrame = null;
        mainMenuLabel = null;
        exitFrame = null;
        exitLabel = null;
    }
}
