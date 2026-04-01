import acm.graphics.*;
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
 *   - Card hand (narrative system)
 *   - Relic flags: hasHalfDamage, hasReflect, hasIntangible (Task 26)
 *   - MarkOfHero flag (Task 24)
 *
 * Preserves the original Player API (getHP, setHP, dealDamage, getName, etc.)
 * so all existing code (GameState, dialogue, HUD, save/load) compiles unchanged.
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

    /** Ticks the intangible ability lasts when activated. */
    private static final int INTANGIBLE_DURATION = 25;

    /** Cooldown ticks for the intangible ability after use. */
    private static final int INTANGIBLE_COOLDOWN = 300;

    /** Length of the brief fade pulse when god mode absorbs a hit. */
    private static final int GOD_MODE_FADE_TICKS = 14;

    // ==========================================================
    // FIELDS — identity & narrative
    // ==========================================================

    private Hand hand;
    private String name = "Adventurer";
    private String profession = "Wanderer";

    // ==========================================================
    // FIELDS — combat
    // ==========================================================

    /** Active sword swing (null when not attacking). */
    private SwordSwing activeSwing;

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

    /** Toggleable debug invulnerability. */
    private boolean godModeEnabled;

    /** Short fade-style pulse after god mode absorbs a hit. */
    private int godModeFadeTicks;

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
        this.hand = new Hand();
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
        this.hand = new Hand();
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
        if (godModeFadeTicks > 0) godModeFadeTicks--;
        if (intangibleActiveTicks > 0) {
            intangibleActiveTicks--;
            if (intangibleActiveTicks <= 0) {
                isIntangibleActive = false;
            }
        }
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
     * Activates the intangible ability if available.
     * Called by InputHandler one-shot action on ability key.
     */
    public void activateIntangible() {
        if (!hasIntangible) return;
        if (intangibleCooldownTicks > 0) return;
        if (isIntangibleActive) return;

        isIntangibleActive = true;
        intangibleActiveTicks = INTANGIBLE_DURATION;
        intangibleCooldownTicks = INTANGIBLE_COOLDOWN;
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

    /** Toggles persistent no-damage debug mode. */
    public void toggleGodMode() {
        godModeEnabled = !godModeEnabled;
        if (!godModeEnabled) {
            godModeFadeTicks = 0;
            applySpriteVisibility(true);
        }
    }

    /** @return true while debug god mode is enabled */
    public boolean isGodModeEnabled() {
        return godModeEnabled;
    }

    /** @return remaining ticks on the god-mode hit fade pulse */
    public int getGodModeFadeTicks() {
        return godModeFadeTicks;
    }

    /**
     * Returns whether the body sprite should be visible this tick.
     * God mode uses a brief fade-style pulse when a hit is absorbed.
     */
    public boolean shouldShowSprite() {
        boolean visible = true;

        if (godModeFadeTicks > 0) {
            int elapsed = GOD_MODE_FADE_TICKS - godModeFadeTicks;
            double progress = GOD_MODE_FADE_TICKS <= 1
                ? 1.0
                : elapsed / (double) (GOD_MODE_FADE_TICKS - 1);

            if (progress < 0.25) {
                visible = elapsed % 2 == 0;
            } else if (progress < 0.55) {
                visible = false;
            } else if (progress < 0.85) {
                visible = elapsed % 2 == 0;
            }
        }

        if (iframesTicks > 0 && iframesTicks % 4 < 2) {
            visible = false;
        }
        return visible;
    }

    private void triggerGodModeFade() {
        if (godModeFadeTicks <= 0) {
            godModeFadeTicks = GOD_MODE_FADE_TICKS;
        }
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
        if (godModeEnabled) {
            triggerGodModeFade();
            return;
        }
        if (iframesTicks > 0) return;
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

    /** @return the player's hand of cards */
    public Hand getHand() {
        return hand;
    }

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
        super.draw(canvas);

        // Draw active sword swing
        if (activeSwing != null) {
            activeSwing.draw(canvas);
        }
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
