import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import acm.graphics.GLabel;
import acm.graphics.GObject;
import acm.graphics.GRect;

/**
 * Black-screen narrative pane shown at the start of a new game.
 * Lines are revealed one at a time: E finishes the current typing line,
 * or starts the next one. After the last line, E advances to TutorialPane.
 */
public class OpeningNarrativePane extends GraphicsPane {

    private static final String[] PARAGRAPHS = {
        "This place has always been a waypoint.",
        "Walled in and centrally located, it draws merchants and travelers from every direction \u2014 a permanent stop on every trader\u2019s route. The inn keeps the lights on. The stalls do the rest.",
        "This week was like any other. Until the monsters came.",
        "Most people ran. You didn\u2019t make it out.",
        "The bridge is broken. The gate holds, for now. And a goat is staring at you like you owe it something."
    };

    private static final int WRAP_CHARS    = 62;
    private static final int FONT_SIZE     = 17;
    private static final double START_Y    = 154;
    private static final double LINE_STEP  = 28;
    private static final double PARA_GAP   = 14;
    private static final int CHARS_PER_TICK = 2;

    // Pre-computed layout
    private List<String> textLines;
    private List<Double> yPositions;

    // Runtime state — reset each showContent()
    private int    cursor;        // index of next line to start revealing
    private boolean typing;       // true while a line is being typed out
    private int    charsShown;    // chars revealed in the current line
    private GLabel typingLabel;   // label currently being typed into

    public OpeningNarrativePane(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
    }

    /** Needs the game loop so onTick drives the typewriter effect. */
    @Override
    public boolean needsGameLoop() {
        return true;
    }

    @Override
    public void showContent() {
        buildLayout();

        // Black background
        GRect bg = new GRect(originX(), originY(),
                mainScreen.getLayoutWidth(), mainScreen.getLayoutHeight());
        bg.setFilled(true);
        bg.setColor(Color.BLACK);
        bg.setFillColor(Color.BLACK);
        place(bg);

        // Hint at bottom — place first so font metrics are available, then center
        GLabel hint = new GLabel("press 'e' to progress", 0, 0);
        hint.setFont("Courier New-BOLD-" + Math.max(12, scaleFontSize(16)));
        hint.setColor(Color.WHITE);
        place(hint);
        hint.setLocation(centeredX(hint), originY() + mainScreen.getLayoutHeight() * 0.92);

        // Reset reveal state
        cursor      = 0;
        typing      = false;
        charsShown  = 0;
        typingLabel = null;

        addSettingsCornerButton();
    }

    @Override
    public void hideContent() {
        for (GObject obj : contents) {
            mainScreen.remove(obj);
        }
        contents.clear();
        typingLabel = null;
    }

    @Override
    public void onTick(double dt) {
        if (!typing || typingLabel == null) return;

        String full = textLines.get(cursor - 1); // cursor already incremented when typing started
        charsShown = Math.min(charsShown + CHARS_PER_TICK, full.length());
        typingLabel.setLabel(full.substring(0, charsShown));

        if (charsShown >= full.length()) {
            typing = false;
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_E) {
            onAdvance();
        }
    }

    private void onAdvance() {
        if (typing) {
            // Skip to end of current line
            String full = textLines.get(cursor - 1);
            typingLabel.setLabel(full);
            charsShown = full.length();
            typing = false;
        } else if (cursor < textLines.size()) {
            startNextLine();
        } else {
            mainScreen.beginGameplay();
        }
    }

    private void startNextLine() {
        String line = textLines.get(cursor);
        double y    = yPositions.get(cursor);

        // Place with full text so font metrics are available for centering,
        // then reset to empty so the typewriter fills it in from the correct position.
        GLabel lbl = new GLabel(line, 0, 0);
        lbl.setFont(italicFont(FONT_SIZE));
        lbl.setColor(Color.WHITE);
        place(lbl);
        lbl.setLocation(centeredX(lbl), scaleY(y));
        lbl.setLabel("");

        typingLabel = lbl;
        charsShown  = 0;
        cursor++;
        typing = true;
    }

    private void buildLayout() {
        textLines  = new ArrayList<>();
        yPositions = new ArrayList<>();
        double y = START_Y;
        for (int i = 0; i < PARAGRAPHS.length; i++) {
            if (i > 0) y += PARA_GAP;
            for (String line : wrapText(PARAGRAPHS[i], WRAP_CHARS)) {
                textLines.add(line);
                yPositions.add(y);
                y += LINE_STEP;
            }
        }
    }

    private String italicFont(int base) {
        return "Courier New-BOLD-" + Math.max(10, scaleFontSize(base));
    }
}
