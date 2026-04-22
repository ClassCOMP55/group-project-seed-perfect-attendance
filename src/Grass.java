/*
Person 2: Grass — cuttable grass tile that may drop a Coin when hit by a sword
Who RIGs it: Room — holds Grass instances in its WorldObject list.
               Each tick: Room checks SwordSwing hitbox overlap with each Grass and calls onHit().
               Grass objects are placed in C2 (Forest) and potentially other outdoor areas.

Extends: WorldObject

===============
PLAN OF ACTION
===============

- CLASS ROLE
- Grass is a cuttable decoration placed on the floor.
- It is NOT triggered by the interact key (J) — it reacts to SwordSwing hits only.
- When cut: plays a short cut animation, then has a random chance to drop a Coin into the room.
- Once cut, Grass stays cut for the rest of the room visit. It resets when the player re-enters
  the room (Room.reset() creates fresh Grass objects or calls reset() on existing ones).

- FIELDS
- boolean isCut              — true once the sword has hit this grass
- float coinDropChance       — 0.0 to 1.0; suggested default: 0.5 (50% chance per design doc TBD)

- onHit() BEHAVIOR
  1. If already cut, return immediately.
  2. Set isCut = true.
  3. Hide the sprite (grass is now gone visually).
  4. Roll Math.random() < coinDropChance — if true, create a new Coin at this position and
     add it to the Room's droppedItems list.
     Room needs to be accessible here — either pass it in via constructor or use a callback.
     Recommended: constructor takes a Runnable or Consumer<Item> dropCallback so Grass
     does not need a direct reference to Room.
  5. TODO: play a short cut animation (sprite flicker or quick fade) before hiding.

- COIN DROP CALLBACK
- Grass should NOT hold a reference to Room directly — that creates tight coupling.
- Instead, Room passes a Consumer<Item> dropCallback when constructing Grass:
    new Grass(x, y, canvas, item -> room.addDroppedItem(item))
- When onHit() decides to drop a coin, it calls dropCallback.accept(new Coin(x, y)).
- This keeps Grass decoupled from Room.

- RESET
- Room.reset() either re-constructs all Grass objects fresh or calls grass.reset() on each one.
- grass.reset() sets isCut = false and re-shows the sprite.
*/

import acm.graphics.GCanvas;
import acm.graphics.GImage;
import acm.graphics.GLabel;
import acm.graphics.GRect;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

/**
 * Cuttable grass. Reacts to SwordSwing hits via onHit().
 * May drop a Coin on cut. Resets on room re-entry.
 * See PLAN OF ACTION above before implementing.
 */
public class Grass extends WorldObject {

    // =========================================================
    // CONSTANTS
    // =========================================================

    /** Default coin drop probability (50%). TBD per design doc. */
    private static final float DEFAULT_COIN_DROP_CHANCE = 0.5f;

    /** Minimum time in seconds before a cut grass tile becomes harvestable again. */
    private static final double MIN_REGROW_DURATION_SECONDS = 20.0;

    /** Maximum time in seconds before a cut grass tile becomes harvestable again. */
    private static final double MAX_REGROW_DURATION_SECONDS = 25.5;

    /** Placeholder grass color until real sprite is wired. */
    private static final Color GRASS_COLOR = new Color(60, 160, 60);

    /** Border color so the debug patch reads clearly on top of the floor tile. */
    private static final Color GRASS_BORDER_COLOR = new Color(26, 92, 26);

    /** Ready-state debug label color. */
    private static final Color DEBUG_LABEL_READY_COLOR = new Color(245, 255, 245);

    /** Regrowth-state debug label color. */
    private static final Color DEBUG_LABEL_GROWING_COLOR = new Color(255, 236, 140);

    // =========================================================
    // FIELDS
    // =========================================================

    /** True once this grass has been cut by the player's sword. */
    private boolean isCut;

    /** Probability (0.0–1.0) that cutting this grass drops a Coin. */
    private final float coinDropChance;

    /**
     * Called when this grass drops a coin.
     * Receives the new Coin item — passes it to Room.addDroppedItem().
     * Keeps Grass decoupled from Room.
     */
    private final Consumer<Item> dropCallback;

    /** True when this tile may keep dropping coins after regrowing. */
    private final boolean repeatCoinDrops;

    /** Grass sprite loaded from assets; null if load fails. */
    private GImage grassSprite;

    /** Placeholder visual shown when sprite is unavailable. */
    private GRect placeholder;

    /** Small always-on debug label so testers can identify the patch quickly. */
    private GLabel debugLabel;

    /** Seconds remaining before the tile regrows. 0 means harvestable now. */
    private double regrowTimerSeconds;

    /** Tracks one-per-visit coin drops for debug/test grass that should not be farmable forever. */
    private boolean coinDropExhausted;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    /**
     * @param x            top-left world pixel X
     * @param y            top-left world pixel Y
     * @param dropCallback called with a new Coin if the coin-drop roll succeeds; may be null
     */
    public Grass(double x, double y, Consumer<Item> dropCallback) {
        this(x, y, DEFAULT_COIN_DROP_CHANCE, dropCallback);
    }

    /**
     * @param x              top-left world pixel X
     * @param y              top-left world pixel Y
     * @param coinDropChance probability of dropping a coin when harvested
     * @param dropCallback   called with a new Coin if the coin-drop roll succeeds; may be null
     */
    public Grass(double x, double y, float coinDropChance, Consumer<Item> dropCallback) {
        this(x, y, coinDropChance, dropCallback, true);
    }

    /**
     * @param x               top-left world pixel X
     * @param y               top-left world pixel Y
     * @param coinDropChance  probability of dropping a coin when harvested
     * @param dropCallback    called with a new Coin if the coin-drop roll succeeds; may be null
     * @param repeatCoinDrops true when every regrow cycle may drop again; false to cap at one
     *                        successful coin drop per room visit
     */
    public Grass(double x, double y, float coinDropChance, Consumer<Item> dropCallback,
                 boolean repeatCoinDrops) {
        super(x, y, 100, 100);
        this.isCut             = false;
        this.coinDropChance    = Math.max(0.0f, Math.min(1.0f, coinDropChance));
        this.dropCallback      = dropCallback;
        this.repeatCoinDrops   = repeatCoinDrops;
        this.regrowTimerSeconds = 0.0;
        this.coinDropExhausted = false;

        this.grassSprite = loadSprite("assets/visuals/png's/grass_to_cut.png");

        this.placeholder = new GRect(x, y, 48, 48);
        this.placeholder.setFilled(true);
        this.placeholder.setFillColor(GRASS_COLOR);
        this.placeholder.setColor(GRASS_BORDER_COLOR);

        this.debugLabel = new GLabel("grass");
        this.debugLabel.setFont("SansSerif-BOLD-10");

        refreshVisualState();
    }

    // =========================================================
    // WORLD OBJECT OVERRIDES
    // =========================================================

    @Override
    public void draw(GCanvas canvas) {
        if (!visible) return;
        refreshVisualState();
        if (grassSprite != null) {
            canvas.add(grassSprite);
        } else {
            canvas.add(placeholder);
        }
    }

    @Override
    public void removeFrom(GCanvas canvas) {
        if (grassSprite != null) canvas.remove(grassSprite);
        canvas.remove(placeholder);
        canvas.remove(debugLabel);
    }

    @Override
    public void update(double dt) {
        if (!isCut) return;

        regrowTimerSeconds -= dt;
        if (regrowTimerSeconds <= 0.0) {
            finishRegrow();
        } else {
            updateDebugLabel();
        }
    }

    /**
     * Called by Room when a SwordSwing hitbox overlaps this grass.
     * Cuts the grass and optionally drops a Coin.
     */
    @Override
    public void onHit() {
        if (isCut) return;

        isCut = true;
        GameSFX.play(GameSFX.SFX.GRASS_CUT);
        regrowTimerSeconds = randomRegrowDurationSeconds();
        hitbox.updatePosition(-99999, -99999);
        if (grassSprite != null) {
            grassSprite.setVisible(false);
        }
        placeholder.setVisible(false);
        updateDebugLabel();

        if (dropCallback != null && !coinDropExhausted && Math.random() < coinDropChance) {
            dropCallback.accept(new Coin(x + 24, y + 24));
            if (!repeatCoinDrops) {
                coinDropExhausted = true;
            }
        }
    }

    // =========================================================
    // RESET
    // =========================================================

    /** Restores this grass to its uncut state for room re-entry. */
    public void reset() {
        isCut = false;
        regrowTimerSeconds = 0.0;
        coinDropExhausted = false;
        hitbox.updatePosition(x, y);
        refreshVisualState();
    }

    // =========================================================
    // GETTERS
    // =========================================================

    public boolean isCut() { return isCut; }

    @Override
    public void panVisual(double panX, double panY) {
        if (grassSprite != null) grassSprite.move(panX, panY);
        placeholder.move(panX, panY);
        debugLabel.move(panX, panY);
    }

    @Override
    public void resetVisualPosition() {
        refreshVisualState();
    }

    /** Restores the tile to its ready state after the grow timer completes. */
    private void finishRegrow() {
        isCut = false;
        regrowTimerSeconds = 0.0;
        hitbox.updatePosition(x, y);
        refreshVisualState();
    }

    /** Keeps sprite/placeholder visibility, label text, and label placement in sync with the current state. */
    private void refreshVisualState() {
        boolean showVisual = visible && !isCut;
        if (grassSprite != null) {
            grassSprite.setLocation(x, y);
            grassSprite.setVisible(showVisual);
        }
        placeholder.setLocation(x, y);
        placeholder.setVisible(grassSprite == null && showVisual);
        debugLabel.setVisible(showVisual);
        updateDebugLabel();
    }

    /** Shows either the ready label or the remaining grow timer. */
    private void updateDebugLabel() {
        if (isCut) {
            int tenthsRemaining = (int) Math.ceil(Math.max(0.0, regrowTimerSeconds) * 10.0);
            double shownSeconds = tenthsRemaining / 10.0;
            debugLabel.setColor(DEBUG_LABEL_GROWING_COLOR);
            debugLabel.setLabel(String.format("grow %.1fs", shownSeconds));
        } else {
            debugLabel.setColor(DEBUG_LABEL_READY_COLOR);
            debugLabel.setLabel("grass");
        }
        centerDebugLabel();
    }

    /** Centers the debug label within the tile after every label-width change. */
    private void centerDebugLabel() {
        double labelX = x + (48 - debugLabel.getWidth()) / 2.0;
        double labelY = y + 30.0;
        debugLabel.setLocation(labelX, labelY);
    }

    /** Rolls a fresh regrow duration so the patch feels less robotic during testing. */
    private double randomRegrowDurationSeconds() {
        if (MAX_REGROW_DURATION_SECONDS <= MIN_REGROW_DURATION_SECONDS) {
            return MIN_REGROW_DURATION_SECONDS;
        }
        return MIN_REGROW_DURATION_SECONDS
            + Math.random() * (MAX_REGROW_DURATION_SECONDS - MIN_REGROW_DURATION_SECONDS);
    }

    private GImage loadSprite(String path) {
        try {
            BufferedImage source = ImageIO.read(new File(path));
            if (source == null) return null;
            BufferedImage trimmed = trimTransparentBounds(source);
            GImage image = new GImage(trimmed);
            image.setSize(100, 100);
            image.setLocation(x, y);
            return image;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private BufferedImage trimTransparentBounds(BufferedImage source) {
        int w = source.getWidth(), h = source.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                if (((source.getRGB(px, py) >>> 24) & 0xFF) == 0) continue;
                if (px < minX) minX = px;
                if (py < minY) minY = py;
                if (px > maxX) maxX = px;
                if (py > maxY) maxY = py;
            }
        }
        return (maxX < minX || maxY < minY) ? source
            : source.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }
}
