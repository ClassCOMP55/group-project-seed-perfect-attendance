import acm.graphics.GImage;

/**
 * CutsceneFrame.java
 *
 * Immutable data class representing one frame in a cutscene sequence.
 * Each frame has an image, overlay text, a hold duration (how long
 * the frame stays fully visible), and a fade duration (how long the
 * fade-in and fade-out transitions take).
 *
 * Person 1 — Engine & Sequences
 */
public class CutsceneFrame {

    private final GImage image;
    private final String text;
    private final int holdDurationMs;
    private final int fadeDurationMs;

    /**
     * Creates a cutscene frame.
     *
     * @param image          the image to display (null for text-only frames)
     * @param text           overlay text (null or empty for image-only frames)
     * @param holdDurationMs how long the frame stays fully visible in ms
     * @param fadeDurationMs how long the fade-in and fade-out each take in ms
     */
    public CutsceneFrame(GImage image, String text, int holdDurationMs, int fadeDurationMs) {
        this.image = image;
        this.text = text;
        this.holdDurationMs = holdDurationMs;
        this.fadeDurationMs = fadeDurationMs;
    }

    /** @return the frame image (may be null for text-only frames) */
    public GImage getImage() { return image; }

    /** @return the overlay text (may be null) */
    public String getText() { return text; }

    /** @return how long the frame stays fully visible in ms */
    public int getHoldDurationMs() { return holdDurationMs; }

    /** @return how long each fade transition takes in ms */
    public int getFadeDurationMs() { return fadeDurationMs; }
}
