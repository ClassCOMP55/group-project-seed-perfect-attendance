/*
Person 2+4: Item — base class for every collectible and usable object in the game
Who RIGs it: Player — holds a List<Item> as inventory; calls item.onUse() from PauseModal inventory tab
             Room — holds a List<Item> for coins/drops lying on the ground; checks player overlap each tick
             WorldObjects (OreNode, Chest, etc.) — create Item instances and give them to Player on interact

Extends: nothing (base class)
Extended by: Coin, HealingBread
             Implied future items: Pickaxe, Ore, BrokenLever, FixedLever (TBD — not yet designed as full classes)

===============
PLAN OF ACTION
===============

- CLASS ROLE
- Item is the common base for everything that can be picked up, held in inventory, or used.
- Item does NOT handle world physics or movement — it is either in a Player's inventory (no world position)
  or lying on the ground as a dropped pickup (has a world position for drawing and overlap checks).
- Item does NOT talk to the canvas directly unless it is a world drop. In inventory, PauseModal draws it.

- TWO STATES AN ITEM CAN BE IN
- IN INVENTORY: held by Player. No world position. Drawn by PauseModal/InventoryMenu using icon.
- WORLD DROP: lying in a Room. Has worldX, worldY. Room draws it, checks player overlap, calls onCollect().

- FIELDS
- String itemId        — unique string key (e.g. "healing_bread", "pickaxe", "coin", "ore")
- String displayName   — human-readable name shown in inventory (e.g. "Healing Bread")
- GImage icon          — small icon drawn in inventory slots (TBD: placeholder GRect until sprites exist)
- boolean stackable    — if true, Player groups duplicates and shows "Name x N" in inventory
- int stackCount       — how many of this item the player holds (only meaningful if stackable = true)
- double worldX, worldY — pixel position when lying in the world as a drop (0 if in inventory)
- boolean inWorld      — true if this item is a world drop awaiting pickup; false if in Player inventory

- CONSTRUCTOR
- Item(String itemId, String displayName, boolean stackable)
  Sets id, name, stackable. icon defaults to null (set later when sprites are ready).
  stackCount = 1 on construction.

- KEY METHODS (stubs — logic written during implementation sprint)
- void onCollect(Player p)
    Called by Room when the player overlaps this item on the ground.
    Default: add this item to p's inventory, set inWorld = false, remove from Room's drop list.
    Subclasses override for special behavior (e.g. Coin directly increments coins counter).

- void onUse(Player p)
    Called by PauseModal when player selects and uses this item.
    Default: does nothing (non-consumables are read-only in inventory).
    Subclasses override: HealingBread restores hearts and removes one from stack.

- void draw(GCanvas canvas)
    Draws the world-drop sprite at (worldX, worldY). Only called by Room when inWorld = true.

- void removeFrom(GCanvas canvas)
    Removes world-drop sprite from canvas. Called by Room after onCollect() fires.

- boolean isUsable()
    Returns true if this item can be used from inventory (e.g. HealingBread = true, Pickaxe = false).

- WHAT ITEM DOES NOT DO
- Item does not check overlap with the player itself — Room's update() loop does that.
- Item does not open any UI — PauseModal handles the inventory display.
- Item does not write to SaveData directly — Player's inventory list is what gets saved.
*/

import acm.graphics.GCanvas;
import acm.graphics.GImage;

/**
 * Base class for every collectible and usable object in the game.
 * Subclasses: {@link Coin}, HealingBread (Person 4).
 * See the PLAN OF ACTION block above before implementing.
 */
public class Item {

    // =========================================================
    // FIELDS
    // =========================================================

    /** Unique string key for this item type (e.g. "healing_bread", "pickaxe", "coin"). */
    protected final String itemId;

    /** Human-readable name shown in inventory UI. */
    protected final String displayName;

    /** Small icon drawn in inventory slots. Null until sprites are wired. */
    protected GImage icon;

    /** If true, duplicates stack in inventory and display as "Name x N". */
    protected final boolean stackable;

    /** How many of this item are in the stack. Always 1 for non-stackables. */
    protected int stackCount;

    /** World pixel X when this item is lying on the ground as a drop. */
    protected double worldX;

    /** World pixel Y when this item is lying on the ground as a drop. */
    protected double worldY;

    /** True if this item is in the world waiting to be picked up; false if in Player's inventory. */
    protected boolean inWorld;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    /**
     * @param itemId      unique key (e.g. "coin", "healing_bread")
     * @param displayName shown in inventory UI
     * @param stackable   true if duplicates should stack (e.g. HealingBread, Coin)
     */
    public Item(String itemId, String displayName, boolean stackable) {
        this.itemId      = itemId;
        this.displayName = displayName;
        this.stackable   = stackable;
        this.stackCount  = 1;
        this.inWorld     = false;
    }

    // =========================================================
    // CORE BEHAVIOR — override in subclasses
    // =========================================================

    /**
     * Called by Room when the player walks over this world drop.
     * Default: adds this item to the player's inventory and marks inWorld = false.
     * Subclasses (e.g. Coin) override to bypass inventory and directly change player state.
     *
     * @param p the Player who collected this item
     */
    public void onCollect(Player p) {
        if (p == null) return;
        inWorld = false;
        p.collectItem(this);
    }

    /**
     * Called by PauseModal when the player selects and uses this item from inventory.
     * Default: does nothing. Only consumables (HealingBread) override this.
     *
     * @param p the Player using this item
     */
    public void onUse(Player p) {
        // TODO: subclass override only — base item cannot be used
    }

    /**
     * Draws the world-drop sprite at (worldX, worldY).
     * Only called by Room when inWorld = true.
     *
     * @param canvas the game canvas
     */
    public void draw(GCanvas canvas) {
        // TODO: add icon (or placeholder GRect) to canvas at worldX, worldY
    }

    /**
     * Removes the world-drop sprite from the canvas.
     * Called by Room after onCollect() fires.
     *
     * @param canvas the game canvas
     */
    public void removeFrom(GCanvas canvas) {
        // TODO: canvas.remove(icon or placeholder)
    }

    // =========================================================
    // QUERIES
    // =========================================================

    /**
     * Returns true if this item can be used from inventory (e.g. HealingBread).
     * Non-consumables (Pickaxe, relics) return false — they are read-only in the menu.
     */
    public boolean isUsable() {
        return false; // override in consumable subclasses
    }

    /**
     * Human-readable description for the pause inventory panel.
     * Subclasses override for item-specific copy.
     */
    public String getDescription() {
        return displayName + " cannot be used from the inventory yet.";
    }

    /**
     * Pickup bounds for auto-collect while this item is on the ground.
     * World drops can override this if their visual is smaller than a full tile.
     */
    public Hitbox getPickupHitbox() {
        return new Hitbox(worldX, worldY, 24, 24);
    }

    /** Sets the world position for this item as a ground drop. */
    public void setWorldPosition(double x, double y) {
        this.worldX  = x;
        this.worldY  = y;
        this.inWorld = true;
    }

    // =========================================================
    // GETTERS
    // =========================================================

    public String getItemId()      { return itemId; }
    public String getDisplayName() { return displayName; }
    public GImage getIcon()        { return icon; }
    public boolean isStackable()   { return stackable; }
    public int getStackCount()     { return stackCount; }
    public boolean isInWorld()     { return inWorld; }
    public double getWorldX()      { return worldX; }
    public double getWorldY()      { return worldY; }

    /** Increments stack count. Only call if stackable = true. */
    public void incrementStack()   { stackCount++; }

    /** Adds multiple items into an existing stack. */
    public void incrementStackBy(int amount) {
        if (amount > 0) {
            stackCount += amount;
        }
    }

    /** Decrements stack count. Returns true if stack is now empty (remove from inventory). */
    public boolean decrementStack() {
        stackCount--;
        return stackCount <= 0;
    }
}
