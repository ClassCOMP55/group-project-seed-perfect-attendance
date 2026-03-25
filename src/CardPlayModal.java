import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import acm.graphics.GLabel;
import acm.graphics.GObject;
import acm.graphics.GRect;

/**
 * Modal overlay displayed on top of the current scene when the player
 * encounters an obstacle. Shows the obstacle details and the player's
 * available cards. The player clicks a card to play it — each card type
 * produces a unique narrative outcome. The card is consumed after use.
 * If the player has no cards, the game over screen is triggered.
 */
public class CardPlayModal extends GraphicsPane {

    // --- State ---
    private ObstacleScene currentObstacle; // The obstacle currently being resolved
    private Runnable onComplete;           // Called when the player clicks Continue
    private boolean outcomeShowing;        // True after a card has been played
    private boolean keepCard;             // If true, card is not consumed after use (tutorial mode)
    /** When {@link #outcomeShowing}, preserved so resize can redraw the outcome step. */
    private Outcome outcomeSnapshot;

    // --- UI element lists ---
    private List<GLabel> cardButtons;      // One clickable label per card in hand
    private List<Integer> cardIndices;     // Tracks which hand index each button maps to
    private GLabel outcomeLabel;           // Shows the outcome narrative text
    private GLabel healthLabel;            // Shows the health change (e.g. "-10 HP")
    private GLabel continueButton;         // Clicked to dismiss the modal
    private GRect dimOverlay;              // Semi-transparent background behind the modal box
    private GRect modalBox;               // The white modal panel

    /** Card row UI (boxes + labels) so we can remove them when a card is played. */
    private final List<GObject> cardRowObjects = new ArrayList<>();
    /** Hit areas for whole card (not just the name label). */
    private final List<GRect> cardHitRects = new ArrayList<>();

    /**
     * Creates a new CardPlayModal.
     * @param mainScreen the main application reference
     */
    public CardPlayModal(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
    }

    /**
     * Shows the obstacle modal on top of the current screen.
     * If the player has no cards, the no-cards screen is shown immediately.
     *
     * @param obstacle   the ObstacleScene to display
     * @param onComplete Runnable called when the player dismisses the modal
     */
    public void showObstacle(ObstacleScene obstacle, Runnable onComplete) {
        showObstacle(obstacle, onComplete, false);
    }

    public void showObstacle(ObstacleScene obstacle, Runnable onComplete, boolean keepCard) {
        this.currentObstacle = obstacle;
        this.onComplete = onComplete;
        this.outcomeShowing = false;
        this.keepCard = keepCard;
        cardButtons = new ArrayList<>();
        cardIndices = new ArrayList<>();
        cardRowObjects.clear();
        cardHitRects.clear();
        renderModal();
        restackOnTop();
    }

    /**
     * Redraws the obstacle modal at the new size. If the player already chose a card,
     * restores the outcome view without changing hand or HP.
     */
    public void refreshLayout() {
        if (currentObstacle == null || contents.isEmpty()) {
            return;
        }
        Outcome snap = outcomeSnapshot;
        ObstacleScene obs = currentObstacle;
        Runnable oc = onComplete;
        boolean kc = keepCard;
        hideContent();
        currentObstacle = obs;
        onComplete = oc;
        keepCard = kc;
        if (snap != null) {
            outcomeSnapshot = snap;
            renderModalAfterOutcome(snap);
        } else {
            showObstacle(obs, oc, kc);
        }
        restackOnTop();
    }

    /**
     * Re-adds all modal objects so they sit above the current scene. Required
     * after {@link MainApplication} refreshes the scene (e.g. on resize).
     */
    public void restackOnTop() {
        ArrayList<GObject> snapshot = new ArrayList<>(contents);
        for (GObject g : snapshot) {
            mainScreen.remove(g);
            mainScreen.add(g);
        }
    }

    /** Renders the full modal overlay onto the screen. */
    private void renderModal() {
        // --- Dim overlay covering the whole window ---
        dimOverlay = new GRect(0, 0, mainScreen.getWidth(), mainScreen.getHeight());
        dimOverlay.setFilled(true);
        dimOverlay.setFillColor(new Color(0, 0, 0, 140)); // semi-transparent black
        dimOverlay.setColor(new Color(0, 0, 0, 0));
        contents.add(dimOverlay);
        mainScreen.add(dimOverlay);

        // --- Modal panel: fills the *entire* graphics window (no black band inside the window) ---
        double pad = 14;
        double boxX = pad;
        double boxY = pad;
        double boxW = mainScreen.getWidth() - 2 * pad;
        double boxH = mainScreen.getHeight() - 2 * pad;
        modalBox = new GRect(boxX, boxY, boxW, boxH);
        modalBox.setFilled(true);
        modalBox.setFillColor(new Color(245, 240, 255));
        modalBox.setColor(new Color(100, 80, 140));
        contents.add(modalBox);
        mainScreen.add(modalBox);

        // --- Obstacle title (inside panel, with side padding for long titles) ---
        GLabel title = new GLabel(currentObstacle.getTitle(), 0, 0);
        title.setFont(scaledFont(20));
        title.setColor(new Color(60, 20, 100));
        double titleY = boxY + (scaleY(28) - scaleY(0));
        title.setLocation(boxX + (boxW - title.getWidth()) / 2.0, titleY);
        contents.add(title);
        mainScreen.add(title);

        // --- Description: multiple lines so text never hugs the right edge ---
        double descY = titleY + (scaleY(36) - scaleY(0));
        addWrappedDescriptionLines(boxX, boxW, descY, currentObstacle.getDescription());

        // --- Prompt or no-cards message ---
        if (mainScreen.getPlayer().getHand().isEmpty()) {
            renderNoCardsState(boxY, boxW);
        } else {
            renderCardSelectionState(boxX, boxY, boxW, boxH);
        }
    }

    /** Rebuilds dim + panel + story + outcome text (after a card was already played). */
    private void renderModalAfterOutcome(Outcome outcome) {
        double pad = 14;
        double boxX = pad;
        double boxY = pad;
        double boxW = mainScreen.getWidth() - 2 * pad;
        double boxH = mainScreen.getHeight() - 2 * pad;

        dimOverlay = new GRect(0, 0, mainScreen.getWidth(), mainScreen.getHeight());
        dimOverlay.setFilled(true);
        dimOverlay.setFillColor(new Color(0, 0, 0, 140));
        dimOverlay.setColor(new Color(0, 0, 0, 0));
        contents.add(dimOverlay);
        mainScreen.add(dimOverlay);

        modalBox = new GRect(boxX, boxY, boxW, boxH);
        modalBox.setFilled(true);
        modalBox.setFillColor(new Color(245, 240, 255));
        modalBox.setColor(new Color(100, 80, 140));
        contents.add(modalBox);
        mainScreen.add(modalBox);

        GLabel title = new GLabel(currentObstacle.getTitle(), 0, 0);
        title.setFont(scaledFont(20));
        title.setColor(new Color(60, 20, 100));
        double titleY = boxY + (scaleY(28) - scaleY(0));
        title.setLocation(boxX + (boxW - title.getWidth()) / 2.0, titleY);
        contents.add(title);
        mainScreen.add(title);

        double descY = titleY + (scaleY(36) - scaleY(0));
        addWrappedDescriptionLines(boxX, boxW, descY, currentObstacle.getDescription());

        mountOutcomeLabels(outcome);
        outcomeShowing = true;
        outcomeSnapshot = outcome;
    }

    /**
     * Splits description into short lines (GLabel does not auto-wrap).
     * Respects newlines, then hard-wraps long segments.
     */
    private void addWrappedDescriptionLines(double panelLeft, double panelWidth, double startY, String raw) {
        if (raw == null) {
            return;
        }
        String text = raw.replace("\r", "");
        int maxChars = Math.max(24, (int) (42 * (panelWidth / (double) MainApplication.WINDOW_WIDTH)));

        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            String p = paragraph.trim();
            while (p.length() > maxChars) {
                int cut = p.lastIndexOf(' ', maxChars);
                if (cut < maxChars / 2) {
                    cut = maxChars;
                }
                lines.add(p.substring(0, cut).trim());
                p = p.substring(cut).trim();
            }
            if (!p.isEmpty()) {
                lines.add(p);
            }
        }
        if (lines.isEmpty()) {
            lines.add("");
        }

        double lineGap = scaleY(22) - scaleY(0);
        double y = startY;
        for (String line : lines) {
            GLabel row = new GLabel(line, 0, 0);
            row.setFont(scaledFont(13));
            row.setColor(Color.DARK_GRAY);
            double x = panelLeft + (panelWidth - row.getWidth()) / 2.0;
            row.setLocation(x, y);
            contents.add(row);
            mainScreen.add(row);
            y += lineGap;
        }
    }

    /**
     * Renders the card selection view — shows the prompt and all cards in hand
     * as clickable buttons.
     */
    private void renderCardSelectionState(double panelLeft, double panelTop, double panelW, double panelH) {
        // Prompt — centered in panel, placed in upper-middle area
        GLabel prompt = new GLabel(currentObstacle.getPrompt(), 0, 0);
        prompt.setFont(scaledFont(14));
        prompt.setColor(new Color(80, 50, 120));
        double promptY = panelTop + panelH * 0.38;
        prompt.setLocation(panelLeft + (panelW - prompt.getWidth()) / 2.0, promptY);
        contents.add(prompt);
        mainScreen.add(prompt);

        // --- Cards: size from panel so they stay inside the purple box ---
        List<Card> cards = mainScreen.getPlayer().getHand().getCards();
        double gap = scaleX(14) - scaleX(0);
        int n = cards.size();
        double cardW = Math.min(scaleX(200) - scaleX(0),
                (panelW - gap * (n + 1)) / Math.max(1, n));
        double cardH = scaleY(130) - scaleY(0);

        double totalRowW = n * cardW + (n - 1) * gap;
        double rowLeft = panelLeft + (panelW - totalRowW) / 2.0;
        // Sit cards in lower half with breathing room above bottom edge
        double cardTop = panelTop + panelH - cardH - (scaleY(28) - scaleY(0));
        if (cardTop < promptY + (scaleY(40) - scaleY(0))) {
            cardTop = promptY + (scaleY(40) - scaleY(0));
        }

        double[] xPositions = new double[n];
        for (int i = 0; i < n; i++) {
            xPositions[i] = rowLeft + i * (cardW + gap);
        }

        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            double left = xPositions[i];

            // Card background — top-aligned row (no more baseline/height mismatch)
            GRect cardBox = new GRect(left, cardTop, cardW, cardH);
            cardBox.setFilled(true);
            cardBox.setFillColor(new Color(210, 195, 255));
            cardBox.setColor(new Color(130, 100, 200));
            contents.add(cardBox);
            mainScreen.add(cardBox);
            cardRowObjects.add(cardBox);
            cardHitRects.add(cardBox);

            GLabel cardBtn = new GLabel(card.getName(), 0, 0);
            cardBtn.setFont(scaledFont(14));
            cardBtn.setColor(new Color(40, 20, 80));

            GLabel typeLabel = new GLabel("[" + card.getType().name() + "]", 0, 0);
            typeLabel.setFont(scaledFont(10));
            typeLabel.setColor(new Color(100, 80, 150));

            // Vertical stack centered inside the card (GLabel y = text baseline)
            double gapBetween = scaleY(6) - scaleY(0);
            double stackH = cardBtn.getHeight() + gapBetween + typeLabel.getHeight();
            double nameBaseline = cardTop + (cardH - stackH) / 2.0 + cardBtn.getAscent();
            double typeBaseline = nameBaseline - cardBtn.getAscent() + cardBtn.getHeight() + gapBetween + typeLabel.getAscent();

            double nameX = left + (cardW - cardBtn.getWidth()) / 2.0;
            double typeX = left + (cardW - typeLabel.getWidth()) / 2.0;

            // Keep type tag fully inside the card (baseline math safety)
            double typeBottom = typeBaseline - typeLabel.getAscent() + typeLabel.getHeight();
            if (typeBottom > cardTop + cardH - 4) {
                double shift = typeBottom - (cardTop + cardH - 4);
                nameBaseline -= shift;
                typeBaseline -= shift;
            }

            cardBtn.setLocation(nameX, nameBaseline);
            typeLabel.setLocation(typeX, typeBaseline);

            contents.add(cardBtn);
            mainScreen.add(cardBtn);
            contents.add(typeLabel);
            mainScreen.add(typeLabel);
            cardRowObjects.add(cardBtn);
            cardRowObjects.add(typeLabel);

            cardButtons.add(cardBtn);
            cardIndices.add(i);
        }
    }

    /**
     * Renders the no-cards state — warns the player and shows a continue button
     * that will trigger game over.
     */
    private void renderNoCardsState(double panelTop, double panelW) {
        GLabel warning = new GLabel("You have no cards left!", 0, 0);
        warning.setFont(scaledFont(16));
        warning.setColor(new Color(180, 30, 30));
        warning.setLocation(centeredX(warning), panelTop + (scaleY(120) - scaleY(0)));
        contents.add(warning);
        mainScreen.add(warning);

        GLabel sub = new GLabel("You cannot face this obstacle.", 0, 0);
        sub.setFont(scaledFont(13));
        sub.setColor(Color.DARK_GRAY);
        sub.setLocation(centeredX(sub), panelTop + (scaleY(155) - scaleY(0)));
        contents.add(sub);
        mainScreen.add(sub);

        // Continue button leads to game over
        continueButton = new GLabel("[ Game Over ]", 0, 0);
        continueButton.setFont(scaledFont(15));
        continueButton.setColor(new Color(180, 30, 30));
        continueButton.setLocation(centeredX(continueButton), panelTop + (scaleY(300) - scaleY(0)));
        contents.add(continueButton);
        mainScreen.add(continueButton);
    }

    /**
     * Applies the outcome of playing the given card:
     * removes the card from hand, updates health, and shows outcome text.
     *
     * @param cardIndex index of the card in the player's hand
     */
    private void resolveAndShow(int cardIndex) {
        // Remove selected card from hand and resolve outcome (tutorial mode keeps the card)
        Card played = keepCard
            ? mainScreen.getPlayer().getHand().getCards().get(cardIndex)
            : mainScreen.getPlayer().getHand().removeCard(cardIndex);
        Outcome outcome = currentObstacle.resolveCard(played);

        // Apply health change
        if (outcome.getHealthDifference() != 0) {
            mainScreen.getPlayer().dealDamage(-outcome.getHealthDifference()); // negative = heal
        }

        // Remove entire card row (boxes, names, type tags)
        for (GObject g : cardRowObjects) {
            mainScreen.remove(g);
            contents.remove(g);
        }
        cardRowObjects.clear();
        cardHitRects.clear();
        cardButtons.clear();
        cardIndices.clear();

        mountOutcomeLabels(outcome);
        outcomeSnapshot = outcome;
        outcomeShowing = true;

        System.out.println("Card played: " + played.getName()
            + " | Outcome: " + outcome.getType()
            + " | HP change: " + outcome.getHealthDifference());
    }

    private void mountOutcomeLabels(Outcome outcome) {
        outcomeLabel = new GLabel(outcome.getText(), 0, 0);
        outcomeLabel.setFont(scaledFont(14));
        outcomeLabel.setColor(outcomeColor(outcome.getType()));
        outcomeLabel.setLocation(centeredX(outcomeLabel), scaleY(220));
        contents.add(outcomeLabel);
        mainScreen.add(outcomeLabel);

        if (outcome.getHealthDifference() != 0) {
            String hpText = outcome.getHealthDifference() > 0
                ? "+" + outcome.getHealthDifference() + " HP"
                : outcome.getHealthDifference() + " HP";
            healthLabel = new GLabel(hpText, 0, 0);
            healthLabel.setFont(scaledFont(14));
            healthLabel.setColor(outcome.getHealthDifference() > 0
                ? new Color(30, 140, 30)
                : new Color(180, 30, 30));
            healthLabel.setLocation(centeredX(healthLabel), scaleY(260));
            contents.add(healthLabel);
            mainScreen.add(healthLabel);
        } else {
            healthLabel = null;
        }

        continueButton = new GLabel("[ Continue ]", 0, 0);
        continueButton.setFont(scaledFont(15));
        continueButton.setColor(new Color(60, 20, 100));
        continueButton.setLocation(centeredX(continueButton), scaleY(380));
        contents.add(continueButton);
        mainScreen.add(continueButton);
    }

    /**
     * Returns a display colour based on outcome type.
     * @param type the OutcomeType
     * @return the matching Color
     */
    private Color outcomeColor(OutcomeType type) {
        switch (type) {
            case POSITIVE: return new Color(30, 130, 30);
            case NEGATIVE: return new Color(180, 30, 30);
            default:       return Color.DARK_GRAY;
        }
    }

    private static boolean containsPoint(GObject g, double x, double y) {
        return g != null && g.contains(x, y);
    }

    /**
     * Handles a left-button release using coordinates (not {@code getElementAt}),
     * so hits work even when the scene was redrawn on top of the modal.
     */
    public void handlePointer(double x, double y) {
        if (continueButton != null && containsPoint(continueButton, x, y)) {
            hideContent();
            if (mainScreen.getPlayer().getHP() <= 0) {
                mainScreen.switchToGameOverScreen();
                return;
            }
            if (mainScreen.getPlayer().getHand().isEmpty() && !outcomeShowing) {
                mainScreen.switchToGameOverScreen();
            } else {
                onComplete.run();
            }
            return;
        }
        if (!outcomeShowing) {
            for (int i = 0; i < cardHitRects.size(); i++) {
                if (containsPoint(cardHitRects.get(i), x, y)) {
                    resolveAndShow(cardIndices.get(i));
                    return;
                }
            }
        }
    }

    @Override
    public void hideContent() {
        for (GObject item : contents) {
            mainScreen.remove(item);
        }
        contents.clear();
        cardButtons.clear();
        cardIndices.clear();
        cardRowObjects.clear();
        cardHitRects.clear();
        outcomeShowing = false;
        outcomeSnapshot = null;
    }

}
