/*
Person 2: TrainingDummy — an attackable practice target in A1 that teaches the player how to fight
Who RIGs it: Room (A1) — holds it in WorldObject list.
               Each tick: Room checks SwordSwing hitbox overlap with TrainingDummy and calls onHit().
               TrainingDummy is also passable — it does not block the player's movement.

Extends: WorldObject

===============
PLAN OF ACTION
===============

- CLASS ROLE
- TrainingDummy exists only in A1 (Market). It is there to teach the player that the sword works.
- It takes hits from SwordSwing and reacts with an animation/color flash.
- It NEVER dies — hitCount can grow indefinitely but health is irrelevant here.
- It does NOT interact via J key — it responds to sword hits only.
- It is passable — the player can walk through it (hitbox is used only for SwordSwing overlap detection,
  not for player movement blocking).

- FIELDS
- int hitCount    — total number of times this dummy has been hit (for fun/debug display, not gameplay)

- onHit() BEHAVIOR
  1. Increment hitCount.
  2. Play a "react" animation: brief color flash or sprite frame change.
     Placeholder: quickly set fill color to a bright color then restore after a few ticks.
  3. TODO: play a hit SFX (Person 4 / SoundManager — not wired yet).
  4. TrainingDummy does not take damage or die. No health system needed here.

- PASSABILITY NOTE
- Room must NOT add TrainingDummy to any collision/movement-blocking check.
- Its WorldObject hitbox exists only so SwordSwing can overlap-detect it via Room's sword check.
- When Room iterates WorldObjects for player-movement blocking, TrainingDummy should be skipped.
  One approach: WorldObject subclasses can override a isPassable() method returning true by default.
  PathBlocker would override it to return false. TrainingDummy stays at the default (true).

- RESET
- TrainingDummy does NOT reset hitCount on room re-entry — it is a decoration, not a puzzle element.
  (The player cannot leave A1 and re-enter in a way that matters for gameplay — PathBlocker seals south.)
*/

import acm.graphics.GCanvas;
import acm.graphics.GRect;

import java.awt.Color;

/**
 * An attackable practice target in A1. Reacts to sword hits. Never dies. Passable.
 * See PLAN OF ACTION above before implementing.
 */
public class TrainingDummy extends WorldObject {

    // =========================================================
    // CONSTANTS
    // =========================================================

    private static final Color DUMMY_COLOR    = new Color(180, 140, 100);
    private static final Color DUMMY_HIT_COLOR = new Color(255, 80, 80);

    /** Number of ticks the hit-flash lasts. */
    private static final int HIT_FLASH_TICKS = 8;

    // =========================================================
    // FIELDS
    // =========================================================

    /** Total number of sword hits received. Grows indefinitely. */
    private int hitCount;

    /** Countdown timer for the hit-flash visual. 0 = no flash active. */
    private int flashTimer;

    /** Placeholder visual until real dummy sprite is ready. */
    private GRect placeholder;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    /**
     * @param x top-left world pixel X
     * @param y top-left world pixel Y
     */
    public TrainingDummy(double x, double y) {
        super(x, y, 48, 96); // taller than one tile — looks more like a practice dummy
        this.hitCount  = 0;
        this.flashTimer = 0;

        this.placeholder = new GRect(x, y, 48, 96);
        this.placeholder.setFilled(true);
        this.placeholder.setFillColor(DUMMY_COLOR);
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

    /** Advances the hit-flash timer each tick. */
    @Override
    public void update(double dt) {
        if (flashTimer > 0) {
            flashTimer--;
            if (flashTimer == 0) {
                placeholder.setFillColor(DUMMY_COLOR); // restore normal color
            }
        }
    }

    /**
     * Called by Room when a SwordSwing hitbox overlaps this dummy.
     * Reacts with a color flash. Never dies.
     */
    @Override
    public void onHit() {
        hitCount++;
        flashTimer = HIT_FLASH_TICKS;
        placeholder.setFillColor(DUMMY_HIT_COLOR);
        // TODO: SoundManager.play("dummy_hit") — wire when SoundManager is ready (Person 4)
    }

    // =========================================================
    // GETTERS
    // =========================================================

    public int getHitCount() { return hitCount; }
}
