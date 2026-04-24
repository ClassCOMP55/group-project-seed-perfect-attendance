import acm.graphics.GCanvas;
import acm.graphics.GImage;
import acm.graphics.GLabel;
import acm.graphics.GRect;

import java.awt.image.BufferedImage;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Lightweight stationary NPC placeholder for room-level dialogue testing.
 * Lives in the Room's WorldObject list so it can use the same interact flow as signs.
 */
public class WorldNpc extends WorldObject {
    private static final double NPC_SPRITE_TARGET_HEIGHT = 48.0;
    private static final double TITLE_BASELINE_Y = -10.0;
    private static final double HINT_BASELINE_Y = 64.0;

    private static final Color NPC_BODY_COLOR = new Color(92, 150, 210);
    private static final Color NPC_BODY_EDGE  = new Color(45, 83, 124);
    private static final Color NPC_BELT_COLOR = new Color(244, 221, 126);
    private static final Color NPC_LABEL_COLOR = new Color(220, 237, 255);
    private static final Color NPC_HINT_COLOR  = new Color(222, 241, 184);

    private final String speakerName;
    private final String[] dialogueLines;
    private String[] rewardDialogueLines = new String[0];
    private String[] postRewardDialogueLines = new String[0];

    private Dialogue dialogue;
    private GameSFX.SFX voiceSound = null;
    private Predicate<String> hasStoryFlag;
    private Consumer<String> addStoryFlag;
    private String introCompleteFlag;
    private String rewardGrantedFlag;
    private Predicate<Player> rewardOwnedCheck;
    private Consumer<Player> rewardGrantAction;
    private Predicate<Player> rewardUnlockCondition;

    private final GImage npcSprite;
    private final GRect body;
    private final GRect belt;
    private final GLabel titleLabel;
    private final GLabel hintLabel;
    private double spriteRenderWidth = 48.0;
    private double spriteRenderHeight = 48.0;
    private final double spriteTargetHeight;

    /**
     * Creates a WorldNpc using the default sprite ("the drunk").
     * Use the overload below if you need a different NPC sprite.
     */
    public WorldNpc(double x, double y, String speakerName, String[] dialogueLines, Dialogue dialogue) {
        this(x, y, speakerName, dialogueLines, dialogue, "assets/visuals/png's/the drunk.png");
    }

    /**
     * Creates a WorldNpc with a custom sprite path at the default size (48px tall).
     *
     * @param spritePath path to the NPC's PNG, e.g. {@code "assets/visuals/png's/little girl (puzzle helper).png"}
     */
    public WorldNpc(double x, double y, String speakerName, String[] dialogueLines, Dialogue dialogue,
                    String spritePath) {
        this(x, y, speakerName, dialogueLines, dialogue, spritePath, NPC_SPRITE_TARGET_HEIGHT);
    }

    /**
     * Creates a WorldNpc with a custom sprite path and a custom rendered height.
     * Use a smaller value (e.g. 32) for child NPCs.
     *
     * @param spritePath    path to the NPC's PNG
     * @param spriteHeight  desired rendered height in pixels
     */
    public WorldNpc(double x, double y, String speakerName, String[] dialogueLines, Dialogue dialogue,
                    String spritePath, double spriteHeight) {
        super(x, y, 48, 48);
        this.spriteTargetHeight = spriteHeight;
        this.speakerName = (speakerName == null || speakerName.trim().isEmpty())
            ? "Villager"
            : speakerName.trim();
        this.dialogueLines = dialogueLines == null ? new String[0] : dialogueLines.clone();
        this.dialogue = dialogue;
        this.npcSprite = loadNpcSprite(spritePath);

        this.body = new GRect(x, y, 48, 48);
        this.body.setFilled(true);
        this.body.setFillColor(NPC_BODY_COLOR);
        this.body.setColor(NPC_BODY_EDGE);

        this.belt = new GRect(x + 6, y + 28, 36, 8);
        this.belt.setFilled(true);
        this.belt.setFillColor(NPC_BELT_COLOR);
        this.belt.setColor(NPC_BELT_COLOR.darker());

        this.titleLabel = new GLabel("NPC", x, y);
        this.titleLabel.setFont("Courier New-BOLD-14");
        this.titleLabel.setColor(NPC_LABEL_COLOR);

        this.hintLabel = new GLabel("e to interact", x, y);
        this.hintLabel.setFont("Courier New-BOLD-12");
        this.hintLabel.setColor(NPC_HINT_COLOR);

        resetVisualPosition();
    }

    @Override
    public void draw(GCanvas canvas) {
        if (!visible) return;
        resetVisualPosition();
        if (npcSprite != null) {
            canvas.add(npcSprite);
        } else {
            canvas.add(body);
            canvas.add(belt);
        }
        canvas.add(titleLabel);
        canvas.add(hintLabel);
        positionLabels();
    }

    @Override
    public void removeFrom(GCanvas canvas) {
        if (npcSprite != null) {
            canvas.remove(npcSprite);
        }
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
        if (npcSprite != null) {
            npcSprite.move(panX, panY);
        }
        body.move(panX, panY);
        belt.move(panX, panY);
        titleLabel.move(panX, panY);
        hintLabel.move(panX, panY);
    }

    @Override
    public void resetVisualPosition() {
        if (npcSprite != null) {
            npcSprite.setLocation(x + (48.0 - spriteRenderWidth) / 2.0, y + (48.0 - spriteRenderHeight));
        }
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

        String[] linesToShow = dialogueLines;
        Runnable onComplete = () -> GamePlayState.setCurrent(GamePlayState.PLAYING);

        boolean unlockItemPresent = rewardUnlockCondition != null && rewardUnlockCondition.test(p);
        // When an item-based unlock is configured, the reward is gated exclusively on the item.
        // Without one, use the normal two-step intro→reward flow.
        boolean canShowReward = (rewardUnlockCondition != null)
            ? (unlockItemPresent && !hasStoryFlag(rewardGrantedFlag))
            : shouldUseRewardDialogue();

        if (hasRewardSequenceCompleted(p)) {
            if (postRewardDialogueLines.length > 0) {
                linesToShow = postRewardDialogueLines;
            }
        } else if (canShowReward) {
            if (rewardDialogueLines.length > 0) {
                linesToShow = rewardDialogueLines;
                onComplete = () -> {
                    if (rewardGrantAction != null && !playerAlreadyOwnsReward(p)) {
                        rewardGrantAction.accept(p);
                    }
                    markStoryFlag(introCompleteFlag);
                    markStoryFlag(rewardGrantedFlag);
                    GamePlayState.setCurrent(GamePlayState.PLAYING);
                };
            }
        } else if (dialogueLines.length > 0 && !hasStoryFlag(introCompleteFlag)) {
            onComplete = () -> {
                markStoryFlag(introCompleteFlag);
                GamePlayState.setCurrent(GamePlayState.PLAYING);
            };
        }

        if (linesToShow.length == 0) return;

        GamePlayState.setCurrent(GamePlayState.DIALOGUE);
        if (voiceSound != null) dialogue.setVoiceSound(voiceSound);
        dialogue.open(
            linesToShow,
            speakerName,
            true,
            onComplete
        );
    }

    /** Sets the voice sound played on each character tick while this NPC speaks. */
    public void setVoiceSound(GameSFX.SFX sfx) {
        this.voiceSound = sfx;
    }

    public void setDialogue(Dialogue dialogue) {
        this.dialogue = dialogue;
    }

    public void setStoryFlagHooks(Predicate<String> hasStoryFlag, Consumer<String> addStoryFlag) {
        this.hasStoryFlag = hasStoryFlag;
        this.addStoryFlag = addStoryFlag;
    }

    public void setRewardUnlockCondition(Predicate<Player> condition) {
        this.rewardUnlockCondition = condition;
    }

    public void configureTwoStepReward(String introCompleteFlag,
                                       String rewardGrantedFlag,
                                       String[] rewardDialogueLines,
                                       String[] postRewardDialogueLines,
                                       Predicate<Player> rewardOwnedCheck,
                                       Consumer<Player> rewardGrantAction) {
        this.introCompleteFlag = normalizeFlag(introCompleteFlag);
        this.rewardGrantedFlag = normalizeFlag(rewardGrantedFlag);
        this.rewardDialogueLines = rewardDialogueLines == null ? new String[0] : rewardDialogueLines.clone();
        this.postRewardDialogueLines =
            postRewardDialogueLines == null ? new String[0] : postRewardDialogueLines.clone();
        this.rewardOwnedCheck = rewardOwnedCheck;
        this.rewardGrantAction = rewardGrantAction;
    }

    private GImage loadNpcSprite(String path) {
        return loadSprite(path);
    }

    private GImage loadSprite(String path) {
        try {
            BufferedImage source = ImageIO.read(new File(path));
            if (source == null) return null;
            BufferedImage trimmed = trimTransparentBounds(source);
            GImage image = new GImage(trimmed);
            double nativeWidth = Math.max(1.0, image.getWidth());
            double nativeHeight = Math.max(1.0, image.getHeight());
            double scale = spriteTargetHeight / nativeHeight;
            spriteRenderWidth = nativeWidth * scale;
            spriteRenderHeight = spriteTargetHeight;
            image.setSize(spriteRenderWidth, spriteRenderHeight);
            image.setLocation(x + (48.0 - spriteRenderWidth) / 2.0, y + (48.0 - spriteRenderHeight));
            return image;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private BufferedImage trimTransparentBounds(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;

        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                int alpha = (source.getRGB(px, py) >>> 24) & 0xFF;
                if (alpha == 0) continue;
                if (px < minX) minX = px;
                if (py < minY) minY = py;
                if (px > maxX) maxX = px;
                if (py > maxY) maxY = py;
            }
        }

        if (maxX < minX || maxY < minY) {
            return source;
        }
        return source.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private boolean shouldUseRewardDialogue() {
        return hasStoryFlag(introCompleteFlag) && !hasStoryFlag(rewardGrantedFlag);
    }

    private boolean hasRewardSequenceCompleted(Player player) {
        return hasStoryFlag(rewardGrantedFlag) || playerAlreadyOwnsReward(player);
    }

    private boolean playerAlreadyOwnsReward(Player player) {
        return player != null && rewardOwnedCheck != null && rewardOwnedCheck.test(player);
    }

    private boolean hasStoryFlag(String flag) {
        return flag != null && hasStoryFlag != null && hasStoryFlag.test(flag);
    }

    private void markStoryFlag(String flag) {
        if (flag != null && addStoryFlag != null) {
            addStoryFlag.accept(flag);
        }
    }

    private String normalizeFlag(String flag) {
        if (flag == null) return null;
        String normalized = flag.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
