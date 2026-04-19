import acm.graphics.GCanvas;
import acm.graphics.GLabel;

import java.awt.Color;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Interactable tree cluster that can only be cleared once the player has the Mark of the Hero.
 * While locked, it shows an "e to interact" hint like other world objects.
 * On unlock, it swaps the room art to the open variant and converts the blocked tiles to floor.
 */
public class HeroThicket extends WorldObject {
    private static final double TITLE_BASELINE_Y = -10.0;
    private static final double HINT_BASELINE_Y = 64.0;
    private static final double INTERACT_PADDING_PX = 24.0;

    private static final Color TITLE_COLOR = new Color(222, 241, 184);
    private static final Color HINT_COLOR = new Color(245, 231, 192);

    private final String speakerName;
    private final String unlockFlag;
    private final int[][] blockedTiles;
    private final TileMap tileMap;
    private final Room room;
    private final String[] openBackgroundPaths;
    private final double visualWidth;

    private final GLabel titleLabel;
    private final GLabel hintLabel;

    private Dialogue dialogue;
    private Predicate<String> hasStoryFlag;
    private Consumer<String> addStoryFlag;
    private boolean unlocked;
    private GCanvas canvas;
    private String[] unlockDialogueLines;

    public HeroThicket(double x, double y,
                       double width, double height,
                       String speakerName,
                       String unlockFlag,
                       int[][] blockedTiles,
                       TileMap tileMap,
                       Room room,
                       Dialogue dialogue,
                       String... openBackgroundPaths) {
        super(x, y, width, height);
        this.visualWidth = width;
        this.speakerName = (speakerName == null || speakerName.trim().isEmpty())
            ? "Trees"
            : speakerName.trim();
        this.unlockFlag = normalizeFlag(unlockFlag);
        this.blockedTiles = copyTiles(blockedTiles);
        this.tileMap = tileMap;
        this.room = room;
        this.dialogue = dialogue;
        this.openBackgroundPaths =
            openBackgroundPaths == null ? new String[0] : openBackgroundPaths.clone();
        this.unlocked = false;

        // The thicket itself sits on blocked tiles, so interaction needs a forgiving border
        // around the visible tree mass or the player can stand beside it and still miss the target.
        this.hitbox = new Hitbox(
            x - INTERACT_PADDING_PX,
            y - INTERACT_PADDING_PX,
            width + INTERACT_PADDING_PX * 2.0,
            height + INTERACT_PADDING_PX * 2.0
        );

        this.titleLabel = new GLabel(this.speakerName, x, y);
        this.titleLabel.setFont("SansSerif-BOLD-14");
        this.titleLabel.setColor(TITLE_COLOR);

        this.hintLabel = new GLabel("e to interact", x, y);
        this.hintLabel.setFont("SansSerif-PLAIN-12");
        this.hintLabel.setColor(HINT_COLOR);

        resetVisualPosition();
    }

    @Override
    public void draw(GCanvas canvas) {
        this.canvas = canvas;
        if (!visible || unlocked) return;
        resetVisualPosition();
        canvas.add(titleLabel);
        canvas.add(hintLabel);
    }

    @Override
    public void removeFrom(GCanvas canvas) {
        canvas.remove(titleLabel);
        canvas.remove(hintLabel);
        if (this.canvas == canvas) {
            this.canvas = null;
        }
    }

    @Override
    public boolean isInteractable() {
        return !unlocked;
    }

    @Override
    public void panVisual(double panX, double panY) {
        titleLabel.move(panX, panY);
        hintLabel.move(panX, panY);
    }

    @Override
    public void resetVisualPosition() {
        double centerX = x + visualWidth / 2.0;
        titleLabel.setLocation(centerX - titleLabel.getWidth() / 2.0, y + TITLE_BASELINE_Y);
        hintLabel.setLocation(centerX - hintLabel.getWidth() / 2.0, y + HINT_BASELINE_Y);
    }

    @Override
    public void onInteract(Player p) {
        if (unlocked || dialogue == null || dialogue.isOpen()) return;

        if (p == null || !p.hasMarkOfHero()) {
            GamePlayState.setCurrent(GamePlayState.DIALOGUE);
            dialogue.open(
                new String[]{"You lack the proof of heroism required to proceed. Turn back."},
                speakerName,
                false,
                () -> GamePlayState.setCurrent(GamePlayState.PLAYING)
            );
            return;
        }

        unlockInternal();
        GameSFX.play(GameSFX.SFX.GRASS_CUT);

        String[] lines = (unlockDialogueLines != null && unlockDialogueLines.length > 0)
            ? unlockDialogueLines
            : new String[]{"The thicket parts and the way forward opens."};

        GamePlayState.setCurrent(GamePlayState.DIALOGUE);
        dialogue.open(
            lines,
            speakerName,
            false,
            () -> GamePlayState.setCurrent(GamePlayState.PLAYING)
        );
    }

    public void setDialogue(Dialogue dialogue) {
        this.dialogue = dialogue;
    }

    public void setUnlockDialogue(String... lines) {
        this.unlockDialogueLines = lines;
    }

    public void setStoryFlagHooks(Predicate<String> hasStoryFlag, Consumer<String> addStoryFlag) {
        this.hasStoryFlag = hasStoryFlag;
        this.addStoryFlag = addStoryFlag;
    }

    public void syncPersistentState() {
        if (hasStoryFlag(unlockFlag)) {
            forceUnlock();
        }
    }

    public void forceUnlock() {
        if (unlocked) return;
        unlockInternal();
    }

    private void unlockInternal() {
        unlocked = true;
        applyUnlockedTiles();
        if (room != null && openBackgroundPaths.length > 0) {
            room.replaceBackgroundImage(openBackgroundPaths);
        }
        markStoryFlag(unlockFlag);
        if (canvas != null) {
            removeFrom(canvas);
        }
        hide();
    }

    private void applyUnlockedTiles() {
        if (tileMap == null) return;
        for (int[] tile : blockedTiles) {
            tileMap.setTileType(tile[0], tile[1], Tile.TileType.FLOOR, "assets/tile_floor.png");
        }
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

    private static int[][] copyTiles(int[][] source) {
        if (source == null) return new int[0][0];
        int[][] copy = new int[source.length][2];
        for (int i = 0; i < source.length; i++) {
            copy[i][0] = source[i][0];
            copy[i][1] = source[i][1];
        }
        return copy;
    }
}
