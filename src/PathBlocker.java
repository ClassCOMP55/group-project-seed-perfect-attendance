/*
Person 1+2: PathBlocker — an impassable invisible barrier placed over A1's south exit
Who RIGs it: The opening sequence (Person 1's territory) — calls room.addObject(pathBlocker) after the
               post-cutscene dialogue finishes. WorldMap then calls WorldMap.closeExit("A1", DOWN) at
               the same moment so exit detection also stops.
             Room — holds it in WorldObject list; its hitbox blocks player movement passively.

Extends: WorldObject

===============
PLAN OF ACTION
===============

- CLASS ROLE
- PathBlocker is the simplest WorldObject: it is a solid, impassable barrier with no behavior.
- It is placed over the south exit of A1 after the opening cutscene fires (the large monsters
  smash through and the Goat Wizard seals the path behind them).
- Once placed, PathBlocker is NEVER removed during the game session.
- It has no interact behavior, no animation, no contact response.

- HOW IT BLOCKS MOVEMENT
- PathBlocker's hitbox overlaps the south wall tiles of A1 (or the gap where the exit would be).
- Room's passability check (or player movement code) checks WorldObject hitboxes for impassable
  objects. PathBlocker must be registered as impassable.
- WorldMap also calls closeExit("A1", DOWN) simultaneously so exit detection does not fire.
- Both the hitbox AND the closed exit work together — belt-and-suspenders approach.

- VISUAL
- PathBlocker can be invisible (no sprite) or show a rubble/debris sprite to suggest the path is blocked.
- For now: invisible (no placeholder drawn). Sprite can be added when assets are ready.

- PLACEMENT
- Placed at the south edge of A1, spanning the full exit gap (likely a few tiles wide).
- Exact pixel position set when the opening sequence fires (Person 1's responsibility).
- Width: wide enough to cover the exit fully (at least 3 tiles = 144px to block the center opening).
- Height: one tile tall (48px) is sufficient.

- WHAT PATHBLOCKER DOES NOT DO
- Does not animate or change state.
- Does not respond to J key or sword hits.
- Does not reset on room re-entry (it stays placed forever once added).
*/

import acm.graphics.GCanvas;

/**
 * Impassable barrier placed over A1's south exit after the opening cutscene.
 * Never removed. No visual by default — just an active Hitbox.
 * See PLAN OF ACTION above before implementing.
 */
public class PathBlocker extends WorldObject {

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    /**
     * Creates an impassable barrier at the given position.
     *
     * @param x      top-left world pixel X
     * @param y      top-left world pixel Y
     * @param width  blocker width in pixels (cover the full exit gap)
     * @param height blocker height in pixels (one tile = 48px is sufficient)
     */
    public PathBlocker(double x, double y, double width, double height) {
        super(x, y, width, height);
    }

    // =========================================================
    // WORLD OBJECT OVERRIDES
    // =========================================================

    @Override
    public void draw(GCanvas canvas) {
        // Intentionally empty — PathBlocker is invisible by default.
        // Add a rubble GImage here when art assets are ready.
    }

    // No onInteract, onContact, or onHit overrides needed — PathBlocker is purely passive.
    // Its hitbox does all the work.
}
