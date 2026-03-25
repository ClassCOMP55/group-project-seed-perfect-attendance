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
 * Scene 1 — The Market: A Chance Encounter.
 *
 * This is the game's first narrative scene, following character creation.
 * The player arrives mid-transaction at an open-air market, encounters
 * Caelomund the goat wizard, and must deal with a pursuing construct.
 *
 * The scene is driven by a dialogue engine that presents text line-by-line
 * (click to advance) with branching choices at 6 decision points. The
 * engine reads from {@link DialogueNode} and {@link DialogueChoice} data
 * objects defined in the static data section below.
 *
 * <b>4 Parts:</b>
 * <ol>
 *   <li>The Transaction — tutorial card interaction with vendor Oret</li>
 *   <li>The Goat Introduces Himself — Caelomund enters, two choices</li>
 *   <li>The Decision Point — central choice, real card spend</li>
 *   <li>Convergence: The Alley — closing choices, transition to Scene 2</li>
 * </ol>
 *
 * Visual placeholders (colored rectangles) are used for all character
 * sprites and the market background. These are marked with TODO comments
 * for the graphics team to replace with real assets.
 *
 * @see DialogueNode
 * @see DialogueChoice
 * @see GameState
 */
public class Scene1Pane extends GraphicsPane {

    /** Set {@code false} to hide the top-left scene banner. */
    private static final boolean DEBUG_SCENE_BANNER = true;
    private static final Color C_DEBUG_BANNER = new Color(100, 220, 160);

    // =========================================================
    // COLOUR PALETTE
    // =========================================================

    // -- Background --
    /** Market background base colour (dark, atmospheric). */
    private static final Color C_BG           = new Color(22, 18, 32);
    /** Market stall strip colour (lighter band across middle). */
    private static final Color C_STALLS       = new Color(45, 38, 58);

    // -- Dialogue box --
    /** Dialogue box fill (semi-transparent dark). */
    private static final Color C_DBOX_BG      = new Color(15, 12, 25, 220);
    /** Dialogue box border. */
    private static final Color C_DBOX_BORDER  = new Color(100, 80, 140);
    /** Main dialogue text colour. */
    private static final Color C_TEXT         = new Color(220, 220, 235);
    /** Continue indicator colour. */
    private static final Color C_CONTINUE    = new Color(140, 130, 170);

    // -- Speaker name colours --
    private static final Color C_NARRATOR    = new Color(150, 145, 170);
    private static final Color C_ORET        = new Color(220, 170, 80);
    private static final Color C_CAELOMUND   = new Color(80, 200, 120);
    private static final Color C_PLAYER_NAME = new Color(130, 170, 220);

    // -- Choice buttons (reuses CharacterCreationPane pattern) --
    private static final Color C_BTN_BG      = new Color(28, 40, 78);
    private static final Color C_BTN_HOVER   = new Color(52, 68, 118);
    private static final Color C_BTN_BORDER  = new Color(68, 82, 135);
    private static final Color C_BTN_TEXT    = new Color(210, 210, 230);

    // Fade timing
    private static final int FADE_IN_FRAMES = 40;
    private static final int FADE_IN_FRAME_MS = 30;
    private static final int FADE_OUT_FRAMES = 40;
    private static final int FADE_OUT_FRAME_MS = 30;

    // -- Card type tag colours (same order as CardType enum) --
    private static final Color[] TYPE_COLORS = {
        new Color(0,   180, 216),  // WAYFINDER     — cyan
        new Color(199, 125, 255),  // SILVER_TONGUE — purple
        new Color(255, 107, 107),  // HEARTSEEKER   — coral
        new Color(255, 209, 102)   // WILDCARD      — gold
    };

    // -- Sprite placeholder colours --
    private static final Color C_ORET_SPRITE      = new Color(180, 140, 60);
    private static final Color C_CAEL_SPRITE      = new Color(60, 160, 90);
    private static final Color C_CONSTRUCT_SPRITE = new Color(160, 50, 50);
    private static final Color C_PLAYER_SPRITE    = new Color(80, 110, 160);

    // =========================================================
    // LAYOUT CONSTANTS (logical 700x500 design space)
    // =========================================================

    // -- Dialogue box --
    private static final double DBOX_X  = 30;
    private static final double DBOX_Y  = 320;
    private static final double DBOX_W  = 640;
    private static final double DBOX_H  = 160;

    // -- Text inside dialogue box --
    private static final double SPEAKER_X = 45;
    private static final double SPEAKER_Y = 340;
    private static final double TEXT_X    = 45;
    private static final double TEXT_Y    = 362;
    private static final double TEXT_LINE_GAP = 20;
    private static final int    TEXT_WRAP_CHARS = 62;

    // -- Continue indicator --
    private static final double CONT_X = 630;
    private static final double CONT_Y = 465;

    // -- Choice buttons --
    private static final double CHOICE_X  = 40;
    private static final double CHOICE_W  = 620;
    private static final double CHOICE_H  = 32;
    private static final double CHOICE_Y0 = 328;
    private static final double CHOICE_GAP = 6;

    // -- Sprite positions --
    private static final double NPC_SPRITE_X = 60;
    private static final double NPC_SPRITE_Y = 60;
    private static final double PLAYER_SPRITE_X = 520;
    private static final double PLAYER_SPRITE_Y = 60;

    // =========================================================
    // DIALOGUE STATE
    // =========================================================

    /** The id of the currently active dialogue node. */
    private String currentNodeId;

    /** Index of the line currently displayed within the active node. */
    private int currentLineIndex;

    /** True when choice buttons are displayed instead of dialogue text. */
    private boolean showingChoice;

    /** The active choice object (non-null only when showingChoice is true). */
    private DialogueChoice activeChoice;

    /** Hit rectangles for choice buttons — used for click detection. */
    private final List<GRect> choiceBoxes = new ArrayList<>();

    /** Index of the hovered choice button, or -1 if none. */
    private int hoveredChoice = -1;

    // -- GObject references for elements that get updated frequently --
    /** All GObjects in the dialogue box area (cleared on each line advance). */
    private final List<GObject> dialogueElements = new ArrayList<>();

    /** True while the fade-from-black overlay is animating on entry. */
    private volatile boolean fadingIn = false;
    /** True while the fade-to-black overlay is animating on exit. */
    private volatile boolean fadingOut = false;
    /** Full-screen overlay for fade transitions. */
    private GRect fadeOverlay;

    // =========================================================
    // DIALOGUE DATA — all nodes and choices for the scene
    // =========================================================

    /** All dialogue nodes indexed by id for fast lookup. */
    private static final Map<String, DialogueNode> NODES = new LinkedHashMap<>();

    /** All choices indexed by the node id they follow. */
    private static final Map<String, DialogueChoice> CHOICES = new LinkedHashMap<>();

    // Standard option types array — same order for every choice
    private static final CardType[] STD_TYPES = {
        CardType.WAYFINDER, CardType.SILVER_TONGUE,
        CardType.HEARTSEEKER, CardType.WILDCARD
    };

    /*
     * Static initializer — populates all dialogue nodes and choices.
     * Organized by Part (matching the script structure).
     */
    static {
        // =============================================================
        // PART 1 — THE TRANSACTION (Tutorial)
        // =============================================================

        // -- Opening narration --
        addNode("p1_opening", DialogueNode.NARRATOR, new String[]{
            "The market buzzes around you — stalls crowded, vendors calling out prices.",
            "You are mid-transaction with ORET, a stout vendor of general supplies.",
            "He has seen everything and is moved by none of it."
        }, "p1_oret_intro");

        // -- Oret speaks --
        addNode("p1_oret_intro", DialogueNode.ORET, new String[]{
            "That's what I said the first time. Price is the price.",
            "You want it or you leaving?"
        }, "p1_tutorial_prompt");

        // -- Tutorial prompt (triggers the tutorial choice) --
        addNode("p1_tutorial_prompt", DialogueNode.NARRATOR, new String[]{
            "[TUTORIAL: This is your first card interaction. Choose a response below.",
            "Your card will be returned after this tutorial choice.]"
        }, null); // null nextNodeId = choice follows

        // Tutorial choice — card spent but returned (isTutorial = true)
        addChoice("p1_tutorial_prompt",
            new String[]{
                "\"What's it actually worth to you if I walk away?\"",
                "\"Come on. We both know that number has some flex in it.\"",
                "\"I'm not trying to take from you. I just need to know if there's room.\"",
                "\"What if I just left something here as a deposit and came back?\""
            },
            new String[]{"p1_br_wf", "p1_br_st", "p1_br_hs", "p1_br_wc"},
            "p1_rejoin",
            true,  // spendsCard
            true,  // isTutorial — card returned
            new int[]{2, 2, 2, 2},
            new String[]{null, null, null, null}
        );

        // -- Part 1 branches --
        addNode("p1_br_wf", DialogueNode.PLAYER, new String[]{
            "I have been in enough markets to know when a price is softening.",
            "You want to move this today. So let us find the number where we both go home satisfied."
        }, "p1_br_wf_resp");
        addNode("p1_br_wf_resp", DialogueNode.ORET, new String[]{
            "...You know your stuff. Fine.",
            "I'm too tired to negotiate with that much effort today. Here you go."
        }, null); // -> rejoin

        addNode("p1_br_st", DialogueNode.PLAYER, new String[]{
            "Look — I like your stall. I like you.",
            "I would like to give you my coin. Help me do that."
        }, "p1_br_st_resp");
        addNode("p1_br_st_resp", DialogueNode.ORET, new String[]{
            "Ha! You're a shrewd one. You know what, I cannot even be annoyed at that.",
            "Alright. Knock two off the top. Final.",
            "Just keep this \"favor\" in mind if I need something from you."
        }, null); // -> rejoin

        addNode("p1_br_hs", DialogueNode.PLAYER, new String[]{
            "I am not here to haggle you into the ground.",
            "I just need to make this work with what I have. Is there anything we can do?"
        }, "p1_br_hs_resp");
        addNode("p1_br_hs_resp", DialogueNode.ORET, new String[]{
            "...Yeah. Times are tough on us all.",
            "I can't give it to you for what you want, but let me give you some of what",
            "you want and a couple of things you didn't ask for, on me.",
            "My heart goes out to you, but I can only do so much, friend."
        }, null); // -> rejoin

        addNode("p1_br_wc", DialogueNode.PLAYER, new String[]{
            "Hypothetically. If something urgent came up and I had to be elsewhere for a bit —",
            "what would you need from me to hold this?"
        }, "p1_br_wc_resp");
        addNode("p1_br_wc_resp", DialogueNode.ORET, new String[]{
            "That's not how this works.",
            "But what you're offering is too tempting for me to pass up on.",
            "Leave it here, take half of what you want.",
            "I'll hold this for a couple days and pray something happens to you, heh heh."
        }, null); // -> rejoin

        // -- Rejoin: commotion begins --
        addNode("p1_rejoin", DialogueNode.NARRATOR, new String[]{
            "The transaction resolves. You pocket your goods."
        }, "p1_commotion");

        // TODO [AUDIO]: Play crash SFX here
        addNode("p1_commotion", DialogueNode.NARRATOR, new String[]{
            "A distant crash. The sound of a stall toppling. Startled market-goers.",
            "The commotion is getting closer."
        }, "p1_oret_react");

        addNode("p1_oret_react", DialogueNode.ORET, new String[]{
            "What in the —"
        }, "p2_goat_enters");

        // =============================================================
        // PART 2 — THE GOAT INTRODUCES HIMSELF
        // =============================================================

        // TODO [GRAPHICS]: Show Caelomund sprite entering from right side
        addNode("p2_goat_enters", DialogueNode.NARRATOR, new String[]{
            "A goat barrels into the scene — moving with purpose.",
            "He carries a rolled scroll clamped in his mouth.",
            "He skids to a stop directly in front of you, breathing hard,",
            "eyes scanning you with focused, calculating intelligence.",
            "A beat. The goat drops the scroll from his mouth."
        }, "p2_cael_intro");

        addNode("p2_cael_intro", DialogueNode.CAELOMUND, new String[]{
            "You. Do not move.",
            "I am Caelomund Vaen Solmere. Archmage of the Seventh Meridian,",
            "Senior Fellow of the Aelindric Conclave, Keeper of the Solmere Tower-Keep,",
            "and I am currently —"
        }, "p2_crash");

        // TODO [AUDIO]: Play louder crash SFX
        addNode("p2_crash", DialogueNode.NARRATOR, new String[]{
            "Another crash, louder. Something heavy hitting the ground two stalls over."
        }, "p2_cael_haste");

        addNode("p2_cael_haste", DialogueNode.CAELOMUND, new String[]{
            "— in moderate haste. I require an escort. You will do."
        }, null); // choice follows

        // Reactive choice — no card spend, +1 archetype
        addChoice("p2_cael_haste",
            new String[]{
                "\"...A talking goat.\"",
                "\"I charge for escort work. Just so we're clear.\"",
                "\"Are you alright? You look like you've been running.\"",
                "\"Sure. Where are we going?\""
            },
            new String[]{"p2_br_wf", "p2_br_st", "p2_br_hs", "p2_br_wc"},
            "p2_rejoin1",
            false, false, // no card spend
            new int[]{1, 1, 1, 1},
            new String[]{null, null, null, null}
        );

        // -- Part 2 branches (react to goat) --
        addNode("p2_br_wf", DialogueNode.PLAYER, new String[]{
            "Well, what's going on with you? A talking goat with manners and a story.",
            "Haughty air, heavy breathing. I want to confirm what I am seeing."
        }, "p2_br_wf_resp");
        addNode("p2_br_wf_resp", DialogueNode.CAELOMUND, new String[]{
            "Your eyes function correctly, yes. Good.",
            "That is already more than I expected from someone standing near this stall."
        }, null); // -> rejoin

        addNode("p2_br_st", DialogueNode.PLAYER, new String[]{
            "Bold intro. Love it. But let us talk terms before I go anywhere."
        }, "p2_br_st_resp");
        addNode("p2_br_st_resp", DialogueNode.CAELOMUND, new String[]{
            "Compensation. Yes. We will discuss that at a time when I am not",
            "being inconvenienced. Which is not now."
        }, null); // -> rejoin

        addNode("p2_br_hs", DialogueNode.PLAYER, new String[]{
            "You're breathing hard. Whatever is back there — is it getting closer?"
        }, "p2_br_hs_resp");
        addNode("p2_br_hs_resp", DialogueNode.CAELOMUND, new String[]{
            "I am not alright. I am a centuries-old archmage in the body of a livestock animal.",
            "But I appreciate that you noticed something is wrong.",
            "That is, apparently, not universal."
        }, null); // -> rejoin

        addNode("p2_br_wc", DialogueNode.PLAYER, new String[]{
            "You know what, a talking goat needs an escort, I'm the person in the market.",
            "Let's go. Direction?"
        }, "p2_br_wc_resp");
        addNode("p2_br_wc_resp", DialogueNode.CAELOMUND, new String[]{
            "...You are either exceptionally decisive or exceptionally unobservant.",
            "I will determine which as we walk."
        }, null); // -> rejoin

        // -- Rejoin: Caelomund explains his story --
        addNode("p2_rejoin1", DialogueNode.CAELOMUND, new String[]{
            "My keep. To the northeast, past the river and through the Thornwood.",
            "Three days on foot, perhaps two if you maintain a reasonable pace."
        }, "p2_story");

        addNode("p2_story", DialogueNode.CAELOMUND, new String[]{
            "My apprentice — Bastian Myrwick — has, for reasons I can only attribute",
            "to sudden and spectacular madness, decided to polymorph me,",
            "seize my tower, and refuse to answer correspondence.",
            "I have been in this form for days.",
            "He threw a fit around lunchtime. I thought nothing of it.",
            "By dinner, he had apparently decided that this was grounds for a coup.",
            "The Wand of Polymorph was mine, for the record.",
            "He has not earned the right to use it unsupervised."
        }, null); // choice follows

        // Story response — no card spend, +2 archetype, sets trust/doubt flags
        addChoice("p2_story",
            new String[]{
                "\"A fit about what, exactly?\"",
                "\"Alright. What's in it for me?\"",
                "\"Have you tried talking to him?\"",
                "\"And you picked me out of this whole market. Flattering.\""
            },
            new String[]{"p2_story_br_wf", "p2_story_br_st", "p2_story_br_hs", "p2_story_br_wc"},
            "p2_rejoin2",
            false, false, // no card spend
            new int[]{2, 2, 2, 2},
            new String[]{"PLAYER_DOUBT", "GOAT_TRUST", "PLAYER_DOUBT", "GOAT_TRUST"}
        );

        // -- Story branches --
        addNode("p2_story_br_wf", DialogueNode.PLAYER, new String[]{
            "People do not generally polymorph their teachers over nothing.",
            "What was the fit about?"
        }, "p2_story_br_wf_resp");
        addNode("p2_story_br_wf_resp", DialogueNode.CAELOMUND, new String[]{
            "The fit was about the pace of his instruction.",
            "He expressed the opinion that I was moving too slowly.",
            "I expressed the opinion that I had been teaching for four centuries",
            "and knew the appropriate pace. The conversation ended. Or so I believed."
        }, null); // -> rejoin

        addNode("p2_story_br_st", DialogueNode.PLAYER, new String[]{
            "I believe you. Mostly. But I work for coin, not charity.",
            "What are you offering?"
        }, "p2_story_br_st_resp");
        addNode("p2_story_br_st_resp", DialogueNode.CAELOMUND, new String[]{
            "The keep, once reclaimed, has a library valued at approximately",
            "forty thousand in rare texts alone.",
            "I am not ungenerous to those who are useful. You will be compensated."
        }, null); // -> rejoin

        addNode("p2_story_br_hs", DialogueNode.PLAYER, new String[]{
            "Were you able to find out if Bastian is alright?",
            "Is he under the effect of a spell or some creature?"
        }, "p2_story_br_hs_resp");
        addNode("p2_story_br_hs_resp", DialogueNode.CAELOMUND, new String[]{
            "There is nothing to investigate. He polymorphed me.",
            "That does not merit an inquisition.",
            "That is an act of war against a superior, and it will be treated as such."
        }, null); // -> rejoin
        // TODO [GRAPHICS]: Caelomund's expression flickers — briefly angered

        addNode("p2_story_br_wc", DialogueNode.PLAYER, new String[]{
            "Out of everyone here. Why me?"
        }, "p2_story_br_wc_resp");
        addNode("p2_story_br_wc_resp", DialogueNode.CAELOMUND, new String[]{
            "You have an air about you. Capable. Adaptable.",
            "Likely in need of something to do or something to gain.",
            "I have learned, over the centuries, to recognize usefulness quickly.",
            "Do not take it as a compliment."
        }, null); // -> rejoin

        // -- Rejoin: construct approaches --
        addNode("p2_rejoin2", DialogueNode.NARRATOR, new String[]{
            "The commotion is now clearly approaching — directional, coming from the left.",
            "An irritated shout: a vendor's cart being shoved aside."
        }, "p2_oret_flees");

        addNode("p2_oret_flees", DialogueNode.NARRATOR, new String[]{
            "Oret glances toward the noise with narrowed eyes.",
            "He says nothing. Then he looks at you with an expression that clearly says:",
            "this is your problem now — and flees."
        }, "p2_cael_construct");

        addNode("p2_cael_construct", DialogueNode.CAELOMUND, new String[]{
            "That will be one of Bastian's constructs.",
            "He has been sending them. I have been avoiding them.",
            "I have been doing so for days and I am tired of it."
        }, "p3_intro");

        // =============================================================
        // PART 3 — THE DECISION POINT (real card spend)
        // =============================================================

        // TODO [GRAPHICS]: Show construct sprite — brutish, pale orcish figure
        //   with its face on its chest. Ferocious eyes where the pecs are,
        //   a salivating mouth across the abdomen, pointed hound's nose between.
        addNode("p3_intro", DialogueNode.NARRATOR, new String[]{
            "The construct enters the edge of the market — a brutish figure.",
            "Its colour is pale and sickly. Something is deeply wrong about it.",
            "Its face is on its chest — ferocious eyes, a salivating mouth,",
            "and a pointed hound's nose. It sniffs the air and begins to turn",
            "toward Caelomund. It may spot him soon if you don't move."
        }, null); // choice follows

        // Central choice — REAL card spend
        addChoice("p3_intro",
            new String[]{
                "\"We don't fight it. We leave now — east gate.\"",
                "\"Give me thirty seconds to cause a scene.\"",
                "\"What does it want? Can we negotiate?\"",
                "\"Stand back. I'm going to go talk to it.\""
            },
            new String[]{"p3_br_wf", "p3_br_st", "p3_br_hs", "p3_br_wc"},
            "p4_alley",
            true,   // spendsCard
            false,  // NOT tutorial — card is really consumed
            new int[]{2, 2, 2, 2},
            new String[]{"GOAT_TRUST,SCROLL_LOST", "GOAT_TRUST", "GOAT_TRUST,APPRENTICE_EMPATHY", "GOAT_RESPECT"}
        );

        // -- Branch A: Way Finder / Flee --
        addNode("p3_br_wf", DialogueNode.CAELOMUND, new String[]{
            "The east gate. Yes. That is not entirely without merit. Move."
        }, "p3_br_wf_narr");
        addNode("p3_br_wf_narr", DialogueNode.NARRATOR, new String[]{
            "You and Caelomund slip through the stalls, then through the buildings.",
            "The creature's movement is tracked audibly — close but not on you.",
            "Caelomund glances back toward where the scroll remains on the ground.",
            "He says nothing. His jaw tightens.",
            "You emerge into a quieter alley. The creature circles, confused."
        }, "p3_br_wf_end");
        addNode("p3_br_wf_end", DialogueNode.CAELOMUND, new String[]{
            "Adequate. It will reorient within the hour.",
            "Hopefully the city guard take care of it before then.",
            "We have some time, but not much."
        }, null); // -> rejoin

        // -- Branch B: Silver Tongue / Distraction --
        addNode("p3_br_st", DialogueNode.CAELOMUND, new String[]{
            "...You are proposing to cause a disturbance on purpose."
        }, "p3_br_st_player");
        addNode("p3_br_st_player", DialogueNode.PLAYER, new String[]{
            "I am proposing to cause a *useful* disturbance. There is a difference."
        }, "p3_br_st_narr");
        // TODO [GRAPHICS]: Show fire/chaos animation in market
        addNode("p3_br_st_narr", DialogueNode.NARRATOR, new String[]{
            "You move toward the far side of the market.",
            "A stall erupts in flames a moment later.",
            "The creature tracks the noise and heat, turns away from Caelomund.",
            "You slip back, grab the scroll, and pull Caelomund into a side alley."
        }, "p3_br_st_end");
        addNode("p3_br_st_end", DialogueNode.CAELOMUND, new String[]{
            "That was... undignified. And it worked.",
            "I am processing both of those things simultaneously.",
            "That bell will bring the city guard. You are aware of that."
        }, "p3_br_st_player2");
        addNode("p3_br_st_player2", DialogueNode.PLAYER, new String[]{
            "I am aware of that."
        }, null); // -> rejoin

        // -- Branch C: Heart Seeker / Confront --
        addNode("p3_br_hs", DialogueNode.CAELOMUND, new String[]{
            "You are walking toward it."
        }, "p3_br_hs_player");
        addNode("p3_br_hs_player", DialogueNode.PLAYER, new String[]{
            "I want to see what it does when it sees me first."
        }, "p3_br_hs_narr");
        addNode("p3_br_hs_narr", DialogueNode.NARRATOR, new String[]{
            "You step into the creature's sightline. It stops. Sniffs.",
            "Its nose works in slow, searching circles.",
            "It has not barked again. It is not lunging. It is tracking."
        }, "p3_br_hs_cael");
        addNode("p3_br_hs_cael", DialogueNode.CAELOMUND, new String[]{
            "It is a dog. Or it was. Bastian has been practicing with the Wand.",
            "The results have been... inconsistent."
        }, "p3_br_hs_player2");
        addNode("p3_br_hs_player2", DialogueNode.PLAYER, new String[]{
            "So it is not trying to hurt anyone. It is trying to find you."
        }, "p3_br_hs_cael2");
        addNode("p3_br_hs_cael2", DialogueNode.CAELOMUND, new String[]{
            "It is trying to return me to the keep. There is a difference."
        }, "p3_br_hs_narr2");
        addNode("p3_br_hs_narr2", DialogueNode.NARRATOR, new String[]{
            "You do not raise your voice. You take a slow step forward.",
            "You crouch slightly, making yourself smaller.",
            "The creature's posture is uncertain — still tracking, but registering calm.",
            "You produce something edible and set it on the ground.",
            "The creature sniffs. Twice. It goes for the food.",
            "Its attention breaks from Caelomund's trail entirely."
        }, "p3_br_hs_player3");
        addNode("p3_br_hs_player3", DialogueNode.PLAYER, new String[]{
            "Now. Quietly."
        }, "p3_br_hs_narr3");
        addNode("p3_br_hs_narr3", DialogueNode.NARRATOR, new String[]{
            "You move into a side alley at a deliberate, unhurried pace.",
            "The creature does not follow. You grabbed the scroll on the way."
        }, "p3_br_hs_end");
        addNode("p3_br_hs_end", DialogueNode.CAELOMUND, new String[]{
            "You fed Bastian's construct."
        }, "p3_br_hs_player4");
        addNode("p3_br_hs_player4", DialogueNode.PLAYER, new String[]{
            "I fed a confused dog."
        }, "p3_br_hs_cael3");
        addNode("p3_br_hs_cael3", DialogueNode.CAELOMUND, new String[]{
            "...The distinction is noted."
        }, null); // -> rejoin

        // -- Branch D: Wildcard / Command --
        addNode("p3_br_wc", DialogueNode.CAELOMUND, new String[]{
            "You are going to — no. No, stop —"
        }, "p3_br_wc_narr");
        addNode("p3_br_wc_narr", DialogueNode.NARRATOR, new String[]{
            "You walk directly toward the construct. It stops moving.",
            "Its chest-face tilts. It has not encountered someone walking toward it before.",
            "Everyone runs."
        }, "p3_br_wc_player");
        addNode("p3_br_wc_player", DialogueNode.PLAYER, new String[]{
            "Sit."
        }, "p3_br_wc_narr2");
        addNode("p3_br_wc_narr2", DialogueNode.NARRATOR, new String[]{
            "A beat. The creature does not sit. But it also does not lunge.",
            "It is working through something."
        }, "p3_br_wc_player2");
        addNode("p3_br_wc_player2", DialogueNode.PLAYER, new String[]{
            "Sit."
        }, "p3_br_wc_narr3");
        // TODO [AUDIO]: Play confused creature whine/growl SFX
        addNode("p3_br_wc_narr3", DialogueNode.NARRATOR, new String[]{
            "A low, confused sound from the creature. Not a bark.",
            "Something between a whine and a growl.",
            "It shifts its weight. Then — slowly, legs bending wrong —",
            "it lowers itself toward the ground. Not quite sitting. But not standing either."
        }, "p3_br_wc_player3");
        addNode("p3_br_wc_player3", DialogueNode.PLAYER, new String[]{
            "Good. Stay."
        }, "p3_br_wc_narr4");
        addNode("p3_br_wc_narr4", DialogueNode.NARRATOR, new String[]{
            "You turn and walk back toward Caelomund at a measured pace.",
            "The creature watches you go. It does not follow."
        }, "p3_br_wc_end");
        addNode("p3_br_wc_end", DialogueNode.CAELOMUND, new String[]{
            "I want you to know that I am deeply unsettled by what just happened."
        }, "p3_br_wc_player4");
        addNode("p3_br_wc_player4", DialogueNode.PLAYER, new String[]{
            "It worked."
        }, "p3_br_wc_cael2");
        addNode("p3_br_wc_cael2", DialogueNode.CAELOMUND, new String[]{
            "That is not — I am not disputing that it worked.",
            "I am saying that I watched you command a polymorphed guard dog",
            "with a face on its chest to sit, and it nearly did,",
            "and I do not know how to account for that."
        }, "p3_br_wc_player5");
        addNode("p3_br_wc_player5", DialogueNode.PLAYER, new String[]{
            "The framework is: it's still a dog."
        }, null); // -> rejoin

        // =============================================================
        // PART 4 — CONVERGENCE: THE ALLEY
        // =============================================================

        addNode("p4_alley", DialogueNode.NARRATOR, new String[]{
            "The noise of the market has changed. What was midday bustle is now edged —",
            "raised voices, people moving quickly. Whatever just happened, the city felt it."
        }, "p4_cael_stuck");

        addNode("p4_cael_stuck", DialogueNode.CAELOMUND, new String[]{
            "We cannot leave tonight.",
            "The creature will reorient. The guard will be asking questions.",
            "And I am a goat, which means every door in this city is functionally",
            "closed to me after dark. We are staying."
        }, "p4_player_problems");

        addNode("p4_player_problems", DialogueNode.PLAYER, new String[]{
            "So we have two problems."
        }, "p4_cael_minimum");

        addNode("p4_cael_minimum", DialogueNode.CAELOMUND, new String[]{
            "At minimum."
        }, null); // choice follows

        // Alley reaction 1 — no card spend, +1 archetype
        addChoice("p4_cael_minimum",
            new String[]{
                "\"The guard first. We handle that before it finds us.\"",
                "\"I can talk our way through both of those.\"",
                "\"Is there anyone in this city you trust?\"",
                "\"This would be easier if you looked less like a goat.\""
            },
            new String[]{"p4_br1_wf", "p4_br1_st", "p4_br1_hs", "p4_br1_wc"},
            "p4_rejoin1",
            false, false,
            new int[]{1, 1, 1, 1},
            new String[]{null, null, null, null}
        );

        addNode("p4_br1_wf", DialogueNode.PLAYER, new String[]{
            "If we are going to be questioned, I would rather it be on our terms."
        }, "p4_br1_wf_resp");
        addNode("p4_br1_wf_resp", DialogueNode.CAELOMUND, new String[]{
            "Agreed. The guard responds to whoever speaks with the most authority.",
            "Present the situation as resolved before they can decide it isn't."
        }, null);

        addNode("p4_br1_st", DialogueNode.PLAYER, new String[]{
            "Guard is just a conversation. Shelter is just a negotiation.",
            "Neither of those is a real problem."
        }, "p4_br1_st_resp");
        addNode("p4_br1_st_resp", DialogueNode.CAELOMUND, new String[]{
            "Your confidence is noted.",
            "I will reserve judgment until the guard is actually in front of us."
        }, null);

        addNode("p4_br1_hs", DialogueNode.PLAYER, new String[]{
            "We do not have to solve this alone if there is someone willing."
        }, "p4_br1_hs_resp");
        addNode("p4_br1_hs_resp", DialogueNode.CAELOMUND, new String[]{
            "I have been a goat for days.",
            "I have not had the opportunity to establish trust with anyone in this city.",
            "That is, in fact, why I needed you."
        }, null);

        addNode("p4_br1_wc", DialogueNode.PLAYER, new String[]{
            "I am just saying. Is there anything in your considerable magical knowledge",
            "that addresses that temporarily?"
        }, "p4_br1_wc_resp");
        addNode("p4_br1_wc_resp", DialogueNode.CAELOMUND, new String[]{
            "I am a centuries-old archmage polymorphed against my will.",
            "If I could alter my appearance, I would have done so before I started eating hay.",
            "Thank you for the suggestion."
        }, null);

        // -- Rejoin and guard whistle --
        // TODO [AUDIO]: Guard whistle SFX
        addNode("p4_rejoin1", DialogueNode.NARRATOR, new String[]{
            "Somewhere back in the market, a guard whistle. Then organized footsteps.",
            "The city is responding."
        }, "p4_cael_reflect");

        addNode("p4_cael_reflect", DialogueNode.CAELOMUND, new String[]{
            "The [PROFESSION] and the goat are hiding in an alley.",
            "I have been telling myself this story differently in my head.",
            "This is not the version I had in mind."
        }, null); // choice follows

        // Alley reaction 2 — no card spend, +1 archetype
        addChoice("p4_cael_reflect",
            new String[]{
                "\"Tell me everything you know about the guard rotation.\"",
                "\"We still haven't discussed what you're paying me.\"",
                "\"Every story starts somewhere.\"",
                "\"Honestly? I think this version is more interesting.\""
            },
            new String[]{"p4_br2_wf", "p4_br2_st", "p4_br2_hs", "p4_br2_wc"},
            "p4_end",
            false, false,
            new int[]{1, 1, 1, 1},
            new String[]{null, null, null, null}
        );

        addNode("p4_br2_wf", DialogueNode.PLAYER, new String[]{
            "If we are going to be here tonight, I want to know what we are working with."
        }, null);

        addNode("p4_br2_st", DialogueNode.PLAYER, new String[]{
            "I am not moving until the number is on the table."
        }, null);

        addNode("p4_br2_hs", DialogueNode.PLAYER, new String[]{
            "This is just the part before it gets better."
        }, null);

        addNode("p4_br2_wc", DialogueNode.PLAYER, new String[]{
            "The other version probably has a lot fewer goats."
        }, null);

        // -- Scene end --
        addNode("p4_end", DialogueNode.NARRATOR, new String[]{
            "Caelomund does not respond — or he does so only with a long exhale",
            "and the faint impression of someone who has, for the first time in days,",
            "stopped moving.",
            "The guard is coming. Shelter does not yet exist. Both problems are unsolved."
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

    /**
     * Creates Scene1Pane.
     * @param mainScreen the main application reference
     */
    public Scene1Pane(MainApplication mainScreen) {
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

        drawBackground();
        addSceneDebugBanner();
        drawDialogueBox();
        addSettingsCornerButton();

        GameState gs = mainScreen.getGameState();
        String ck = gs.getScene1NodeId();
        if (ck != null && !ck.isEmpty() && NODES.containsKey(ck)) {
            restoreScene1FromGameState(gs);
        } else {
            currentRejoinId = null;
            advanceToNode("p1_opening");
            // Fade in from black on fresh entry (not save restore)
            startFadeIn();
        }
    }

    @Override
    public void hideContent() {
        fadingIn = false;
        fadingOut = false;
        fadeOverlay = null;
        syncScene1CheckpointToGameState();
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
        GLabel dbg = pixelLabel("[DEBUG] Scene #1", 10, C_DEBUG_BANNER);
        dbg.setLocation(originX() + (scaleX(8) - scaleX(0)), scaleY(12));
        place(dbg);
    }

    /**
     * Draws the market background using placeholder rectangles.
     * The upper area shows character sprite placeholders and a
     * market stall strip. The graphics team will replace these
     * with real sprites and tilemap art.
     */
    private void drawBackground() {
        // TODO [GRAPHICS]: Replace with market background tilemap/sprite
        // Full background
        place(rect(0, 0, mainScreen.getWidth(), mainScreen.getHeight(), C_BG, C_BG));

        // Market stall strip (lighter band across the middle)
        place(srect(0, 100, 700, 120, C_STALLS, C_STALLS));

        // Player sprite placeholder (always visible)
        // TODO [GRAPHICS]: Replace with player character sprite
        drawSpriteRect(PLAYER_SPRITE_X, PLAYER_SPRITE_Y, 100, 140,
            C_PLAYER_SPRITE, mainScreen.getGameState().getPlayerName());
    }

    /**
     * Draws a placeholder sprite rectangle with a name label below it.
     * These are temporary stand-ins for the graphics team.
     *
     * @param lx     logical X
     * @param ly     logical Y
     * @param lw     logical width
     * @param lh     logical height
     * @param color  fill colour
     * @param label  name label text
     */
    private void drawSpriteRect(double lx, double ly, double lw, double lh,
                                Color color, String label) {
        place(srect(lx, ly, lw, lh, color, color.darker()));

        GLabel nameLbl = pixelLabel(label, 9, color.brighter());
        double cx = scaleX(lx) + (scaleX(lx + lw) - scaleX(lx) - nameLbl.getWidth()) / 2.0;
        nameLbl.setLocation(cx, scaleY(ly + lh + 14));
        place(nameLbl);
    }

    /** Draws the semi-transparent dialogue box frame at the bottom of the screen. */
    private void drawDialogueBox() {
        place(srect(DBOX_X, DBOX_Y, DBOX_W, DBOX_H, C_DBOX_BG, C_DBOX_BORDER));
    }

    // =========================================================
    // NPC SPRITE MANAGEMENT
    // =========================================================

    /** GObjects for the current NPC sprite (cleared when NPC changes). */
    private final List<GObject> npcSpriteElements = new ArrayList<>();

    /**
     * Updates the NPC sprite area based on which part of the scene we're in.
     * Shows the relevant NPC placeholder on the left side of the screen.
     *
     * @param nodeId the current dialogue node id (used to determine which NPC)
     */
    private void updateNpcSprite(String nodeId) {
        // Remove old NPC sprite
        for (GObject obj : npcSpriteElements) {
            mainScreen.remove(obj);
            contents.remove(obj);
        }
        npcSpriteElements.clear();

        // Determine which NPC to show based on the current part of the scene
        if (nodeId.startsWith("p1_")) {
            // Part 1: Oret the vendor
            // TODO [GRAPHICS]: Replace with Oret character sprite
            addNpcSprite(NPC_SPRITE_X, NPC_SPRITE_Y, 100, 140, C_ORET_SPRITE, "ORET");
        } else if (nodeId.startsWith("p2_") || nodeId.startsWith("p4_")) {
            // Parts 2 & 4: Caelomund the goat (shorter sprite)
            // TODO [GRAPHICS]: Replace with Caelomund goat sprite
            addNpcSprite(NPC_SPRITE_X, NPC_SPRITE_Y + 40, 80, 100, C_CAEL_SPRITE, "CAELOMUND");
        } else if (nodeId.startsWith("p3_intro") || nodeId.equals("p3_intro")) {
            // Part 3 intro: show both construct and Caelomund
            // TODO [GRAPHICS]: Replace with construct creature sprite
            addNpcSprite(NPC_SPRITE_X - 20, NPC_SPRITE_Y - 10, 120, 160, C_CONSTRUCT_SPRITE, "CONSTRUCT");
            addNpcSprite(NPC_SPRITE_X + 120, NPC_SPRITE_Y + 40, 60, 80, C_CAEL_SPRITE, "CAELOMUND");
        } else if (nodeId.startsWith("p3_")) {
            // Part 3 branches: Caelomund
            addNpcSprite(NPC_SPRITE_X, NPC_SPRITE_Y + 40, 80, 100, C_CAEL_SPRITE, "CAELOMUND");
        }
    }

    /**
     * Adds an NPC sprite placeholder to the screen and tracks it for removal.
     */
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
    // DIALOGUE ENGINE — advancing through nodes and lines
    // =========================================================

    /**
     * Loads a dialogue node and displays its first line.
     * Also updates the NPC sprite based on which part of the scene we're in.
     *
     * @param nodeId the id of the node to load
     */
    private void advanceToNode(String nodeId) {
        // Check for scene end
        if ("SCENE_END".equals(nodeId)) {
            endScene();
            return;
        }

        currentNodeId = nodeId;
        currentLineIndex = 0;
        showingChoice = false;
        activeChoice = null;

        // Update NPC sprite for this part of the scene
        updateNpcSprite(nodeId);

        // Display the first line of this node
        renderDialogueLine();
    }

    /**
     * Renders the current dialogue line inside the dialogue box.
     * Clears any previous dialogue text, then draws the speaker name
     * and the current line (word-wrapped to fit the box).
     */
    private void renderDialogueLine() {
        clearDialogueElements();

        DialogueNode node = NODES.get(currentNodeId);
        if (node == null) return;

        // -- Speaker name label --
        String speaker = node.getSpeaker();
        if (speaker != null) {
            String displayName = resolveDisplaySpeaker(speaker);
            Color speakerColor = getSpeakerColor(speaker);
            GLabel speakerLbl = pixelLabel(displayName + ":", 11, speakerColor);
            speakerLbl.setLocation(scaleX(SPEAKER_X), scaleY(SPEAKER_Y));
            addDialogueElement(speakerLbl);
        }

        // -- Dialogue text (word-wrapped) --
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

        // -- Continue indicator --
        GLabel cont = pixelLabel(">>>", 9, C_CONTINUE);
        cont.setLocation(scaleX(CONT_X), scaleY(CONT_Y));
        addDialogueElement(cont);

        syncScene1CheckpointToGameState();
    }

    /**
     * Shows a set of 4 choice buttons in the dialogue box area.
     * Each button shows the option text and a card type tag.
     *
     * @param choice the DialogueChoice to display
     */
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

            // Button border
            addDialogueElement(rect(bx - 1, by - 1, bw + 2, bh + 2, C_BTN_BORDER, C_BTN_BORDER));

            // Button fill (tracked for hover and click)
            GRect box = rect(bx, by, bw, bh, C_BTN_BG, C_BTN_BG);
            addDialogueElement(box);
            choiceBoxes.add(box);

            // Option text
            GLabel optLbl = pixelLabel(texts[i], 9, C_BTN_TEXT);
            double textY = by + (bh + optLbl.getAscent()) / 2.0 - optLbl.getDescent() / 2.0;
            optLbl.setLocation(bx + scaleX(10) - scaleX(0), textY);
            addDialogueElement(optLbl);

            // Card type tag (right-aligned, coloured)
            int typeIndex = types[i].ordinal();
            String tag = "[" + types[i].name().substring(0, 2) + "]";
            GLabel tagLbl = pixelLabel(tag, 8, TYPE_COLORS[typeIndex]);
            tagLbl.setLocation(bx + bw - tagLbl.getWidth() - (scaleX(8) - scaleX(0)), textY);
            addDialogueElement(tagLbl);
        }
        syncScene1CheckpointToGameState();
    }

    // =========================================================
    // CHOICE HANDLING
    // =========================================================

    /**
     * Processes the player's selection of a dialogue choice option.
     * Awards archetype points, sets narrative flags, handles card
     * spend, and advances to the appropriate branch node.
     *
     * @param index the option index (0–3)
     */
    private void handleChoiceSelected(int index) {
        if (activeChoice == null) return;

        DialogueChoice choice = activeChoice;
        CardType chosenType = choice.getOptionTypes()[index];

        // -- Award archetype points --
        int points = choice.getArchetypePoints()[index];
        mainScreen.getGameState().addArchetypePoints(chosenType, points);

        // -- Set narrative flags --
        String flagStr = choice.getFlagChanges()[index];
        if (flagStr != null) {
            // Support comma-separated flags (e.g. "GOAT_TRUST,SCROLL_LOST")
            for (String flag : flagStr.split(",")) {
                mainScreen.getGameState().setFlag(flag.trim());
            }
        }

        // -- Handle card spend --
        if (choice.spendsCard() && !choice.isTutorial()) {
            // Real card spend — consume a card from the player's hand
            Hand hand = mainScreen.getPlayer().getHand();
            if (!hand.isEmpty()) {
                // Try to find a card matching the chosen type
                int cardIdx = -1;
                List<Card> cards = hand.getCards();
                for (int i = 0; i < cards.size(); i++) {
                    if (cards.get(i).getType() == chosenType) {
                        cardIdx = i;
                        break;
                    }
                }
                // If no matching card, consume the first available
                if (cardIdx < 0) {
                    cardIdx = 0;
                }
                Card spent = hand.removeCard(cardIdx);
                System.out.println("[Scene 1] Card consumed: " + spent.getName()
                    + " (" + spent.getType() + ")");
            }
        }

        // -- Debug output --
        System.out.println("[Scene 1] Choice: " + chosenType
            + " | +" + points + " archetype"
            + (flagStr != null ? " | flags: " + flagStr : ""));

        // -- Store which branch was chosen, then advance --
        String branchNodeId = choice.getBranchNodeIds()[index];
        String rejoinId = choice.getRejoinNodeId();
        showingChoice = false;
        activeChoice = null;
        choiceBoxes.clear();

        // Load the branch node, setting up the rejoin for when the branch ends
        currentRejoinId = rejoinId;
        advanceToNode(branchNodeId);
    }

    /**
     * The rejoin node id to advance to when the current branch's
     * node chain ends (i.e. a node has nextNodeId == null and no
     * choice registered). This is set when a choice branch begins.
     */
    private String currentRejoinId = null;

    // =========================================================
    // MOUSE EVENTS
    // =========================================================

    /**
     * Advances dialogue on click, or handles choice selection.
     * - If a choice is showing: check which button was clicked.
     * - Otherwise: advance to the next line or next node.
     */
    @Override
    public void mouseClicked(MouseEvent e) {
        if (fadingIn || fadingOut) return;

        double x = e.getX(), y = e.getY();

        if (showingChoice) {
            // Check choice button clicks
            for (int i = 0; i < choiceBoxes.size(); i++) {
                if (choiceBoxes.get(i).contains(x, y)) {
                    handleChoiceSelected(i);
                    return;
                }
            }
            return; // Click outside choices — ignore
        }

        // -- Advance dialogue --
        DialogueNode node = NODES.get(currentNodeId);
        if (node == null) return;

        currentLineIndex++;
        if (currentLineIndex < node.getLines().length) {
            // More lines in this node — show the next one
            renderDialogueLine();
        } else {
            // Node exhausted — check what comes next
            String nextId = node.getNextNodeId();

            if (nextId != null) {
                // Explicit next node
                advanceToNode(nextId);
            } else if (CHOICES.containsKey(currentNodeId)) {
                // A choice is registered after this node
                showChoiceButtons(CHOICES.get(currentNodeId));
            } else if (currentRejoinId != null) {
                // Branch ended — advance to the rejoin node
                String rejoin = currentRejoinId;
                currentRejoinId = null;
                advanceToNode(rejoin);
            } else {
                // No next node, no choice, no rejoin — scene is over
                endScene();
            }
        }
    }

    /**
     * Hover effect on choice buttons — same pattern as
     * {@link CharacterCreationPane#mouseMoved(MouseEvent)}.
     */
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

    /**
     * Called when the scene's dialogue is complete.
     * Prints a debug summary and transitions to Scene 2.
     */
    private void endScene() {
        System.out.println("[Scene 1] Scene complete!");
        mainScreen.getGameState().clearScene1Checkpoint();
        mainScreen.autosaveIfSlotActive();
        currentNodeId = null;
        mainScreen.getGameState().printDebugSummary();
        startFadeOut();
    }

    /** Restores layout from {@link GameState} after load or window resize. */
    private void restoreScene1FromGameState(GameState gs) {
        String id = gs.getScene1NodeId();
        DialogueNode node = NODES.get(id);
        if (node == null) {
            currentRejoinId = null;
            advanceToNode("p1_opening");
            return;
        }
        currentNodeId = id;
        currentLineIndex = gs.getScene1LineIndex();
        currentRejoinId = gs.getScene1RejoinId();

        if (gs.isScene1ShowingChoice()) {
            if (!CHOICES.containsKey(id)) {
                currentRejoinId = null;
                advanceToNode("p1_opening");
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

    private void syncScene1CheckpointToGameState() {
        if (currentNodeId == null) {
            return;
        }
        GameState gs = mainScreen.getGameState();
        gs.updateScene1Checkpoint(
            currentNodeId,
            currentLineIndex,
            showingChoice,
            currentRejoinId != null ? currentRejoinId : "");
        mainScreen.autosaveIfSlotActive();
    }

    // =========================================================
    // HELPERS — dialogue element management
    // =========================================================

    /**
     * Adds a GObject to both the main contents list and the
     * dialogue-specific tracking list (for efficient clearing).
     */
    private void addDialogueElement(GObject obj) {
        dialogueElements.add(obj);
        place(obj);
    }

    /**
     * Removes all dialogue-area elements (text, buttons) from the
     * screen. Called before rendering a new line or choice set.
     */
    private void clearDialogueElements() {
        for (GObject obj : dialogueElements) {
            mainScreen.remove(obj);
            contents.remove(obj);
        }
        dialogueElements.clear();
        choiceBoxes.clear();
    }

    // =========================================================
    // FADE TRANSITIONS
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
                if (fadingIn) {
                    mainScreen.remove(fadeOverlay);
                    contents.remove(fadeOverlay);
                    fadeOverlay = null;
                    fadingIn = false;
                }
            } catch (InterruptedException e) { /* clean exit */ }
        }).start();
    }

    private void startFadeOut() {
        fadeOverlay = new GRect(0, 0, mainScreen.getWidth(), mainScreen.getHeight());
        fadeOverlay.setFilled(true);
        fadeOverlay.setFillColor(new Color(0, 0, 0, 0));
        fadeOverlay.setColor(new Color(0, 0, 0, 0));
        contents.add(fadeOverlay);
        mainScreen.add(fadeOverlay);
        fadingOut = true;

        new Thread(() -> {
            try {
                for (int frame = 1; frame <= FADE_OUT_FRAMES; frame++) {
                    if (!fadingOut) return;
                    double t = (double) frame / FADE_OUT_FRAMES;
                    int alpha = (int) (t * 255);
                    Color c = new Color(0, 0, 0, alpha);
                    fadeOverlay.setFillColor(c);
                    fadeOverlay.setColor(new Color(0, 0, 0, 0));
                    Thread.sleep(FADE_OUT_FRAME_MS);
                }
                if (!fadingOut) return;
                Thread.sleep(300);
                if (fadingOut) {
                    fadingOut = false;
                    mainScreen.switchToScene1To2TransitionScreen();
                }
            } catch (InterruptedException e) { /* clean exit */ }
        }).start();
    }

    // =========================================================
    // HELPERS
    // =========================================================

    /**
     * Returns the display name for a speaker constant.
     * Replaces "PLAYER" with the player's actual name.
     *
     * @param speaker the speaker constant from DialogueNode
     * @return display name for the speaker label
     */
    private String resolveDisplaySpeaker(String speaker) {
        if (DialogueNode.PLAYER.equals(speaker)) {
            return mainScreen.getGameState().getPlayerName().toUpperCase();
        }
        return speaker;
    }

    /**
     * Returns the colour to use for a speaker's name label.
     *
     * @param speaker the speaker constant from DialogueNode
     * @return the colour for that speaker
     */
    private Color getSpeakerColor(String speaker) {
        switch (speaker) {
            case DialogueNode.ORET:      return C_ORET;
            case DialogueNode.CAELOMUND: return C_CAELOMUND;
            case DialogueNode.PLAYER:    return C_PLAYER_NAME;
            default:                     return C_NARRATOR;
        }
    }

    /**
     * Replaces [NAME] and [PROFESSION] tokens in dialogue text with
     * the player's actual name and profession from {@link MainApplication#getGameState()}.
     *
     * @param text raw dialogue text with potential tokens
     * @return text with tokens replaced
     */
    private String replaceTokens(String text) {
        return text
            .replace("[NAME]", mainScreen.getGameState().getPlayerName())
            .replace("[PROFESSION]", mainScreen.getGameState().getPlayerProfession());
    }
}
