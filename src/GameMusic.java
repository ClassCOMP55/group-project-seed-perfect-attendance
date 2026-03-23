import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.BooleanControl;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Background music: main menu and character-creation quiz (journey theme).
 */
public final class GameMusic {

    private static Clip mainMenuClip;
    private static Clip journeyBeginsClip;

    private GameMusic() {
    }

    /**
     * Loops main menu music if not already playing. Safe to call when returning to the start menu.
     */
    public static synchronized void startMainMenuMusic() {
        if (mainMenuClip != null && mainMenuClip.isActive()) {
            applyVolume(mainMenuClip);
            return;
        }
        stopMainMenuMusic();
        InputStream raw = openMusicStream("/audio/music/main-menu.wav", "main-menu.wav");
        if (raw == null) {
            System.err.println("GameMusic: could not find audio/music/main-menu.wav (classpath or assets/)");
            return;
        }
        try (InputStream in = new BufferedInputStream(raw)) {
            AudioInputStream ais = AudioSystem.getAudioInputStream(in);
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            applyVolume(clip);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            mainMenuClip = clip;
        } catch (IOException | UnsupportedAudioFileException | LineUnavailableException e) {
            System.err.println("GameMusic: failed to play main-menu.wav");
            e.printStackTrace();
        }
    }

    /**
     * Stops and releases main menu music (e.g. when entering gameplay).
     */
    public static synchronized void stopMainMenuMusic() {
        if (mainMenuClip == null) {
            return;
        }
        try {
            mainMenuClip.stop();
            mainMenuClip.flush();
            mainMenuClip.close();
        } catch (Exception e) {
            // ignore
        }
        mainMenuClip = null;
    }

    /**
     * Loops journey-begins theme during the character quiz; stops when leaving that screen.
     */
    public static synchronized void startJourneyBeginsMusic() {
        if (journeyBeginsClip != null && journeyBeginsClip.isActive()) {
            applyVolume(journeyBeginsClip);
            return;
        }
        stopJourneyBeginsMusic();
        InputStream raw = openMusicStream("/audio/music/journey-begins.wav", "journey-begins.wav");
        if (raw == null) {
            System.err.println("GameMusic: could not find audio/music/journey-begins.wav (classpath or assets/)");
            return;
        }
        try (InputStream in = new BufferedInputStream(raw)) {
            AudioInputStream ais = AudioSystem.getAudioInputStream(in);
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            applyVolume(clip);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            journeyBeginsClip = clip;
        } catch (IOException | UnsupportedAudioFileException | LineUnavailableException e) {
            System.err.println("GameMusic: failed to play journey-begins.wav");
            e.printStackTrace();
        }
    }

    public static synchronized void stopJourneyBeginsMusic() {
        if (journeyBeginsClip == null) {
            return;
        }
        try {
            journeyBeginsClip.stop();
            journeyBeginsClip.flush();
            journeyBeginsClip.close();
        } catch (Exception e) {
            // ignore
        }
        journeyBeginsClip = null;
    }

    /** Re-apply volume from {@link GameSettings} (call after slider changes). */
    public static synchronized void refreshVolume() {
        if (mainMenuClip != null) {
            applyVolume(mainMenuClip);
        }
        if (journeyBeginsClip != null) {
            applyVolume(journeyBeginsClip);
        }
    }

    private static InputStream openMusicStream(String classpathPath, String fileName) {
        InputStream fromClasspath = GameMusic.class.getResourceAsStream(classpathPath);
        if (fromClasspath != null) {
            return fromClasspath;
        }
        Path p = Paths.get("assets", "audio", "music", fileName);
        try {
            if (Files.isRegularFile(p)) {
                return Files.newInputStream(p);
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private static void applyVolume(Clip clip) {
        int p = GameSettings.getVolumePercent();
        try {
            BooleanControl mute = (BooleanControl) clip.getControl(BooleanControl.Type.MUTE);
            if (p <= 0) {
                mute.setValue(true);
                return;
            }
            mute.setValue(false);
        } catch (IllegalArgumentException ex) {
            // no mute control
        }

        float t = p / 100f;

        try {
            FloatControl vol = (FloatControl) clip.getControl(FloatControl.Type.VOLUME);
            vol.setValue(Math.max(0f, Math.min(1f, t)));
            return;
        } catch (IllegalArgumentException ex) {
            // not all lines expose VOLUME
        }

        try {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float min = gain.getMinimum();
            float max = gain.getMaximum();
            if (p <= 0) {
                gain.setValue(min);
                return;
            }
            float db = max + 20f * (float) Math.log10(Math.max(t, 1e-4f));
            if (db < min) {
                db = min;
            }
            if (db > max) {
                db = max;
            }
            gain.setValue(db);
        } catch (IllegalArgumentException ex) {
            // no gain control on this system
        }
    }
}
