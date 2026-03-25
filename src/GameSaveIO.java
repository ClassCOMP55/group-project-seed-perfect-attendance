import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads/writes three JSON save files under {@code game/save1.json} … {@code save3.json}.
 * Minimal JSON — no external library; strings are escaped for {@code "}.
 */
public final class GameSaveIO {

    public static final int SAVE_COUNT = 3;
    private static final int FORMAT_VERSION = 1;

    private GameSaveIO() {
    }

    public static Path savePath(int slot) {
        if (slot < 1 || slot > SAVE_COUNT) {
            throw new IllegalArgumentException("slot 1–3");
        }
        return Paths.get("game", "save" + slot + ".json");
    }

    public static void ensureGameDirectory() throws IOException {
        Path dir = Paths.get("game");
        if (!Files.isDirectory(dir)) {
            Files.createDirectories(dir);
        }
    }

    public static boolean slotOccupied(int slot) {
        Path p = savePath(slot);
        try {
            return Files.isRegularFile(p) && Files.size(p) > 2;
        } catch (IOException e) {
            return false;
        }
    }

    /** Removes the save file for this slot if it exists (no-op on failure). */
    public static void deleteSave(int slot) {
        try {
            Files.deleteIfExists(savePath(slot));
        } catch (IOException e) {
            System.err.println("Could not delete save " + slot + ": " + e.getMessage());
        }
    }

    public static void writeSave(int slot, GameState state) throws IOException {
        ensureGameDirectory();
        Player pl = state.getPlayer();
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"version\": ").append(FORMAT_VERSION).append(",\n");
        json.append("  \"hp\": ").append(pl.getHP()).append(",\n");
        json.append("  \"personalityQuizCompleted\": ").append(state.isPersonalityQuizCompleted()).append(",\n");
        json.append("  \"scene\": \"").append(state.getCurrentScene().name()).append("\",\n");
        if (!state.isPersonalityQuizCompleted()) {
            json.append("  \"quizQuestionIndex\": ").append(state.getPersonalityQuizQuestionIndex()).append(",\n");
            json.append("  \"quizScores\": [");
            CardType[] types = CardType.values();
            for (int i = 0; i < types.length; i++) {
                json.append(state.getPersonalityQuizScore(types[i]));
                if (i < types.length - 1) {
                    json.append(", ");
                }
            }
            json.append("],\n");
        }
        json.append("  \"cards\": [\n");
        List<Card> cards = pl.getHand().getCards();
        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            json.append("    {");
            json.append("\"id\":\"").append(escape(c.getId())).append("\",");
            json.append("\"name\":\"").append(escape(c.getName())).append("\",");
            json.append("\"description\":\"").append(escape(c.getDescription())).append("\",");
            json.append("\"type\":\"").append(c.getType().name()).append("\"");
            json.append("}");
            if (i < cards.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  ]\n}\n");
        Files.write(savePath(slot), json.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static void loadIntoState(int slot, GameState state) throws IOException {
        Path p = savePath(slot);
        if (!Files.isRegularFile(p)) {
            throw new IOException("No save in slot " + slot);
        }
        String raw = new String(Files.readAllBytes(p), StandardCharsets.UTF_8).trim();
        if (raw.isEmpty()) {
            throw new IOException("Empty save file");
        }
        int hp = readIntField(raw, "hp", 100);
        boolean quiz = readBoolField(raw, "personalityQuizCompleted", false);
        GameSceneId scene = parseScene(readStringField(raw, "scene", "SCENE_1"));
        List<Card> cards = parseCards(readArraySection(raw, "cards"));
        int quizQ = readIntField(raw, "quizQuestionIndex", 0);
        int[] quizScores = readIntArrayField(raw, "quizScores", CardType.values().length);

        Player pl = new Player();
        pl.setHP(hp);
        pl.getHand().clear();
        for (Card c : cards) {
            pl.getHand().addCard(c);
        }
        state.applyLoadedSave(slot, pl, quiz, scene, quizQ, quizScores);
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Reads a JSON int array like {@code [1, 0, 2, 0]}; missing key yields zeros. */
    private static int[] readIntArrayField(String json, String key, int expectedLen) {
        int[] out = new int[expectedLen];
        String pat = "\"" + key + "\"";
        int i = json.indexOf(pat);
        if (i < 0) {
            return out;
        }
        int lb = json.indexOf('[', i);
        if (lb < 0) {
            return out;
        }
        int idx = 0;
        int j = lb + 1;
        while (j < json.length() && idx < expectedLen) {
            while (j < json.length() && (Character.isWhitespace(json.charAt(j)) || json.charAt(j) == ',')) {
                j++;
            }
            if (j >= json.length() || json.charAt(j) == ']') {
                break;
            }
            int start = j;
            if (json.charAt(j) == '-') {
                j++;
            }
            while (j < json.length() && Character.isDigit(json.charAt(j))) {
                j++;
            }
            try {
                out[idx++] = Integer.parseInt(json.substring(start, j).trim());
            } catch (NumberFormatException e) {
                idx++;
            }
        }
        return out;
    }

    private static int readIntField(String json, String key, int def) {
        String k = "\"" + key + "\"";
        int i = json.indexOf(k);
        if (i < 0) {
            return def;
        }
        int colon = json.indexOf(':', i);
        if (colon < 0) {
            return def;
        }
        int j = colon + 1;
        while (j < json.length() && Character.isWhitespace(json.charAt(j))) {
            j++;
        }
        int end = j;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        try {
            return Integer.parseInt(json.substring(j, end).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean readBoolField(String json, String key, boolean def) {
        String k = "\"" + key + "\"";
        int i = json.indexOf(k);
        if (i < 0) {
            return def;
        }
        int t = json.indexOf("true", i);
        int f = json.indexOf("false", i);
        if (t < 0 && f < 0) {
            return def;
        }
        if (t >= 0 && (f < 0 || t < f)) {
            return true;
        }
        return false;
    }

    private static String readStringField(String json, String key, String def) {
        String pat = "\"" + key + "\"";
        int i = json.indexOf(pat);
        if (i < 0) {
            return def;
        }
        int q1 = json.indexOf('"', i + pat.length());
        if (q1 < 0) {
            return def;
        }
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) {
            return def;
        }
        return unescape(json.substring(q1 + 1, q2));
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

    private static String readArraySection(String json, String key) {
        String pat = "\"" + key + "\"";
        int i = json.indexOf(pat);
        if (i < 0) {
            return "[]";
        }
        int lb = json.indexOf('[', i);
        if (lb < 0) {
            return "[]";
        }
        int depth = 0;
        for (int j = lb; j < json.length(); j++) {
            char c = json.charAt(j);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return json.substring(lb, j + 1);
                }
            }
        }
        return "[]";
    }

    private static List<Card> parseCards(String arrayJson) {
        List<Card> out = new ArrayList<>();
        int i = 0;
        while (i < arrayJson.length()) {
            int ob = arrayJson.indexOf('{', i);
            if (ob < 0) {
                break;
            }
            int depth = 0;
            int end = ob;
            for (int j = ob; j < arrayJson.length(); j++) {
                char c = arrayJson.charAt(j);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        end = j;
                        break;
                    }
                }
            }
            String obj = arrayJson.substring(ob, end + 1);
            String id = readStringField(obj, "id", "card");
            String name = readStringField(obj, "name", "Card");
            String desc = readStringField(obj, "description", "");
            String typeName = readStringField(obj, "type", "WILDCARD");
            CardType type = CardType.WILDCARD;
            try {
                type = CardType.valueOf(typeName);
            } catch (IllegalArgumentException ignored) {
                // keep WILDCARD
            }
            out.add(new Card(id, name, desc, type));
            i = end + 1;
        }
        return out;
    }

    private static GameSceneId parseScene(String name) {
        if (name == null || name.isEmpty()) {
            return GameSceneId.SCENE_1;
        }
        try {
            return GameSceneId.valueOf(name);
        } catch (IllegalArgumentException e) {
            return GameSceneId.SCENE_1;
        }
    }
}
