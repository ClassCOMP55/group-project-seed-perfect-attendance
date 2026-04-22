import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import acm.graphics.GLabel;
import acm.graphics.GObject;
import acm.graphics.GRect;

/**
 * Black-screen epilogue shown after Calumund's post-boss dialogue.
 * Lines are revealed one at a time: E finishes the current typing line,
 * or starts the next one. After the last line, E returns to the start menu.
 */
public class EndingNarrativePane extends GraphicsPane {

    private static final String[] PARAGRAPHS = {
        "Bastian Myrwick is gone \u2014 lost to the very power he couldn\u2019t control. The polymorph wand shattered with him.",
        "Calumund Vaen Solmare stood in the ruins of the keep, very much a wizard again, and very much himself. The polymorph wand\u2019s effects, it seemed, had not fully worn off.",
        "He has since developed an unusual appetite for grass. He finds this deeply undignified.",
        "Word spread quickly, as it does along trade routes. The waypoint filled back up. Merchants returned, stalls went back up, and the bridge held firm under the weight of travelers who had no idea what it had taken to fix it.",
        "Calumund stayed. He made it known he could use someone who knows how to swing a sword. What you do with that is up to you.",
        "The road is open again.",
        "That\u2019s enough."
    };

    private static final int WRAP_CHARS    = 62;
    private static final int FONT_SIZE     = 17;
    private static final double START_Y    = 60;
    private static final double LINE_STEP  = 28;
    private static final double PARA_GAP   = 14;
    private static final int CHARS_PER_TICK = 2;

    private List<String> textLines;
    private List<Double> yPositions;

    private int    cursor;
    private boolean typing;
    private int    charsShown;
    private GLabel typingLabel;

    public EndingNarrativePane(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
    }

    @Override
    public boolean needsGameLoop() {
        return true;
    }

    @Override
    public void showContent() {
        buildLayout();

        GRect bg = new GRect(originX(), originY(),
                mainScreen.getLayoutWidth(), mainScreen.getLayoutHeight());
        bg.setFilled(true);
        bg.setColor(Color.BLACK);
        bg.setFillColor(Color.BLACK);
        place(bg);

        // Hint at bottom — place first so font metrics are available, then center
        GLabel hint = new GLabel("press 'e' to progress", 0, 0);
        hint.setFont("Monospaced-PLAIN-" + Math.max(12, scaleFontSize(16)));
        hint.setColor(Color.WHITE);
        place(hint);
        hint.setLocation(centeredX(hint), originY() + mainScreen.getLayoutHeight() * 0.92);

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

        String full = textLines.get(cursor - 1);
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
            String full = textLines.get(cursor - 1);
            typingLabel.setLabel(full);
            charsShown = full.length();
            typing = false;
        } else if (cursor < textLines.size()) {
            startNextLine();
        } else {
            mainScreen.switchToStartMenuScreen();
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
        return "Comic Sans MS-ITALIC-" + Math.max(10, scaleFontSize(base));
    }
}
