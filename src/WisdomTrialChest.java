import acm.graphics.GCanvas;
import acm.graphics.GImage;
import acm.graphics.GRect;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

/**
 * Trial of Wisdom chest that quizzes the player with three consecutive riddles
 * before awarding the reflect relic.
 */
public class WisdomTrialChest extends WorldObject {
    private static final Color CHEST_CLOSED_COLOR = new Color(180, 140, 60);
    private static final double CHEST_SPRITE_TARGET_HEIGHT = 72.0;

    private static final String SPEAKER_NAME = "Chest";

    private static final String[] RIDDLE_PROMPTS = {
        "A hero's burden grows with every strength they gain. What must guide their choices when they wield such power?",
        "A password guards what lies behind my lock. What bond, more precious than gold or steel, opens doors that strength alone cannot?",
        "True heroes refuse to accept their limits. What do they reach for when the impossible stands before them?"
    };

    private static final String[][] RIDDLE_OPTIONS = {
        { "A) Wisdom", "B) Responsibility", "C) Courage" },
        { "A) Trust", "B) Friend", "C) Loyalty" },
        { "A) Beyond", "B) Ultra", "C) Instinct" }
    };

    private static final int[] CORRECT_OPTION_INDEX = { 1, 1, 1 };

    private final String chestId;
    private boolean isOpen;
    private Dialogue dialogue;
    private Consumer<String> collectedItemRecorder;

    private final GImage chestSprite;
    private final BufferedImage openImage;
    private final GRect placeholder;
    private double spriteRenderWidth = 48.0;
    private double spriteRenderHeight = 48.0;

    public WisdomTrialChest(double x, double y, String chestId) {
        super(x, y, 48, 48);
        this.chestId = chestId;
        this.isOpen = false;
        this.chestSprite = loadSprite("assets/visuals/png's/chest_closed.png");
        this.openImage   = loadRawImage("assets/visuals/png's/chest.png");

        this.placeholder = new GRect(x, y, 48, 48);
        this.placeholder.setFilled(true);
        this.placeholder.setFillColor(CHEST_CLOSED_COLOR);
    }

    @Override
    public void draw(GCanvas canvas) {
        if (!visible) return;
        resetVisualPosition();
        if (chestSprite != null) {
            canvas.add(chestSprite);
        } else {
            canvas.add(placeholder);
        }
    }

    @Override
    public void removeFrom(GCanvas canvas) {
        if (chestSprite != null) {
            canvas.remove(chestSprite);
        }
        canvas.remove(placeholder);
    }

    @Override
    public void panVisual(double panX, double panY) {
        if (chestSprite != null) {
            chestSprite.move(panX, panY);
        }
        placeholder.move(panX, panY);
    }

    @Override
    public void resetVisualPosition() {
        if (chestSprite != null) {
            chestSprite.setLocation(x + (48.0 - spriteRenderWidth) / 2.0, y + (48.0 - spriteRenderHeight));
        }
        placeholder.setLocation(x, y);
    }

    @Override
    public boolean isInteractable() {
        return !isOpen;
    }

    @Override
    public void onInteract(Player p) {
        if (isOpen || p == null || dialogue == null || dialogue.isOpen()) {
            return;
        }
        askRiddle(p, 0);
    }

    private void askRiddle(Player player, int riddleIndex) {
        if (dialogue == null || riddleIndex < 0 || riddleIndex >= RIDDLE_PROMPTS.length) {
            return;
        }

        GamePlayState.setCurrent(GamePlayState.DIALOGUE);
        dialogue.openChoicePrompt(
            SPEAKER_NAME,
            RIDDLE_PROMPTS[riddleIndex],
            RIDDLE_OPTIONS[riddleIndex],
            () -> handleRiddleAnswer(player, riddleIndex)
        );
    }

    private void handleRiddleAnswer(Player player, int riddleIndex) {
        boolean correct = dialogue != null && dialogue.getSelectedOption() == CORRECT_OPTION_INDEX[riddleIndex];
        if (!correct) {
            showFailureDialogue();
            return;
        }

        if (riddleIndex + 1 < RIDDLE_PROMPTS.length) {
            askRiddle(player, riddleIndex + 1);
            return;
        }

        openAndGrantReward(player);
    }

    private void showFailureDialogue() {
        if (dialogue == null) return;
        GamePlayState.setCurrent(GamePlayState.DIALOGUE);
        dialogue.open(
            new String[]{
                "That answer rings false.",
                "Return when your wisdom is sharper."
            },
            SPEAKER_NAME,
            false,
            () -> GamePlayState.setCurrent(GamePlayState.PLAYING)
        );
    }

    private void openAndGrantReward(Player player) {
        if (isOpen || player == null) {
            return;
        }

        isOpen = true;
        swapToOpenSprite();
        GameSFX.play(GameSFX.SFX.CHEST_OPEN);
        player.setHasReflect(true);
        player.collectItem(new ReflectRelicItem());
        if (collectedItemRecorder != null) {
            collectedItemRecorder.accept(chestId);
        }

        if (dialogue != null) {
            GamePlayState.setCurrent(GamePlayState.DIALOGUE);
            dialogue.open(
                new String[]{"You obtained Relic of Wisdom!"},
                SPEAKER_NAME,
                false,
                () -> GamePlayState.setCurrent(GamePlayState.PLAYING)
            );
        }
    }

    public void forceOpen() {
        isOpen = true;
        swapToOpenSprite();
    }

    public void setDialogue(Dialogue dialogue) {
        this.dialogue = dialogue;
    }

    public void setCollectedItemRecorder(Consumer<String> recorder) {
        this.collectedItemRecorder = recorder;
    }

    public String getChestId() {
        return chestId;
    }

    public boolean isOpen() {
        return isOpen;
    }

    public boolean isLocked() {
        return false;
    }

    private void swapToOpenSprite() {
        if (chestSprite == null || openImage == null) return;
        BufferedImage trimmed = trimTransparentBounds(openImage);
        double nativeWidth  = Math.max(1.0, trimmed.getWidth());
        double nativeHeight = Math.max(1.0, trimmed.getHeight());
        double scale = CHEST_SPRITE_TARGET_HEIGHT / nativeHeight;
        spriteRenderWidth  = Math.max(48.0, nativeWidth * scale);
        spriteRenderHeight = CHEST_SPRITE_TARGET_HEIGHT;
        chestSprite.setImage(trimmed);
        chestSprite.setSize(spriteRenderWidth, spriteRenderHeight);
        resetVisualPosition();
    }

    private BufferedImage loadRawImage(String path) {
        try {
            return ImageIO.read(new File(path));
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private GImage loadSprite(String path) {
        try {
            BufferedImage source = ImageIO.read(new File(path));
            if (source == null) return null;
            BufferedImage trimmed = trimTransparentBounds(source);
            GImage image = new GImage(trimmed);
            double nativeWidth = Math.max(1.0, image.getWidth());
            double nativeHeight = Math.max(1.0, image.getHeight());
            double scale = CHEST_SPRITE_TARGET_HEIGHT / nativeHeight;
            spriteRenderWidth = Math.max(48.0, nativeWidth * scale);
            spriteRenderHeight = CHEST_SPRITE_TARGET_HEIGHT;
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
}
