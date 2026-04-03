/**
 * Global preferences (graphics theme template, music, SFX). Other screens can
 * read these values when applying visuals or playing audio.
 */
public final class GameSettings {

    /** Placeholder graphics themes — wire into panes later for real palette swaps. */
    public enum GraphicsTemplate {
        DEFAULT("Default"),
        SOFT_PURPLE("Soft purple"),
        FOREST("Forest");

        private final String displayName;

        GraphicsTemplate(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public GraphicsTemplate next() {
            GraphicsTemplate[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    private static GraphicsTemplate graphicsTemplate = GraphicsTemplate.DEFAULT;
    /** 0-100; 0 is effectively muted for background music. */
    private static int musicVolumePercent = 100;
    /** 0-100; 0 is effectively muted for sound effects. */
    private static int sfxVolumePercent = 100;

    private GameSettings() {
    }

    private static int clampPercent(int percent) {
        if (percent < 0) {
            return 0;
        }
        if (percent > 100) {
            return 100;
        }
        return percent;
    }

    /**
     * Sets volume during startup from {@link SettingsIO} only — does not write the file.
     */
    public static void applyStartupVolume(int percent) {
        applyStartupAudioVolumes(percent, percent);
    }

    /**
     * Sets music/SFX volumes during startup from {@link SettingsIO} only — does not write the file.
     */
    public static void applyStartupAudioVolumes(int musicPercent, int sfxPercent) {
        musicVolumePercent = clampPercent(musicPercent);
        sfxVolumePercent = clampPercent(sfxPercent);
    }

    public static GraphicsTemplate getGraphicsTemplate() {
        return graphicsTemplate;
    }

    public static void setGraphicsTemplate(GraphicsTemplate t) {
        if (t != null) {
            graphicsTemplate = t;
        }
    }

    public static void cycleGraphicsTemplate() {
        graphicsTemplate = graphicsTemplate.next();
    }

    public static boolean isSoundEnabled() {
        return musicVolumePercent > 0 || sfxVolumePercent > 0;
    }

    /** Legacy single-slider readback; mirrors the music channel. */
    public static int getVolumePercent() {
        return musicVolumePercent;
    }

    /** Legacy single-slider setter; applies the same value to both channels. */
    public static void setVolumePercent(int percent) {
        int clamped = clampPercent(percent);
        musicVolumePercent = clamped;
        sfxVolumePercent = clamped;
    }

    public static int getMusicVolumePercent() {
        return musicVolumePercent;
    }

    public static void setMusicVolumePercent(int percent) {
        musicVolumePercent = clampPercent(percent);
    }

    public static int getSfxVolumePercent() {
        return sfxVolumePercent;
    }

    public static void setSfxVolumePercent(int percent) {
        sfxVolumePercent = clampPercent(percent);
    }

    /** Legacy toggle: mutes to 0 or restores to 75%. */
    public static void toggleSound() {
        if (isSoundEnabled()) {
            musicVolumePercent = 0;
            sfxVolumePercent = 0;
        } else {
            musicVolumePercent = 100;
            sfxVolumePercent = 100;
        }
    }
}
