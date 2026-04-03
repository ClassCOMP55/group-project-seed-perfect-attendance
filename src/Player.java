import acm.graphics.*;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Player.java
 *
 * The player entity. Extends Entity for position, hitbox, tile-aware movement,
 * health, and sprite rendering. Adds:
 *   - InputHandler-driven movement (WASD/arrows) with wall sliding
 *   - Sword attack (spawns SwordSwing)
 *   - Hole-fall respawn
 *   - Relic flags: hasHalfDamage, hasReflect, hasIntangible (Task 26)
 *   - MarkOfHero flag (Task 24)
 *
 * Person 1 — Engine & Sequences
 */
public class Player extends Entity {

    // ==========================================================
    // CONSTANTS
    // ==========================================================

    /** Player movement speed in pixels per second. */
    private static final double PLAYER_SPEED = 270.0;

    /** Default max health in hearts. */
    private static final int MAX_HEARTS = 3;

    /** Ticks of invincibility after taking damage (i-frames). */
    private static final int IFRAMES_DURATION = 40;

    /** Cooldown ticks between sword swings. */
    private static final int ATTACK_COOLDOWN = 20;

    /**
     * Intangible ability: active window length in game ticks.
     * Tuned for {@code GameLoop} default 16 ms/tick (~62.5 ticks/s).
     * Debug: if duration feels wrong, verify {@link GameLoop#getTickRate()} (ms per tick) and retune with
     * {@code Math.round(seconds * 1000.0 / tickRateMs)}.
     */
    private static final int INTANGIBLE_DURATION = 312;

    /**
     * Intangible ability: ticks until {@link #activateIntangible()} can succeed again (~60 s at 16 ms/tick).
     * Set equal to full cooldown on each activation; decrements every tick alongside
     * {@link #intangibleActiveTicks}, so the ability stays locked out until this reaches 0.
     */
    private static final int INTANGIBLE_COOLDOWN = 3750;

    // ==========================================================
    // FIELDS — identity
    // ==========================================================

    private String name = "Adventurer";
    private String profession = "Wanderer";

    // ==========================================================
    // FIELDS — combat
    // ==========================================================

    /** Active sword swing (null when not attacking). */
    private SwordSwing activeSwing;

    /** Swing that just expired — held until draw() can remove its visuals from the canvas. */
    private SwordSwing expiredSwing;

    /** Cooldown ticks remaining before another swing is allowed. */
    private int attackCooldownTicks;

    /** Invincibility frames remaining after taking damage. */
    private int iframesTicks;

    // ==========================================================
    // FIELDS — respawn
    // ==========================================================

    /** Respawn point — set to room entry position on each room enter. */
    private double respawnX;
    private double respawnY;

    // ==========================================================
    // FIELDS — relic flags (Task 26)
    // ==========================================================

    /** Half-Damage relic: incoming damage is halved. */
    private boolean hasHalfDamage;

    /** Reflect relic: SwordSwing can reflect projectiles. */
    private boolean hasReflect;

    /** Intangible relic: ability button grants brief invincibility. */
    private boolean hasIntangible;

    /** True while the intangible ability window is active. */
    private boolean isIntangibleActive;

    /** Ticks remaining on the intangible active window. */
    private int intangibleActiveTicks;

    /** Cooldown ticks remaining before intangible can be used again. */
    private int intangibleCooldownTicks;

    /**
     * Visual-only GOval for relic invulnerability; not a hitbox.
     * Lazily created on first intangible frame; must be removed in {@link #removeSpriteFromCanvas}
     * and when ability ends so we do not orphan GObjects on the canvas.
     */
    private GOval intangibleAura;

    /** Tracks whether {@link #intangibleAura} was {@code canvas.add}'d (for safe remove / pan). */
    private boolean intangibleAuraOnCanvas;

    // ==========================================================
    // FIELDS — progression (Task 24)
    // ==========================================================

    /** Set to true at the end of the opening sequence. Gates check this. */
    private boolean hasMarkOfHero;

    // ==========================================================
    // CONSTRUCTOR
    // ==========================================================

    /** Sprite asset path — actual file in the assets folder. */
    private static final String PLAYER_SPRITE =
        "assets/visuals/characters/normalized/player-1-idle-front.gif";
    private static final String NORMALIZED_SPRITE_DIR = "assets/visuals/characters/normalized/";
    private static final String PLAYER_IDLE_FRONT = NORMALIZED_SPRITE_DIR + "player-1-idle-front.gif";
    private static final String PLAYER_IDLE_BACK  = NORMALIZED_SPRITE_DIR + "player-1-idle-back.gif";
    private static final String PLAYER_IDLE_LEFT  = NORMALIZED_SPRITE_DIR + "player-1-idle-left.gif";
    private static final String PLAYER_IDLE_RIGHT = NORMALIZED_SPRITE_DIR + "player-1-idle-right.gif";
    private static final String PLAYER_WALK_FRONT = NORMALIZED_SPRITE_DIR + "player-walk-forward.gif";
    private static final String PLAYER_WALK_BACK  = NORMALIZED_SPRITE_DIR + "player-walking-back.gif";
    private static final String PLAYER_WALK_LEFT  = NORMALIZED_SPRITE_DIR + "player-walk-left.gif";
    private static final String PLAYER_WALK_RIGHT = NORMALIZED_SPRITE_DIR + "player-walking-right.gif";

    private final Map<Direction, GImage> idleByDirection = new EnumMap<>(Direction.class);
    private final Map<Direction, GImage> walkByDirection = new EnumMap<>(Direction.class);

    /**
     * Creates a Player with no TileMap (e.g. for save-load / menu use).
     * Do NOT call move() on a Player created this way.
     */
    public Player() {
        super(0, 0, PLAYER_SPRITE, null, MAX_HEARTS, PLAYER_SPEED);
        this.respawnX = x;
        this.respawnY = y;
        initializeDirectionalSprites();
    }

    /**
     * Creates a new Player at (startX, startY) on the given tile map.
     *
     * @param startX  center X in world pixels
     * @param startY  center Y in world pixels
     * @param tileMap the tile map for collision
     */
    public Player(double startX, double startY, TileMap tileMap) {
        super(startX, startY, PLAYER_SPRITE, tileMap, MAX_HEARTS, PLAYER_SPEED);
        this.respawnX = startX;
        this.respawnY = startY;
        initializeDirectionalSprites();
    }

    // ==========================================================
    // TILE MAP — for room transitions
    // ==========================================================

    /**
     * Updates the tile map reference (e.g. when entering a new room).
     * @param tileMap the new room's tile map
     */
    public void setTileMap(TileMap tileMap) {
        this.tileMap = tileMap;
    }

    // ==========================================================
    // RESPAWN
    // ==========================================================

    /**
     * Sets the respawn point. Call when the player enters a new room.
     * @param rx respawn center X
     * @param ry respawn center Y
     */
    public void setSpawnPosition(double rx, double ry) {
        this.respawnX = rx;
        this.respawnY = ry;
    }

    /** Warps the player back to the respawn point (hole fall, death, etc.). */
    private void fallInHole() {
        x = respawnX;
        y = respawnY;
        hitbox.updatePosition(x - 24, y - 24);
        syncVisualPosition();
    }

    // ==========================================================
    // UPDATE — called each tick by the game loop
    // ==========================================================

    /**
     * Main per-tick update. Handles movement, attack, hole detection,
     * and cooldown timers.
     *
     * @param input       the InputHandler to poll for held keys
     * @param enemies     active enemies in the current room (for sword hit detection)
     * @param projectiles active projectiles in the current room
     * @param dt          delta-time in seconds (e.g. 0.016 for ~60fps)
     */
    public void update(InputHandler input, List<Enemy> enemies, List<Projectile> projectiles, double dt) {
        if (iframesTicks > 0) iframesTicks--;
        if (attackCooldownTicks > 0) attackCooldownTicks--;

        // Relic intangible: countdown active window, then clear flag (takeDamage checks isIntangibleActive).
        if (intangibleActiveTicks > 0) {
            intangibleActiveTicks--;
            if (intangibleActiveTicks <= 0) {
                isIntangibleActive = false;
            }
        }
        // Cooldown counts every tick including while active — see INTANGIBLE_COOLDOWN javadoc.
        if (intangibleCooldownTicks > 0) intangibleCooldownTicks--;

        double dx = 0;
        double dy = 0;
        if (input.isHeld(KeyEvent.VK_W) || input.isHeld(KeyEvent.VK_UP))    dy -= 1;
        if (input.isHeld(KeyEvent.VK_S) || input.isHeld(KeyEvent.VK_DOWN))  dy += 1;
        if (input.isHeld(KeyEvent.VK_A) || input.isHeld(KeyEvent.VK_LEFT))  dx -= 1;
        if (input.isHeld(KeyEvent.VK_D) || input.isHeld(KeyEvent.VK_RIGHT)) dx += 1;

        boolean moving = dx != 0 || dy != 0;
        if (moving) {
            double len = Math.sqrt(dx * dx + dy * dy);
            move((dx / len) * speed * dt, (dy / len) * speed * dt);
        }
        applyDirectionalVisual(moving);

        if (isOverHole()) {
            fallInHole();
        }

        if (activeSwing != null) {
            activeSwing.update(enemies, projectiles, hasReflect);
            if (activeSwing.isExpired()) {
                expiredSwing = activeSwing;
                activeSwing = null;
            }
        }

        if (projectiles != null) {
            for (Projectile p : projectiles) {
                if (p.isAlive()) {
                    p.checkHit(this, true);
                }
            }
        }
    }

    /**
     * Convenience overload for callers that don't track dt.
     * Uses the default ~60fps tick rate.
     */
    public void update(InputHandler input, List<Enemy> enemies, List<Projectile> projectiles) {
        update(input, enemies, projectiles, 0.016);
    }

    // ==========================================================
    // ATTACK — sword swing
    // ==========================================================

    /**
     * Attempts to start a sword swing. Called by InputHandler one-shot action.
     * Respects attack cooldown.
     */
    public void attack() {
        if (attackCooldownTicks > 0) return;
        if (activeSwing != null) return;

        activeSwing = new SwordSwing(x, y, facing);
        attackCooldownTicks = ATTACK_COOLDOWN;
    }

    /** @return the current active sword swing, or null if not attacking */
    public SwordSwing getActiveSwing() {
        return activeSwing;
    }

    // ==========================================================
    // INTANGIBLE ABILITY (Task 26)
    // ==========================================================

    /**
     * Relic-gated invulnerability (bound to K from gameplay panes). No-ops silently if gated;
     * callers that need user feedback should branch on the return value or query getters.
     *
     * @return true only if this call started a new intangible window
     */
    public boolean activateIntangible() {
        if (!hasIntangible) {
            return false; // obtain relic first (chest / save / setHasIntangible)
        }
        if (intangibleCooldownTicks > 0) {
            return false; // still recharging — inspect getIntangibleCooldownTicks()
        }
        if (isIntangibleActive) {
            return false; // already invulnerable this window
        }

        isIntangibleActive = true;
        intangibleActiveTicks = INTANGIBLE_DURATION;
        intangibleCooldownTicks = INTANGIBLE_COOLDOWN;
        return true;
    }

    /** @return true if the intangible window is currently active */
    public boolean isIntangibleActive() {
        return isIntangibleActive;
    }

    /** @return ticks remaining on intangible cooldown (0 = ready) */
    public int getIntangibleCooldownTicks() {
        return intangibleCooldownTicks;
    }

    /** @return max cooldown ticks for intangible (for HUD bar scaling) */
    public int getIntangibleCooldownMax() {
        return INTANGIBLE_COOLDOWN;
    }

    /**
     * Returns whether the body sprite should be visible this tick (post-hit i-frame flicker).
     */
    public boolean shouldShowSprite() {
        boolean visible = true;
        if (iframesTicks > 0 && iframesTicks % 4 < 2) {
            visible = false;
        }
        return visible;
    }

    private void applySpriteVisibility(boolean visible) {
        if (sprite != null) {
            sprite.setVisible(visible);
        }
        GImage frame = animator.getCurrentFrame();
        if (frame != null) {
            frame.setVisible(visible);
        }
    }

    // ==========================================================
    // DAMAGE — with relic support
    // ==========================================================

    /**
     * Applies damage to the player, respecting i-frames, intangible,
     * and half-damage relic.
     *
     * @param amount raw damage to apply
     */
    @Override
    public void takeDamage(int amount) {
        if (iframesTicks > 0) return;
        // Relic ability: full invulnerability for projectile/enemy/contact damage paths that use takeDamage.
        if (isIntangibleActive) return;

        if (hasHalfDamage) {
            amount = Math.max(1, amount / 2);
        }

        health = Math.max(0, health - amount);
        iframesTicks = IFRAMES_DURATION;
    }

    // ==========================================================
    // ORIGINAL API — preserved for backward compatibility
    // ==========================================================

    /**
     * Returns the player's current health (hearts).
     * Maps to Entity.health for compatibility with existing code.
     * @return current HP
     */
    public int getHP() {
        return health;
    }

    /**
     * Sets current hearts (clamped 0–max, e.g. when loading a save).
     * @param hp hearts to set
     */
    public void setHP(int hp) {
        health = Math.max(0, Math.min(MAX_HEARTS, hp));
    }

    /**
     * Reduces the player's health by the given amount.
     * Legacy method — delegates to takeDamage for relic support.
     * @param amount the damage to deal
     */
    public void dealDamage(int amount) {
        takeDamage(amount);
    }

    /** @return the player's display name */
    public String getName() {
        return name;
    }

    /** Sets the player's display name (used as [NAME] in dialogue). */
    public void setName(String name) {
        if (name != null && !name.trim().isEmpty()) {
            this.name = name.trim();
        }
    }

    /** @return the player's profession */
    public String getProfession() {
        return profession;
    }

    /** Sets the player's profession (used as [PROFESSION] in dialogue). */
    public void setProfession(String profession) {
        if (profession != null && !profession.trim().isEmpty()) {
            this.profession = profession.trim();
        }
    }

    // ==========================================================
    // POSITION — convenience setters for room transitions
    // ==========================================================

    /** Sets the player's center position and syncs hitbox/sprite. */
    public void setPosition(double newX, double newY) {
        this.x = newX;
        this.y = newY;
        hitbox.updatePosition(x - 24, y - 24);
        syncVisualPosition();
    }

    // ==========================================================
    // RELIC FLAGS (Task 26)
    // ==========================================================

    public boolean hasHalfDamage()  { return hasHalfDamage; }
    public boolean hasReflect()     { return hasReflect; }
    public boolean hasIntangible()  { return hasIntangible; }
    public boolean hasMarkOfHero()  { return hasMarkOfHero; }

    public void setHasHalfDamage(boolean v)  { this.hasHalfDamage = v; }
    public void setHasReflect(boolean v)     { this.hasReflect = v; }
    public void setHasIntangible(boolean v)  { this.hasIntangible = v; }
    public void setHasMarkOfHero(boolean v)  { this.hasMarkOfHero = v; }

    // ==========================================================
    // DRAW
    // ==========================================================

    /**
     * Draws the player and active sword swing onto the canvas.
     * Flickers the sprite during i-frames for visual feedback.
     *
     * @param canvas the GCanvas to draw onto
     */
    @Override
    public void draw(GCanvas canvas) {
        applySpriteVisibility(shouldShowSprite());
        // Z-order: add/update aura before super.draw() so Entity.draw() puts the sprite on top.
        if (isIntangibleActive) {
            updateIntangibleAura(canvas);
        } else {
            removeIntangibleAuraFromCanvas(canvas);
        }
        super.draw(canvas);

        // Clean up expired swing visuals from the canvas
        if (expiredSwing != null) {
            expiredSwing.removeFrom(canvas);
            expiredSwing = null;
        }

        // Draw active sword swing
        if (activeSwing != null) {
            activeSwing.draw(canvas);
        }
    }

    /**
     * RoomTransition pans the player sprite each tick; keep the blue aura aligned or it will drift.
     */
    @Override
    public void panVisual(double panX, double panY) {
        super.panVisual(panX, panY);
        if (intangibleAura != null && intangibleAuraOnCanvas) {
            intangibleAura.move(panX, panY);
        }
    }

    /**
     * Teardown when leaving gameplay / room: aura must leave with the player or it sticks on canvas.
     */
    @Override
    public void removeSpriteFromCanvas(GCanvas canvas) {
        removeIntangibleAuraFromCanvas(canvas);
        super.removeSpriteFromCanvas(canvas);
    }

    /**
     * Positions and styles the intangible halo (pulsing size + opaque blue intensity).
     * Uses fully opaque {@link Color}s — ACM/Swing often ignores alpha on {@link GOval}, which made the aura invisible.
     * Re-adds the oval each frame before {@code super.draw} so it stays above room tiles (new tiles added during
     * transitions would otherwise bury a one-time {@code add}).
     */
    private void updateIntangibleAura(GCanvas canvas) {
        if (intangibleAura == null) {
            intangibleAura = new GOval(0, 0, 64, 64);
            intangibleAura.setFilled(true);
        }
        int elapsed = INTANGIBLE_DURATION - intangibleActiveTicks;
        // Two sine rates: slow “breathing” + faster flicker reads as a magical pulse in motion.
        double slow = 0.5 + 0.5 * Math.sin(elapsed * 0.25);
        double fast = 0.5 + 0.5 * Math.sin(elapsed * 1.15);
        double blend = 0.55 * slow + 0.45 * fast;
        int r = (int) (25 + 40 * blend);
        int g = (int) (95 + 110 * blend);
        int b = (int) (195 + 60 * blend);
        r = Math.min(255, Math.max(0, r));
        g = Math.min(255, Math.max(0, g));
        b = Math.min(255, Math.max(0, b));
        intangibleAura.setFillColor(new Color(r, g, b));
        intangibleAura.setColor(new Color(Math.min(255, r + 50), Math.min(255, g + 45), Math.min(255, b + 25)));
        double diameter = 54 + 20 * slow + 8 * fast;
        intangibleAura.setSize(diameter, diameter);
        intangibleAura.setLocation(x - diameter / 2, y - diameter / 2);
        if (intangibleAuraOnCanvas) {
            canvas.remove(intangibleAura);
        }
        canvas.add(intangibleAura);
        intangibleAuraOnCanvas = true;
    }

    /** Called when intangible ends or canvas is cleared; keeps intangibleAura reusable next activation. */
    private void removeIntangibleAuraFromCanvas(GCanvas canvas) {
        if (canvas == null || intangibleAura == null || !intangibleAuraOnCanvas) {
            return;
        }
        canvas.remove(intangibleAura);
        intangibleAuraOnCanvas = false;
    }

    /**
     * Removes the active sword swing visual from the canvas.
     * Call during room transition cleanup.
     */
    public void removeSwingFrom(GCanvas canvas) {
        if (activeSwing != null) {
            activeSwing.removeFrom(canvas);
            activeSwing = null;
        }
    }

    private void initializeDirectionalSprites() {
        idleByDirection.put(Direction.DOWN, new GImage(PLAYER_IDLE_FRONT, 0, 0));
        idleByDirection.put(Direction.UP, new GImage(PLAYER_IDLE_BACK, 0, 0));
        idleByDirection.put(Direction.LEFT, new GImage(PLAYER_IDLE_LEFT, 0, 0));
        idleByDirection.put(Direction.RIGHT, new GImage(PLAYER_IDLE_RIGHT, 0, 0));

        walkByDirection.put(Direction.DOWN, new GImage(PLAYER_WALK_FRONT, 0, 0));
        walkByDirection.put(Direction.UP, new GImage(PLAYER_WALK_BACK, 0, 0));
        walkByDirection.put(Direction.LEFT, new GImage(PLAYER_WALK_LEFT, 0, 0));
        walkByDirection.put(Direction.RIGHT, new GImage(PLAYER_WALK_RIGHT, 0, 0));

        applyDirectionalVisual(false);
    }

    private void applyDirectionalVisual(boolean moving) {
        Map<Direction, GImage> source;
        source = moving ? walkByDirection : idleByDirection;
        GImage visual = source.get(facing);
        if (visual == null) {
            visual = source.get(Direction.DOWN);
        }
        if (visual != null) {
            animator.setFallbackFrame(visual);
            syncVisualPosition();
        }
    }
}
