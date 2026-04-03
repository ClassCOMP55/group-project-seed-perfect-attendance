import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persists global preferences in {@code game/settings.json} (created on first run).
 */
public final class SettingsIO {

    private static final Path SETTINGS_PATH = Paths.get("game", "settings.json");
    private static final Pattern LEGACY_VOLUME_PATTERN =
        Pattern.compile("\"volumePercent\"\\s*:\\s*(\\d+)");
    private static final Pattern MUSIC_VOLUME_PATTERN =
        Pattern.compile("\"musicVolumePercent\"\\s*:\\s*(\\d+)");
    private static final Pattern SFX_VOLUME_PATTERN =
        Pattern.compile("\"sfxVolumePercent\"\\s*:\\s*(\\d+)");
    private static final long MAX_SETTINGS_FILE_BYTES = 4 * 1024;

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
            GameSettings.applyStartupAudioVolumes(100, 100);
            persist();
            return;
        }
        String raw = readSettingsText();
        int legacyVolume = parseVolumePercent(raw, LEGACY_VOLUME_PATTERN, 100);
        int musicVolume = parseVolumePercent(raw, MUSIC_VOLUME_PATTERN, legacyVolume);
        int sfxVolume = parseVolumePercent(raw, SFX_VOLUME_PATTERN, legacyVolume);
        GameSettings.applyStartupAudioVolumes(musicVolume, sfxVolume);
    }

    private static int parseVolumePercent(String json, Pattern pattern, int defaultVal) {
        Matcher m = pattern.matcher(json);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                return defaultVal;
            }
        }
        return defaultVal;
    }

    /** Writes current {@link GameSettings} music/SFX volume (0-100) as JSON. */
    public static void persist() {
        try {
            Path dir = SETTINGS_PATH.getParent();
            if (dir != null && !Files.isDirectory(dir)) {
                Files.createDirectories(dir);
            }
            int musicVolume = GameSettings.getMusicVolumePercent();
            int sfxVolume = GameSettings.getSfxVolumePercent();
            String json =
                "{\n"
                    + "  \"musicVolumePercent\": " + musicVolume + ",\n"
                    + "  \"sfxVolumePercent\": " + sfxVolume + "\n"
                    + "}\n";
            writeAtomically(json);
        } catch (IOException e) {
            System.err.println("Settings save failed: " + e.getMessage());
        }
    }

    private static String readSettingsText() throws IOException {
        if (!Files.isRegularFile(SETTINGS_PATH, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Settings file is not a regular file");
        }
        long size = Files.size(SETTINGS_PATH);
        if (size > MAX_SETTINGS_FILE_BYTES) {
            throw new IOException("Settings file too large");
        }
        return new String(Files.readAllBytes(SETTINGS_PATH), StandardCharsets.UTF_8);
    }

    private static void writeAtomically(String json) throws IOException {
        Path dir = SETTINGS_PATH.getParent();
        if (dir == null) {
            throw new IOException("Settings path has no parent directory");
        }
        Path temp = Files.createTempFile(dir, "settings", ".tmp");
        try {
            Files.write(temp, json.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temp, SETTINGS_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, SETTINGS_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
