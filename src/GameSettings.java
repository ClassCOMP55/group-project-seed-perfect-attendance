/**
 * Global preferences (graphics theme template, sound). Other screens can read
 * these values when applying visuals or playing audio.
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
    /** 0–100; 0 is effectively muted for gameplay/audio hooks. */
    private static int volumePercent = 100;

    private GameSettings() {
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
        return volumePercent > 0;
    }

    public static int getVolumePercent() {
        return volumePercent;
    }

    public static void setVolumePercent(int percent) {
        if (percent < 0) {
            volumePercent = 0;
        } else if (percent > 100) {
            volumePercent = 100;
        } else {
            volumePercent = percent;
        }
    }

    /** Legacy toggle: mutes to 0 or restores to 75%. */
    public static void toggleSound() {
        if (volumePercent > 0) {
            volumePercent = 0;
        } else {
            volumePercent = 100;
        }
    }
}
