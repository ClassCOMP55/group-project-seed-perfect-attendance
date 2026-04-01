/*
Roberto: SaveData — snapshot of all game state written to / read from a save file
Who RIGs it: SaveManager — creates SaveData instances on load; SavePoint — fills them on save;
             GameSavesPane — receives them on load and applies to Player + GameState
No extends (plain data class — fields and getters only)

===============
PLAN OF ACTION
===============

- CLASS ROLE (DATA ONLY)
- SaveData is a plain data holder. Fields + getters, no logic.
- SaveData does not read or write files; that is SaveManager's job.
- SaveData does not apply state to the Player or world; callers do that after loading.

- FIELDS TO STORE
- int slot (1–3)                  — which save slot this belongs to
- int hp, int maxHp               — player hearts at time of save
- int coins                       — currency at time of save
- String roomId                   — ID of the room containing the SavePoint used (for respawn destination)
- double spawnX, spawnY           — pixel position of the SavePoint (player lands here on load)
- boolean hasHalfDamage           — relic flag (from Player.java)
- boolean hasReflect              — relic flag (from Player.java)
- boolean hasIntangible           — relic flag (from Player.java)
- boolean hasMarkOfHero           — relic flag (from Player.java)
- List<String> collectedItemIds   — items already picked up (won't re-spawn on load)
- List<String> storyFlags         — NPC / story progression flags

- CONSTRUCTORS
- One all-fields constructor (used by SaveManager after parsing a file).
- One static factory: SaveData.from(int slot, Player p, GameState s, String roomId, double spawnX, double spawnY)
  Reads the live game state into a new SaveData snapshot. Called by SavePoint on save.

- NO GAME LOGIC
- No references to GCanvas, TileMap, InputHandler, or any rendering class.
- No file I/O of any kind.
*/

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of all Zelda-game state needed to restore a save slot.
 * Built by {@link SaveData#from} at save time; parsed back by {@link SaveManager#loadSave}.
 */
public class SaveData {

	// =========================================================
	// FIELDS
	// =========================================================

	/** Save slot this data belongs to (1–3). */
	private final int slot;

	/** Player hearts at time of save. */
	private final int hp;

	/** Player max hearts at time of save. */
	private final int maxHp;

	/** Coins held at time of save. */
	private final int coins;

	/** ID of the room containing the SavePoint used (for respawn routing on load). */
	private final String roomId;

	/** Exact pixel position to place the player on load (center of the SavePoint). */
	private final double spawnX;
	private final double spawnY;

	// Relic power-up flags — mirrors fields in Player.java
	private final boolean hasHalfDamage;
	private final boolean hasReflect;
	private final boolean hasIntangible;
	private final boolean hasMarkOfHero;

	/** Item IDs already collected (so they do not re-spawn on load). */
	private final List<String> collectedItemIds;

	/** Story / NPC progression flags. */
	private final List<String> storyFlags;

	// =========================================================
	// ALL-FIELDS CONSTRUCTOR  (used by SaveManager on parse)
	// =========================================================

	public SaveData(int slot, int hp, int maxHp, int coins,
	                String roomId, double spawnX, double spawnY,
	                boolean hasHalfDamage, boolean hasReflect,
	                boolean hasIntangible, boolean hasMarkOfHero,
	                List<String> collectedItemIds, List<String> storyFlags) {
		this.slot          = slot;
		this.hp            = hp;
		this.maxHp         = maxHp;
		this.coins         = coins;
		this.roomId        = roomId != null ? roomId : "";
		this.spawnX        = spawnX;
		this.spawnY        = spawnY;
		this.hasHalfDamage = hasHalfDamage;
		this.hasReflect    = hasReflect;
		this.hasIntangible = hasIntangible;
		this.hasMarkOfHero = hasMarkOfHero;
		this.collectedItemIds = collectedItemIds != null
			? new ArrayList<>(collectedItemIds) : new ArrayList<>();
		this.storyFlags       = storyFlags != null
			? new ArrayList<>(storyFlags) : new ArrayList<>();
	}

	// =========================================================
	// STATIC FACTORY  (used by SavePoint to snapshot live state)
	// =========================================================

	/**
	 * Builds a SaveData snapshot from the current live game state.
	 * Called by SavePoint just before writing to disk.
	 *
	 * @param slot    the active save slot (1–3)
	 * @param player  the live Player instance
	 * @param state   the live GameState instance
	 * @param roomId  ID of the room containing the SavePoint
	 * @param spawnX  center X of the SavePoint (respawn position)
	 * @param spawnY  center Y of the SavePoint (respawn position)
	 */
	public static SaveData from(int slot, Player player, GameState state,
	                             String roomId, double spawnX, double spawnY) {
		// TODO: replace 0 with state.getCoins() once the coin system is implemented (P2)
		int coins = 0;

		// TODO: replace empty list with actual collected item IDs once item tracking is implemented (P2)
		List<String> collectedItems = new ArrayList<>();

		// TODO: replace empty list with state.getStoryFlags() once getStoryFlags() is added to GameState (P4)
		List<String> flags = new ArrayList<>();

		return new SaveData(
			slot,
			player.getHP(),
			player.getMaxHealth(),
			coins,
			roomId,
			spawnX,
			spawnY,
			player.hasHalfDamage(),
			player.hasReflect(),
			player.hasIntangible(),
			player.hasMarkOfHero(),
			collectedItems,
			flags
		);
	}

	// =========================================================
	// GETTERS
	// =========================================================

	public int     getSlot()           { return slot; }
	public int     getHp()             { return hp; }
	public int     getMaxHp()          { return maxHp; }
	public int     getCoins()          { return coins; }
	public String  getRoomId()         { return roomId; }
	public double  getSpawnX()         { return spawnX; }
	public double  getSpawnY()         { return spawnY; }
	public boolean isHasHalfDamage()   { return hasHalfDamage; }
	public boolean isHasReflect()      { return hasReflect; }
	public boolean isHasIntangible()   { return hasIntangible; }
	public boolean isHasMarkOfHero()   { return hasMarkOfHero; }

	/** Returns an unmodifiable view of collected item IDs. */
	public List<String> getCollectedItemIds() {
		return Collections.unmodifiableList(collectedItemIds);
	}

	/** Returns an unmodifiable view of story flags. */
	public List<String> getStoryFlags() {
		return Collections.unmodifiableList(storyFlags);
	}
}
