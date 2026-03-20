import java.awt.Color;
import java.awt.event.MouseEvent;
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

    // --- UI element lists ---
    private List<GLabel> cardButtons;      // One clickable label per card in hand
    private List<Integer> cardIndices;     // Tracks which hand index each button maps to
    private GLabel outcomeLabel;           // Shows the outcome narrative text
    private GLabel healthLabel;            // Shows the health change (e.g. "-10 HP")
    private GLabel continueButton;         // Clicked to dismiss the modal
    private GRect dimOverlay;              // Semi-transparent background behind the modal box
    private GRect modalBox;               // The white modal panel

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
        this.currentObstacle = obstacle;
        this.onComplete = onComplete;
        this.outcomeShowing = false;
        cardButtons = new ArrayList<>();
        cardIndices = new ArrayList<>();
        renderModal();
    }

    /** Renders the full modal overlay onto the screen. */
    private void renderModal() {
        // --- Dim overlay covering the whole screen ---
        dimOverlay = new GRect(0, 0, mainScreen.getWidth(), mainScreen.getHeight());
        dimOverlay.setFilled(true);
        dimOverlay.setFillColor(new Color(0, 0, 0, 140)); // semi-transparent black
        dimOverlay.setColor(new Color(0, 0, 0, 0));
        contents.add(dimOverlay);
        mainScreen.add(dimOverlay);

        // --- Modal box background ---
        double boxX = scaleX(60);
        double boxY = scaleY(40);
        double boxW = scaleX(580) - scaleX(60);
        double boxH = scaleY(440) - scaleY(40);
        modalBox = new GRect(boxX, boxY, boxW, boxH);
        modalBox.setFilled(true);
        modalBox.setFillColor(new Color(245, 240, 255));
        modalBox.setColor(new Color(100, 80, 140));
        contents.add(modalBox);
        mainScreen.add(modalBox);

        // --- Obstacle title ---
        GLabel title = new GLabel(currentObstacle.getTitle(), 0, 0);
        title.setFont(scaledFont(20));
        title.setColor(new Color(60, 20, 100));
        title.setLocation(centeredX(title), scaleY(80));
        contents.add(title);
        mainScreen.add(title);

        // --- Obstacle description ---
        GLabel desc = new GLabel(currentObstacle.getDescription(), 0, 0);
        desc.setFont(scaledFont(13));
        desc.setColor(Color.DARK_GRAY);
        desc.setLocation(centeredX(desc), scaleY(120));
        contents.add(desc);
        mainScreen.add(desc);

        // --- Prompt or no-cards message ---
        if (mainScreen.getPlayer().getHand().isEmpty()) {
            renderNoCardsState();
        } else {
            renderCardSelectionState();
        }
    }

    /**
     * Renders the card selection view — shows the prompt and all cards in hand
     * as clickable buttons.
     */
    private void renderCardSelectionState() {
        // Prompt label
        GLabel prompt = new GLabel(currentObstacle.getPrompt(), 0, 0);
        prompt.setFont(scaledFont(14));
        prompt.setColor(new Color(80, 50, 120));
        prompt.setLocation(centeredX(prompt), scaleY(170));
        contents.add(prompt);
        mainScreen.add(prompt);

        // --- Card buttons ---
        List<Card> cards = mainScreen.getPlayer().getHand().getCards();
        double[] xPositions = computeCardXPositions(cards.size());
        double cardY = scaleY(270);

        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);

            // Card background box
            double cardW = scaleX(150) - scaleX(0);
            double cardH = scaleY(80) - scaleY(0);
            GRect cardBox = new GRect(xPositions[i], cardY - cardH + 8, cardW, cardH);
            cardBox.setFilled(true);
            cardBox.setFillColor(new Color(210, 195, 255));
            cardBox.setColor(new Color(130, 100, 200));
            contents.add(cardBox);
            mainScreen.add(cardBox);

            // Card name label
            GLabel cardBtn = new GLabel(card.getName(), 0, 0);
            cardBtn.setFont(scaledFont(13));
            cardBtn.setColor(new Color(40, 20, 80));
            cardBtn.setLocation(xPositions[i] + (cardW - cardBtn.getWidth()) / 2, cardY);
            contents.add(cardBtn);
            mainScreen.add(cardBtn);
            cardButtons.add(cardBtn);
            cardIndices.add(i);

            // Card type label below name
            GLabel typeLabel = new GLabel("[" + card.getType().name() + "]", 0, 0);
            typeLabel.setFont(scaledFont(11));
            typeLabel.setColor(new Color(100, 80, 150));
            typeLabel.setLocation(xPositions[i] + (cardW - typeLabel.getWidth()) / 2, cardY + scaleY(20) - scaleY(0));
            contents.add(typeLabel);
            mainScreen.add(typeLabel);
        }
    }

    /**
     * Renders the no-cards state — warns the player and shows a continue button
     * that will trigger game over.
     */
    private void renderNoCardsState() {
        GLabel warning = new GLabel("You have no cards left!", 0, 0);
        warning.setFont(scaledFont(16));
        warning.setColor(new Color(180, 30, 30));
        warning.setLocation(centeredX(warning), scaleY(200));
        contents.add(warning);
        mainScreen.add(warning);

        GLabel sub = new GLabel("You cannot face this obstacle.", 0, 0);
        sub.setFont(scaledFont(13));
        sub.setColor(Color.DARK_GRAY);
        sub.setLocation(centeredX(sub), scaleY(240));
        contents.add(sub);
        mainScreen.add(sub);

        // Continue button leads to game over
        continueButton = new GLabel("[ Game Over ]", 0, 0);
        continueButton.setFont(scaledFont(15));
        continueButton.setColor(new Color(180, 30, 30));
        continueButton.setLocation(centeredX(continueButton), scaleY(380));
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
        // Remove selected card from hand and resolve outcome
        Card played = mainScreen.getPlayer().getHand().removeCard(cardIndex);
        Outcome outcome = currentObstacle.resolveCard(played);

        // Apply health change
        if (outcome.getHealthDifference() != 0) {
            mainScreen.getPlayer().dealDamage(-outcome.getHealthDifference()); // negative = heal
        }

        // Hide card buttons
        for (GLabel btn : cardButtons) {
            mainScreen.remove(btn);
            contents.remove(btn);
        }
        cardButtons.clear();

        // --- Show outcome text ---
        outcomeLabel = new GLabel(outcome.getText(), 0, 0);
        outcomeLabel.setFont(scaledFont(14));
        outcomeLabel.setColor(outcomeColor(outcome.getType()));
        outcomeLabel.setLocation(centeredX(outcomeLabel), scaleY(220));
        contents.add(outcomeLabel);
        mainScreen.add(outcomeLabel);

        // Health change label
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
        }

        // Continue button
        continueButton = new GLabel("[ Continue ]", 0, 0);
        continueButton.setFont(scaledFont(15));
        continueButton.setColor(new Color(60, 20, 100));
        continueButton.setLocation(centeredX(continueButton), scaleY(380));
        contents.add(continueButton);
        mainScreen.add(continueButton);

        outcomeShowing = true;

        System.out.println("Card played: " + played.getName()
            + " | Outcome: " + outcome.getType()
            + " | HP change: " + outcome.getHealthDifference());
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

    /**
     * Computes evenly-spaced X positions for card buttons based on count.
     * @param count number of cards to display
     * @return array of x positions
     */
    private double[] computeCardXPositions(int count) {
        double cardW = scaleX(150) - scaleX(0);
        double gap = scaleX(20) - scaleX(0);
        double totalW = count * cardW + (count - 1) * gap;
        double startX = (mainScreen.getWidth() - totalW) / 2.0;
        double[] positions = new double[count];
        for (int i = 0; i < count; i++) {
            positions[i] = startX + i * (cardW + gap);
        }
        return positions;
    }

    @Override
    public void hideContent() {
        for (GObject item : contents) {
            mainScreen.remove(item);
        }
        contents.clear();
        cardButtons.clear();
        cardIndices.clear();
        outcomeShowing = false;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        GObject clicked = mainScreen.getElementAtLocation(e.getX(), e.getY());

        // --- Continue button clicked ---
        if (clicked == continueButton) {
            hideContent();
            if (mainScreen.getPlayer().getHand().isEmpty() && !outcomeShowing) {
                // No cards case — game over
                mainScreen.switchToGameOverScreen();
            } else {
                onComplete.run();
            }
            return;
        }

        // --- Card button clicked ---
        if (!outcomeShowing) {
            for (int i = 0; i < cardButtons.size(); i++) {
                if (clicked == cardButtons.get(i)) {
                    resolveAndShow(cardIndices.get(i));
                    return;
                }
            }
        }
    }

}
