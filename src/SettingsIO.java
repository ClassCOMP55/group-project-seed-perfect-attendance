import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persists global preferences in {@code game/settings.json} (created on first run).
 */
public final class SettingsIO {

    private static final Path SETTINGS_PATH = Paths.get("game", "settings.json");
    private static final Pattern VOLUME_PATTERN = Pattern.compile("\"volumePercent\"\\s*:\\s*(\\d+)");

    private SettingsIO() {
    }

    public static Path path() {
        return SETTINGS_PATH;
    }

    /**
     * Loads volume from disk, or creates {@code game/settings.json} with default 100.
     */
    public static void loadOrCreate() throws IOException {
        Path dir = SETTINGS_PATH.getParent();
        if (dir != null && !Files.isDirectory(dir)) {
            Files.createDirectories(dir);
        }
        if (!Files.isRegularFile(SETTINGS_PATH)) {
            GameSettings.applyStartupVolume(100);
            persist();
            return;
        }
        String raw = new String(Files.readAllBytes(SETTINGS_PATH), StandardCharsets.UTF_8);
        int v = parseVolumePercent(raw, 100);
        GameSettings.applyStartupVolume(v);
    }

    private static int parseVolumePercent(String json, int defaultVal) {
        Matcher m = VOLUME_PATTERN.matcher(json);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                return defaultVal;
            }
        }
        return defaultVal;
    }

    /** Writes current {@link GameSettings} volume (0–100) as JSON. */
    public static void persist() {
        try {
            Path dir = SETTINGS_PATH.getParent();
            if (dir != null && !Files.isDirectory(dir)) {
                Files.createDirectories(dir);
            }
            int v = GameSettings.getVolumePercent();
            String json = "{\n  \"volumePercent\": " + v + "\n}\n";
            Files.write(SETTINGS_PATH, json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("Settings save failed: " + e.getMessage());
        }
    }
}
