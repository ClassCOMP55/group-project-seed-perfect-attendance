import acm.graphics.GCanvas;
import acm.graphics.GLabel;
import acm.graphics.GRect;

import java.awt.Color;

/**
 * Lightweight stationary NPC placeholder for room-level dialogue testing.
 * Lives in the Room's WorldObject list so it can use the same interact flow as signs.
 */
public class WorldNpc extends WorldObject {
    private static final double TITLE_BASELINE_Y = -10.0;
    private static final double HINT_BASELINE_Y = 64.0;

    private static final Color NPC_BODY_COLOR = new Color(92, 150, 210);
    private static final Color NPC_BODY_EDGE  = new Color(45, 83, 124);
    private static final Color NPC_BELT_COLOR = new Color(244, 221, 126);
    private static final Color NPC_LABEL_COLOR = new Color(220, 237, 255);
    private static final Color NPC_HINT_COLOR  = new Color(222, 241, 184);

    private final String speakerName;
    private final String[] dialogueLines;

    private Dialogue dialogue;

    private final GRect body;
    private final GRect belt;
    private final GLabel titleLabel;
    private final GLabel hintLabel;

    public WorldNpc(double x, double y, String speakerName, String[] dialogueLines, Dialogue dialogue) {
        super(x, y, 48, 48);
        this.speakerName = (speakerName == null || speakerName.trim().isEmpty())
            ? "Villager"
            : speakerName.trim();
        this.dialogueLines = dialogueLines == null ? new String[0] : dialogueLines.clone();
        this.dialogue = dialogue;

        this.body = new GRect(x, y, 48, 48);
        this.body.setFilled(true);
        this.body.setFillColor(NPC_BODY_COLOR);
        this.body.setColor(NPC_BODY_EDGE);

        this.belt = new GRect(x + 6, y + 28, 36, 8);
        this.belt.setFilled(true);
        this.belt.setFillColor(NPC_BELT_COLOR);
        this.belt.setColor(NPC_BELT_COLOR.darker());

        this.titleLabel = new GLabel("NPC", x, y);
        this.titleLabel.setFont("SansSerif-BOLD-14");
        this.titleLabel.setColor(NPC_LABEL_COLOR);

        this.hintLabel = new GLabel("e to interact", x, y);
        this.hintLabel.setFont("SansSerif-PLAIN-12");
        this.hintLabel.setColor(NPC_HINT_COLOR);

        resetVisualPosition();
    }

    @Override
    public void draw(GCanvas canvas) {
        if (!visible) return;
        resetVisualPosition();
        canvas.add(body);
        canvas.add(belt);
        canvas.add(titleLabel);
        canvas.add(hintLabel);
        positionLabels();
    }

    @Override
    public void removeFrom(GCanvas canvas) {
        canvas.remove(body);
        canvas.remove(belt);
        canvas.remove(titleLabel);
        canvas.remove(hintLabel);
    }

    @Override
    public boolean isInteractable() {
        return true;
    }

    @Override
    public void panVisual(double panX, double panY) {
        body.move(panX, panY);
        belt.move(panX, panY);
        titleLabel.move(panX, panY);
        hintLabel.move(panX, panY);
    }

    @Override
    public void resetVisualPosition() {
        body.setLocation(x, y);
        belt.setLocation(x + 6, y + 28);
        positionLabels();
    }

    private void positionLabels() {
        double centerX = x + body.getWidth() / 2.0;
        titleLabel.setLocation(centerX - titleLabel.getWidth() / 2.0, y + TITLE_BASELINE_Y);
        hintLabel.setLocation(centerX - hintLabel.getWidth() / 2.0, y + HINT_BASELINE_Y);
    }

    @Override
    public void onInteract(Player p) {
        if (dialogue == null || dialogue.isOpen()) return;
        if (dialogueLines.length == 0) return;

        GamePlayState.setCurrent(GamePlayState.DIALOGUE);
        dialogue.open(
            dialogueLines,
            speakerName,
            true,
            () -> GamePlayState.setCurrent(GamePlayState.PLAYING)
        );
    }

    public void setDialogue(Dialogue dialogue) {
        this.dialogue = dialogue;
    }
}
