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

    /** Draws the scene background text (placeholder until art is added). */
    private void addSceneBackground() {
        GLabel text = new GLabel("Scene 1 — The Overgrown Path", 0, 0);
        text.setColor(Color.BLACK);
        text.setFont(scaledFont(22));
        text.setLocation(centeredX(text), scaleY(70));
        contents.add(text);
        mainScreen.add(text);

        GLabel sub = new GLabel("A thick wall of thorns blocks the road ahead.", 0, 0);
        sub.setColor(Color.DARK_GRAY);
        sub.setFont(scaledFont(14));
        sub.setLocation(centeredX(sub), scaleY(120));
        contents.add(sub);
        mainScreen.add(sub);
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
