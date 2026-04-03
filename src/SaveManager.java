/*
Roberto: SaveManager — reads and writes SaveData to game/save1.json … save3.json
Who RIGs it: SavePoint — calls writeSave() after player confirms; GameSavesPane — calls loadSave() and slotOccupied()
No extends (static utility class — all methods are static, no instances)

===============
PLAN OF ACTION
===============

- CLASS ROLE (FILE I/O ONLY)
- SaveManager handles all disk reads and writes for the "Zelda-like" save system.
- SaveManager does not apply loaded data to the Player or world; callers do that.
- SaveManager replaces GameSaveIO for the new game. GameSaveIO is kept only so old code still compiles.

- FILE FORMAT
- Hand-written JSON, version field = 3.
  (Old VN saves used version 1 and 2; version 3 signals a Zelda save.)
- Paths: game/save1.json, game/save2.json, game/save3.json — same directory as old system.
- No external JSON library; fields are written and parsed manually (same pattern as GameSaveIO).

- KEY METHODS
- writeSave(int slot, SaveData data)    — builds JSON string from SaveData, writes to file.
- SaveData loadSave(int slot)           — reads file, parses fields, returns a populated SaveData.
- boolean slotOccupied(int slot)        — true if a version-3 save file exists and is non-empty.
- void deleteSave(int slot)             — deletes the file for that slot.

- BACKWARD COMPAT NOTE
- Old saves (version 1 or 2) are treated as empty by slotOccupied().
  The player will see those slots as "Create Save" — a clean reset on first launch after the pivot.

- SAVE COUNT
- SAVE_COUNT = 3 (same as GameSaveIO.SAVE_COUNT).

- CLEANUP (do after full team confirms nothing references old system)
- DELETE src/GameSaveIO.java — fully replaced by this class.
- STRIP from src/GameState.java (do not delete the file, just remove dead fields):
    scene1NodeId, scene1LineIndex, scene1ShowingChoice, scene1RejoinId
    scene2NodeId, scene2LineIndex, scene2ShowingChoice, scene2RejoinId
    personalityQuizQuestionIndex, personalityQuizScores, archetypeScores
    appendFormatV2SaveJson(), updateScene1Checkpoint(), clearScene1Checkpoint(), and Scene 2 equivalents

- P1 FIX NEEDED — Player.setHP() currently clamps to MAX_HEARTS = 3 (hardcoded constant).
  SavePoint calls player.setHP(player.getMaxHealth()) to heal on save, but the clamp means
  it will never heal beyond 3 hearts even if maxHealth is 6 or 12.
  P1 must change setHP() to clamp against maxHealth (the live field) instead of MAX_HEARTS.

- ROOM WIRING CHECKLIST (P2 / P4 — do when the Inn or SaveCrystal room is built)
  Drop these four wiring lines into the room that hosts a SavePoint:

    // 1. Room setup — create and add the SavePoint
    savePoint = new SavePoint(x, y, "INN_ROOM", SavePoint.SavePointType.INN_DOOR, x, y);
    savePoint.addTo(canvas);

    // 2. Room setup — register the interact key (fires once per press, not on hold)
    inputHandler.onPress(KeyEvent.VK_J,
        () -> savePoint.tryInteract(player, mainScreen.getDialogue(), gameState));

    // 3. Room tick (inside update loop)
    savePoint.update(dt);

    // 4. Room teardown / transition out
    savePoint.removeFrom(canvas);
    inputHandler.removeOnPress(KeyEvent.VK_J);
*/

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Static helper that reads and writes {@link SaveData} as hand-written JSON
 * under {@code game/save1.json} … {@code save3.json}.
 * Version field 3 distinguishes Zelda saves from old VN saves (versions 1–2).
 */
public final class SaveManager {

	public static final int SAVE_COUNT    = 3;
	private static final int FORMAT_VERSION = 3;
	private static final long MAX_SAVE_FILE_BYTES = 64 * 1024;
	private static final int MAX_STRING_ARRAY_ENTRIES = 256;
	private static final int MAX_STRING_ENTRY_LENGTH = 128;
	private static final int MAX_SAVE_HEARTS = 99;
	private static final int MAX_SAVE_COINS = 999_999;
	private static final int MAX_SAVE_HEALING_BREAD = 99;
	private static final double DEFAULT_SPAWN_X = 320.0;
	private static final double DEFAULT_SPAWN_Y = 240.0;

	private SaveManager() {}

	// =========================================================
	// PATH HELPERS
	// =========================================================

	public static Path savePath(int slot) {
		if (slot < 1 || slot > SAVE_COUNT) {
			throw new IllegalArgumentException("slot must be 1–3");
		}
		return Paths.get("game", "save" + slot + ".json");
	}

	private static void ensureGameDirectory() throws IOException {
		Path dir = Paths.get("game");
		if (!Files.isDirectory(dir)) {
			Files.createDirectories(dir);
		}
	}

	// =========================================================
	// SLOT STATUS
	// =========================================================

	/**
	 * Returns true only if a version-3 (Zelda) save exists for this slot.
	 * Old VN saves (version 1 or 2) are treated as empty.
	 */
	public static boolean slotOccupied(int slot) {
		Path p = savePath(slot);
		try {
			if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) || Files.size(p) < 2) {
				return false;
			}
			if (Files.size(p) > MAX_SAVE_FILE_BYTES) {
				return false;
			}
			String raw = readSaveText(p).trim();
			return readIntField(raw, "version", 0) == FORMAT_VERSION;
		} catch (IOException e) {
			return false;
		}
	}

	/** Deletes the save file for this slot. No-op if the file does not exist. */
	public static void deleteSave(int slot) {
		try {
			boolean existed = Files.deleteIfExists(savePath(slot));
			System.out.println("[SaveManager] " + (existed ? "Deleted" : "No file for") + " slot " + slot);
		} catch (IOException e) {
			System.err.println("[SaveManager] Could not delete slot " + slot + ": " + e.getMessage());
		}
	}

	// =========================================================
	// WRITE
	// =========================================================

	/**
	 * Serialises {@code data} to JSON and writes it to {@code game/saveN.json}.
	 *
	 * @param slot the save slot (1–3)
	 * @param data the snapshot to persist
	 * @throws IOException if the file cannot be written
	 */
	public static void writeSave(int slot, SaveData data) throws IOException {
		ensureGameDirectory();
		StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"version\": ").append(FORMAT_VERSION).append(",\n");
		json.append("  \"slot\": ").append(slot).append(",\n");
		json.append("  \"hp\": ").append(data.getHp()).append(",\n");
		json.append("  \"maxHp\": ").append(data.getMaxHp()).append(",\n");
		json.append("  \"coins\": ").append(data.getCoins()).append(",\n");
		json.append("  \"healingBreadCount\": ").append(data.getHealingBreadCount()).append(",\n");
		json.append("  \"roomId\": \"").append(escape(data.getRoomId())).append("\",\n");
		json.append("  \"spawnX\": ").append(data.getSpawnX()).append(",\n");
		json.append("  \"spawnY\": ").append(data.getSpawnY()).append(",\n");
		json.append("  \"hasHalfDamage\": ").append(data.isHasHalfDamage()).append(",\n");
		json.append("  \"hasReflect\": ").append(data.isHasReflect()).append(",\n");
		json.append("  \"hasIntangible\": ").append(data.isHasIntangible()).append(",\n");
		json.append("  \"hasMarkOfHero\": ").append(data.isHasMarkOfHero()).append(",\n");
		json.append("  \"lastSavedAtMillis\": ").append(data.getLastSavedAtMillis()).append(",\n");

		json.append("  \"collectedItems\": [");
		List<String> items = data.getCollectedItemIds();
		for (int i = 0; i < items.size(); i++) {
			json.append("\"").append(escape(items.get(i))).append("\"");
			if (i < items.size() - 1) json.append(", ");
		}
		json.append("],\n");

		json.append("  \"storyFlags\": [");
		List<String> flags = data.getStoryFlags();
		for (int i = 0; i < flags.size(); i++) {
			json.append("\"").append(escape(flags.get(i))).append("\"");
			if (i < flags.size() - 1) json.append(", ");
		}
		json.append("]\n}\n");

		writeAtomically(savePath(slot), json.toString());
		System.out.println("[SaveManager] Wrote slot " + slot
			+ " room=" + data.getRoomId() + " hp=" + data.getHp());
	}

	// =========================================================
	// LOAD
	// =========================================================

	/**
	 * Reads {@code game/saveN.json} and returns a populated {@link SaveData}.
	 *
	 * @param slot the save slot (1–3)
	 * @return the loaded snapshot
	 * @throws IOException if the file does not exist or cannot be read
	 */
	public static SaveData loadSave(int slot) throws IOException {
		Path p = savePath(slot);
		if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException("No save file for slot " + slot);
		}
		String raw = readSaveText(p).trim();
		if (raw.isEmpty()) {
			throw new IOException("Save file is empty for slot " + slot);
		}

		int    version      = readIntField(raw, "version", 0);
		if (version != FORMAT_VERSION) {
			throw new IOException("Unsupported save version " + version + " for slot " + slot);
		}
		int    savedSlot    = readIntField(raw, "slot", slot);
		int    maxHp        = clampInt(readIntField(raw, "maxHp", 3), 1, MAX_SAVE_HEARTS);
		int    hp           = clampInt(readIntField(raw, "hp", 3), 0, maxHp);
		int    coins        = clampInt(readIntField(raw, "coins", 0), 0, MAX_SAVE_COINS);
		int    healingBread = clampInt(
			readIntField(raw, "healingBreadCount", 0), 0, MAX_SAVE_HEALING_BREAD);
		String roomId       = readStringField(raw, "roomId", "");
		double spawnX       = sanitizeFiniteDouble(readDoubleField(raw, "spawnX", DEFAULT_SPAWN_X), DEFAULT_SPAWN_X);
		double spawnY       = sanitizeFiniteDouble(readDoubleField(raw, "spawnY", DEFAULT_SPAWN_Y), DEFAULT_SPAWN_Y);
		boolean halfDmg     = readBoolField(raw, "hasHalfDamage", false);
		boolean reflect     = readBoolField(raw, "hasReflect", false);
		boolean intangible  = readBoolField(raw, "hasIntangible", false);
		boolean mark        = readBoolField(raw, "hasMarkOfHero", false);
		long lastSavedAtMillis = Math.max(0L, readLongField(raw, "lastSavedAtMillis", 0L));
		List<String> items  = readStringArrayField(raw, "collectedItems");
		List<String> flags  = readStringArrayField(raw, "storyFlags");

		System.out.println("[SaveManager] Loaded slot " + slot
			+ " version=" + version + " room=" + roomId + " hp=" + hp);

		return new SaveData(savedSlot, hp, maxHp, coins, healingBread, roomId, spawnX, spawnY,
		                    halfDmg, reflect, intangible, mark, items, flags, lastSavedAtMillis);
	}

	private static String readSaveText(Path path) throws IOException {
		long size = Files.size(path);
		if (size > MAX_SAVE_FILE_BYTES) {
			throw new IOException("Save file too large: " + path.getFileName());
		}
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
	}

	private static void writeAtomically(Path target, String text) throws IOException {
		Path dir = target.getParent();
		if (dir == null) {
			throw new IOException("Save path has no parent directory: " + target);
		}
		Path temp = Files.createTempFile(dir, target.getFileName().toString(), ".tmp");
		try {
			Files.write(temp, text.getBytes(StandardCharsets.UTF_8));
			try {
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ex) {
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temp);
		}
	}

	// =========================================================
	// JSON PARSE HELPERS  (same pattern as GameSaveIO)
	// =========================================================

	private static int readIntField(String json, String key, int def) {
		String k = "\"" + key + "\"";
		int i = json.indexOf(k);
		if (i < 0) return def;
		int colon = json.indexOf(':', i);
		if (colon < 0) return def;
		int j = colon + 1;
		while (j < json.length() && Character.isWhitespace(json.charAt(j))) j++;
		int end = j;
		if (end < json.length() && json.charAt(end) == '-') end++;
		while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
		try {
			return Integer.parseInt(json.substring(j, end).trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static long readLongField(String json, String key, long def) {
		String k = "\"" + key + "\"";
		int i = json.indexOf(k);
		if (i < 0) return def;
		int colon = json.indexOf(':', i);
		if (colon < 0) return def;
		int j = colon + 1;
		while (j < json.length() && Character.isWhitespace(json.charAt(j))) j++;
		int end = j;
		if (end < json.length() && json.charAt(end) == '-') end++;
		while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
		try {
			return Long.parseLong(json.substring(j, end).trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static double readDoubleField(String json, String key, double def) {
		String k = "\"" + key + "\"";
		int i = json.indexOf(k);
		if (i < 0) return def;
		int colon = json.indexOf(':', i);
		if (colon < 0) return def;
		int j = colon + 1;
		while (j < json.length() && Character.isWhitespace(json.charAt(j))) j++;
		int end = j;
		if (end < json.length() && json.charAt(end) == '-') end++;
		while (end < json.length()
			&& (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.')) end++;
		try {
			return Double.parseDouble(json.substring(j, end).trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static boolean readBoolField(String json, String key, boolean def) {
		String k = "\"" + key + "\"";
		int keyPos = json.indexOf(k);
		if (keyPos < 0) return def;
		int colon = json.indexOf(':', keyPos + k.length());
		if (colon < 0) return def;
		int j = colon + 1;
		while (j < json.length() && Character.isWhitespace(json.charAt(j))) j++;
		if (json.regionMatches(j, "true",  0, 4)) return true;
		if (json.regionMatches(j, "false", 0, 5)) return false;
		return def;
	}

	private static String readStringField(String json, String key, String def) {
		String pat = "\"" + key + "\"";
		int i = json.indexOf(pat);
		if (i < 0) return def;
		int q1 = json.indexOf('"', i + pat.length());
		if (q1 < 0) return def;
		int q2 = json.indexOf('"', q1 + 1);
		if (q2 < 0) return def;
		return unescape(json.substring(q1 + 1, q2));
	}

	private static List<String> readStringArrayField(String json, String key) {
		List<String> out = new ArrayList<>();
		String pat = "\"" + key + "\"";
		int i = json.indexOf(pat);
		if (i < 0) return out;
		int lb = json.indexOf('[', i);
		if (lb < 0) return out;
		int j = lb + 1;
		while (j < json.length()) {
			int q = json.indexOf('"', j);
			if (q < 0) break;
			q++;
			StringBuilder sb = new StringBuilder();
			while (q < json.length()) {
				char c = json.charAt(q);
				if (c == '\\' && q + 1 < json.length()) {
					sb.append(json.charAt(q + 1));
					q += 2;
				} else if (c == '"') {
					break;
				} else {
					sb.append(c);
					q++;
				}
			}
			if (out.size() >= MAX_STRING_ARRAY_ENTRIES) {
				break;
			}
			String value = sb.toString();
			if (value.length() > MAX_STRING_ENTRY_LENGTH) {
				value = value.substring(0, MAX_STRING_ENTRY_LENGTH);
			}
			out.add(value);
			j = q + 1;
			// Stop if we hit the closing bracket
			int nextBracket = json.indexOf(']', j);
			int nextQuote   = json.indexOf('"', j);
			if (nextBracket >= 0 && (nextQuote < 0 || nextBracket < nextQuote)) break;
		}
		return out;
	}

	private static String escape(String s) {
		if (s == null) return "";
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static String unescape(String s) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\\' && i + 1 < s.length()) {
				b.append(s.charAt(i + 1));
				i++;
			} else {
				b.append(c);
			}
		}
		return b.toString();
	}

	private static int clampInt(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double sanitizeFiniteDouble(double value, double fallback) {
		return Double.isFinite(value) ? value : fallback;
	}
}
