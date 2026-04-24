import java.awt.Color;
import java.awt.event.MouseEvent;

import acm.graphics.GImage;
import acm.graphics.GObject;
import acm.graphics.GOval;
import acm.graphics.GRect;

/**
 * Settings screen — displays Settings.png full-screen.
 * Two draggable slider knobs sit on top of the baked-in slider tracks (Sound + Music).
 * One hit zone covers the baked-in Back button.
 */
public class SettingsPane extends NightScenePane {

    private static final String BG = "assets/visuals/start screen/Settings.png";

    // Sound (SFX) slider track — tune after first run
    private static final int SOUND_LEFT  = 425;
    private static final int SOUND_RIGHT = 870;
    private static final int SOUND_Y     = 455;

    // Music slider track — tune after first run
    private static final int MUSIC_LEFT  = 425;
    private static final int MUSIC_RIGHT = 870;
    private static final int MUSIC_Y     = 545;

    // Knob appearance
    private static final int KNOB_SIZE = 20;

    // Back button hit zone — tune after first run
    private static final int BACK_X = 465;
    private static final int BACK_Y = 583;
    private static final int BACK_W = 365;
    private static final int BACK_H = 45;

    private GOval soundKnob;
    private GOval musicKnob;
    private GRect backZone;
    /** -1 = not dragging, 0 = dragging sound knob, 1 = dragging music knob */
    private int dragging = -1;

    public SettingsPane(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
    }

    @Override
    public void showContent() {
        GImage bg = new GImage(BG, 0, 0);
        bg.setSize(mainScreen.getWidth(), mainScreen.getHeight());
        addGraphic(bg);

        soundKnob = buildKnob();
        positionKnob(soundKnob, GameSettings.getSfxVolumePercent(),
                SOUND_LEFT, SOUND_RIGHT, SOUND_Y);

        musicKnob = buildKnob();
        positionKnob(musicKnob, GameSettings.getMusicVolumePercent(),
                MUSIC_LEFT, MUSIC_RIGHT, MUSIC_Y);

        backZone = new GRect(BACK_X, BACK_Y, BACK_W, BACK_H);
        backZone.setFilled(false);
        backZone.setColor(new Color(0, 0, 0, 0));
        addGraphic(backZone);
    }

    private GOval buildKnob() {
        GOval k = new GOval(0, 0, KNOB_SIZE, KNOB_SIZE);
        k.setFilled(true);
        k.setFillColor(Color.WHITE);
        k.setColor(new Color(255, 215, 120)); // gold border
        addGraphic(k);
        return k;
    }

    private void positionKnob(GOval knob, int percent, int left, int right, int trackY) {
        double t  = percent / 100.0;
        double cx = left + t * (right - left);
        knob.setLocation(cx - KNOB_SIZE / 2.0, trackY - KNOB_SIZE / 2.0);
    }

    private int percentFromX(double x, int left, int right) {
        double span = right - left;
        if (span <= 0) return 0;
        double t = (x - left) / span;
        t = Math.max(0, Math.min(1, t));
        return (int) Math.round(t * 100);
    }

    private boolean nearTrack(double x, double y, int left, int right, int trackY) {
        if (y < trackY - KNOB_SIZE || y > trackY + KNOB_SIZE) return false;
        return x >= left - 10 && x <= right + 10;
    }

    @Override
    public void hideContent() {
        for (GObject item : contents) {
            mainScreen.remove(item);
        }
        contents.clear();
        soundKnob = null;
        musicKnob = null;
        backZone  = null;
        dragging  = -1;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        int mx = e.getX(), my = e.getY();
        if (mx >= BACK_X && mx <= BACK_X + BACK_W
                && my >= BACK_Y && my <= BACK_Y + BACK_H) {
            GameSFX.play(GameSFX.SFX.CLICKING);
            mainScreen.switchToStartMenuScreen();
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        double x = e.getX(), y = e.getY();
        if (soundKnob != null && soundKnob.contains(x, y)) {
            dragging = 0;
        } else if (musicKnob != null && musicKnob.contains(x, y)) {
            dragging = 1;
        } else if (nearTrack(x, y, SOUND_LEFT, SOUND_RIGHT, SOUND_Y)) {
            dragging = 0;
            GameSettings.setSfxVolumePercent(percentFromX(x, SOUND_LEFT, SOUND_RIGHT));
            positionKnob(soundKnob, GameSettings.getSfxVolumePercent(),
                    SOUND_LEFT, SOUND_RIGHT, SOUND_Y);
            GameSFX.refreshVolume();
            SettingsIO.persist();
        } else if (nearTrack(x, y, MUSIC_LEFT, MUSIC_RIGHT, MUSIC_Y)) {
            dragging = 1;
            GameSettings.setMusicVolumePercent(percentFromX(x, MUSIC_LEFT, MUSIC_RIGHT));
            positionKnob(musicKnob, GameSettings.getMusicVolumePercent(),
                    MUSIC_LEFT, MUSIC_RIGHT, MUSIC_Y);
            GameMusic.refreshVolume();
            SettingsIO.persist();
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (dragging == 0) {
            GameSettings.setSfxVolumePercent(percentFromX(e.getX(), SOUND_LEFT, SOUND_RIGHT));
            positionKnob(soundKnob, GameSettings.getSfxVolumePercent(),
                    SOUND_LEFT, SOUND_RIGHT, SOUND_Y);
            GameSFX.refreshVolume();
        } else if (dragging == 1) {
            GameSettings.setMusicVolumePercent(percentFromX(e.getX(), MUSIC_LEFT, MUSIC_RIGHT));
            positionKnob(musicKnob, GameSettings.getMusicVolumePercent(),
                    MUSIC_LEFT, MUSIC_RIGHT, MUSIC_Y);
            GameMusic.refreshVolume();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (dragging >= 0) {
            SettingsIO.persist();
        }
        dragging = -1;
    }
}
