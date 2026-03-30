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
    private static final int FORMAT_VERSION = 2;

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
            boolean existed = Files.deleteIfExists(savePath(slot));
            if (existed) {
                System.out.println("[SaveIO] Deleted save slot " + slot + " (" + savePath(slot) + ")");
            } else {
                System.out.println("[SaveIO] Delete slot " + slot + " — file did not exist");
            }
        } catch (IOException e) {
            System.err.println("[SaveIO] Could not delete save " + slot + ": " + e.getMessage());
        }
    }

    // writeSave and loadIntoState will be added here once the new game's save data is defined.

    /** Reads a JSON string array {@code ["a","b"]}; missing key yields empty list. */
    private static List<String> readStringArrayField(String json, String key) {
        List<String> out = new ArrayList<>();
        String arr = readArraySection(json, key);
        int j = 0;
        while (j < arr.length()) {
            int q = arr.indexOf('"', j);
            if (q < 0) {
                break;
            }
            q++;
            StringBuilder sb = new StringBuilder();
            while (q < arr.length()) {
                char c = arr.charAt(q);
                if (c == '\\' && q + 1 < arr.length()) {
                    sb.append(arr.charAt(q + 1));
                    q += 2;
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                    q++;
                }
            }
            out.add(sb.toString());
            j = q + 1;
        }
        return out;
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

    /**
     * Reads a JSON boolean for {@code key}. Only the token immediately after {@code :} is used
     * (avoids matching {@code true}/{@code false} inside later strings, e.g. card descriptions).
     */
    private static boolean readBoolField(String json, String key, boolean def) {
        String k = "\"" + key + "\"";
        int keyPos = json.indexOf(k);
        if (keyPos < 0) {
            return def;
        }
        int colon = json.indexOf(':', keyPos + k.length());
        if (colon < 0) {
            return def;
        }
        int j = colon + 1;
        while (j < json.length() && Character.isWhitespace(json.charAt(j))) {
            j++;
        }
        if (json.regionMatches(j, "true", 0, 4)) {
            return true;
        }
        if (json.regionMatches(j, "false", 0, 5)) {
            return false;
        }
        return def;
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

}
