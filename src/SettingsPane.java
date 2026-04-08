import java.awt.Color;
import java.awt.event.MouseEvent;

import acm.graphics.GLabel;
import acm.graphics.GLine;
import acm.graphics.GObject;
import acm.graphics.GOval;
import acm.graphics.GRoundRect;

/**
 * Settings: night theme; three aligned columns; volume slider with live 0–100 readout.
 */
public class SettingsPane extends NightScenePane {

    private GLabel settingsSubtitle;
    private GLabel graphicsHeader;
    private GLabel graphicsValueLabel;
    private GLabel soundHeader;
    private GLabel volumePercentLabel;
    private GLabel creditsHeader;
    private GLabel[] creditLines;
    private GRoundRect backFrame;
    private GLabel backLabel;

    private GLabel arrowLeft;
    private GLabel arrowRight;
    private GLine trackLine;
    private GOval sliderKnob;

    /** Center X of the Sound column (for re-centering the volume number). */
    private double soundColumnCenterX;
    /** Baseline Y for the big volume number. */
    private double volumeNumberBaselineY;

    private double sliderTrackLeft;
    private double sliderTrackRight;
    private double sliderY;
    private double knobSize;
    private boolean draggingSlider;

    public SettingsPane(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
    }

    @Override
    public void showContent() {
        double ox = originX();
        double lw = mainScreen.getLayoutWidth();

        paintNightSky();
        addTitleBanner();

        settingsSubtitle = new GLabel("Settings", 0, 0);
        settingsSubtitle.setFont(displayFont(17));
        settingsSubtitle.setColor(NIGHT_GOLD);
        settingsSubtitle.setLocation(centeredX(settingsSubtitle), scaleY(206));
        addGraphic(settingsSubtitle);

        double c1 = ox + lw * 0.20;
        soundColumnCenterX = ox + lw * 0.50;
        double c3 = ox + lw * 0.80;

        double yHead = scaleY(242);
        double yValueRow = scaleY(276);
        double lineStep = scaleY(23) - scaleY(0);
        double yTrackCenter = scaleY(318);

        graphicsHeader = new GLabel("Graphics:", 0, 0);
        graphicsHeader.setFont(displayFont(17));
        graphicsHeader.setColor(NIGHT_CREAM);
        placeLabelCenter(graphicsHeader, c1, yHead);
        addGraphic(graphicsHeader);

        graphicsValueLabel = new GLabel(graphicsAngleText(), 0, 0);
        graphicsValueLabel.setFont(displayFont(15));
        graphicsValueLabel.setColor(NIGHT_CREAM);
        placeLabelCenter(graphicsValueLabel, c1, yValueRow);
        addGraphic(graphicsValueLabel);

        creditsHeader = new GLabel("Credits:", 0, 0);
        creditsHeader.setFont(displayFont(17));
        creditsHeader.setColor(NIGHT_CREAM);
        placeLabelCenter(creditsHeader, c3, yHead);
        addGraphic(creditsHeader);

        soundHeader = new GLabel("Sound:", 0, 0);
        soundHeader.setFont(displayFont(17));
        soundHeader.setColor(NIGHT_CREAM);
        placeLabelCenter(soundHeader, soundColumnCenterX, yHead);
        addGraphic(soundHeader);

        volumeNumberBaselineY = yValueRow;
        volumePercentLabel = new GLabel(volumePercentText(), 0, 0);
        volumePercentLabel.setFont(displayFont(20));
        volumePercentLabel.setColor(NIGHT_GOLD);
        placeLabelCenter(volumePercentLabel, soundColumnCenterX, volumeNumberBaselineY);
        addGraphic(volumePercentLabel);

        buildSlider(soundColumnCenterX, yTrackCenter);

        String[] names = { "Charles", "Roberto", "Angel", "Gorge" };
        creditLines = new GLabel[names.length];
        for (int i = 0; i < names.length; i++) {
            GLabel line = new GLabel(names[i], 0, 0);
            line.setFont(displayFont(14));
            line.setColor(NIGHT_CREAM);
            placeLabelCenter(line, c3, yValueRow + i * lineStep);
            creditLines[i] = line;
            addGraphic(line);
        }

        double bw = scaleX(168) - scaleX(0);
        double bh = nightButtonHeight();
        double margin = scaleY(44) - scaleY(0);
        double backTop = scaleY(500) - margin - bh;
        backFrame = addNightButton(ox, lw, backTop, bw, bh);
        backLabel = new GLabel("Back", 0, 0);
        backLabel.setFont(displayFont(18));
        backLabel.setColor(NIGHT_GOLD);
        centerLabelInRect(backLabel, backFrame);
        addGraphic(backLabel);
    }

    private static String volumePercentText() {
        return Integer.toString(GameSettings.getVolumePercent());
    }

    private void refreshVolumeLabel() {
        if (volumePercentLabel == null) {
            return;
        }
        volumePercentLabel.setLabel(volumePercentText());
        placeLabelCenter(volumePercentLabel, soundColumnCenterX, volumeNumberBaselineY);
    }

    private void buildSlider(double centerX, double trackCenterY) {
        knobSize = Math.max(scaleY(16) - scaleY(0), 12);
        double halfTrack = scaleX(95) - scaleX(0);
        sliderTrackLeft = centerX - halfTrack;
        sliderTrackRight = centerX + halfTrack;
        sliderY = trackCenterY - knobSize / 2;

        double trackY = trackCenterY;

        double arrowGap = scaleX(18) - scaleX(0);
        arrowLeft = new GLabel("<", 0, 0);
        arrowLeft.setFont(displayFont(18));
        arrowLeft.setColor(NIGHT_GOLD);
        placeLabelCenter(arrowLeft, sliderTrackLeft - arrowGap, trackY);
        addGraphic(arrowLeft);

        arrowRight = new GLabel(">", 0, 0);
        arrowRight.setFont(displayFont(18));
        arrowRight.setColor(NIGHT_GOLD);
        placeLabelCenter(arrowRight, sliderTrackRight + arrowGap, trackY);
        addGraphic(arrowRight);

        GLabel minMark = new GLabel("0", 0, 0);
        minMark.setFont(displayFont(11));
        minMark.setColor(new Color(160, 170, 200));
        placeLabelCenter(minMark, sliderTrackLeft, trackY + scaleY(18) - scaleY(0));
        addGraphic(minMark);

        GLabel maxMark = new GLabel("100", 0, 0);
        maxMark.setFont(displayFont(11));
        maxMark.setColor(new Color(160, 170, 200));
        placeLabelCenter(maxMark, sliderTrackRight, trackY + scaleY(18) - scaleY(0));
        addGraphic(maxMark);

        trackLine = new GLine(sliderTrackLeft, trackY, sliderTrackRight, trackY);
        trackLine.setColor(new Color(160, 170, 210));
        addGraphic(trackLine);

        sliderKnob = new GOval(0, 0, knobSize, knobSize);
        sliderKnob.setFilled(true);
        sliderKnob.setFillColor(NIGHT_BUTTON_FILL);
        sliderKnob.setColor(NIGHT_GOLD);
        positionKnobFromVolume();
        addGraphic(sliderKnob);
    }

    private void placeLabelCenter(GLabel g, double centerX, double baselineY) {
        g.setLocation(centerX - g.getWidth() / 2, baselineY);
    }

    private void positionKnobFromVolume() {
        int v = GameSettings.getVolumePercent();
        double t = v / 100.0;
        double cx = sliderTrackLeft + t * (sliderTrackRight - sliderTrackLeft);
        double knobX = cx - knobSize / 2;
        sliderKnob.setLocation(knobX, sliderY);
        refreshVolumeLabel();
        GameMusic.refreshVolume();
        GameSFX.refreshVolume();
    }

    private int volumeFromKnobCenterX(double cx) {
        double span = sliderTrackRight - sliderTrackLeft;
        if (span <= 1e-6) {
            return GameSettings.getVolumePercent();
        }
        double t = (cx - sliderTrackLeft) / span;
        if (t < 0) {
            t = 0;
        }
        if (t > 1) {
            t = 1;
        }
        return (int) Math.round(t * 100);
    }

    private static String graphicsAngleText() {
        return "< " + GameSettings.getGraphicsTemplate().getDisplayName() + " >";
    }

    @Override
    public void hideContent() {
        for (GObject item : contents) {
            mainScreen.remove(item);
        }
        contents.clear();
        creditLines = null;
        volumePercentLabel = null;
        draggingSlider = false;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        GObject hit = mainScreen.getElementAtLocation(e.getX(), e.getY());
        if (hit == backLabel || hit == backFrame) {
            mainScreen.switchToStartMenuScreen();
            return;
        }
        if (hit == graphicsValueLabel) {
            GameSettings.cycleGraphicsTemplate();
            graphicsValueLabel.setLabel(graphicsAngleText());
            return;
        }
        if (hit == arrowLeft) {
            GameSettings.setVolumePercent(GameSettings.getVolumePercent() - 5);
            positionKnobFromVolume();
            SettingsIO.persist();
            return;
        }
        if (hit == arrowRight) {
            GameSettings.setVolumePercent(GameSettings.getVolumePercent() + 5);
            positionKnobFromVolume();
            SettingsIO.persist();
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        double x = e.getX();
        double y = e.getY();
        if (sliderKnob != null && sliderKnob.contains(x, y)) {
            draggingSlider = true;
            return;
        }
        if (trackLine != null && nearTrack(x, y)) {
            draggingSlider = true;
            GameSettings.setVolumePercent(volumeFromKnobCenterX(x));
            positionKnobFromVolume();
            SettingsIO.persist();
        }
    }

    private boolean nearTrack(double x, double y) {
        double mid = sliderY + knobSize / 2;
        if (y < mid - knobSize || y > mid + knobSize) {
            return false;
        }
        double pad = scaleX(12) - scaleX(0);
        return x >= sliderTrackLeft - pad && x <= sliderTrackRight + pad;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (!draggingSlider) {
            return;
        }
        GameSettings.setVolumePercent(volumeFromKnobCenterX(e.getX()));
        positionKnobFromVolume();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (draggingSlider) {
            SettingsIO.persist();
        }
        draggingSlider = false;
    }
}
