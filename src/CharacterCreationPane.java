import java.awt.Color;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import acm.graphics.GLabel;
import acm.graphics.GObject;
import acm.graphics.GRect;

/**
 * CharacterCreationPane — pixel/bit-art dungeon quiz screen.
 *
 * Presents a 10-question personality quiz. A right-side panel shows
 * live mini card art and fill bars so the player can see which card
 * types they are trending toward as they answer. After all questions
 * the top 2 card types are added to the player's hand.
 *
 * Visual style: dark dungeon pixel aesthetic — stone brick tiled
 * background, torch decorations at the panel divider, per-type
 * pixel card art in the right panel.
 *
 * All coordinates use the logical 700 x 500 design space and are
 * converted to actual screen pixels via scaleX / scaleY, so the
 * screen scales cleanly to any window size.
 */
public class CharacterCreationPane extends GraphicsPane {

    // =========================================================
    // QUESTION DATA
    // =========================================================

    /** The 10 quiz questions, shown one at a time. */
    private static final String[] QUESTIONS = {
        "You arrive at a fork in an unfamiliar road. You...",
        "A stranger asks you for help finding a place. You...",
        "You find a locked chest with no key nearby. You...",
        "Your group is lost in a forest. You...",
        "You need to cross a raging river. You...",
        "A merchant overcharges you for supplies. You...",
        "You discover a hidden cave. You...",
        "An injured traveler blocks your path. You...",
        "You must choose between two dangerous paths. You...",
        "You reach your destination but something feels wrong. You..."
    };

    // Each row: [WAYFINDER, SILVER_TONGUE, HEARTSEEKER, WILDCARD]
    private static final String[][] OPTIONS = {
        {"Use the stars to navigate",       "Ask a passing traveler",          "Trust your gut feeling",           "Flip a coin and commit"},
        {"Draw them a map from memory",     "Chat and walk them there",        "Sense their urgency and hurry",    "Point randomly and walk away"},
        {"Look for clues around it",        "Ask locals if they know more",    "Feel if it is worth the risk",     "Smash it open"},
        {"Study the terrain for clues",     "Rally the group with a speech",   "Stay calm and comfort others",     "Climb a tree and shout"},
        {"Build a raft from nearby wood",   "Negotiate with a ferryman",       "Encourage the group across",       "Swim across on a dare"},
        {"Calculate the fair price calmly", "Bargain your way to a discount",  "Appeal to their sense of honor",   "Distract and sneak extra items"},
        {"Map its layout carefully",        "Tell others about it later",      "Check if anyone else needs it",    "Dive in headfirst"},
        {"Assess the safest route around",  "Call for others to help",         "Stop immediately to help",         "Carry them on your back at a run"},
        {"Analyze tracks and terrain",      "Consult every person nearby",     "Go with the path that feels right","Take both paths at once somehow"},
        {"Re-examine your route data",      "Ask the locals what has changed", "Trust instincts and stay alert",   "Declare it an adventure and proceed"}
    };

    /** Each answer column maps to one CardType (column order must match OPTIONS). */
    private static final CardType[] OPTION_TYPES = {
        CardType.WAYFINDER,
        CardType.SILVER_TONGUE,
        CardType.HEARTSEEKER,
        CardType.WILDCARD
    };

    /** Card {name, description} awarded per type (index matches OPTION_TYPES). */
    private static final String[][] CARD_REWARDS = {
        {"Wayfinder's Compass",    "A keen sense of direction that reveals hidden paths."},
        {"Silver Tongue",          "The art of persuasion - words that open locked doors."},
        {"Heartseeker's Instinct", "Empathy sharp enough to pierce any deception."},
        {"The Wildcard",           "Unpredictable and fearless - chaos made useful."}
    };

    // =========================================================
    // COLOUR PALETTE — dark dungeon
    // =========================================================

    // -- Background / structural --
    /** Full-window base fill behind everything. */
    private static final Color C_BG         = new Color(22,  18,  32);
    /** Stone brick colour (main). */
    private static final Color C_BRICK      = new Color(50,  42,  65);
    /** Stone brick colour (alternate, for texture variation). */
    private static final Color C_BRICK_ALT  = new Color(43,  36,  57);
    /** Left panel tint (semi-transparent so bricks show through). */
    private static final Color C_PANEL_L    = new Color(22,  33,  62,  210);
    /** Right panel tint (semi-transparent). */
    private static final Color C_PANEL_R    = new Color(16,  22,  44,  210);
    /** Divider / header accent. */
    private static final Color C_ACCENT     = new Color(233, 69,  96);
    /** Thin separator lines. */
    private static final Color C_SEP        = new Color(50,  58,  90);
    /** Empty bar background. */
    private static final Color C_BAR_BG     = new Color(35,  35,  52);

    // -- Text --
    /** Primary text. */
    private static final Color C_TEXT       = new Color(210, 210, 230);
    /** Dim / secondary text. */
    private static final Color C_DIM        = new Color(110, 110, 145);

    // -- Answer buttons --
    /** Button fill (idle). */
    private static final Color C_BTN_BG     = new Color(28,  40,  78);
    /** Button fill (hovered). */
    private static final Color C_BTN_HOVER  = new Color(52,  68,  118);
    /** Button outer border. */
    private static final Color C_BTN_BORDER = new Color(68,  82,  135);

    // -- Torch colours --
    private static final Color C_TORCH_WOOD  = new Color(90,  60,  35);
    private static final Color C_FLAME_BASE  = new Color(200, 75,  20);
    private static final Color C_FLAME_MID   = new Color(240, 140, 30);
    private static final Color C_FLAME_TIP   = new Color(255, 220, 60);
    private static final Color C_FLAME_GLOW  = new Color(255, 200, 80,  35);

    // -- Per card-type accent colours (indexed same as OPTION_TYPES) --
    private static final Color[] TYPE_COLORS = {
        new Color(0,   180, 216),  // WAYFINDER     — cyan
        new Color(199, 125, 255),  // SILVER_TONGUE — purple
        new Color(255, 107, 107),  // HEARTSEEKER   — coral
        new Color(255, 209, 102)   // WILDCARD      — gold
    };

    /** Short names shown in the right panel. */
    private static final String[] TYPE_LABELS = {
        "WAYFINDER", "SILVER", "HEARTSEEK", "WILDCARD"
    };

    /**
     * Pixel-art icon drawn inside each mini card.
     * Uses simple ASCII symbols that render clearly in monospaced font.
     */
    private static final String[] TYPE_ICONS = {
        "(+)", "(~)", "(<3)", "(*)"
    };

    // =========================================================
    // LAYOUT — logical 700 x 500 coordinate space
    // =========================================================

    // Panel split
    private static final double LEFT_W   = 447;  // logical width of left panel
    private static final double DIV_X    = 449;  // logical X of divider strip (3 px wide)
    private static final double RIGHT_X  = 453;  // logical X where right panel content starts
    private static final double RIGHT_W  = 247;  // logical width of right panel

    // Left panel — answer buttons
    private static final double BTN_X    = 16;   // logical left edge of buttons
    private static final double BTN_W    = 415;  // logical button width
    private static final double BTN_H    = 44;   // logical button height
    private static final double BTN_GAP  = 7;    // logical vertical gap between buttons
    private static final double BTN_Y0   = 124;  // logical Y of top of first button

    // Right panel — mini card geometry (relative to section top-left)
    private static final double CARD_OFF_X = 5;   // card left margin inside right panel
    private static final double CARD_OFF_Y = 12;  // card top margin inside section
    private static final double CARD_W     = 36;  // card width (logical)
    private static final double CARD_H     = 52;  // card height (logical)

    // Right panel — bar geometry (starts after the mini card)
    private static final double BAR_OFF_X  = 46;  // bar left margin inside right panel
    private static final double BAR_W      = 190; // max logical bar width
    private static final double BAR_H      = 12;  // logical bar height

    // Right panel — section layout
    private static final double SEC_H      = 100; // logical height of one card-type section
    private static final double SEC_Y0     = 50;  // logical Y of first section top

    // =========================================================
    // STATE
    // =========================================================

    /** Index of the question currently displayed. */
    private int currentQuestion;

    /** Running score per card type (incremented after each answer). */
    private Map<CardType, Integer> scores;

    /** Hit rectangles for the four answer buttons (used for hover and click). */
    private final List<GRect> buttonBoxes = new ArrayList<>();

    /** Index of the currently hovered answer button, or -1 if none. */
    private int hoveredButton = -1;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    /**
     * Creates CharacterCreationPane.
     * @param mainScreen the main application reference
     */
    public CharacterCreationPane(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
    }

    // =========================================================
    // LIFECYCLE
    // =========================================================

    @Override
    public void showContent() {
        currentQuestion = 0;
        hoveredButton   = -1;
        scores = new EnumMap<>(CardType.class);
        for (CardType type : CardType.values()) {
            scores.put(type, 0);
        }
        renderScreen();
    }

    @Override
    public void hideContent() {
        for (GObject item : contents) {
            mainScreen.remove(item);
        }
        contents.clear();
        buttonBoxes.clear();
        hoveredButton = -1;
    }

    // =========================================================
    // MAIN RENDER
    // =========================================================

    /**
     * Clears and fully redraws the screen.
     * Called on first show and after every answered question.
     */
    private void renderScreen() {
        for (GObject item : contents) {
            mainScreen.remove(item);
        }
        contents.clear();
        buttonBoxes.clear();
        hoveredButton = -1;

        // Draw in back-to-front order so layering is correct
        drawFullBackground();   // base colour
        drawBrickPattern();     // pixel stone tiles
        drawTorches();          // torch decorations at divider wall
        drawPanelOverlays();    // semi-transparent panel tints over bricks
        drawDivider();          // glowing red divider strip
        drawLeftPanel();        // question + answer buttons
        drawRightPanel();       // card bars + mini card art
        addSettingsCornerButton();
    }

    // =========================================================
    // BACKGROUND LAYERS
    // =========================================================

    /** Fills the entire window with the base background colour. */
    private void drawFullBackground() {
        // Covers the whole window, including areas outside the logical canvas
        place(rect(0, 0, mainScreen.getWidth(), mainScreen.getHeight(), C_BG, C_BG));
    }

    /**
     * Draws a stone-brick tile pattern across the full logical canvas.
     * Alternating rows are offset by half a brick to create a staggered bond pattern.
     * Brick colour alternates slightly for texture variation.
     */
    private void drawBrickPattern() {
        final double BW = 66;   // logical brick width
        final double BH = 25;   // logical brick height
        final double M  = 2.0;  // logical mortar gap (empty space between bricks)

        int rows = (int) Math.ceil(500.0 / BH) + 2;
        int cols = (int) Math.ceil(700.0 / BW) + 3;

        for (int row = 0; row < rows; row++) {
            // Odd rows are shifted right by half a brick
            double xOffset = (row % 2 == 0) ? 0 : BW / 2.0;
            for (int col = -1; col < cols; col++) {
                double lx = col * BW + xOffset;
                double ly = row * BH;
                // Every 5th brick (checkerboard-ish) uses the alternate shade
                Color brickCol = ((row * 3 + col) % 5 == 0) ? C_BRICK_ALT : C_BRICK;
                place(srect(lx + M, ly + M, BW - M * 2, BH - M * 2, brickCol, brickCol));
            }
        }
    }

    /**
     * Draws two wall torches at the top and bottom of the panel divider.
     * Each torch is built from layered GRects: glow aura, flame segments, and handle.
     */
    private void drawTorches() {
        // Torch positions (logical): centred on the divider strip
        drawTorch(DIV_X - 1, 72);   // near top of divider wall
        drawTorch(DIV_X - 1, 388);  // near bottom of divider wall
    }

    /**
     * Draws a single wall torch centred at the given logical coordinates.
     * The torch consists of a glow aura, three flame segments, and a wooden handle.
     *
     * @param lx logical X centre of the torch
     * @param ly logical Y of the torch base (top of the wooden handle)
     */
    private void drawTorch(double lx, double ly) {
        // Wide glow aura behind the flame (semi-transparent)
        place(srect(lx - 12, ly - 26, 26, 28, C_FLAME_GLOW, C_FLAME_GLOW));

        // Flame — three stacked rectangles, narrowing toward the tip
        place(srect(lx - 3, ly - 8,  8, 8, C_FLAME_BASE, C_FLAME_BASE));  // base (wide, deep orange)
        place(srect(lx - 2, ly - 15, 6, 8, C_FLAME_MID,  C_FLAME_MID));   // middle (orange)
        place(srect(lx - 1, ly - 22, 4, 8, C_FLAME_TIP,  C_FLAME_TIP));   // tip (yellow)
        // Tiny bright highlight at very top
        place(srect(lx,     ly - 26, 2, 4, new Color(255, 255, 200), new Color(255, 255, 200)));

        // Wooden handle
        place(srect(lx - 1, ly, 4, 10, C_TORCH_WOOD, C_TORCH_WOOD));

        // Wall bracket (horizontal peg)
        place(srect(lx - 5, ly + 8, 12, 3, new Color(65, 50, 35), new Color(65, 50, 35)));
    }

    /** Draws semi-transparent panel overlays on top of the bricks. */
    private void drawPanelOverlays() {
        // Left panel
        place(srect(0, 0, LEFT_W, 500, C_PANEL_L, C_PANEL_L));
        // Right panel
        place(srect(RIGHT_X, 0, RIGHT_W, 500, C_PANEL_R, C_PANEL_R));
    }

    /** Draws the glowing accent divider strip between the two panels. */
    private void drawDivider() {
        place(srect(DIV_X, 0, 3, 500, C_ACCENT, C_ACCENT));
    }

    // =========================================================
    // LEFT PANEL
    // =========================================================

    /** Draws the header, question text, and answer buttons in the left panel. */
    private void drawLeftPanel() {
        drawLeftHeader();
        drawQuestion();
        drawAnswerButtons();
    }

    /** Draws the "CHARACTER CREATION" title and question progress counter. */
    private void drawLeftHeader() {
        GLabel title = pixelLabel("** CHARACTER CREATION **", 13, C_ACCENT);
        title.setLocation(centeredInPanel(title, 0, LEFT_W), scaleY(22));
        place(title);

        GLabel prog = pixelLabel("Q " + (currentQuestion + 1) + " / " + QUESTIONS.length, 10, C_DIM);
        prog.setLocation(scaleX(BTN_X), scaleY(42));
        place(prog);

        // Horizontal rule below header
        place(srect(BTN_X, 54, LEFT_W - BTN_X * 2, 2, C_SEP, C_SEP));
    }

    /** Draws the current question text, word-wrapped to fit the left panel. */
    private void drawQuestion() {
        List<String> lines = wrapText(QUESTIONS[currentQuestion], 46);
        double lineH = scaleY(22) - scaleY(0);
        double y     = scaleY(70);
        for (String line : lines) {
            GLabel lbl = pixelLabel(line, 12, C_TEXT);
            lbl.setLocation(centeredInPanel(lbl, 0, LEFT_W), y);
            place(lbl);
            y += lineH;
        }
    }

    /**
     * Draws the four chunky pixel-art answer buttons.
     * Each button has an outer border rect and a fill rect stored in
     * {@link #buttonBoxes} for hover detection and click handling.
     */
    private void drawAnswerButtons() {
        String[] prefixes = {"[A]  ", "[B]  ", "[C]  ", "[D]  "};

        for (int i = 0; i < OPTIONS[currentQuestion].length; i++) {
            double logTop = BTN_Y0 + i * (BTN_H + BTN_GAP);
            double bx = scaleX(BTN_X);
            double by = scaleY(logTop);
            double bw = scaleX(BTN_X + BTN_W) - bx;
            double bh = scaleY(logTop + BTN_H) - by;

            // Outer border (1-px wider on each side to simulate thick pixel border)
            place(rect(bx - 2, by - 2, bw + 4, bh + 4, C_BTN_BORDER, C_BTN_BORDER));

            // Button fill — stored for hover/click detection
            GRect box = rect(bx, by, bw, bh, C_BTN_BG, C_BTN_BG);
            place(box);
            buttonBoxes.add(box);

            // Button text — vertically centred
            GLabel lbl = pixelLabel(prefixes[i] + OPTIONS[currentQuestion][i], 12, C_TEXT);
            double textY = by + (bh + lbl.getAscent()) / 2.0 - lbl.getDescent() / 2.0;
            lbl.setLocation(bx + scaleX(12) - scaleX(0), textY);
            place(lbl);
        }
    }

    // =========================================================
    // RIGHT PANEL
    // =========================================================

    /** Draws the right panel header and four card-type sections. */
    private void drawRightPanel() {
        // Panel header
        GLabel hdr = pixelLabel("[ CARDS ]", 11, C_ACCENT);
        hdr.setLocation(centeredInPanel(hdr, RIGHT_X, RIGHT_W), scaleY(22));
        place(hdr);

        // Rule under header
        place(srect(RIGHT_X + 6, 32, RIGHT_W - 12, 2, C_SEP, C_SEP));

        // One section per card type
        for (int i = 0; i < OPTION_TYPES.length; i++) {
            drawCardSection(i);
        }

        // Footer hint
        GLabel hint = pixelLabel("Top 2 earn your cards!", 9, C_DIM);
        hint.setLocation(centeredInPanel(hint, RIGHT_X, RIGHT_W), scaleY(460));
        place(hint);
    }

    /**
     * Draws one card-type section: a mini pixel card on the left and
     * the type name, fill bar, and score on the right.
     *
     * @param i index into OPTION_TYPES / TYPE_COLORS / TYPE_LABELS
     */
    private void drawCardSection(int i) {
        CardType type  = OPTION_TYPES[i];
        int      score = scores.get(type);
        Color    col   = TYPE_COLORS[i];
        double   secY  = SEC_Y0 + i * SEC_H;  // logical top of this section

        // --- Mini card art (left side of section) ---
        drawMiniCard(i, secY);

        // --- Type name label (right of card) ---
        GLabel nameLbl = pixelLabel(TYPE_LABELS[i], 9, col);
        nameLbl.setLocation(scaleX(RIGHT_X + BAR_OFF_X), scaleY(secY + 18));
        place(nameLbl);

        // --- Fill bar ---
        double barX    = scaleX(RIGHT_X + BAR_OFF_X);
        double barY    = scaleY(secY + 32);
        double barMaxW = scaleX(RIGHT_X + BAR_OFF_X + BAR_W) - barX;
        double barH    = scaleY(secY + 32 + BAR_H) - barY;

        // Empty bar background + border
        place(rect(barX, barY, barMaxW, barH, C_BAR_BG, C_SEP));

        // Filled portion proportional to score / 10
        if (score > 0) {
            double fillW = (score / 10.0) * barMaxW;
            place(rect(barX, barY, fillW, barH, col, col));
        }

        // --- Score label ---
        GLabel scoreLbl = pixelLabel(score + " / 10", 9, C_DIM);
        scoreLbl.setLocation(scaleX(RIGHT_X + BAR_OFF_X), scaleY(secY + 50));
        place(scoreLbl);

        // --- Section separator (skip after last) ---
        if (i < OPTION_TYPES.length - 1) {
            place(srect(RIGHT_X + 4, secY + 72, RIGHT_W - 8, 1, C_SEP, C_SEP));
        }
    }

    /**
     * Draws a small pixel-art card for the given card type.
     * The card is positioned within a section at the given logical Y.
     *
     * Design:
     *   - Dark card body with a coloured border
     *   - Top and bottom colour accent stripes
     *   - Centred icon symbol in the card type colour
     *
     * @param typeIndex  index into TYPE_COLORS / TYPE_ICONS
     * @param sectionTopY logical Y of the top of this section
     */
    private void drawMiniCard(int typeIndex, double sectionTopY) {
        Color col = TYPE_COLORS[typeIndex];
        // Darkened shade for interior accents
        Color dark = new Color(
            Math.max(0, col.getRed()   / 4),
            Math.max(0, col.getGreen() / 4),
            Math.max(0, col.getBlue()  / 4)
        );

        double cx = RIGHT_X + CARD_OFF_X; // logical left of card
        double cy = sectionTopY + CARD_OFF_Y;   // logical top of card
        double cw = CARD_W;
        double ch = CARD_H;

        // Drop shadow (offset by 2 logical pixels, semi-transparent)
        place(srect(cx + 2, cy + 2, cw, ch, new Color(0, 0, 0, 90), new Color(0, 0, 0, 0)));

        // Card body: dark fill with coloured border
        place(srect(cx,     cy,     cw,     ch,     new Color(25, 20, 40), col));

        // Top colour stripe
        place(srect(cx + 2, cy + 2, cw - 4, 7, col, col));

        // Bottom colour stripe
        place(srect(cx + 2, cy + ch - 9, cw - 4, 7, dark, dark));

        // Icon label centred in the card body
        GLabel icon = pixelLabel(TYPE_ICONS[typeIndex], 10, col);
        double iconX = scaleX(cx) + (scaleX(cx + cw) - scaleX(cx) - icon.getWidth()) / 2.0;
        double iconY = scaleY(cy + 2 + 7) // below top stripe
            + ((scaleY(cy + ch - 9) - scaleY(cy + 2 + 7)) + icon.getAscent()) / 2.0
            - icon.getDescent() / 2.0;
        icon.setLocation(iconX, iconY);
        place(icon);
    }

    // =========================================================
    // MOUSE EVENTS
    // =========================================================

    /**
     * Highlights the button under the cursor by updating its fill colour in-place.
     * This avoids a full re-render on every mouse move for better performance.
     */
    @Override
    public void mouseMoved(MouseEvent e) {
        int newHover = -1;
        for (int i = 0; i < buttonBoxes.size(); i++) {
            if (buttonBoxes.get(i).contains(e.getX(), e.getY())) {
                newHover = i;
                break;
            }
        }
        if (newHover != hoveredButton) {
            hoveredButton = newHover;
            for (int i = 0; i < buttonBoxes.size(); i++) {
                buttonBoxes.get(i).setFillColor(i == hoveredButton ? C_BTN_HOVER : C_BTN_BG);
            }
        }
    }

    /**
     * Detects which answer button was clicked using bounding-box checks,
     * records the score, and advances to the next question.
     */
    @Override
    public void mouseClicked(MouseEvent e) {
        double x = e.getX(), y = e.getY();
        for (int i = 0; i < buttonBoxes.size(); i++) {
            if (buttonBoxes.get(i).contains(x, y)) {
                handleAnswer(i);
                return;
            }
        }
    }

    /**
     * Records the score for the chosen answer and moves to the next question,
     * or finalises the quiz and transitions to the game.
     *
     * @param optionIndex 0-3 corresponding to OPTION_TYPES columns
     */
    private void handleAnswer(int optionIndex) {
        CardType chosen = OPTION_TYPES[optionIndex];
        scores.put(chosen, scores.get(chosen) + 1);
        currentQuestion++;

        if (currentQuestion < QUESTIONS.length) {
            renderScreen();
        } else {
            awardCards();
            mainScreen.switchToSkyTransitionScreen();
        }
    }

    // =========================================================
    // CARD AWARD LOGIC
    // =========================================================

    /**
     * Determines the top 2 scoring card types and adds their reward
     * cards to the player's hand.
     */
    private void awardCards() {
        CardType first = null, second = null;

        for (CardType type : CardType.values()) {
            int score = scores.get(type);
            if (first == null || score > scores.get(first)) {
                second = first;
                first  = type;
            } else if (second == null || score > scores.get(second)) {
                second = type;
            }
        }

        addRewardCard(first);
        addRewardCard(second);
        System.out.println("Cards awarded: " + first + ", " + second);
    }

    /**
     * Creates a Card for the given type and adds it to the player's hand.
     * @param type the CardType to award (null-safe)
     */
    private void addRewardCard(CardType type) {
        if (type == null) return;
        int index = indexOfType(type);
        Card card = new Card(
            type.name().toLowerCase(),
            CARD_REWARDS[index][0],
            CARD_REWARDS[index][1],
            type
        );
        mainScreen.getPlayer().getHand().addCard(card);
    }

    /**
     * Returns the column index of a CardType in OPTION_TYPES.
     * @param type the CardType to look up
     * @return index 0-3, or 0 if not found
     */
    private int indexOfType(CardType type) {
        for (int i = 0; i < OPTION_TYPES.length; i++) {
            if (OPTION_TYPES[i] == type) return i;
        }
        return 0;
    }

    // =========================================================
    // HELPERS — factory / layout
    // =========================================================

    /**
     * Adds a GObject to both the contents tracking list and the canvas.
     * @param obj the object to add
     */
    private void place(GObject obj) {
        contents.add(obj);
        mainScreen.add(obj);
    }

    /**
     * Creates a GLabel in the pixel (Monospaced-BOLD) font.
     * @param text  label string
     * @param size  logical font size (scaled automatically)
     * @param color text colour
     * @return configured GLabel at (0, 0) — caller sets location
     */
    private GLabel pixelLabel(String text, int size, Color color) {
        GLabel lbl = new GLabel(text, 0, 0);
        lbl.setFont("Monospaced-BOLD-" + scaleFontSize(size));
        lbl.setColor(color);
        return lbl;
    }

    /**
     * Creates a filled GRect using raw pixel coordinates.
     * @param x      pixel X
     * @param y      pixel Y
     * @param w      pixel width
     * @param h      pixel height
     * @param fill   fill colour
     * @param border border colour
     * @return configured GRect (not yet added to canvas)
     */
    private GRect rect(double x, double y, double w, double h, Color fill, Color border) {
        GRect r = new GRect(x, y, w, h);
        r.setFilled(true);
        r.setFillColor(fill);
        r.setColor(border);
        return r;
    }

    /**
     * Creates a filled GRect using logical (700 x 500) coordinates,
     * automatically scaled to pixel coordinates via scaleX / scaleY.
     *
     * @param lx     logical X of left edge
     * @param ly     logical Y of top edge
     * @param lw     logical width
     * @param lh     logical height
     * @param fill   fill colour
     * @param border border colour
     * @return configured GRect (not yet added to canvas)
     */
    private GRect srect(double lx, double ly, double lw, double lh, Color fill, Color border) {
        return rect(
            scaleX(lx),
            scaleY(ly),
            scaleX(lx + lw) - scaleX(lx),
            scaleY(ly + lh) - scaleY(ly),
            fill,
            border
        );
    }

    /**
     * Returns the pixel X that horizontally centres a label within a logical panel.
     *
     * @param label      the label to centre (its width is measured)
     * @param panelLeft  logical left edge of the panel
     * @param panelWidth logical width of the panel
     * @return pixel X position for the label's left edge
     */
    private double centeredInPanel(GLabel label, double panelLeft, double panelWidth) {
        double px = scaleX(panelLeft);
        double pw = scaleX(panelLeft + panelWidth) - px;
        return px + (pw - label.getWidth()) / 2.0;
    }

    /**
     * Word-wraps a string into lines of at most {@code maxChars} characters,
     * breaking only at spaces.
     *
     * @param text     the string to wrap
     * @param maxChars maximum characters per line
     * @return list of wrapped lines (never empty)
     */
    private List<String> wrapText(String text, int maxChars) {
        List<String> lines   = new ArrayList<>();
        String[]     words   = text.split(" ");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            if (current.length() + word.length() + 1 > maxChars && current.length() > 0) {
                lines.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(word).append(" ");
        }
        if (current.length() > 0) {
            lines.add(current.toString().trim());
        }
        return lines;
    }
}
