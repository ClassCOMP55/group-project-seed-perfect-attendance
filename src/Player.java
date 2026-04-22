import acm.graphics.*;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Player.java
 *
 * The player entity. Extends Entity for position, hitbox, tile-aware movement,
 * health, and sprite rendering. Adds:
 *   - InputHandler-driven movement (WASD/arrows) with wall sliding
 *   - Sword attack (spawns SwordSwing)
 *   - Hole-fall death + respawn
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

    /** Default number of hearts shown on the player HUD. */
    public static final int DEFAULT_HEART_COUNT = 3;

    /** Internal health units per heart. One unit = half a heart. */
    public static final int HALF_HEARTS_PER_HEART = 2;

    /** Default max health in half-heart units. */
    private static final int DEFAULT_MAX_HEALTH = DEFAULT_HEART_COUNT * HALF_HEARTS_PER_HEART;

    /** Ticks of invincibility after taking damage (i-frames). */
    private static final int IFRAMES_DURATION = 40;

    /** Cooldown ticks between sword swings (~500ms at 60fps). */
    private static final int ATTACK_COOLDOWN = 30;

    /**
     * Intangible ability: active window length in game ticks.
     * Tuned for {@code GameLoop} default 16 ms/tick (~62.5 ticks/s).
     */
    private static final int INTANGIBLE_DURATION = 312;

    /**
     * Intangible ability: ticks until {@link #activateIntangible()} can succeed again.
     * Set equal to full cooldown on each activation; decrements every tick alongside
     * {@link #intangibleActiveTicks}.
     */
    private static final int INTANGIBLE_COOLDOWN = 938;

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
    // FIELDS — economy and inventory
    // ==========================================================

    /** Currency picked up from world drops. */
    private int coins;

    /** Inventory used by the pause menu and future world interactables. */
    private final List<Item> inventory = new ArrayList<>();

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
    // BUG: These attack GIFs still are not truly normalized to the same body scale/padding
    // as the idle/walk set. The current asset tweak only reduced canvas space, so the player
    // can still read slightly smaller during swings. Fix the source GIF exports next time.
    private static final String PLAYER_ATTACK_FRONT = NORMALIZED_SPRITE_DIR + "player-attack-front.gif";
    private static final String PLAYER_ATTACK_BACK  = NORMALIZED_SPRITE_DIR + "player-attack-back.gif";
    private static final String PLAYER_ATTACK_LEFT  = NORMALIZED_SPRITE_DIR + "player-attack-left.gif";
    private static final String PLAYER_ATTACK_RIGHT = NORMALIZED_SPRITE_DIR + "player-attack-right.gif";
    private static final String PLAYER_DEATH_FRONT = NORMALIZED_SPRITE_DIR + "player-death-animation-front.gif";
    private static final String PLAYER_DEATH_BACK  = NORMALIZED_SPRITE_DIR + "player-death-animation-back.gif";
    private static final String PLAYER_DEATH_LEFT  = NORMALIZED_SPRITE_DIR + "player-death-animation-left.gif";
    private static final String PLAYER_DEATH_RIGHT = NORMALIZED_SPRITE_DIR + "player-death-animation-right.gif";
    private static final String PLAYER_DEATH_FRONT_FINAL = NORMALIZED_SPRITE_DIR + "player-death-animation-front-final.png";
    private static final String PLAYER_DEATH_BACK_FINAL  = NORMALIZED_SPRITE_DIR + "player-death-animation-back-final.png";
    private static final String PLAYER_DEATH_LEFT_FINAL  = NORMALIZED_SPRITE_DIR + "player-death-animation-left-final.png";
    private static final String PLAYER_DEATH_RIGHT_FINAL = NORMALIZED_SPRITE_DIR + "player-death-animation-right-final.png";

    /** One full player attack GIF play-through is about 0.56s in the debug pane. */
    private static final int ATTACK_ANIM_TICKS = 34;

    /** Ticks for one play-through of the death GIF (~500ms ≈ 30 ticks at 60fps). */
    private static final int DEATH_ANIM_TICKS = 34;

    private final Map<Direction, GImage> idleByDirection = new EnumMap<>(Direction.class);
    private final Map<Direction, GImage> walkByDirection = new EnumMap<>(Direction.class);
    private final Map<Direction, String> attackPathByDirection = new EnumMap<>(Direction.class);
    private final Map<Direction, String> deathPathByDirection = new EnumMap<>(Direction.class);
    private final Map<Direction, String> deathFinalPathByDirection = new EnumMap<>(Direction.class);

    /** Active body attack animation visual and timer. */
    private GImage attackVisual = null;
    private int attackAnimTicksLeft = 0;
    private Direction attackAnimDirection = Direction.DOWN;

    /** True once triggerDeathAnimation() has been called — prevents re-triggering. */
    private boolean isDying = false;

    /** Ticks remaining before the animated death GIF is swapped to a static final frame. */
    private int deathAnimTicksLeft = 0;

    /** The GImage currently on canvas during the death state — tracked for clean removal. */
    private GImage deathVisualOnCanvas = null;

    /** The previous death visual that needs removing when swapping GIF → PNG. */
    private GImage previousDeathVisual = null;

    /** True once the initial death visual has been placed on canvas. */
    private boolean deathSpriteAdded = false;

    /**
     * Creates a Player with no TileMap (e.g. for save-load / menu use).
     * Do NOT call move() on a Player created this way.
     */
    public Player() {
        super(0, 0, PLAYER_SPRITE, null, DEFAULT_MAX_HEALTH, PLAYER_SPEED);
        this.respawnX = x;
        this.respawnY = y;
        hitbox = new Hitbox(x - 22, y - 22, 44, 44);
        setSpriteRenderSize(58, 58);
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
        super(startX, startY, PLAYER_SPRITE, tileMap, DEFAULT_MAX_HEALTH, PLAYER_SPEED);
        this.respawnX = startX;
        this.respawnY = startY;
        hitbox = new Hitbox(startX - 22, startY - 22, 44, 44);
        setSpriteRenderSize(58, 58);
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

    @Override
    protected boolean allowsOutOfBoundsMovement() {
        return true;
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

    @Override
    protected boolean isFullyOverHole() {
        if (tileMap == null) return false;
        return tileMap.isHole(x - 20, y)
            && tileMap.isHole(x + 20, y)
            && tileMap.isHole(x, y - 22)
            && tileMap.isHole(x, y + 22);
    }

    /** Converts a hole fall into a real death so GameplayPane can play the normal respawn flow. */
    private void fallInHole() {
        takeDamage(1);
        x = respawnX;
        y = respawnY;
        hitbox.updatePosition(x - 22, y - 22);
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
        if (isDying) return;
        if (iframesTicks > 0) iframesTicks--;
        if (attackCooldownTicks > 0) attackCooldownTicks--;
        if (attackAnimTicksLeft > 0) {
            attackAnimTicksLeft--;
            if (attackAnimTicksLeft <= 0) {
                attackVisual = null;
            }
        }

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
        boolean strafeLock = input.isHeld(KeyEvent.VK_SHIFT);
        if (moving) {
            Direction savedFacing = facing;
            double len = Math.sqrt(dx * dx + dy * dy);
            move((dx / len) * speed * dt, (dy / len) * speed * dt);
            if (strafeLock) facing = savedFacing;
        }
        applyDirectionalVisual(moving);

        if (isFullyOverHole()) {
            fallInHole();
            return;
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
        GameSFX.play(GameSFX.SFX.SWORD_SWING);
        attackAnimDirection = (facing != null) ? facing : Direction.DOWN;
        String path = attackPathByDirection.get(attackAnimDirection);
        if (path == null) path = attackPathByDirection.get(Direction.DOWN);
        if (path != null) {
            // Fresh GImage each swing so the attack GIF starts from frame 1.
            attackVisual = new GImage(path, 0, 0);
            animator.setFallbackFrame(attackVisual);
            syncVisualPosition();
        }
        attackAnimTicksLeft = ATTACK_ANIM_TICKS;
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
     * Relic-gated invulnerability shield (bound to K from gameplay panes). No-ops silently if gated;
     * callers that need user feedback should branch on the return value or query getters.
     *
     * @return true only if this call started a new shield window
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
        // # rig — play GameSFX.SFX.INTANGIBLE_ACTIVATE here once a sound is added to the catalog
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

    /** @return ticks remaining on intangible active window */
    public int getIntangibleActiveTicks() { return intangibleActiveTicks; }

    /** @return max active ticks for intangible */
    public int getIntangibleActiveMax() { return INTANGIBLE_DURATION; }

    /** @return ticks remaining on attack cooldown */
    public int getAttackCooldownTicks() { return attackCooldownTicks; }

    /** @return max attack cooldown ticks */
    public int getAttackCooldownMax() { return ATTACK_COOLDOWN; }

    /** @return ticks remaining on iframe invulnerability */
    public int getIframeTicks() { return iframesTicks; }

    /** @return max iframe ticks */
    public int getIframeMax() { return IFRAMES_DURATION; }

    /**
     * Returns whether the body sprite should be visible this tick (post-hit i-frame flicker).
     */
    public boolean shouldShowSprite() {
        if (isDying) return true; // always visible during death animation
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
        GameSFX.play(GameSFX.SFX.PLAYER_HURT);
    }

    // ==========================================================
    // ORIGINAL API — preserved for backward compatibility
    // ==========================================================

    /**
     * Returns the player's current health in half-heart units.
     * Example: {@code 6 = 3 hearts}, {@code 5 = 2.5 hearts}.
     */
    public int getHP() {
        return health;
    }

    /**
     * Sets current health in half-heart units (clamped 0–max).
     */
    public void setHP(int hp) {
        health = Math.max(0, Math.min(maxHealth, hp));
    }

    /**
     * Sets the player's maximum health in half-heart units while preserving
     * the current health when possible.
     */
    public void setMaxHealth(int maxHealth) {
        this.maxHealth = Math.max(1, maxHealth);
        health = Math.min(health, this.maxHealth);
    }

    /** Adds or removes coins while never letting the wallet drop below zero. */
    public void addCoins(int amount) {
        coins = Math.max(0, coins + amount);
    }

    /** Returns the current wallet total. */
    public int getCoins() {
        return coins;
    }

    /** Overwrites the wallet total (used by save/load and debug setups). */
    public void setCoins(int coins) {
        this.coins = Math.max(0, coins);
    }

    /**
     * Adds an item to the player's inventory, stacking by {@code itemId} when possible.
     */
    public void collectItem(Item item) {
        if (item == null) return;
        item.inWorld = false;
        // # rig — play GameSFX.SFX.ITEM_USE here when a consumable is picked up, once the catalog has a pickup sound

        if (item.isStackable()) {
            Item existing = findInventoryItem(item.getItemId());
            if (existing != null) {
                existing.incrementStackBy(item.getStackCount());
                return;
            }
        }

        inventory.add(item);
    }

    /** Removes one unit from the given inventory item reference. */
    public boolean consumeInventoryItem(Item item) {
        if (item == null) return false;
        if (!inventory.contains(item)) return false;

        if (item.isStackable() && item.getStackCount() > 1) {
            item.decrementStack();
            return true;
        }
        return inventory.remove(item);
    }

    /** Uses the item at the given inventory index if it is usable. */
    public boolean useInventoryItem(int index) {
        Item item = getInventoryItem(index);
        if (item == null || !item.isUsable()) return false;

        int hpBefore = getHP();
        int coinsBefore = coins;
        int sizeBefore = inventory.size();
        int stackBefore = item.getStackCount();

        item.onUse(this);

        boolean stackChanged = inventory.contains(item) && item.getStackCount() != stackBefore;
        return getHP() != hpBefore
            || coins != coinsBefore
            || inventory.size() != sizeBefore
            || stackChanged;
    }

    /** Removes all inventory contents. Used by save-load to rebuild stacks cleanly. */
    public void clearInventory() {
        inventory.clear();
    }

    /** Returns the total count of an itemId across the inventory. */
    public int getItemCount(String itemId) {
        if (itemId == null) return 0;
        Item item = findInventoryItem(itemId);
        return item == null ? 0 : item.getStackCount();
    }

    /** Finds the first inventory entry with the given itemId. */
    public Item findInventoryItem(String itemId) {
        if (itemId == null) return null;
        for (Item item : inventory) {
            if (itemId.equals(item.getItemId())) {
                return item;
            }
        }
        return null;
    }

    /** Read-only view of the player's inventory for UI code. */
    public List<Item> getInventory() {
        return Collections.unmodifiableList(inventory);
    }

    /** Returns the item at the given index, or null if out of bounds. */
    public Item getInventoryItem(int index) {
        if (index < 0 || index >= inventory.size()) return null;
        return inventory.get(index);
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

    /** Uses 22px (half of 44) so wall probing matches the player's reduced hitbox. */
    @Override
    protected int collisionHalf() { return 22; }

    /** Sets the player's center position and syncs hitbox/sprite. */
    public void setPosition(double newX, double newY) {
        this.x = newX;
        this.y = newY;
        hitbox.updatePosition(x - 22, y - 22);
        syncVisualPosition();
    }

    // ==========================================================
    // RELIC FLAGS (Task 26)
    // ==========================================================

    public boolean hasHalfDamage()  { return hasHalfDamage; }
    public boolean hasReflect()     { return hasReflect; }
    public boolean hasIntangible()  { return hasIntangible; }
    public boolean hasMarkOfHero()  { return hasMarkOfHero; }
    public Direction getFacing()    { return facing; }

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
        if (isDying) {
            removeIntangibleAuraFromCanvas(canvas);
            // Bypass Entity.draw() entirely — ACM's setSize() corrupts animated GIFs.
            if (deathVisualOnCanvas == null) return;

            // First death tick: remove old idle/walk sprite, add death GIF
            if (!deathSpriteAdded) {
                // strip everything Entity knows about off the canvas
                if (sprite != null) canvas.remove(sprite);
                GImage af = animator.getCurrentFrame();
                if (af != null && af != sprite) canvas.remove(af);
                // add the death visual
                deathVisualOnCanvas.setVisible(true);
                canvas.add(deathVisualOnCanvas);
                deathSpriteAdded = true;
            }

            // Swap from animated GIF to static PNG when tickDeathAnimation signals
            if (previousDeathVisual != null) {
                canvas.remove(previousDeathVisual);
                previousDeathVisual = null;
                deathVisualOnCanvas.setVisible(true);
                canvas.add(deathVisualOnCanvas);
            }

            // Keep positioned
            deathVisualOnCanvas.setLocation(
                x - deathVisualOnCanvas.getWidth() / 2,
                y - deathVisualOnCanvas.getHeight() / 2);
            return;
        }

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
        removeSwingFrom(canvas);
        if (attackVisual != null) canvas.remove(attackVisual);
        if (deathVisualOnCanvas != null) canvas.remove(deathVisualOnCanvas);
        if (previousDeathVisual != null) canvas.remove(previousDeathVisual);
        attackVisual = null;
        attackAnimTicksLeft = 0;
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
        if (canvas != null && activeSwing != null) {
            activeSwing.removeFrom(canvas);
        }
        activeSwing = null;

        if (canvas != null && expiredSwing != null) {
            expiredSwing.removeFrom(canvas);
        }
        expiredSwing = null;
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

        attackPathByDirection.put(Direction.DOWN, PLAYER_ATTACK_FRONT);
        attackPathByDirection.put(Direction.UP, PLAYER_ATTACK_BACK);
        attackPathByDirection.put(Direction.LEFT, PLAYER_ATTACK_LEFT);
        attackPathByDirection.put(Direction.RIGHT, PLAYER_ATTACK_RIGHT);

        deathPathByDirection.put(Direction.DOWN, PLAYER_DEATH_FRONT);
        deathPathByDirection.put(Direction.UP, PLAYER_DEATH_BACK);
        deathPathByDirection.put(Direction.LEFT, PLAYER_DEATH_LEFT);
        deathPathByDirection.put(Direction.RIGHT, PLAYER_DEATH_RIGHT);

        deathFinalPathByDirection.put(Direction.DOWN, PLAYER_DEATH_FRONT_FINAL);
        deathFinalPathByDirection.put(Direction.UP, PLAYER_DEATH_BACK_FINAL);
        deathFinalPathByDirection.put(Direction.LEFT, PLAYER_DEATH_LEFT_FINAL);
        deathFinalPathByDirection.put(Direction.RIGHT, PLAYER_DEATH_RIGHT_FINAL);

        attackVisual = null;
        attackAnimTicksLeft = 0;
        applyDirectionalVisual(false);
    }

    private void applyDirectionalVisual(boolean moving) {
        if (isDying) return; // lock visuals during death
        if (attackAnimTicksLeft > 0 && attackVisual != null) {
            animator.setFallbackFrame(attackVisual);
            syncVisualPosition();
            return;
        }
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

    /**
     * Switches the player sprite to the death animation for the current facing direction.
     * Called once by GameplayPane when health reaches 0.
     */
    public void triggerDeathAnimation() {
        if (isDying) return;
        isDying = true;
        isIntangibleActive = false;
        intangibleActiveTicks = 0;
        attackVisual = null;
        attackAnimTicksLeft = 0;
        deathAnimTicksLeft = DEATH_ANIM_TICKS;
        deathSpriteAdded = false;
        previousDeathVisual = null;
        String path = deathPathByDirection.get(facing);
        if (path == null) path = deathPathByDirection.get(Direction.DOWN);
        deathVisualOnCanvas = new GImage(path, 0, 0); // fresh GImage every death
        animator.clearFrames();
    }

    /**
     * Called every tick while the player is dying. After the animated GIF has played
     * through once, swaps to a static final-frame PNG so the corpse doesn't loop.
     */
    public void tickDeathAnimation() {
        if (!isDying) return;
        if (deathAnimTicksLeft > 0) {
            deathAnimTicksLeft--;
            if (deathAnimTicksLeft <= 0) {
                String path = deathFinalPathByDirection.get(facing);
                if (path == null) path = deathFinalPathByDirection.get(Direction.DOWN);
                if (path != null) {
                    previousDeathVisual = deathVisualOnCanvas;
                    deathVisualOnCanvas = new GImage(path, 0, 0); // fresh static PNG
                }
            }
        }
    }

    /** True once the death animation has been triggered. */
    public boolean isDying() {
        return isDying;
    }

    /**
     * Resets the player to a fully alive state and warps to spawn.
     * Re-initializes directional sprites since triggerDeathAnimation()
     * clears the animator's frame lists.
     */
    public void resetDeathState() {
        isDying = false;
        deathAnimTicksLeft = 0;
        deathVisualOnCanvas = null;
        previousDeathVisual = null;
        deathSpriteAdded = false;
        health = maxHealth;
        iframesTicks = IFRAMES_DURATION; // brief post-respawn invulnerability
        initializeDirectionalSprites(); // rebuild direction frames wiped by clearFrames()
        x = respawnX;
        y = respawnY;
        hitbox.updatePosition(x - 22, y - 22);
        syncVisualPosition();
    }
}
