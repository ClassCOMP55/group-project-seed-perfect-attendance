import java.awt.Color;
import java.awt.event.KeyEvent;

import acm.graphics.GLabel;
import acm.graphics.GObject;
import acm.graphics.GRect;

/**
 * Black-screen controls/tips card shown after the opening narration.
 * All content is displayed immediately. Press E to begin gameplay.
 */
public class TutorialPane extends GraphicsPane {

    private static final int HEADER_SIZE  = 22;
    private static final int CONTROL_SIZE = 16;
    private static final int PROSE_SIZE   = 15;
    private static final int LINE_STEP    = 26;

    public TutorialPane(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
    }

    @Override
    public void showContent() {
        GRect bg = new GRect(originX(), originY(),
                mainScreen.getLayoutWidth(), mainScreen.getLayoutHeight());
        bg.setFilled(true);
        bg.setColor(Color.BLACK);
        bg.setFillColor(Color.BLACK);
        place(bg);

        Color ink = Color.WHITE;
        double y = scaleY(70);

        // Header
        GLabel header = new GLabel("Controls", 0, 0);
        header.setFont(monoFont(HEADER_SIZE, true));
        header.setColor(ink);
        place(header);
        header.setLocation(centeredX(header), y);
        y += scaleY(LINE_STEP + 8);

        // Control lines — merged into one label per line so they center as a unit
        String[] controls = {
            "WASD   \u2014 Move",
            "SHIFT  \u2014 Strafe (keep facing the same direction while moving)",
            "J      \u2014 Attack",
            "K      \u2014 Use ability (if you have one)"
        };
        for (String line : controls) {
            GLabel lbl = new GLabel(line, 0, 0);
            lbl.setFont(monoFont(CONTROL_SIZE, false));
            lbl.setColor(ink);
            place(lbl);
            lbl.setLocation(centeredX(lbl), y);
            y += scaleY(LINE_STEP);
        }

        y += scaleY(20);

        // Prose paragraphs
        String[] prose = {
            "To heal, open your inventory and use a healing item. There is no automatic healing.",
            "Progress is not saved automatically. Rest at the inn in town to save your game.",
            "Good luck."
        };
        for (String line : prose) {
            for (String wrapped : wrapText(line, 62)) {
                GLabel lbl = new GLabel(wrapped, 0, 0);
                lbl.setFont(monoFont(PROSE_SIZE, false));
                lbl.setColor(ink);
                place(lbl);
                lbl.setLocation(centeredX(lbl), y);
                y += scaleY(LINE_STEP);
            }
            y += scaleY(8);
        }

        // Hint at bottom — place first so font metrics are available, then center
        GLabel hint = new GLabel("press 'e' to progress", 0, 0);
        hint.setFont(monoFont(16, false));
        hint.setColor(Color.WHITE);
        place(hint);
        hint.setLocation(centeredX(hint), originY() + mainScreen.getLayoutHeight() * 0.92);

        addSettingsCornerButton();
    }

    @Override
    public void hideContent() {
        for (GObject obj : contents) {
            mainScreen.remove(obj);
        }
        contents.clear();
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_E) {
            mainScreen.beginGameplay();
        }
    }

    private String monoFont(int base, boolean bold) {
        return "Monospaced-" + (bold ? "BOLD" : "PLAIN") + "-" + Math.max(10, scaleFontSize(base));
    }
}
