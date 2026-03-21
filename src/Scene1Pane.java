import java.awt.Color;

import acm.graphics.GLabel;
import acm.graphics.GObject;

/**
 * Scene 1 — the first obstacle scene.
 * When shown, it displays the scene background and immediately triggers
 * the CardPlayModal overlay so the player can resolve the obstacle.
 * After the obstacle is resolved, the game advances to Scene 2.
 */
public class Scene1Pane extends GraphicsPane {

    /** Logical Y positions (700×500 design space) — tuned so text scales cleanly. */
    private static final double TITLE_LINE1_Y = 48;
    private static final double TITLE_LINE2_Y = 78;
    private static final double SUB_LINE1_Y = 118;
    private static final double SUB_LINE2_Y = 146;

    /**
     * Creates Scene1Pane.
     * @param mainScreen the main application reference
     */
    public Scene1Pane(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
    }

    @Override
    public void showContent() {
        addSceneBackground();
        triggerObstacle();
    }

    @Override
    public void hideContent() {
        for (GObject item : contents) {
            mainScreen.remove(item);
        }
        contents.clear();
    }

    /** Draws the scene header using split lines so nothing clips on small windows. */
    private void addSceneBackground() {
        GLabel line1 = new GLabel("Scene 1", 0, 0);
        line1.setColor(new Color(25, 25, 35));
        line1.setFont(scaledFont(26));
        placeCentered(line1, TITLE_LINE1_Y);

        GLabel line2 = new GLabel("The Overgrown Path", 0, 0);
        line2.setColor(new Color(55, 45, 75));
        line2.setFont(scaledFont(18));
        placeCentered(line2, TITLE_LINE2_Y);

        GLabel sub1 = new GLabel("A thick wall of thorns blocks", 0, 0);
        sub1.setColor(Color.DARK_GRAY);
        sub1.setFont(scaledFont(14));
        placeCentered(sub1, SUB_LINE1_Y);

        GLabel sub2 = new GLabel("the road ahead.", 0, 0);
        sub2.setColor(Color.DARK_GRAY);
        sub2.setFont(scaledFont(14));
        placeCentered(sub2, SUB_LINE2_Y);
    }

    /** Centers horizontally in the layout canvas and places at scaled logical Y. */
    private void placeCentered(GLabel label, double logicalY) {
        contents.add(label);
        mainScreen.add(label);
        label.setLocation(centeredX(label), scaleY(logicalY));
    }

    /**
     * Builds the obstacle for this scene and shows the CardPlayModal.
     * Each CardType produces a unique narrative outcome.
     * On completion, advances to Scene 2.
     */
    private void triggerObstacle() {
        // --- Default outcome (no cards available) ---
        Outcome noCardOutcome = new Outcome(
            OutcomeType.NEGATIVE,
            "You have nothing to help you. You push through and take heavy damage.",
            -20
        );

        // --- Build obstacle ---
        ObstacleScene obstacle = new ObstacleScene(
            "The Overgrown Path",
            "A thick wall of thorns blocks the road ahead.\nYou need to find a way through.",
            "Which card will you use to clear the path?",
            noCardOutcome
        );

        // WAYFINDER: navigates around smartly
        obstacle.addOutcome(CardType.WAYFINDER, new Outcome(
            OutcomeType.POSITIVE,
            "Using your sharp sense of direction, you find a hidden trail around the thorns.",
            0
        ));

        // SILVER_TONGUE: talks to a local for help
        obstacle.addOutcome(CardType.SILVER_TONGUE, new Outcome(
            OutcomeType.POSITIVE,
            "You convince a nearby farmer to lend you a scythe. The path is cleared.",
            0
        ));

        // HEARTSEEKER: feels the right way through
        obstacle.addOutcome(CardType.HEARTSEEKER, new Outcome(
            OutcomeType.NEUTRAL,
            "Your instincts guide you, but the thorns still scratch. You get through slowly.",
            -5
        ));

        // WILDCARD: chaotic approach
        obstacle.addOutcome(CardType.WILDCARD, new Outcome(
            OutcomeType.NEGATIVE,
            "You charge straight through the thorns. You make it, but you are badly scratched.",
            -15
        ));

        // Show the modal — on complete, go to Scene 2
        mainScreen.showObstacle(obstacle, () -> mainScreen.switchToScene2Screen());
    }

}
