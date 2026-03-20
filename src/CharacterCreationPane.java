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
 * Displays a 10-question personality quiz during character creation.
 * Each answer awards points to one of the four card types.
 * After all questions, the top 2 card types are added to the player's hand.
 */
public class CharacterCreationPane extends GraphicsPane {

    // --- Question data ---
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

    // Each row: [WAYFINDER answer, SILVER_TONGUE answer, HEARTSEEKER answer, WILDCARD answer]
    private static final String[][] OPTIONS = {
        {"Use the stars to navigate",      "Ask a passing traveler",         "Trust your gut feeling",         "Flip a coin and commit"},
        {"Draw them a map from memory",    "Chat and walk them there",       "Sense their urgency and hurry",  "Point randomly and walk away"},
        {"Look for clues around it",       "Ask locals if they know more",   "Feel if it is worth the risk",   "Smash it open"},
        {"Study the terrain for clues",    "Rally the group with a speech",  "Stay calm and comfort others",   "Climb a tree and shout"},
        {"Build a raft from nearby wood",  "Negotiate with a ferryman",      "Encourage the group across",     "Swim across on a dare"},
        {"Calculate the fair price calmly","Bargain your way to a discount", "Appeal to their sense of honor", "Distract and sneak extra items"},
        {"Map its layout carefully",       "Tell others about it later",     "Check if anyone else needs it",  "Dive in headfirst"},
        {"Assess the safest route around", "Call for others to help",        "Stop immediately to help",       "Carry them on your back at a run"},
        {"Analyze tracks and terrain",     "Consult every person nearby",    "Go with the path that feels right","Take both paths at once somehow"},
        {"Re-examine your route data",     "Ask the locals what has changed","Trust your instincts and stay alert","Declare it an adventure and proceed"}
    };

    // Card type matched to each column index above
    private static final CardType[] OPTION_TYPES = {
        CardType.WAYFINDER,
        CardType.SILVER_TONGUE,
        CardType.HEARTSEEKER,
        CardType.WILDCARD
    };

    // Card awarded per CardType: {name, description} indexed same as OPTION_TYPES
    private static final String[][] CARD_REWARDS = {
        {"Wayfinder's Compass",    "A keen sense of direction that reveals hidden paths."},
        {"Silver Tongue",          "The art of persuasion - words that open locked doors."},
        {"Heartseeker's Instinct", "Empathy sharp enough to pierce any deception."},
        {"The Wildcard",           "Unpredictable and fearless - chaos made useful."}
    };

    // --- State ---
    private int currentQuestion;
    private Map<CardType, Integer> scores;
    private List<GLabel> optionLabels;

    public CharacterCreationPane(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
    }

    @Override
    public void showContent() {
        currentQuestion = 0;
        scores = new EnumMap<>(CardType.class);
        for (CardType type : CardType.values()) {
            scores.put(type, 0);
        }
        optionLabels = new ArrayList<>();
        renderQuestion();
    }

    @Override
    public void hideContent() {
        for (GObject item : contents) {
            mainScreen.remove(item);
        }
        contents.clear();
        optionLabels.clear();
    }

    /** Clears the screen and draws the current question with its 4 answer options. */
    private void renderQuestion() {
        for (GObject item : contents) {
            mainScreen.remove(item);
        }
        contents.clear();
        optionLabels.clear();

        // Progress label
        GLabel progress = new GLabel("Question " + (currentQuestion + 1) + " of " + QUESTIONS.length, 0, 0);
        progress.setFont(scaledFont(14));
        progress.setColor(Color.GRAY);
        progress.setLocation(centeredX(progress), scaleY(40));
        contents.add(progress);
        mainScreen.add(progress);

        // Question text
        GLabel question = new GLabel(QUESTIONS[currentQuestion], 0, 0);
        question.setFont(scaledFont(16));
        question.setColor(Color.BLACK);
        question.setLocation(centeredX(question), scaleY(100));
        contents.add(question);
        mainScreen.add(question);

        // Answer options A-D
        String[] prefixes = {"A)  ", "B)  ", "C)  ", "D)  "};
        double[] yPositions = {175, 250, 325, 400};

        for (int i = 0; i < OPTIONS[currentQuestion].length; i++) {
            GLabel option = new GLabel(prefixes[i] + OPTIONS[currentQuestion][i], 0, 0);
            option.setFont(scaledFont(15));
            option.setColor(new Color(40, 40, 120));

            double optX = centeredX(option);
            double optY = scaleY(yPositions[i]);

            // Background box for each option
            double boxPad = 10;
            GRect box = new GRect(
                optX - boxPad,
                optY - option.getAscent() - boxPad / 2,
                option.getWidth() + boxPad * 2,
                option.getAscent() + option.getDescent() + boxPad
            );
            box.setFilled(true);
            box.setFillColor(new Color(230, 230, 250));
            box.setColor(new Color(180, 180, 220));

            option.setLocation(optX, optY);

            contents.add(box);
            contents.add(option);
            mainScreen.add(box);
            mainScreen.add(option);
            optionLabels.add(option);
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        GObject clicked = mainScreen.getElementAtLocation(e.getX(), e.getY());
        for (int i = 0; i < optionLabels.size(); i++) {
            if (clicked == optionLabels.get(i)) {
                CardType chosen = OPTION_TYPES[i];
                scores.put(chosen, scores.get(chosen) + 1);
                currentQuestion++;

                if (currentQuestion < QUESTIONS.length) {
                    renderQuestion();
                } else {
                    awardCards();
                    mainScreen.switchToScene1Screen();
                }
                return;
            }
        }
    }

    /**
     * Finds the top 2 scoring CardTypes and adds their reward cards to the player's hand.
     */
    private void awardCards() {
        CardType first = null;
        CardType second = null;

        for (CardType type : CardType.values()) {
            int score = scores.get(type);
            if (first == null || score > scores.get(first)) {
                second = first;
                first = type;
            } else if (second == null || score > scores.get(second)) {
                second = type;
            }
        }

        addRewardCard(first);
        addRewardCard(second);

        System.out.println("Cards awarded: " + first + ", " + second);
    }

    /** Creates a Card for the given type and adds it to the player's hand. */
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

    /** Returns the column index of a CardType in OPTION_TYPES. */
    private int indexOfType(CardType type) {
        for (int i = 0; i < OPTION_TYPES.length; i++) {
            if (OPTION_TYPES[i] == type) return i;
        }
        return 0;
    }
}
