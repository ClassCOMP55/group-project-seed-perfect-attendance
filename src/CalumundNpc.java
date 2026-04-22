import acm.graphics.GCanvas;
import acm.graphics.GImage;
import acm.graphics.GLabel;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.imageio.ImageIO;

/**
 * Calumund Vaen Solmare — the wizard-goat NPC in A1.
 * Loads directional sprites the same way WorldNpc does (ImageIO trim + scale),
 * then swaps which sprite is on canvas based on the player's position each tick.
 */
public class CalumundNpc extends WorldObject {

    // =========================================================
    // CONSTANTS  (same sizing conventions as WorldNpc)
    // =========================================================

    private static final String SPEAKER_NAME    = "Calumund Vaen Solmare";
    private static final double TARGET_H        = 72.0;   // rendered sprite height in px
    private static final double TILE            = 48.0;
    /** 10 px above sprite top — same gap WorldNpc uses. */
    private static final double TITLE_OFFSET_Y  = (TILE - TARGET_H) - 10.0;  // = -34
    /** 16 px below sprite bottom — same gap WorldNpc uses. */
    private static final double HINT_OFFSET_Y   = TILE + 16.0;               // = 64

    private static final String GIF_FORWARD = "assets/visuals/wizard goat/wizard goat walking foward.gif";
    private static final String GIF_BACK    = "assets/visuals/wizard goat/wizard goat walking back.gif";
    private static final String GIF_LEFT    = "assets/visuals/wizard goat/wizard goat walking left.gif";
    private static final String GIF_RIGHT   = "assets/visuals/wizard goat/wizard goat walking right.gif";

    // =========================================================
    // VISUAL FIELDS
    // =========================================================

    private final Map<Direction, GImage>  dirSprites = new EnumMap<>(Direction.class);
    /** Rendered width for each direction (may differ after trim+scale). */
    private final Map<Direction, Double>  spriteWidths = new EnumMap<>(Direction.class);
    private Direction currentDir = Direction.LEFT;

    private final GLabel titleLabel;
    private final GLabel hintLabel;

    private final Supplier<Player> playerSupplier;

    // =========================================================
    // DIALOGUE FIELDS  (mirrors WorldNpc)
    // =========================================================

    private final String[] dialogueLines;
    private String[] rewardDialogueLines     = new String[0];
    private String[] postRewardDialogueLines = new String[0];

    private Dialogue           dialogue;
    private Predicate<String>  hasStoryFlagFn;
    private Consumer<String>   addStoryFlagFn;
    private String             introCompleteFlag;
    private String             rewardGrantedFlag;
    private Predicate<Player>  rewardOwnedCheck;
    private Consumer<Player>   rewardGrantAction;
    private Predicate<Player>  rewardUnlockCondition;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    public CalumundNpc(double x, double y, String[] dialogueLines,
                       Dialogue dialogue, Supplier<Player> playerSupplier) {
        super(x, y, 48, 48);
        this.dialogueLines  = dialogueLines == null ? new String[0] : dialogueLines.clone();
        this.dialogue       = dialogue;
        this.playerSupplier = playerSupplier;

        loadSprite(Direction.DOWN,  GIF_FORWARD);
        loadSprite(Direction.UP,    GIF_BACK);
        loadSprite(Direction.LEFT,  GIF_LEFT);
        loadSprite(Direction.RIGHT, GIF_RIGHT);

        titleLabel = new GLabel(SPEAKER_NAME, x, y);
        titleLabel.setFont("SansSerif-BOLD-14");
        titleLabel.setColor(new Color(220, 237, 255));

        hintLabel = new GLabel("e to interact", x, y);
        hintLabel.setFont("SansSerif-PLAIN-12");
        hintLabel.setColor(new Color(222, 241, 184));

        syncSpritePositions();
        positionLabels();
    }

    // =========================================================
    // WORLD OBJECT OVERRIDES
    // =========================================================

    @Override
    public void draw(GCanvas canvas) {
        if (!visible) return;
        syncSpritePositions();
        GImage sprite = dirSprites.get(currentDir);
        if (sprite != null) canvas.add(sprite);
        canvas.add(titleLabel);
        canvas.add(hintLabel);
        positionLabels();
    }

    @Override
    public void removeFrom(GCanvas canvas) {
        for (GImage img : dirSprites.values()) canvas.remove(img);
        canvas.remove(titleLabel);
        canvas.remove(hintLabel);
    }

    @Override
    public void panVisual(double panX, double panY) {
        for (GImage img : dirSprites.values()) img.move(panX, panY);
        titleLabel.move(panX, panY);
        hintLabel.move(panX, panY);
    }

    @Override
    public void resetVisualPosition() {
        syncSpritePositions();
        positionLabels();
    }

    @Override
    public boolean isInteractable() { return true; }

    @Override
    public void update(double dt) {
        Player p = (playerSupplier != null) ? playerSupplier.get() : null;
        if (p == null) return;
        double dx = p.getX() - x;
        double dy = p.getY() - y;
        if (Math.abs(dx) >= Math.abs(dy)) {
            currentDir = Direction.LEFT;
        } else {
            currentDir = Direction.LEFT;
        }
    }

    @Override
    public void onInteract(Player p) {
        if (dialogue == null || dialogue.isOpen()) return;

        String[] linesToShow = dialogueLines;
        Runnable onComplete = () -> GamePlayState.setCurrent(GamePlayState.PLAYING);

        boolean unlockMet     = rewardUnlockCondition != null && rewardUnlockCondition.test(p);
        boolean rewardDone    = hasFlag(rewardGrantedFlag) || ownsReward(p);
        boolean canShowReward = (rewardUnlockCondition != null)
            ? (unlockMet && !hasFlag(rewardGrantedFlag))
            : (hasFlag(introCompleteFlag) && !hasFlag(rewardGrantedFlag));

        if (rewardDone) {
            if (postRewardDialogueLines.length > 0) linesToShow = postRewardDialogueLines;
        } else if (canShowReward && rewardDialogueLines.length > 0) {
            linesToShow = rewardDialogueLines;
            onComplete = () -> {
                if (rewardGrantAction != null && !ownsReward(p)) rewardGrantAction.accept(p);
                markFlag(introCompleteFlag);
                markFlag(rewardGrantedFlag);
                GamePlayState.setCurrent(GamePlayState.PLAYING);
            };
        } else if (dialogueLines.length > 0 && !hasFlag(introCompleteFlag)) {
            onComplete = () -> {
                markFlag(introCompleteFlag);
                GamePlayState.setCurrent(GamePlayState.PLAYING);
            };
        }

        if (linesToShow.length == 0) return;
        GamePlayState.setCurrent(GamePlayState.DIALOGUE);
        dialogue.setVoiceSound(GameSFX.SFX.GOAT_SFX);
        dialogue.open(linesToShow, SPEAKER_NAME, true, onComplete);
    }

    // =========================================================
    // SETTERS
    // =========================================================

    public void setDialogue(Dialogue d) { this.dialogue = d; }

    public void setStoryFlagHooks(Predicate<String> hasFlag, Consumer<String> addFlag) {
        this.hasStoryFlagFn = hasFlag;
        this.addStoryFlagFn = addFlag;
    }

    public void setRewardUnlockCondition(Predicate<Player> condition) {
        this.rewardUnlockCondition = condition;
    }

    public void configureTwoStepReward(String introFlag, String rewardFlag,
                                       String[] rewardLines, String[] postRewardLines,
                                       Predicate<Player> ownedCheck, Consumer<Player> grantAction) {
        this.introCompleteFlag       = normalize(introFlag);
        this.rewardGrantedFlag       = normalize(rewardFlag);
        this.rewardDialogueLines     = rewardLines     == null ? new String[0] : rewardLines.clone();
        this.postRewardDialogueLines = postRewardLines == null ? new String[0] : postRewardLines.clone();
        this.rewardOwnedCheck        = ownedCheck;
        this.rewardGrantAction       = grantAction;
    }

    // =========================================================
    // SPRITE LOADING  (same ImageIO + trim + scale as WorldNpc)
    // =========================================================

    private void loadSprite(Direction dir, String path) {
        try {
            BufferedImage source = ImageIO.read(new File(path));
            if (source == null) return;
            BufferedImage trimmed = trimBounds(source);
            double nativeH = Math.max(1.0, trimmed.getHeight());
            double scale   = TARGET_H / nativeH;
            double renderW = trimmed.getWidth() * scale;
            GImage img = new GImage(trimmed);
            img.setSize(renderW, TARGET_H);
            dirSprites.put(dir, img);
            spriteWidths.put(dir, renderW);
        } catch (IOException | RuntimeException ignored) {}
    }

    private static BufferedImage trimBounds(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                if (((src.getRGB(px, py) >>> 24) & 0xFF) == 0) continue;
                if (px < minX) minX = px;
                if (py < minY) minY = py;
                if (px > maxX) maxX = px;
                if (py > maxY) maxY = py;
            }
        }
        return (maxX < minX) ? src : src.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    // =========================================================
    // POSITION HELPERS
    // =========================================================

    private void syncSpritePositions() {
        for (Direction dir : dirSprites.keySet()) {
            GImage img = dirSprites.get(dir);
            Double sw  = spriteWidths.get(dir);
            if (img != null && sw != null) {
                img.setLocation(x + (TILE - sw) / 2.0, y + (TILE - TARGET_H));
            }
        }
    }

    private void positionLabels() {
        double cx = x + TILE / 2.0;
        titleLabel.setLocation(cx - titleLabel.getWidth() / 2.0, y + TITLE_OFFSET_Y);
        hintLabel.setLocation(cx - hintLabel.getWidth()  / 2.0, y + HINT_OFFSET_Y);
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private boolean hasFlag(String flag) {
        return flag != null && hasStoryFlagFn != null && hasStoryFlagFn.test(flag);
    }

    private void markFlag(String flag) {
        if (flag != null && addStoryFlagFn != null) addStoryFlagFn.accept(flag);
    }

    private boolean ownsReward(Player p) {
        return p != null && rewardOwnedCheck != null && rewardOwnedCheck.test(p);
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
