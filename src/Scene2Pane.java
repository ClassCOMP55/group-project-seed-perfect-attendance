import java.awt.Color;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import acm.graphics.GLabel;
import acm.graphics.GObject;
import acm.graphics.GRect;

/**
 * Scene 2 — The Market: Loose Ends.
 *
 * Picks up directly from Scene 1. The player has returned from speaking
 * with the guard. Caelomund travels with the player to all locations.
 *
 * <b>Structure:</b>
 * <ol>
 *   <li>Part 1 — The Debrief: player recounts what they told guards (+1 archetype)</li>
 *   <li>Part 2 — Obstacle 1: The Plan: player picks WHERE to go (card spent, +2)</li>
 *   <li>Part 3 — Obstacle 2: The Execution: player picks HOW at the location (card spent, +2)</li>
 *   <li>Part 4 — Convergence: Barn or Inn outcome based on execution result</li>
 * </ol>
 *
 * Cards entering: 2. Cards spent: 2. Cards exiting: 0.
 *
 * @see DialogueNode
 * @see DialogueChoice
 * @see GameState
 */
public class Scene2Pane extends GraphicsPane {

    /** Set {@code false} to hide the top-left scene banner. */
    private static final boolean DEBUG_SCENE_BANNER = true;
    private static final Color C_DEBUG_BANNER = new Color(100, 220, 160);

    // =========================================================
    // COLOUR PALETTE (matches Scene 1 for visual continuity)
    // =========================================================

    private static final Color C_BG           = new Color(28, 22, 38);
    private static final Color C_ALLEY        = new Color(40, 34, 52);

    private static final Color C_DBOX_BG      = new Color(15, 12, 25, 220);
    private static final Color C_DBOX_BORDER  = new Color(100, 80, 140);
    private static final Color C_TEXT         = new Color(220, 220, 235);
    private static final Color C_CONTINUE    = new Color(140, 130, 170);

    private static final Color C_NARRATOR    = new Color(150, 145, 170);
    private static final Color C_CAELOMUND   = new Color(80, 200, 120);
    private static final Color C_PLAYER_CLR  = new Color(130, 170, 220);
    private static final Color C_MARET       = new Color(220, 180, 100);
    private static final Color C_DREV        = new Color(160, 150, 170);
    private static final Color C_ORET        = new Color(220, 170, 80);
    private static final Color C_INNKEEPER   = new Color(180, 140, 120);
    private static final Color C_VENDOR      = new Color(170, 160, 140);

    private static final Color C_BTN_BG      = new Color(28, 40, 78);
    private static final Color C_BTN_HOVER   = new Color(52, 68, 118);
    private static final Color C_BTN_BORDER  = new Color(68, 82, 135);
    private static final Color C_BTN_TEXT    = new Color(210, 210, 230);

    // Fade-in timing
    private static final int FADE_IN_FRAMES = 40;
    private static final int FADE_IN_FRAME_MS = 30;

    private static final Color[] TYPE_COLORS = {
        new Color(0,   180, 216),  // WAYFINDER
        new Color(199, 125, 255),  // SILVER_TONGUE
        new Color(255, 107, 107),  // HEARTSEEKER
        new Color(255, 209, 102)   // WILDCARD
    };

    // Sprite placeholder colours
    private static final Color C_CAEL_SPRITE      = new Color(60, 160, 90);
    private static final Color C_PLAYER_SPRITE    = new Color(80, 110, 160);
    private static final Color C_MARET_SPRITE     = new Color(180, 140, 60);
    private static final Color C_DREV_SPRITE      = new Color(100, 90, 110);
    private static final Color C_ORET_SPRITE      = new Color(180, 140, 60);
    private static final Color C_INNKEEPER_SPRITE = new Color(140, 100, 80);

    // =========================================================
    // LAYOUT CONSTANTS (logical 700x500 design space)
    // =========================================================

    private static final double DBOX_X  = 30;
    private static final double DBOX_Y  = 320;
    private static final double DBOX_W  = 640;
    private static final double DBOX_H  = 160;

    private static final double SPEAKER_X = 45;
    private static final double SPEAKER_Y = 340;
    private static final double TEXT_X    = 45;
    private static final double TEXT_Y    = 362;
    private static final double TEXT_LINE_GAP = 20;
    private static final int    TEXT_WRAP_CHARS = 62;

    private static final double CONT_X = 630;
    private static final double CONT_Y = 465;

    private static final double CHOICE_X  = 40;
    private static final double CHOICE_W  = 620;
    private static final double CHOICE_H  = 32;
    private static final double CHOICE_Y0 = 328;
    private static final double CHOICE_GAP = 6;

    private static final double NPC_SPRITE_X = 60;
    private static final double NPC_SPRITE_Y = 60;
    private static final double PLAYER_SPRITE_X = 520;
    private static final double PLAYER_SPRITE_Y = 60;

    // =========================================================
    // DIALOGUE STATE
    // =========================================================

    private String currentNodeId;
    private int currentLineIndex;
    private boolean showingChoice;
    private DialogueChoice activeChoice;
    private final List<GRect> choiceBoxes = new ArrayList<>();
    private int hoveredChoice = -1;
    private final List<GObject> dialogueElements = new ArrayList<>();
    private String currentRejoinId = null;
    /** True while the fade-from-black overlay is animating. */
    private volatile boolean fadingIn = false;
    /** Full-screen overlay for fade-from-black entrance. */
    private GRect fadeOverlay;

    /**
     * Tracks which Obstacle 1 location was chosen so that Part 3
     * routes to the correct location's execution choices.
     * Values: "LOC_A", "LOC_B", "LOC_C", "LOC_D"
     */
    private String obstacle1Location = null;

    // =========================================================
    // DIALOGUE DATA
    // =========================================================

    private static final Map<String, DialogueNode> NODES = new LinkedHashMap<>();
    private static final Map<String, DialogueChoice> CHOICES = new LinkedHashMap<>();

    private static final CardType[] STD_TYPES = {
        CardType.WAYFINDER, CardType.SILVER_TONGUE,
        CardType.HEARTSEEKER, CardType.WILDCARD
    };

    static {
        // =============================================================
        // PART 1 — THE DEBRIEF
        // =============================================================

        addNode("s2_opening", DialogueNode.NARRATOR, new String[]{
            "The alley. Late afternoon light cuts between buildings in long amber slants.",
            "The post-incident scramble has settled into something quieter.",
            "Stalls being reset, voices at a distance, occasional sounds of a guard",
            "directing foot traffic somewhere."
        }, "s2_cael_report");

        addNode("s2_cael_report", DialogueNode.CAELOMUND, new String[]{
            "You took longer than expected."
        }, "s2_cael_report2");

        addNode("s2_cael_report2", DialogueNode.NARRATOR, new String[]{
            "He says it without accusation. A data point."
        }, "s2_cael_report3");

        addNode("s2_cael_report3", DialogueNode.CAELOMUND, new String[]{
            "Report."
        }, null); // choice follows

        // Choice 1 — Debrief: how did you handle the guards? (+1 archetype, no card spent)
        addChoice("s2_cael_report3",
            new String[]{
                "\"Told them it was a livestock accident.\"",
                "\"Implied discretion would be rewarded.\"",
                "\"Told them the truth. Mostly.\"",
                "\"Pointed them toward a performer who wasn't there.\""
            },
            new String[]{"s2_br1_wf", "s2_br1_st", "s2_br1_hs", "s2_br1_wc"},
            "s2_rejoin1",
            false, false,
            new int[]{1, 1, 1, 1},
            new String[]{null, null, null, null}
        );

        // -- Branch A: Way Finder --
        addNode("s2_br1_wf", DialogueNode.PLAYER, new String[]{
            "I kept it simple. A cart spooked a goat, the goat spooked the crowd,",
            "things fell over. I gave them a story with no loose ends.",
            "The guard captain seemed relieved to have something that fit on a form."
        }, "s2_br1_wf_resp");
        addNode("s2_br1_wf_resp", DialogueNode.CAELOMUND, new String[]{
            "Livestock accident. That is the correct level of detail for a city guard.",
            "You gave them a shape they already knew how to hold."
        }, "s2_br1_wf_resp2");
        addNode("s2_br1_wf_resp2", DialogueNode.CAELOMUND, new String[]{
            "That was well chosen."
        }, "s2_br1_wf_resp3");
        addNode("s2_br1_wf_resp3", DialogueNode.NARRATOR, new String[]{
            "He says it the way someone says something they had no intention",
            "of saying out loud."
        }, "s2_br1_wf_resp4");
        addNode("s2_br1_wf_resp4", DialogueNode.CAELOMUND, new String[]{
            "Do not make anything of it."
        }, null); // -> rejoin

        // -- Branch B: Silver Tongue --
        addNode("s2_br1_st", DialogueNode.PLAYER, new String[]{
            "I suggested that a prominent merchant had a private interest",
            "in keeping the details quiet, and that cooperation would be remembered.",
            "Nobody asked for the merchant's name."
        }, "s2_br1_st_resp");
        addNode("s2_br1_st_resp", DialogueNode.CAELOMUND, new String[]{
            "You bribed them with a promise you cannot keep",
            "on behalf of a merchant who does not exist."
        }, "s2_br1_st_resp2");
        addNode("s2_br1_st_resp2", DialogueNode.CAELOMUND, new String[]{
            "I am not objecting. I am identifying what you did.",
            "There is a difference between a lie and an architecture.",
            "You built an architecture."
        }, null); // -> rejoin

        // -- Branch C: Heart Seeker --
        addNode("s2_br1_hs", DialogueNode.PLAYER, new String[]{
            "I told them plainly what happened. A disturbance, goods knocked over.",
            "I told them I had a hand in settling it.",
            "The guard seemed more tired than suspicious."
        }, "s2_br1_hs_resp");
        addNode("s2_br1_hs_resp", DialogueNode.CAELOMUND, new String[]{
            "You told them the truth."
        }, "s2_br1_hs_resp2");
        addNode("s2_br1_hs_resp2", DialogueNode.PLAYER, new String[]{
            "People usually do accept it, if you say it plainly enough."
        }, "s2_br1_hs_resp3");
        addNode("s2_br1_hs_resp3", DialogueNode.CAELOMUND, new String[]{
            "I have found the opposite to be true, in my experience."
        }, "s2_br1_hs_resp4");
        addNode("s2_br1_hs_resp4", DialogueNode.CAELOMUND, new String[]{
            "...That is possible. I have spent the majority of my centuries",
            "talking to people who had a significant stake in whether I believed them.",
            "Perhaps a market guard has no such stake."
        }, null); // -> rejoin

        // -- Branch D: Wildcard --
        addNode("s2_br1_wc", DialogueNode.PLAYER, new String[]{
            "There was a juggler working the far end of the market this morning.",
            "I described him in considerable detail and suggested his act",
            "escalated in an unexpected direction.",
            "By the time they find him, we will be somewhere else entirely."
        }, "s2_br1_wc_resp");
        addNode("s2_br1_wc_resp", DialogueNode.CAELOMUND, new String[]{
            "You blamed a juggler."
        }, "s2_br1_wc_resp2");
        addNode("s2_br1_wc_resp2", DialogueNode.CAELOMUND, new String[]{
            "A specific juggler. One you had observed.",
            "One who cannot be immediately produced."
        }, "s2_br1_wc_resp3");
        addNode("s2_br1_wc_resp3", DialogueNode.CAELOMUND, new String[]{
            "I want to register an objection to the juggler on general principle."
        }, "s2_br1_wc_resp4");
        addNode("s2_br1_wc_resp4", DialogueNode.PLAYER, new String[]{
            "Registered."
        }, "s2_br1_wc_resp5");
        addNode("s2_br1_wc_resp5", DialogueNode.CAELOMUND, new String[]{
            "Thank you. Let us continue."
        }, null); // -> rejoin

        // -- Rejoin: Caelomund takes stock --
        addNode("s2_rejoin1", DialogueNode.CAELOMUND, new String[]{
            "We have a window. It is not large and it is already smaller",
            "than it was when you left."
        }, "s2_stock1");

        addNode("s2_stock1", DialogueNode.CAELOMUND, new String[]{
            "I have no coin. I had no reason to carry coin.",
            "I have no tools, no provisions, no means of acquiring either",
            "through conventional channels, given that I am currently a goat",
            "and every merchant in this city has had some version of this",
            "afternoon described to them by now."
        }, "s2_stock2");

        addNode("s2_stock2", DialogueNode.CAELOMUND, new String[]{
            "The keep is three days northeast on foot.",
            "Past the river, through the Thornwood.",
            "In this form I cannot carry anything.",
            "Which means you carry everything.",
            "Which means we need something for you to carry."
        }, "s2_stock3");

        addNode("s2_stock3", DialogueNode.CAELOMUND, new String[]{
            "We have nothing to carry."
        }, "s2_scroll_check");

        // Scroll conditional — check if scroll is in hand
        // (If SCROLL_LOST flag is set, skip the scroll dialogue)
        addNode("s2_scroll_check", DialogueNode.NARRATOR, new String[]{
            "Caelomund's attention moves to your pack."
        }, "s2_scroll_talk");

        addNode("s2_scroll_talk", DialogueNode.CAELOMUND, new String[]{
            "You still have the scroll."
        }, "s2_scroll_talk2");

        addNode("s2_scroll_talk2", DialogueNode.CAELOMUND, new String[]{
            "It is the deed to the Solmere Tower-Keep.",
            "It is the only document in existence that establishes,",
            "in legal terms, that I am who I say I am.",
            "Without it, I am a goat with a story.",
            "With it, I am still Caelomund Vaen Solmere."
        }, "s2_scroll_talk3");

        addNode("s2_scroll_talk3", DialogueNode.CAELOMUND, new String[]{
            "I am not asking you to agree.",
            "I am asking you to understand why I will resist",
            "trading it."
        }, "s2_plan_prompt");

        addNode("s2_plan_prompt", DialogueNode.CAELOMUND, new String[]{
            "The question is how we leave this city with what we need",
            "before Bastian sends something else,",
            "or before your story stops holding.",
            "So. [PROFESSION]. What do you suggest?"
        }, null); // -> Part 2 choice

        // =============================================================
        // PART 2 — OBSTACLE 1: THE PLAN (card spend, +2 archetype)
        // =============================================================

        addChoice("s2_plan_prompt",
            new String[]{
                "\"Road traders camp outside the east gate.\"",
                "\"Someone who moves things quietly.\"",
                "\"The vendor from this morning. Oret.\"",
                "\"There are goods with no eyes on them.\""
            },
            new String[]{"s2_plan_wf", "s2_plan_st", "s2_plan_hs", "s2_plan_wc"},
            "s2_plan_rejoin",
            true,   // spends card
            false,  // not tutorial
            new int[]{2, 2, 2, 2},
            new String[]{"S2_LOC_A", "S2_LOC_B", "S2_LOC_C", "S2_LOC_D"}
        );

        // -- Plan A: Way Finder — caravan camp --
        addNode("s2_plan_wf", DialogueNode.PLAYER, new String[]{
            "Road traders camp outside the walls to avoid city fees.",
            "If there is a caravan at the east gate today they will have what we need.",
            "They will not know or care what happened in that market this afternoon."
        }, "s2_plan_wf_resp");
        addNode("s2_plan_wf_resp", DialogueNode.CAELOMUND, new String[]{
            "The east gate caravan camp.",
            "Road traders will not know what happened in the market this afternoon."
        }, "s2_plan_wf_resp2");
        addNode("s2_plan_wf_resp2", DialogueNode.CAELOMUND, new String[]{
            "A person walking a goat out of the city gate is less suspicious",
            "than a person leaving without the goat they were seen with.",
            "We go together."
        }, null); // -> rejoin

        // -- Plan B: Silver Tongue — back channel dealer --
        addNode("s2_plan_st", DialogueNode.PLAYER, new String[]{
            "There is always a person in a place like this who operates",
            "in the space between the official market and the things",
            "the official market does not sell."
        }, "s2_plan_st_resp");
        addNode("s2_plan_st_resp", DialogueNode.CAELOMUND, new String[]{
            "A back-channel dealer. Someone who trades in discretion."
        }, "s2_plan_st_resp2");
        addNode("s2_plan_st_resp2", DialogueNode.CAELOMUND, new String[]{
            "I want it on record that walking into a back-alley transaction",
            "as a goat is not a position I find dignified.",
            "I am going to do it anyway."
        }, null); // -> rejoin

        // -- Plan C: Heart Seeker — return to Oret --
        addNode("s2_plan_hs", DialogueNode.PLAYER, new String[]{
            "Oret ran when the construct arrived.",
            "That means frightened, not hostile.",
            "Someone who was decent once and is already involved",
            "is a better foundation than a stranger."
        }, "s2_plan_hs_resp");
        addNode("s2_plan_hs_resp", DialogueNode.CAELOMUND, new String[]{
            "The vendor. You want to return to the vendor."
        }, "s2_plan_hs_resp2");
        addNode("s2_plan_hs_resp2", DialogueNode.CAELOMUND, new String[]{
            "Do not tell him more than he needs.",
            "He was frightened once today already."
        }, "s2_plan_hs_resp3");
        addNode("s2_plan_hs_resp3", DialogueNode.PLAYER, new String[]{
            "That is unexpectedly considerate."
        }, "s2_plan_hs_resp4");
        addNode("s2_plan_hs_resp4", DialogueNode.CAELOMUND, new String[]{
            "It is practical. Frightened people make poor transactions."
        }, null); // -> rejoin

        // -- Plan D: Wildcard — scavenge the market --
        addNode("s2_plan_wc", DialogueNode.PLAYER, new String[]{
            "Three stalls were disrupted. Goods are on the ground,",
            "displaced, in nobody's immediate possession.",
            "There is a window between 'belonging to someone'",
            "and 'actively in someone's hand'.",
            "That window is currently open."
        }, "s2_plan_wc_resp");
        addNode("s2_plan_wc_resp", DialogueNode.CAELOMUND, new String[]{
            "You are proposing we return to the scene of the incident",
            "and take things."
        }, "s2_plan_wc_resp2");
        addNode("s2_plan_wc_resp2", DialogueNode.PLAYER, new String[]{
            "I am proposing we find things that are currently between owners.",
            "There is a difference."
        }, "s2_plan_wc_resp3");
        addNode("s2_plan_wc_resp3", DialogueNode.CAELOMUND, new String[]{
            "I want both. I will accept one."
        }, null); // -> rejoin

        // -- Plan rejoin: moving together --
        addNode("s2_plan_rejoin", DialogueNode.CAELOMUND, new String[]{
            "We have until the light changes.",
            "Not long after that every transaction in this city becomes",
            "a conversation I do not want us to be part of."
        }, "s2_plan_rejoin2");

        addNode("s2_plan_rejoin2", DialogueNode.NARRATOR, new String[]{
            "He falls in beside you. The city is still out there.",
            "The guard patrol, the closing market,",
            "the day that did not go as planned for anyone in it.",
            "You step out into it together."
        }, "s2_exec_router");

        // Router node — advances to the correct location based on flag
        // This is handled specially in advanceToNode()
        addNode("s2_exec_router", DialogueNode.NARRATOR, new String[]{
            "The city shifts around you as you move through it."
        }, null); // handled by routing logic

        // =============================================================
        // PART 3 — OBSTACLE 2: EXECUTION (16 branches)
        // =============================================================

        // --- LOCATION A: THE CARAVAN CAMP (Maret) ---

        addNode("s2_loc_a_intro", DialogueNode.NARRATOR, new String[]{
            "Just beyond the east gate. Three road-worn wagons circled on packed dirt.",
            "A fire already lit. A lean woman in her fifties is cross-referencing",
            "a manifest against an open crate, talking to herself as she counts."
        }, "s2_loc_a_maret1");

        addNode("s2_loc_a_maret1", DialogueNode.MARET, new String[]{
            "Market side! You two look like you had an afternoon."
        }, "s2_loc_a_maret2");

        addNode("s2_loc_a_maret2", DialogueNode.MARET, new String[]{
            "And you brought a goat.",
            "A very -- that is a very focused-looking goat."
        }, "s2_loc_a_cael1");

        addNode("s2_loc_a_cael1", DialogueNode.CAELOMUND, new String[]{
            "I am not a goat."
        }, "s2_loc_a_maret3");

        addNode("s2_loc_a_maret3", DialogueNode.MARET, new String[]{
            "Right, of course you're not. Come in.",
            "Don't stand at the edge. The horses won't bite.",
            "Well. Tomas might. Don't stand near Tomas."
        }, "s2_loc_a_maret4");

        addNode("s2_loc_a_maret4", DialogueNode.MARET, new String[]{
            "I have been running this route for eleven years",
            "and I have never once had a talking goat in my camp.",
            "Today is a good day. What do you need?"
        }, null); // -> execution choice

        addChoice("s2_loc_a_maret4",
            new String[]{
                "\"Trail rations, three days. Fire kit. Trapping wire.\"",
                "\"You seem like someone who makes a good deal.\"",
                "\"Honestly, we're in a situation and I need help.\"",
                "\"What's the strangest transaction you've made this year?\""
            },
            new String[]{"s2_a_wf", "s2_a_st", "s2_a_hs", "s2_a_wc"},
            "s2_exec_outcome",
            true,   // spends card
            false,
            new int[]{2, 2, 2, 2},
            new String[]{"REST_BARN,MARET_KNOT", "REST_BARN", "REST_BARN,ORET_CORD", "REST_BARN,MARET_KNOT"}
        );

        // A-WF: Way Finder execution at caravan
        addNode("s2_a_wf", DialogueNode.PLAYER, new String[]{
            "Trail rations, three days. Fire kit. Trapping wire. What are you asking?"
        }, "s2_a_wf_2");
        addNode("s2_a_wf_2", DialogueNode.MARET, new String[]{
            "All of that I have. One moment."
        }, "s2_a_wf_3");
        addNode("s2_a_wf_3", DialogueNode.MARET, new String[]{
            "Fire kit I have two of, so you get the good one.",
            "Trail rations I packed myself last week.",
            "Three days heading where?"
        }, "s2_a_wf_4");
        addNode("s2_a_wf_4", DialogueNode.PLAYER, new String[]{
            "Northeast. Past the river and through the Thornwood."
        }, "s2_a_wf_5");
        addNode("s2_a_wf_5", DialogueNode.MARET, new String[]{
            "Thornwood. This time of year. Hm.",
            "The canopy does something to sound in there.",
            "Carries wrong, bounces wrong. Don't let it make you stop moving."
        }, "s2_a_wf_6");
        addNode("s2_a_wf_6", DialogueNode.CAELOMUND, new String[]{
            "The acoustic properties of the Thornwood are a function",
            "of the old-growth root system. Disorienting but not dangerous."
        }, "s2_a_wf_7");
        addNode("s2_a_wf_7", DialogueNode.MARET, new String[]{
            "Nine copper even. Fair for the road.",
            "The trapping wire — there's a knot for the Thornwood.",
            "Worth knowing if you're out there a few days."
        }, "s2_a_wf_8");
        addNode("s2_a_wf_8", DialogueNode.MARET, new String[]{
            "Good traveling to you both. Mind Tomas on your way out."
        }, "s2_a_wf_9");
        addNode("s2_a_wf_9", DialogueNode.CAELOMUND, new String[]{
            "We will."
        }, "s2_a_wf_10");
        addNode("s2_a_wf_10", DialogueNode.NARRATOR, new String[]{
            "He says it naturally. Neither of them marks it.",
            "[GOAT TRUST +1]"
        }, null); // -> outcome

        // A-ST: Silver Tongue execution at caravan
        addNode("s2_a_st", DialogueNode.PLAYER, new String[]{
            "You seem like someone who makes a good deal",
            "before anyone else has thought of the terms."
        }, "s2_a_st_2");
        addNode("s2_a_st_2", DialogueNode.MARET, new String[]{
            "Ha! I've been running a trade route since before you were walking.",
            "'Work with' is what the charming ones say",
            "when they've been caught being charming."
        }, "s2_a_st_3");
        addNode("s2_a_st_3", DialogueNode.MARET, new String[]{
            "The price is what it is because I have eleven years",
            "of knowing exactly what things cost on this road."
        }, "s2_a_st_4");
        addNode("s2_a_st_4", DialogueNode.CAELOMUND, new String[]{
            "She is correct. Attempting to negotiate with someone",
            "who has more information about the value is a losing position."
        }, "s2_a_st_5");
        addNode("s2_a_st_5", DialogueNode.MARET, new String[]{
            "See, I like him. He's direct."
        }, "s2_a_st_6");
        addNode("s2_a_st_6", DialogueNode.NARRATOR, new String[]{
            "You pay. Maret mentions the Thornwood briefly",
            "and sends you off warmly. The charm bounced off cleanly."
        }, null); // -> outcome

        // A-HS: Heart Seeker execution at caravan
        addNode("s2_a_hs", DialogueNode.PLAYER, new String[]{
            "Honestly, we are in a situation",
            "and I need help more than I need a bargain.",
            "I am asking plainly."
        }, "s2_a_hs_2");
        addNode("s2_a_hs_2", DialogueNode.MARET, new String[]{
            "Plain asking. I respect plain asking."
        }, "s2_a_hs_3");
        addNode("s2_a_hs_3", DialogueNode.MARET, new String[]{
            "I've been on this road long enough to know what",
            "'a situation' looks like from the outside.",
            "I had it myself for about three years after I left my last posting."
        }, "s2_a_hs_4");
        addNode("s2_a_hs_4", DialogueNode.MARET, new String[]{
            "I walked out with what I could carry and kept walking",
            "until the walking became the thing itself."
        }, "s2_a_hs_5");
        addNode("s2_a_hs_5", DialogueNode.NARRATOR, new String[]{
            "She sets the supplies out. More than requested.",
            "An extra day of rations, a coil of cord she adds without comment."
        }, "s2_a_hs_6");
        addNode("s2_a_hs_6", DialogueNode.MARET, new String[]{
            "That's on me. Road luck."
        }, "s2_a_hs_7");
        addNode("s2_a_hs_7", DialogueNode.CAELOMUND, new String[]{
            "You walked until the walking became the thing itself."
        }, "s2_a_hs_8");
        addNode("s2_a_hs_8", DialogueNode.MARET, new String[]{
            "You stop running from something and you start going somewhere.",
            "Different feeling."
        }, "s2_a_hs_9");
        addNode("s2_a_hs_9", DialogueNode.CAELOMUND, new String[]{
            "...Yes. I imagine it is."
        }, "s2_a_hs_10");
        addNode("s2_a_hs_10", DialogueNode.NARRATOR, new String[]{
            "He says it in the tone of someone who has just recognized",
            "something they have never had a word for.",
            "Something shifted in Caelomund during this conversation."
        }, null); // -> outcome

        // A-WC: Wildcard execution at caravan
        addNode("s2_a_wc", DialogueNode.PLAYER, new String[]{
            "Before we get to the list —",
            "what's the strangest transaction you've made this year?"
        }, "s2_a_wc_2");
        addNode("s2_a_wc_2", DialogueNode.MARET, new String[]{
            "Oh, that is a good opener.",
            "Nobody opens like that."
        }, "s2_a_wc_3");
        addNode("s2_a_wc_3", DialogueNode.CAELOMUND, new String[]{
            "We are going to be here for a while, are we not."
        }, "s2_a_wc_4");
        addNode("s2_a_wc_4", DialogueNode.MARET, new String[]{
            "There was a man in the fourth month who offered me",
            "a promissory note on a fish harvest three provinces over."
        }, "s2_a_wc_5");
        addNode("s2_a_wc_5", DialogueNode.MARET, new String[]{
            "Winter before last. A man going over the mountain pass",
            "with a cart full of clocks. About forty, all ticking.",
            "Someone had bought them because she wanted to know",
            "what forty clocks ticking in the same room sounded like."
        }, "s2_a_wc_6");
        addNode("s2_a_wc_6", DialogueNode.CAELOMUND, new String[]{
            "I have heard more. In my third century I was documenting",
            "temporal recursion and required a room full of timepieces.",
            "The sound was exactly as she described it.",
            "Like being inside a thought that is thinking itself."
        }, "s2_a_wc_7");
        addNode("s2_a_wc_7", DialogueNode.MARET, new String[]{
            "Third century. How old are you?"
        }, "s2_a_wc_8");
        addNode("s2_a_wc_8", DialogueNode.CAELOMUND, new String[]{
            "Four hundred and twelve, at last count.",
            "The counting becomes imprecise when you have been",
            "a goat for several days."
        }, "s2_a_wc_9");
        addNode("s2_a_wc_9", DialogueNode.NARRATOR, new String[]{
            "Maret demonstrates the trapping knot, including",
            "the wet-weather variation. You pay while she talks.",
            "Caelomund asked for something. That is not nothing.",
            "[GOAT TRUST +1]"
        }, null); // -> outcome

        // --- LOCATION B: THE BACK-ALLEY DEALER (Drev) ---

        addNode("s2_loc_b_intro", DialogueNode.NARRATOR, new String[]{
            "A narrow side street behind the market district.",
            "Drev is positioned against the wall with the particular stillness",
            "of someone who has been there for fifteen years."
        }, "s2_loc_b_drev1");

        addNode("s2_loc_b_drev1", DialogueNode.DREV, new String[]{
            "You were in the market today."
        }, "s2_loc_b_drev2");

        addNode("s2_loc_b_drev2", DialogueNode.DREV, new String[]{
            "Not everyone had a ten-minute conversation with the guard captain.",
            "The construct was tracking the goat."
        }, "s2_loc_b_drev3");

        addNode("s2_loc_b_drev3", DialogueNode.DREV, new String[]{
            "What is the goat."
        }, "s2_loc_b_cael1");

        addNode("s2_loc_b_cael1", DialogueNode.CAELOMUND, new String[]{
            "The goat is an archmage of four hundred and twelve years",
            "who is currently experiencing a significant and temporary inconvenience.",
            "The goat would prefer not to be referred to as 'the goat.'"
        }, "s2_loc_b_drev4");

        addNode("s2_loc_b_drev4", DialogueNode.DREV, new String[]{
            "...Right."
        }, null); // -> execution choice

        addChoice("s2_loc_b_drev4",
            new String[]{
                "\"Here is what I need. What do you want for it?\"",
                "\"You already know the shape — that's worth something.\"",
                "\"I'll tell you the rest. All of it.\"",
                "\"You're Drev.\""
            },
            new String[]{"s2_b_wf", "s2_b_st", "s2_b_hs", "s2_b_wc"},
            "s2_exec_outcome",
            true,   // spends card
            false,
            new int[]{2, 2, 2, 2},
            new String[]{"REST_BARN", "REST_INN", "REST_INN", "REST_BARN"}
        );

        // B-WF: Way Finder execution at dealer
        addNode("s2_b_wf", DialogueNode.PLAYER, new String[]{
            "The construct is dealt with. The guard is handled.",
            "We are leaving tonight. I have a list.",
            "You name prices. We close this before the light changes."
        }, "s2_b_wf_2");
        addNode("s2_b_wf_2", DialogueNode.DREV, new String[]{
            "The apprentice who sent the construct.",
            "Does he know the goat is in this city?"
        }, "s2_b_wf_3");
        addNode("s2_b_wf_3", DialogueNode.PLAYER, new String[]{
            "He sent the construct to find him. The construct is gone.",
            "Which is why we are leaving tonight."
        }, "s2_b_wf_4");
        addNode("s2_b_wf_4", DialogueNode.DREV, new String[]{
            "Six copper for the lot."
        }, "s2_b_wf_5");
        addNode("s2_b_wf_5", DialogueNode.NARRATOR, new String[]{
            "You pay. Six minutes, start to finish.",
            "Clean, efficient."
        }, null); // -> outcome

        // B-ST: Silver Tongue execution at dealer (INN outcome — takes too long)
        addNode("s2_b_st", DialogueNode.PLAYER, new String[]{
            "You've already done half the work of this conversation.",
            "I'd like to compensate you for that in how we talk about the price."
        }, "s2_b_st_2");
        addNode("s2_b_st_2", DialogueNode.DREV, new String[]{
            "You're going to trade what I already know back to me",
            "as if it has a value in coin."
        }, "s2_b_st_3");
        addNode("s2_b_st_3", DialogueNode.NARRATOR, new String[]{
            "The negotiation goes item by item. Each item, a new question.",
            "Rations for six copper. Fire kit for four. Wire for two and a half.",
            "Every price holds. Drev extracts information with each transaction."
        }, "s2_b_st_4");
        addNode("s2_b_st_4", DialogueNode.PLAYER, new String[]{
            "How long have we been here."
        }, "s2_b_st_5");
        addNode("s2_b_st_5", DialogueNode.CAELOMUND, new String[]{
            "Long enough that I am calculating whether we can make the gate."
        }, "s2_b_st_6");
        addNode("s2_b_st_6", DialogueNode.DREV, new String[]{
            "I want to know how this ends. Not now. Later.",
            "If you come back through this city."
        }, null); // -> outcome (INN)

        // B-HS: Heart Seeker execution at dealer (INN outcome — questions cost time)
        addNode("s2_b_hs", DialogueNode.PLAYER, new String[]{
            "Since you already know the shape of it — I'll tell you the rest.",
            "You're going to piece it together anyway.",
            "I'd rather give you the accurate version."
        }, "s2_b_hs_2");
        addNode("s2_b_hs_2", DialogueNode.NARRATOR, new String[]{
            "You tell Drev everything. The archmage, the apprentice,",
            "the polymorph, the tower-keep northeast of here."
        }, "s2_b_hs_3");
        addNode("s2_b_hs_3", DialogueNode.DREV, new String[]{
            "The apprentice took the archmage's wand",
            "and used it on the archmage."
        }, "s2_b_hs_4");
        addNode("s2_b_hs_4", DialogueNode.DREV, new String[]{
            "Ten years. He's been studying under a four-hundred-year-old",
            "archmage for ten years."
        }, "s2_b_hs_5");
        addNode("s2_b_hs_5", DialogueNode.NARRATOR, new String[]{
            "He names fair prices. Produces each item.",
            "But the questions keep coming as he works."
        }, "s2_b_hs_6");
        addNode("s2_b_hs_6", DialogueNode.DREV, new String[]{
            "I'll want to know how this ends.",
            "I'm saying it again because I mean it more",
            "than I did ten minutes ago."
        }, "s2_b_hs_7");
        addNode("s2_b_hs_7", DialogueNode.NARRATOR, new String[]{
            "The alley mouth shows deep orange sky.",
            "The truth produced more questions than either expected.",
            "The questions cost time."
        }, null); // -> outcome (INN)

        // B-WC: Wildcard execution at dealer
        addNode("s2_b_wc", DialogueNode.PLAYER, new String[]{
            "You're Drev."
        }, "s2_b_wc_2");
        addNode("s2_b_wc_2", DialogueNode.NARRATOR, new String[]{
            "Not a question. Drev's entire posture shifts.",
            "Someone just named him before he could set the terms."
        }, "s2_b_wc_3");
        addNode("s2_b_wc_3", DialogueNode.DREV, new String[]{
            "...Who told you that."
        }, "s2_b_wc_4");
        addNode("s2_b_wc_4", DialogueNode.PLAYER, new String[]{
            "Does it matter? I have a list."
        }, "s2_b_wc_5");
        addNode("s2_b_wc_5", DialogueNode.NARRATOR, new String[]{
            "Drev produces items faster than normal.",
            "He wants them gone before anyone else sees",
            "they know his name. Prices named quickly,",
            "without the usual negotiation."
        }, "s2_b_wc_6");
        addNode("s2_b_wc_6", DialogueNode.CAELOMUND, new String[]{
            "He is going to spend the rest of the evening",
            "trying to work out who you know."
        }, "s2_b_wc_7");
        addNode("s2_b_wc_7", DialogueNode.PLAYER, new String[]{
            "I had a name and a street.",
            "The context was his posture."
        }, "s2_b_wc_8");
        addNode("s2_b_wc_8", DialogueNode.CAELOMUND, new String[]{
            "That was either very good instinct or extraordinary luck."
        }, "s2_b_wc_9");
        addNode("s2_b_wc_9", DialogueNode.PLAYER, new String[]{
            "It was both. They're not mutually exclusive."
        }, null); // -> outcome (BARN)

        // --- LOCATION C: ORET'S STALL ---

        addNode("s2_loc_c_intro", DialogueNode.NARRATOR, new String[]{
            "The far edge of the market. Oret's stall is shuttered.",
            "Oret is around the corner loading crates into a storage alcove.",
            "He sees you coming. He stops.",
            "Then he sees Caelomund. He stops more completely."
        }, "s2_loc_c_oret1");

        addNode("s2_loc_c_oret1", DialogueNode.ORET, new String[]{
            "You brought the goat back."
        }, "s2_loc_c_cael1");

        addNode("s2_loc_c_cael1", DialogueNode.CAELOMUND, new String[]{
            "You appear to have recovered from your earlier departure."
        }, "s2_loc_c_oret2");

        addNode("s2_loc_c_oret2", DialogueNode.ORET, new String[]{
            "Yeah. Well."
        }, "s2_loc_c_cael2");

        addNode("s2_loc_c_cael2", DialogueNode.CAELOMUND, new String[]{
            "I am not criticizing.",
            "I understand flight as a response to things one does not understand."
        }, "s2_loc_c_oret3");

        addNode("s2_loc_c_oret3", DialogueNode.ORET, new String[]{
            "...The goat is a philosopher."
        }, "s2_loc_c_player1");

        addNode("s2_loc_c_player1", DialogueNode.PLAYER, new String[]{
            "The goat is an archmage. But yes."
        }, "s2_loc_c_oret4");

        addNode("s2_loc_c_oret4", DialogueNode.ORET, new String[]{
            "What do you need."
        }, null); // -> execution choice

        addChoice("s2_loc_c_oret4",
            new String[]{
                "\"Trail rations, fire kit, wire. Name a price.\"",
                "\"You and I made a good transaction this morning.\"",
                "\"I'm sorry for how this afternoon went. I need help.\"",
                "\"What would it take for today to feel worth something?\""
            },
            new String[]{"s2_c_wf", "s2_c_st", "s2_c_hs", "s2_c_wc"},
            "s2_exec_outcome",
            true,   // spends card
            false,
            new int[]{2, 2, 2, 2},
            new String[]{"REST_BARN", "REST_BARN", "REST_BARN,ORET_CORD,GOAT_TRUST,APPRENTICE_EMPATHY", "REST_BARN"}
        );

        // C-WF: Way Finder execution at Oret's
        addNode("s2_c_wf", DialogueNode.PLAYER, new String[]{
            "I have a list and a limited window.",
            "You have what I need. Name the price and we move."
        }, "s2_c_wf_2");
        addNode("s2_c_wf_2", DialogueNode.CAELOMUND, new String[]{
            "Your stall was not damaged."
        }, "s2_c_wf_3");
        addNode("s2_c_wf_3", DialogueNode.ORET, new String[]{
            "Luck. The commotion came from the far side."
        }, "s2_c_wf_4");
        addNode("s2_c_wf_4", DialogueNode.CAELOMUND, new String[]{
            "I will make reparations. When I am restored. I want that noted."
        }, "s2_c_wf_5");
        addNode("s2_c_wf_5", DialogueNode.ORET, new String[]{
            "...You're going to pay Pellin back."
        }, "s2_c_wf_6");
        addNode("s2_c_wf_6", DialogueNode.NARRATOR, new String[]{
            "Something shifts in his face."
        }, "s2_c_wf_7");
        addNode("s2_c_wf_7", DialogueNode.ORET, new String[]{
            "My cousin has a farm. Two miles northeast, past the gate.",
            "Tell her Oret sent you. She'll let you sleep in the barn."
        }, "s2_c_wf_8");
        addNode("s2_c_wf_8", DialogueNode.NARRATOR, new String[]{
            "He names a fair price. You pay.",
            "Caelomund's acknowledgment of responsibility earned them the directions."
        }, null); // -> outcome

        // C-ST: Silver Tongue execution at Oret's
        addNode("s2_c_st", DialogueNode.PLAYER, new String[]{
            "You and I made a good transaction this morning.",
            "I am asking for another one."
        }, "s2_c_st_2");
        addNode("s2_c_st_2", DialogueNode.ORET, new String[]{
            "This morning you were someone in a hurry with coin.",
            "That was a clean situation."
        }, "s2_c_st_3");
        addNode("s2_c_st_3", DialogueNode.ORET, new String[]{
            "You didn't tell me there was going to be a thing",
            "with a face on its chest."
        }, "s2_c_st_4");
        addNode("s2_c_st_4", DialogueNode.PLAYER, new String[]{
            "I did not know there was going to be a thing",
            "with a face on its chest."
        }, "s2_c_st_5");
        addNode("s2_c_st_5", DialogueNode.NARRATOR, new String[]{
            "Oret goes to the cart, not warm, not hostile.",
            "He names a price. You pay."
        }, "s2_c_st_6");
        addNode("s2_c_st_6", DialogueNode.ORET, new String[]{
            "The northeast gate closes at dark. If you move now you can make it."
        }, "s2_c_st_7");
        addNode("s2_c_st_7", DialogueNode.ORET, new String[]{
            "I want you both out of my market. That is my entire motivation."
        }, null); // -> outcome

        // C-HS: Heart Seeker execution at Oret's (best outcome — extra supplies, flags)
        addNode("s2_c_hs", DialogueNode.PLAYER, new String[]{
            "I am sorry for the way this afternoon went.",
            "I did not plan any of it. And I need help.",
            "I am asking because you were decent to me earlier",
            "and I am hoping that means something."
        }, "s2_c_hs_2");
        addNode("s2_c_hs_2", DialogueNode.ORET, new String[]{
            "You didn't plan any of it."
        }, "s2_c_hs_3");
        addNode("s2_c_hs_3", DialogueNode.PLAYER, new String[]{
            "I came to your market this morning to buy something.",
            "I left with a talking goat and a construct on my heels."
        }, "s2_c_hs_4");
        addNode("s2_c_hs_4", DialogueNode.NARRATOR, new String[]{
            "Oret decides a thing and does it."
        }, "s2_c_hs_5");
        addNode("s2_c_hs_5", DialogueNode.NARRATOR, new String[]{
            "He goes to the back of the cart and returns with more",
            "than the minimum. An extra day of rations and a coil of good rope."
        }, "s2_c_hs_6");
        addNode("s2_c_hs_6", DialogueNode.ORET, new String[]{
            "The rope. The Thornwood — if that's where you're headed,",
            "there's a section where the trail floods this time of year.",
            "You want to be able to pull each other out."
        }, "s2_c_hs_7");
        addNode("s2_c_hs_7", DialogueNode.ORET, new String[]{
            "My cousin's farm is two miles northeast of the gate.",
            "Lena. Tell her I sent you. She won't ask about the goat."
        }, "s2_c_hs_8");
        addNode("s2_c_hs_8", DialogueNode.CAELOMUND, new String[]{
            "How do you know she will not ask?"
        }, "s2_c_hs_9");
        addNode("s2_c_hs_9", DialogueNode.ORET, new String[]{
            "Because if I'm sending someone with a goat,",
            "she knows it's probably a whole thing",
            "and she should just not."
        }, "s2_c_hs_10");
        addNode("s2_c_hs_10", DialogueNode.CAELOMUND, new String[]{
            "That is a functional definition of trust."
        }, "s2_c_hs_11");
        addNode("s2_c_hs_11", DialogueNode.ORET, new String[]{
            "Yeah. I guess it is."
        }, null); // -> outcome

        // C-WC: Wildcard execution at Oret's
        addNode("s2_c_wc", DialogueNode.PLAYER, new String[]{
            "What would it take for today to feel like",
            "it was worth something?"
        }, "s2_c_wc_2");
        addNode("s2_c_wc_2", DialogueNode.ORET, new String[]{
            "Tell me the goat is not coming back."
        }, "s2_c_wc_3");
        addNode("s2_c_wc_3", DialogueNode.CAELOMUND, new String[]{
            "I have a tower-keep northeast of here",
            "that I have inhabited for three centuries.",
            "When I am restored to my proper form,",
            "I will compensate Pellin for his stall",
            "and you will never see me again."
        }, "s2_c_wc_4");
        addNode("s2_c_wc_4", DialogueNode.ORET, new String[]{
            "My cousin has a farm two miles northeast. Lena.",
            "Tell her I sent you."
        }, "s2_c_wc_5");
        addNode("s2_c_wc_5", DialogueNode.ORET, new String[]{
            "Today was a lot and somehow it is ending",
            "in a way I do not hate."
        }, "s2_c_wc_6");
        addNode("s2_c_wc_6", DialogueNode.CAELOMUND, new String[]{
            "That is a generous reading of events."
        }, "s2_c_wc_7");
        addNode("s2_c_wc_7", DialogueNode.ORET, new String[]{
            "I am a generous reader."
        }, null); // -> outcome

        // --- LOCATION D: THE MARKET IN RECOVERY ---

        addNode("s2_loc_d_intro", DialogueNode.NARRATOR, new String[]{
            "The market, near-closing. Most stalls shut or shutting.",
            "The afternoon's aftermath still visible: displaced goods,",
            "a fire kit near the market green,",
            "a stall whose owner has not yet returned."
        }, "s2_loc_d_cael1");

        addNode("s2_loc_d_cael1", DialogueNode.CAELOMUND, new String[]{
            "This is what I caused."
        }, "s2_loc_d_narr1");

        addNode("s2_loc_d_narr1", DialogueNode.NARRATOR, new String[]{
            "He does not say it with self-pity. A plain statement of causality."
        }, "s2_loc_d_cael2");

        addNode("s2_loc_d_cael2", DialogueNode.CAELOMUND, new String[]{
            "I caused the damage. I will repair it when I am able.",
            "I wanted to say that out loud, even if only to you,",
            "because it is true."
        }, "s2_loc_d_player1");

        addNode("s2_loc_d_player1", DialogueNode.PLAYER, new String[]{
            "Noted."
        }, "s2_loc_d_cael3");

        addNode("s2_loc_d_cael3", DialogueNode.CAELOMUND, new String[]{
            "Thank you."
        }, null); // -> execution choice

        addChoice("s2_loc_d_cael3",
            new String[]{
                "\"I read the scene before I move.\"",
                "\"I find the vendor who most wants this over.\"",
                "\"I'll do it right -- pay for anything with an owner.\"",
                "\"I move fast and you tell me if I'm making a mistake.\""
            },
            new String[]{"s2_d_wf", "s2_d_st", "s2_d_hs", "s2_d_wc"},
            "s2_exec_outcome",
            true,   // spends card
            false,
            new int[]{2, 2, 2, 2},
            new String[]{"REST_BARN,GOAT_RESPECT", "REST_BARN,GOAT_TRUST", "REST_INN,APPRENTICE_EMPATHY", "REST_INN,GOAT_TRUST"}
        );

        // D-WF: Way Finder execution at market
        addNode("s2_d_wf", DialogueNode.PLAYER, new String[]{
            "Stay with me. Move when I move."
        }, "s2_d_wf_2");
        addNode("s2_d_wf_2", DialogueNode.NARRATOR, new String[]{
            "You move through the market. First pass: the ground.",
            "Second pass: who has too much to carry."
        }, "s2_d_wf_3");
        addNode("s2_d_wf_3", DialogueNode.NARRATOR, new String[]{
            "A vendor with surplus trail rations. Half price.",
            "Hardware vendor halfway through tying down his cart. Two copper.",
            "Methodical and clean."
        }, "s2_d_wf_4");
        addNode("s2_d_wf_4", DialogueNode.CAELOMUND, new String[]{
            "You mapped the market. Before you engaged with any part of it."
        }, "s2_d_wf_5");
        addNode("s2_d_wf_5", DialogueNode.PLAYER, new String[]{
            "It is just how I think."
        }, "s2_d_wf_6");
        addNode("s2_d_wf_6", DialogueNode.CAELOMUND, new String[]{
            "I walk into any space and read it entirely",
            "before engaging. I had not seen anyone else",
            "do it in some time."
        }, "s2_d_wf_7");
        addNode("s2_d_wf_7", DialogueNode.NARRATOR, new String[]{
            "The fire kit near the market green. No stall in sight."
        }, "s2_d_wf_8");
        addNode("s2_d_wf_8", DialogueNode.CAELOMUND, new String[]{
            "The stall it came from closed two hours ago.",
            "I watched him go.",
            "I have been in this city for three days",
            "with nothing to do but observe."
        }, "s2_d_wf_9");
        addNode("s2_d_wf_9", DialogueNode.NARRATOR, new String[]{
            "You take the kit. Done before the last vendors finish closing.",
            "Caelomund recognized something of himself in how you move.",
            "[GOAT RESPECT +1]"
        }, null); // -> outcome

        // D-ST: Silver Tongue execution at market
        addNode("s2_d_st", DialogueNode.PLAYER, new String[]{
            "Follow my lead. Do not say anything unless I signal you."
        }, "s2_d_st_2");
        addNode("s2_d_st_2", DialogueNode.CAELOMUND, new String[]{
            "You are giving me stage directions."
        }, "s2_d_st_3");
        addNode("s2_d_st_3", DialogueNode.PLAYER, new String[]{
            "Who is currently a goat in a closing market.",
            "Please follow my lead."
        }, "s2_d_st_4");
        addNode("s2_d_st_4", DialogueNode.NARRATOR, new String[]{
            "You find the vendor most eager to end the day.",
            "Rations bought at exhausted-end-of-day price.",
            "Second vendor for the wire. Same approach."
        }, "s2_d_st_5");
        addNode("s2_d_st_5", DialogueNode.VENDOR, new String[]{
            "Oi. That's from -- is that the goat?"
        }, "s2_d_st_6");
        addNode("s2_d_st_6", DialogueNode.CAELOMUND, new String[]{
            "I was given specific instructions not to speak."
        }, "s2_d_st_7");
        addNode("s2_d_st_7", DialogueNode.PLAYER, new String[]{
            "Those instructions are temporarily suspended.",
            "Say something reassuring."
        }, "s2_d_st_8");
        addNode("s2_d_st_8", DialogueNode.CAELOMUND, new String[]{
            "The situation in the market earlier has been resolved.",
            "We are leaving the city tonight. The fire kit is displaced inventory.",
            "We are taking it."
        }, "s2_d_st_9");
        addNode("s2_d_st_9", DialogueNode.CAELOMUND, new String[]{
            "That was your lead."
        }, "s2_d_st_10");
        addNode("s2_d_st_10", DialogueNode.PLAYER, new String[]{
            "That was your improvisation."
        }, "s2_d_st_11");
        addNode("s2_d_st_11", DialogueNode.CAELOMUND, new String[]{
            "I improvised within the parameters of your lead."
        }, "s2_d_st_12");
        addNode("s2_d_st_12", DialogueNode.PLAYER, new String[]{
            "You did fine."
        }, "s2_d_st_13");
        addNode("s2_d_st_13", DialogueNode.NARRATOR, new String[]{
            "Something in Caelomund's posture eases slightly.",
            "They worked together. It worked.",
            "[GOAT TRUST +1]"
        }, null); // -> outcome

        // D-HS: Heart Seeker execution at market (INN outcome — paid too much, took too long)
        addNode("s2_d_hs", DialogueNode.PLAYER, new String[]{
            "We had a hand in the chaos here.",
            "I'm going to find what I can find,",
            "and pay for anything that has an owner."
        }, "s2_d_hs_2");
        addNode("s2_d_hs_2", DialogueNode.CAELOMUND, new String[]{
            "That will cost more than taking."
        }, "s2_d_hs_3");
        addNode("s2_d_hs_3", DialogueNode.PLAYER, new String[]{
            "I know."
        }, "s2_d_hs_4");
        addNode("s2_d_hs_4", DialogueNode.CAELOMUND, new String[]{
            "You are going to do it anyway."
        }, "s2_d_hs_5");
        addNode("s2_d_hs_5", DialogueNode.PLAYER, new String[]{
            "Yes."
        }, "s2_d_hs_6");
        addNode("s2_d_hs_6", DialogueNode.NARRATOR, new String[]{
            "You track back to each stall, each vendor.",
            "You pay for the fire kit. Three copper for the rations.",
            "By the time you have what you need, the market is quiet.",
            "The gate is still open, but barely."
        }, "s2_d_hs_7");
        addNode("s2_d_hs_7", DialogueNode.CAELOMUND, new String[]{
            "You paid more than you had to. For everything."
        }, "s2_d_hs_8");
        addNode("s2_d_hs_8", DialogueNode.PLAYER, new String[]{
            "Yes."
        }, "s2_d_hs_9");
        addNode("s2_d_hs_9", DialogueNode.CAELOMUND, new String[]{
            "I have spent four hundred years in rooms full of people",
            "who wanted things from me or from each other.",
            "I have very rarely been in the company of someone",
            "who simply paid for things. Because they were owed.",
            "Because it was right."
        }, "s2_d_hs_10");
        addNode("s2_d_hs_10", DialogueNode.CAELOMUND, new String[]{
            "I am not saying it was strategically sound.",
            "I am saying I noticed it."
        }, "s2_d_hs_11");
        addNode("s2_d_hs_11", DialogueNode.NARRATOR, new String[]{
            "They missed the window but Caelomund said something true.",
            "[APPRENTICE EMPATHY +1]"
        }, null); // -> outcome (INN)

        // D-WC: Wildcard execution at market (INN outcome)
        addNode("s2_d_wc", DialogueNode.PLAYER, new String[]{
            "Stay close. If I am about to take something",
            "that matters to someone, say something."
        }, "s2_d_wc_2");
        addNode("s2_d_wc_2", DialogueNode.CAELOMUND, new String[]{
            "You want me to serve as your moral early-warning system."
        }, "s2_d_wc_3");
        addNode("s2_d_wc_3", DialogueNode.NARRATOR, new String[]{
            "The fire kit near the market green. No obvious owner.",
            "You pocket it. Caelomund says nothing."
        }, "s2_d_wc_4");
        addNode("s2_d_wc_4", DialogueNode.CAELOMUND, new String[]{
            "Those rations belong to the stall with the green awning.",
            "He is coming back. People do not leave weight measures",
            "when they are done."
        }, "s2_d_wc_5");
        addNode("s2_d_wc_5", DialogueNode.PLAYER, new String[]{
            "You are better at this than I expected."
        }, "s2_d_wc_6");
        addNode("s2_d_wc_6", DialogueNode.CAELOMUND, new String[]{
            "I am observant. It survived the transformation."
        }, "s2_d_wc_7");
        addNode("s2_d_wc_7", DialogueNode.NARRATOR, new String[]{
            "You move for the trapping wire. The vendor turns around."
        }, "s2_d_wc_8");
        addNode("s2_d_wc_8", DialogueNode.VENDOR, new String[]{
            "Oi. That's from my stall.",
            "Is that the -- that's the goat from earlier --"
        }, "s2_d_wc_9");
        addNode("s2_d_wc_9", DialogueNode.CAELOMUND, new String[]{
            "Whatever you are about to say, do not."
        }, "s2_d_wc_10");
        addNode("s2_d_wc_10", DialogueNode.VENDOR, new String[]{
            "How much for the wire."
        }, "s2_d_wc_11");
        addNode("s2_d_wc_11", DialogueNode.PLAYER, new String[]{
            "Whatever you want for it."
        }, "s2_d_wc_12");
        addNode("s2_d_wc_12", DialogueNode.VENDOR, new String[]{
            "Double. For my nerves."
        }, "s2_d_wc_13");
        addNode("s2_d_wc_13", DialogueNode.CAELOMUND, new String[]{
            "You paid double for wire you were attempting",
            "to take for free."
        }, "s2_d_wc_14");
        addNode("s2_d_wc_14", DialogueNode.PLAYER, new String[]{
            "The plan evolved."
        }, "s2_d_wc_15");
        addNode("s2_d_wc_15", DialogueNode.CAELOMUND, new String[]{
            "...I found the part where I identified",
            "the vendor's return window satisfactory.",
            "That part worked."
        }, "s2_d_wc_16");
        addNode("s2_d_wc_16", DialogueNode.NARRATOR, new String[]{
            "Bold, partially successful, cost more than planned.",
            "[GOAT TRUST +1]"
        }, null); // -> outcome (INN)

        // =============================================================
        // PART 4 — CONVERGENCE: BARN OR INN
        // =============================================================

        // Outcome router — handled by special logic in advanceToNode
        addNode("s2_exec_outcome", DialogueNode.NARRATOR, new String[]{
            "The sky: deep orange at the horizon, blue beginning at the top.",
            "Both of you know what it means."
        }, "s2_outcome_router");

        addNode("s2_outcome_router", DialogueNode.NARRATOR, new String[]{
            "The day is ending."
        }, null); // routed to barn or inn

        // --- BARN OUTCOME ---
        addNode("s2_barn", DialogueNode.CAELOMUND, new String[]{
            "The gate is still open."
        }, "s2_barn_2");

        addNode("s2_barn_2", DialogueNode.PLAYER, new String[]{
            "Then we move now."
        }, "s2_barn_3");

        addNode("s2_barn_3", DialogueNode.NARRATOR, new String[]{
            "You move through the city together. Player leading,",
            "Caelomund beside you. The east gate approaches",
            "in the last amber light.",
            "A guard opens his mouth and then closes it,",
            "deciding he has had enough afternoon.",
            "You pass through."
        }, "s2_barn_4");

        addNode("s2_barn_4", DialogueNode.NARRATOR, new String[]{
            "They walk. The city falls behind them.",
            "The silence between them is not uncomfortable.",
            "It is the silence of people who have been moving",
            "through a tense situation and are now through it."
        }, "s2_barn_5");

        addNode("s2_barn_5", DialogueNode.CAELOMUND, new String[]{
            "I want to say something and I want you to understand",
            "that I am saying it practically, not sentimentally."
        }, "s2_barn_6");

        addNode("s2_barn_6", DialogueNode.CAELOMUND, new String[]{
            "You managed the city well.",
            "The guard, the supplies, the situation as a whole.",
            "I have been in this form for several days.",
            "Before you, I was managing it alone and doing it poorly.",
            "I want to acknowledge that the addition of your competence",
            "has materially improved my position."
        }, "s2_barn_7");

        addNode("s2_barn_7", DialogueNode.PLAYER, new String[]{
            "That is almost a thank you."
        }, "s2_barn_8");

        addNode("s2_barn_8", DialogueNode.CAELOMUND, new String[]{
            "It is a factual assessment."
        }, "s2_barn_9");

        addNode("s2_barn_9", DialogueNode.PLAYER, new String[]{
            "I know. That's why it works as one."
        }, "s2_barn_10");

        addNode("s2_barn_10", DialogueNode.CAELOMUND, new String[]{
            "You are very difficult to fluster."
        }, "s2_barn_11");

        addNode("s2_barn_11", DialogueNode.PLAYER, new String[]{
            "The baseline is: I am not easily flustered."
        }, "s2_barn_12");

        addNode("s2_barn_12", DialogueNode.CAELOMUND, new String[]{
            "Noted."
        }, "s2_barn_13");

        addNode("s2_barn_13", DialogueNode.NARRATOR, new String[]{
            "A farmstead ahead, two miles out. A barn.",
            "Warm light inside — dim, unhurried.",
            "Behind you, Caelomund walks at goat-pace. Steady."
        }, "SCENE_END");

        // --- INN OUTCOME ---
        addNode("s2_inn", DialogueNode.CAELOMUND, new String[]{
            "We missed the window."
        }, "s2_inn_2");

        addNode("s2_inn_2", DialogueNode.PLAYER, new String[]{
            "We need somewhere inside the city."
        }, "s2_inn_3");

        addNode("s2_inn_3", DialogueNode.CAELOMUND, new String[]{
            "I have been in this city for three days",
            "and I have slept in a stable and under a market cart.",
            "None of my lodging options are good."
        }, "s2_inn_4");

        addNode("s2_inn_4", DialogueNode.NARRATOR, new String[]{
            "You find the inn the way one finds these places.",
            "Not by looking, but by following the kind of street",
            "that leads to them.",
            "The sign depicts something that may once have been a horse."
        }, "s2_inn_5");

        addNode("s2_inn_5", DialogueNode.CAELOMUND, new String[]{
            "I do not like the sign."
        }, "s2_inn_6");

        addNode("s2_inn_6", DialogueNode.PLAYER, new String[]{
            "The sign is not our room."
        }, "s2_inn_7");

        addNode("s2_inn_7", DialogueNode.INNKEEPER, new String[]{
            "Room is three copper. The goat sleeps on the floor.",
            "The goat does anything unusual, that is your problem."
        }, "s2_inn_8");

        addNode("s2_inn_8", DialogueNode.CAELOMUND, new String[]{
            "I am a centuries-old archmage."
        }, "s2_inn_9");

        addNode("s2_inn_9", DialogueNode.PLAYER, new String[]{
            "I know."
        }, "s2_inn_10");

        addNode("s2_inn_10", DialogueNode.CAELOMUND, new String[]{
            "I simply want that noted."
        }, "s2_inn_11");

        addNode("s2_inn_11", DialogueNode.PLAYER, new String[]{
            "Noted. Every time."
        }, "s2_inn_12");

        addNode("s2_inn_12", DialogueNode.NARRATOR, new String[]{
            "The room. Small, low, functional.",
            "A window that faces another wall."
        }, "s2_inn_13");

        addNode("s2_inn_13", DialogueNode.CAELOMUND, new String[]{
            "I have slept in a tower of my own construction",
            "for three hundred and twelve years.",
            "This is a different kind of place."
        }, "s2_inn_14");

        addNode("s2_inn_14", DialogueNode.PLAYER, new String[]{
            "Tell me about the tower."
        }, "s2_inn_15");

        addNode("s2_inn_15", DialogueNode.CAELOMUND, new String[]{
            "...Why."
        }, "s2_inn_16");

        addNode("s2_inn_16", DialogueNode.PLAYER, new String[]{
            "Because you mentioned it. Because we have nowhere to be",
            "until morning. Because I want to know what we are walking toward."
        }, "s2_inn_17");

        addNode("s2_inn_17", DialogueNode.CAELOMUND, new String[]{
            "The Solmere Tower-Keep was a hollowed colossal tree.",
            "It was alive when I found it. I spent eleven years converting it.",
            "The library alone took four.",
            "There are rooms that have not been entered",
            "since the second century of my residence."
        }, "s2_inn_18");

        addNode("s2_inn_18", DialogueNode.CAELOMUND, new String[]{
            "Bastian is intelligent. He is also currently running",
            "on desperation and grief and a wand he is not fully trained to use."
        }, "s2_inn_19");

        addNode("s2_inn_19", DialogueNode.CAELOMUND, new String[]{
            "He is someone who felt he had no other option.",
            "That is what I keep returning to.",
            "Not what he did. Why he felt he had no other option.",
            "I have not arrived at a satisfying answer."
        }, null); // -> choice

        addChoice("s2_inn_19",
            new String[]{
                "\"Maybe that's worth having an answer to before we arrive.\"",
                "\"What were the conditions that led to no other option?\"",
                "\"Three days is a long time to think about an unsatisfying answer.\"",
                "\"Three days is a long time to think about an unsatisfying answer.\""
            },
            new String[]{"s2_inn_br_hs", "s2_inn_br_wf", "s2_inn_br_wc", "s2_inn_br_wc"},
            "s2_inn_end",
            false, false,
            new int[]{1, 1, 1, 1},
            new String[]{null, "PLAYER_DOUBT", null, null}
        );

        addNode("s2_inn_br_hs", DialogueNode.PLAYER, new String[]{
            "If we know why he felt cornered,",
            "we might know how to reach him."
        }, "s2_inn_br_hs_r");
        addNode("s2_inn_br_hs_r", DialogueNode.CAELOMUND, new String[]{
            "...Yes. Perhaps."
        }, null);

        addNode("s2_inn_br_wf", DialogueNode.PLAYER, new String[]{
            "Something specific pushed him to this.",
            "What was the state of things between you, before?"
        }, "s2_inn_br_wf_r");
        addNode("s2_inn_br_wf_r", DialogueNode.CAELOMUND, new String[]{
            "The conditions were... complicated.",
            "I will think about how to answer that correctly.",
            "Not tonight."
        }, null);

        addNode("s2_inn_br_wc", DialogueNode.PLAYER, new String[]{
            "Three days is a long time to think",
            "about an unsatisfying answer."
        }, "s2_inn_br_wc_r");
        addNode("s2_inn_br_wc_r", DialogueNode.CAELOMUND, new String[]{
            "That is more optimistic than I feel.",
            "But you may be right."
        }, null);

        addNode("s2_inn_end", DialogueNode.NARRATOR, new String[]{
            "He faces the wall. The room is what it is.",
            "Outside, the city finishes its turn into night."
        }, "SCENE_END");
    }

    /** Convenience method to register a dialogue node. */
    private static void addNode(String id, String speaker, String[] lines, String nextId) {
        NODES.put(id, new DialogueNode(id, speaker, lines, nextId));
    }

    /** Convenience method to register a choice after a specific node. */
    private static void addChoice(String afterNodeId, String[] texts, String[] branchIds,
            String rejoinId, boolean spendsCard, boolean isTutorial,
            int[] points, String[] flags) {
        CHOICES.put(afterNodeId, new DialogueChoice(
            afterNodeId, texts, STD_TYPES, branchIds, rejoinId,
            spendsCard, isTutorial, points, flags
        ));
    }

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    public Scene2Pane(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
    }

    // =========================================================
    // LIFECYCLE
    // =========================================================

    @Override
    public void showContent() {
        hoveredChoice = -1;
        choiceBoxes.clear();
        dialogueElements.clear();
        obstacle1Location = null;

        drawBackground();
        addSceneDebugBanner();
        drawDialogueBox();
        addSettingsCornerButton();
        showPlayerHUD(mainScreen.getGameState().getPlayer());

        GameState gs = mainScreen.getGameState();

        // Restore obstacle1Location from flags if resuming
        if (gs.hasFlag("S2_LOC_A")) obstacle1Location = "LOC_A";
        else if (gs.hasFlag("S2_LOC_B")) obstacle1Location = "LOC_B";
        else if (gs.hasFlag("S2_LOC_C")) obstacle1Location = "LOC_C";
        else if (gs.hasFlag("S2_LOC_D")) obstacle1Location = "LOC_D";

        String ck = gs.getScene2NodeId();
        if (ck != null && !ck.isEmpty() && NODES.containsKey(ck)) {
            restoreFromGameState(gs);
        } else {
            currentRejoinId = null;
            advanceToNode("s2_opening");
            // Fade in from black on fresh entry (not save restore)
            startFadeIn();
        }
    }

    @Override
    public void hideContent() {
        fadingIn = false;
        fadeOverlay = null;
        syncCheckpointToGameState();
        for (GObject item : contents) {
            mainScreen.remove(item);
        }
        contents.clear();
        choiceBoxes.clear();
        dialogueElements.clear();
        showingChoice = false;
        hoveredChoice = -1;
    }

    // =========================================================
    // BACKGROUND & STATIC UI
    // =========================================================

    private void addSceneDebugBanner() {
        if (!DEBUG_SCENE_BANNER) {
            return;
        }
        GLabel dbg = pixelLabel("[DEBUG] Scene #2", 10, C_DEBUG_BANNER);
        dbg.setLocation(originX() + (scaleX(8) - scaleX(0)), scaleY(12));
        place(dbg);
    }

    private void drawBackground() {
        place(rect(0, 0, mainScreen.getWidth(), mainScreen.getHeight(), C_BG, C_BG));
        // Alley strip
        place(srect(0, 100, 700, 120, C_ALLEY, C_ALLEY));
        // Player sprite placeholder
        drawSpriteRect(PLAYER_SPRITE_X, PLAYER_SPRITE_Y, 100, 140,
            C_PLAYER_SPRITE, mainScreen.getGameState().getPlayerName());
    }

    private void drawSpriteRect(double lx, double ly, double lw, double lh,
                                Color color, String label) {
        place(srect(lx, ly, lw, lh, color, color.darker()));
        GLabel nameLbl = pixelLabel(label, 9, color.brighter());
        double cx = scaleX(lx) + (scaleX(lx + lw) - scaleX(lx) - nameLbl.getWidth()) / 2.0;
        nameLbl.setLocation(cx, scaleY(ly + lh + 14));
        place(nameLbl);
    }

    private void drawDialogueBox() {
        place(srect(DBOX_X, DBOX_Y, DBOX_W, DBOX_H, C_DBOX_BG, C_DBOX_BORDER));
    }

    // =========================================================
    // NPC SPRITE MANAGEMENT
    // =========================================================

    private final List<GObject> npcSpriteElements = new ArrayList<>();

    private void updateNpcSprite(String nodeId) {
        for (GObject obj : npcSpriteElements) {
            mainScreen.remove(obj);
            contents.remove(obj);
        }
        npcSpriteElements.clear();

        if (nodeId.startsWith("s2_loc_a") || nodeId.startsWith("s2_a_")) {
            // Location A: Maret + Caelomund
            addNpcSprite(NPC_SPRITE_X, NPC_SPRITE_Y, 100, 140, C_MARET_SPRITE, "MARET");
            addNpcSprite(NPC_SPRITE_X + 120, NPC_SPRITE_Y + 40, 60, 80, C_CAEL_SPRITE, "CAELOMUND");
        } else if (nodeId.startsWith("s2_loc_b") || nodeId.startsWith("s2_b_")) {
            // Location B: Drev + Caelomund
            addNpcSprite(NPC_SPRITE_X, NPC_SPRITE_Y, 100, 140, C_DREV_SPRITE, "DREV");
            addNpcSprite(NPC_SPRITE_X + 120, NPC_SPRITE_Y + 40, 60, 80, C_CAEL_SPRITE, "CAELOMUND");
        } else if (nodeId.startsWith("s2_loc_c") || nodeId.startsWith("s2_c_")) {
            // Location C: Oret + Caelomund
            addNpcSprite(NPC_SPRITE_X, NPC_SPRITE_Y, 100, 140, C_ORET_SPRITE, "ORET");
            addNpcSprite(NPC_SPRITE_X + 120, NPC_SPRITE_Y + 40, 60, 80, C_CAEL_SPRITE, "CAELOMUND");
        } else if (nodeId.startsWith("s2_loc_d") || nodeId.startsWith("s2_d_")) {
            // Location D: Market recovery — just Caelomund
            addNpcSprite(NPC_SPRITE_X, NPC_SPRITE_Y + 40, 80, 100, C_CAEL_SPRITE, "CAELOMUND");
        } else if (nodeId.startsWith("s2_inn_") && !nodeId.equals("s2_inn_br_hs")
                && !nodeId.equals("s2_inn_br_wf") && !nodeId.equals("s2_inn_br_wc")
                && !nodeId.equals("s2_inn_br_hs_r") && !nodeId.equals("s2_inn_br_wf_r")
                && !nodeId.equals("s2_inn_br_wc_r") && !nodeId.equals("s2_inn_end")) {
            // Inn: Innkeeper + Caelomund (early), then just Caelomund (room)
            if (nodeId.compareTo("s2_inn_12") < 0) {
                addNpcSprite(NPC_SPRITE_X, NPC_SPRITE_Y, 100, 140, C_INNKEEPER_SPRITE, "INNKEEPER");
                addNpcSprite(NPC_SPRITE_X + 120, NPC_SPRITE_Y + 40, 60, 80, C_CAEL_SPRITE, "CAELOMUND");
            } else {
                addNpcSprite(NPC_SPRITE_X, NPC_SPRITE_Y + 40, 80, 100, C_CAEL_SPRITE, "CAELOMUND");
            }
        } else {
            // Default: Caelomund only (alley, debrief, etc.)
            addNpcSprite(NPC_SPRITE_X, NPC_SPRITE_Y + 40, 80, 100, C_CAEL_SPRITE, "CAELOMUND");
        }
    }

    private void addNpcSprite(double lx, double ly, double lw, double lh,
                              Color color, String label) {
        GRect sprite = srect(lx, ly, lw, lh, color, color.darker());
        npcSpriteElements.add(sprite);
        place(sprite);
        GLabel nameLbl = pixelLabel(label, 8, color.brighter());
        double cx = scaleX(lx) + (scaleX(lx + lw) - scaleX(lx) - nameLbl.getWidth()) / 2.0;
        nameLbl.setLocation(cx, scaleY(ly + lh + 12));
        npcSpriteElements.add(nameLbl);
        place(nameLbl);
    }

    // =========================================================
    // DIALOGUE ENGINE
    // =========================================================

    private void advanceToNode(String nodeId) {
        if ("SCENE_END".equals(nodeId)) {
            endScene();
            return;
        }

        // --- ROUTING LOGIC ---
        // After Part 2, route to correct location for Part 3
        if ("s2_exec_router".equals(nodeId)) {
            // Show the router node text first, then route on next click
            currentNodeId = nodeId;
            currentLineIndex = 0;
            showingChoice = false;
            activeChoice = null;
            updateNpcSprite(nodeId);
            renderDialogueLine();
            return;
        }

        // After Part 3, route to barn or inn
        if ("s2_outcome_router".equals(nodeId)) {
            currentNodeId = nodeId;
            currentLineIndex = 0;
            showingChoice = false;
            activeChoice = null;
            updateNpcSprite(nodeId);
            renderDialogueLine();
            return;
        }

        // Scroll conditional — skip scroll dialogue if scroll was lost
        if ("s2_scroll_check".equals(nodeId)) {
            GameState gs = mainScreen.getGameState();
            if (gs.hasFlag("SCROLL_LOST")) {
                advanceToNode("s2_plan_prompt");
                return;
            }
        }

        currentNodeId = nodeId;
        currentLineIndex = 0;
        showingChoice = false;
        activeChoice = null;
        updateNpcSprite(nodeId);
        renderDialogueLine();
    }

    /**
     * Routes from the exec_router to the appropriate location.
     */
    private String getLocationNodeForFlag() {
        if ("LOC_A".equals(obstacle1Location)) return "s2_loc_a_intro";
        if ("LOC_B".equals(obstacle1Location)) return "s2_loc_b_intro";
        if ("LOC_C".equals(obstacle1Location)) return "s2_loc_c_intro";
        if ("LOC_D".equals(obstacle1Location)) return "s2_loc_d_intro";
        return "s2_loc_a_intro"; // fallback
    }

    /**
     * Routes from outcome_router to barn or inn.
     */
    private String getOutcomeNode() {
        GameState gs = mainScreen.getGameState();
        if (gs.hasFlag("REST_INN")) return "s2_inn";
        return "s2_barn"; // default to barn
    }

    private void renderDialogueLine() {
        clearDialogueElements();

        DialogueNode node = NODES.get(currentNodeId);
        if (node == null) return;

        String speaker = node.getSpeaker();
        if (speaker != null) {
            String displayName = resolveDisplaySpeaker(speaker);
            Color speakerColor = getSpeakerColor(speaker);
            GLabel speakerLbl = pixelLabel(displayName + ":", 11, speakerColor);
            speakerLbl.setLocation(scaleX(SPEAKER_X), scaleY(SPEAKER_Y));
            addDialogueElement(speakerLbl);
        }

        String rawLine = node.getLines()[currentLineIndex];
        String line = replaceTokens(rawLine);
        List<String> wrapped = wrapText(line, TEXT_WRAP_CHARS);

        double y = scaleY(TEXT_Y);
        double lineGap = scaleY(TEXT_LINE_GAP) - scaleY(0);
        for (String segment : wrapped) {
            GLabel textLbl = pixelLabel(segment, 10, C_TEXT);
            textLbl.setLocation(scaleX(TEXT_X), y);
            addDialogueElement(textLbl);
            y += lineGap;
        }

        GLabel cont = pixelLabel(">>>", 9, C_CONTINUE);
        cont.setLocation(scaleX(CONT_X), scaleY(CONT_Y));
        addDialogueElement(cont);

        syncCheckpointToGameState();
    }

    private void showChoiceButtons(DialogueChoice choice) {
        clearDialogueElements();
        choiceBoxes.clear();
        hoveredChoice = -1;
        showingChoice = true;
        activeChoice = choice;

        String[] texts = choice.getOptionTexts();
        CardType[] types = choice.getOptionTypes();

        for (int i = 0; i < texts.length; i++) {
            double logY = CHOICE_Y0 + i * (CHOICE_H + CHOICE_GAP);
            double bx = scaleX(CHOICE_X);
            double by = scaleY(logY);
            double bw = scaleX(CHOICE_X + CHOICE_W) - bx;
            double bh = scaleY(logY + CHOICE_H) - by;

            addDialogueElement(rect(bx - 1, by - 1, bw + 2, bh + 2, C_BTN_BORDER, C_BTN_BORDER));

            GRect box = rect(bx, by, bw, bh, C_BTN_BG, C_BTN_BG);
            addDialogueElement(box);
            choiceBoxes.add(box);

            GLabel optLbl = pixelLabel(texts[i], 9, C_BTN_TEXT);
            double textY = by + (bh + optLbl.getAscent()) / 2.0 - optLbl.getDescent() / 2.0;
            optLbl.setLocation(bx + scaleX(10) - scaleX(0), textY);
            addDialogueElement(optLbl);

            int typeIndex = types[i].ordinal();
            String tag = "[" + types[i].name().substring(0, 2) + "]";
            GLabel tagLbl = pixelLabel(tag, 8, TYPE_COLORS[typeIndex]);
            tagLbl.setLocation(bx + bw - tagLbl.getWidth() - (scaleX(8) - scaleX(0)), textY);
            addDialogueElement(tagLbl);
        }
        syncCheckpointToGameState();
    }

    // =========================================================
    // CHOICE HANDLING
    // =========================================================

    private void handleChoiceSelected(int index) {
        if (activeChoice == null) return;

        DialogueChoice choice = activeChoice;
        CardType chosenType = choice.getOptionTypes()[index];

        // Award archetype points
        int points = choice.getArchetypePoints()[index];
        mainScreen.getGameState().addArchetypePoints(chosenType, points);

        // Set narrative flags
        String flagStr = choice.getFlagChanges()[index];
        if (flagStr != null) {
            for (String flag : flagStr.split(",")) {
                String trimmed = flag.trim();
                mainScreen.getGameState().setFlag(trimmed);

                // Track obstacle 1 location choice
                if (trimmed.equals("S2_LOC_A")) obstacle1Location = "LOC_A";
                else if (trimmed.equals("S2_LOC_B")) obstacle1Location = "LOC_B";
                else if (trimmed.equals("S2_LOC_C")) obstacle1Location = "LOC_C";
                else if (trimmed.equals("S2_LOC_D")) obstacle1Location = "LOC_D";
            }
        }

        // Handle card spend
        if (choice.spendsCard() && !choice.isTutorial()) {
            Hand hand = mainScreen.getPlayer().getHand();
            if (!hand.isEmpty()) {
                int cardIdx = -1;
                List<Card> cards = hand.getCards();
                for (int i = 0; i < cards.size(); i++) {
                    if (cards.get(i).getType() == chosenType) {
                        cardIdx = i;
                        break;
                    }
                }
                if (cardIdx < 0) cardIdx = 0;
                Card spent = hand.removeCard(cardIdx);
                System.out.println("[Scene 2] Card consumed: " + spent.getName()
                    + " (" + spent.getType() + ")");
            }
        }

        // Debug output
        System.out.println("[Scene 2] Choice: " + chosenType
            + " | +" + points + " archetype"
            + (flagStr != null ? " | flags: " + flagStr : ""));

        String branchNodeId = choice.getBranchNodeIds()[index];
        String rejoinId = choice.getRejoinNodeId();
        showingChoice = false;
        activeChoice = null;
        choiceBoxes.clear();

        currentRejoinId = rejoinId;
        advanceToNode(branchNodeId);
    }

    // =========================================================
    // MOUSE EVENTS
    // =========================================================

    @Override
    public void mouseClicked(MouseEvent e) {
        if (fadingIn) return;

        double x = e.getX(), y = e.getY();

        if (showingChoice) {
            for (int i = 0; i < choiceBoxes.size(); i++) {
                if (choiceBoxes.get(i).contains(x, y)) {
                    handleChoiceSelected(i);
                    return;
                }
            }
            return;
        }

        DialogueNode node = NODES.get(currentNodeId);
        if (node == null) return;

        currentLineIndex++;
        if (currentLineIndex < node.getLines().length) {
            renderDialogueLine();
        } else {
            // Special routing for router nodes
            if ("s2_exec_router".equals(currentNodeId)) {
                advanceToNode(getLocationNodeForFlag());
                return;
            }
            if ("s2_outcome_router".equals(currentNodeId)) {
                advanceToNode(getOutcomeNode());
                return;
            }

            String nextId = node.getNextNodeId();
            if (nextId != null) {
                advanceToNode(nextId);
            } else if (CHOICES.containsKey(currentNodeId)) {
                showChoiceButtons(CHOICES.get(currentNodeId));
            } else if (currentRejoinId != null) {
                String rejoin = currentRejoinId;
                currentRejoinId = null;
                advanceToNode(rejoin);
            } else {
                endScene();
            }
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (!showingChoice) return;

        int newHover = -1;
        for (int i = 0; i < choiceBoxes.size(); i++) {
            if (choiceBoxes.get(i).contains(e.getX(), e.getY())) {
                newHover = i;
                break;
            }
        }
        if (newHover != hoveredChoice) {
            hoveredChoice = newHover;
            for (int i = 0; i < choiceBoxes.size(); i++) {
                choiceBoxes.get(i).setFillColor(i == hoveredChoice ? C_BTN_HOVER : C_BTN_BG);
            }
        }
    }

    // =========================================================
    // SCENE TRANSITION
    // =========================================================

    private void endScene() {
        System.out.println("[Scene 2] Scene complete!");
        mainScreen.getGameState().clearScene2Checkpoint();
        mainScreen.autosaveIfSlotActive();
        currentNodeId = null;
        mainScreen.getGameState().printDebugSummary();
        // Transition to Resting Scene 1
        mainScreen.switchToRestingScene1Screen();
    }

    private void restoreFromGameState(GameState gs) {
        String id = gs.getScene2NodeId();
        DialogueNode node = NODES.get(id);
        if (node == null) {
            currentRejoinId = null;
            advanceToNode("s2_opening");
            return;
        }
        currentNodeId = id;
        currentLineIndex = gs.getScene2LineIndex();
        currentRejoinId = gs.getScene2RejoinId();

        if (gs.isScene2ShowingChoice()) {
            if (!CHOICES.containsKey(id)) {
                currentRejoinId = null;
                advanceToNode("s2_opening");
                return;
            }
            showingChoice = false;
            activeChoice = null;
            choiceBoxes.clear();
            updateNpcSprite(id);
            showChoiceButtons(CHOICES.get(id));
            return;
        }

        showingChoice = false;
        activeChoice = null;
        choiceBoxes.clear();
        String[] lines = node.getLines();
        if (lines.length > 0 && currentLineIndex >= lines.length) {
            currentLineIndex = lines.length - 1;
        }
        if (lines.length == 0) {
            currentLineIndex = 0;
        }
        updateNpcSprite(id);
        renderDialogueLine();
    }

    private void syncCheckpointToGameState() {
        if (currentNodeId == null) return;
        GameState gs = mainScreen.getGameState();
        gs.updateScene2Checkpoint(
            currentNodeId,
            currentLineIndex,
            showingChoice,
            currentRejoinId != null ? currentRejoinId : "");
        mainScreen.autosaveIfSlotActive();
    }

    // =========================================================
    // FADE-IN FROM BLACK
    // =========================================================

    private void startFadeIn() {
        fadeOverlay = new GRect(0, 0, mainScreen.getWidth(), mainScreen.getHeight());
        fadeOverlay.setFilled(true);
        fadeOverlay.setFillColor(new Color(0, 0, 0, 255));
        fadeOverlay.setColor(new Color(0, 0, 0, 0));
        contents.add(fadeOverlay);
        mainScreen.add(fadeOverlay);
        fadingIn = true;

        new Thread(() -> {
            try {
                // Brief hold on black before fade begins
                Thread.sleep(200);
                for (int frame = 1; frame <= FADE_IN_FRAMES; frame++) {
                    if (!fadingIn) return;
                    double t = (double) frame / FADE_IN_FRAMES;
                    int alpha = (int) ((1.0 - t) * 255);
                    Color c = new Color(0, 0, 0, alpha);
                    fadeOverlay.setFillColor(c);
                    fadeOverlay.setColor(new Color(0, 0, 0, 0));
                    Thread.sleep(FADE_IN_FRAME_MS);
                }
                // Remove overlay when done
                if (fadingIn) {
                    mainScreen.remove(fadeOverlay);
                    contents.remove(fadeOverlay);
                    fadeOverlay = null;
                    fadingIn = false;
                }
            } catch (InterruptedException e) {
                // clean exit
            }
        }).start();
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private void addDialogueElement(GObject obj) {
        dialogueElements.add(obj);
        place(obj);
    }

    private void clearDialogueElements() {
        for (GObject obj : dialogueElements) {
            mainScreen.remove(obj);
            contents.remove(obj);
        }
        dialogueElements.clear();
        choiceBoxes.clear();
    }

    private String resolveDisplaySpeaker(String speaker) {
        if (DialogueNode.PLAYER.equals(speaker)) {
            return mainScreen.getGameState().getPlayerName().toUpperCase();
        }
        return speaker;
    }

    private Color getSpeakerColor(String speaker) {
        switch (speaker) {
            case DialogueNode.CAELOMUND: return C_CAELOMUND;
            case DialogueNode.PLAYER:    return C_PLAYER_CLR;
            case DialogueNode.MARET:     return C_MARET;
            case DialogueNode.DREV:      return C_DREV;
            case DialogueNode.ORET:      return C_ORET;
            case DialogueNode.INNKEEPER: return C_INNKEEPER;
            case DialogueNode.VENDOR:    return C_VENDOR;
            default:                     return C_NARRATOR;
        }
    }

    private String replaceTokens(String text) {
        return text
            .replace("[NAME]", mainScreen.getGameState().getPlayerName())
            .replace("[PROFESSION]", mainScreen.getGameState().getPlayerProfession());
    }
}
