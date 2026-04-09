/*
Person 2: OreNode — the mineable ore deposit in B2 that gives Ore and BrokenLever
Who RIGs it: Room (B2) — holds it in WorldObject list, routes J key to onInteract().

Extends: WorldObject

===============
PLAN OF ACTION
===============

- CLASS ROLE
- OreNode is the visually distinct interactable object in B2.
- It signals to the player (by its appearance) that it can be used.
- Mining requires the Pickaxe in inventory. Without it, show a hint dialogue.
- On successful mining: give the player Ore + BrokenLever simultaneously, remove self from room.
- One-time use. isMined stays true even on room re-entry (like Chest — permanent state).

- FIELDS
- boolean isMined           — true after player has mined this node; never resets
- Dialogue dialogue          — optional hint reference for "you need a pickaxe" message

- onInteract() BEHAVIOR
  1. If isMined, return immediately.
  2. Check player inventory for item with itemId == "pickaxe".
     If not found: dialogue hint "You'll need a pickaxe to mine this." → return.
  3. Set isMined = true.
  4. Hide this object (call hide() from WorldObject — removes hitbox and visual).
  5. Give player BOTH items simultaneously:
       p.collectItem(new Item("ore",          "Ore",           false))
       p.collectItem(new Item("broken_lever", "Broken Lever",  false))
  6. TODO: play a short mining animation (flash/particles) before hiding — TBD.

- DESIGN NOTE: Pickaxe Location
- The design doc marks Pickaxe location as TBD (Goat Wizard gift, chest, or NPC).
- OreNode only checks for "pickaxe" itemId — it does not care how the player got it.

- SAVE STATE
- isMined is persistent — once mined, OreNode is gone for the session.
- SaveData.collectedItemIds should include "ore_node_b2" as a flag once mined.
  Room checks this on load and calls oreNode.forceMined() to hide it immediately.
*/

import acm.graphics.GCanvas;
import acm.graphics.GRect;

import java.awt.Color;

/**
 * The ore deposit in B2. Requires Pickaxe to mine. Gives Ore + BrokenLever on success.
 * One-time use — permanently removed after mining.
 * See PLAN OF ACTION above before implementing.
 */
public class OreNode extends WorldObject {

    // =========================================================
    // CONSTANTS
    // =========================================================

    private static final Color ORE_COLOR = new Color(100, 80, 140);

    public static final String PICKAXE_ID      = "pickaxe";
    public static final String ORE_ID          = "ore";
    public static final String BROKEN_LEVER_ID = "broken_lever";
    public static final String SAVE_FLAG_ID    = "ore_node_b2";

    // =========================================================
    // FIELDS
    // =========================================================

    /** True after the player has mined this node. Never resets. */
    private boolean isMined;

    /** Optional Dialogue reference for "you need a pickaxe" hint. */
    private Dialogue dialogue;

    /** Placeholder visual until real ore sprite is ready. */
    private GRect placeholder;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    /**
     * @param x top-left world pixel X
     * @param y top-left world pixel Y
     */
    public OreNode(double x, double y) {
        super(x, y, 48, 48);
        this.isMined = false;

        this.placeholder = new GRect(x, y, 48, 48);
        this.placeholder.setFilled(true);
        this.placeholder.setFillColor(ORE_COLOR);
    }

    // =========================================================
    // WORLD OBJECT OVERRIDES
    // =========================================================

    @Override
    public void draw(GCanvas canvas) {
        if (visible) canvas.add(placeholder);
    }

    @Override
    public void removeFrom(GCanvas canvas) {
        canvas.remove(placeholder);
    }

    /**
     * Called by Room when the player presses J while facing this node.
     * Requires Pickaxe in inventory. Gives Ore and BrokenLever on success.
     *
     * @param p the Player interacting
     */
    @Override
    public boolean isInteractable() {
        return !isMined;
    }

    @Override
    public void onInteract(Player p) {
        if (isMined) return;

        if (p.findInventoryItem(PICKAXE_ID) == null) {
            if (dialogue != null && !dialogue.isOpen()) {
                GamePlayState.setCurrent(GamePlayState.DIALOGUE);
                dialogue.open(
                    new String[]{"This ore vein looks mineable, but you'll need a pickaxe."},
                    "Ore Vein",
                    false,
                    () -> GamePlayState.setCurrent(GamePlayState.PLAYING)
                );
            }
            return;
        }

        isMined = true;
        hide();

        p.collectItem(new Item(ORE_ID, "Ore", false));
        p.collectItem(new Item(BROKEN_LEVER_ID, "Broken Lever", false));

        if (dialogue != null && !dialogue.isOpen()) {
            GamePlayState.setCurrent(GamePlayState.DIALOGUE);
            dialogue.open(
                new String[]{
                    "You mined the ore vein!",
                    "Obtained Ore and a Broken Lever embedded in the rock."
                },
                "Ore Vein",
                false,
                () -> GamePlayState.setCurrent(GamePlayState.PLAYING)
            );
        }
    }

    @Override
    public void panVisual(double panX, double panY) {
        placeholder.move(panX, panY);
    }

    @Override
    public void resetVisualPosition() {
        placeholder.setLocation(x, y);
    }

    // =========================================================
    // FORCE MINED — called on load when SaveData shows this was already mined
    // =========================================================

    /** Silently mines this node without giving items. Called during Room setup on save load. */
    public void forceMined() {
        isMined = true;
        hide();
    }

    // =========================================================
    // SETTERS / GETTERS
    // =========================================================

    public void    setDialogue(Dialogue d) { this.dialogue = d; }
    public boolean isMined()               { return isMined; }
}
