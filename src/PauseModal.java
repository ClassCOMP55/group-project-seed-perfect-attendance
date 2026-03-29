import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import acm.graphics.*;

/*
(a lot fo this was set up by Charles/Gorge before the pivot on 03/27/26 )
Roberto: PauseModal/Pause Menu
Who RIGs it: TBD
Does not own Player or combat
Extends GraphicsPane
*/


/*
================================
THE PLAN FOR THE NEW PAUSE MENU
================================

TEAM CONTRACT (who owns what)
- MainApplication owns "is the game paused?" (flag / lifecycle). Document there when implemented.
  PauseModal does not set global pause by itself; it is shown/hidden from MainApplication.
- PauseModal only READS data: Player (inventory items, relic flags, facing for portrait) and
  current settings (same source as SettingsPane / SettingsIO — keep one source of truth).
- PauseModal does not own combat, save files, or world update rules.

OPEN / CLOSE / STACKING
- Open with ESC during gameplay. No on-screen X button on the game HUD for pause.
- Cannot open pause during room transition or loading-style moments (enforce in MainApplication).
- Pause stacks on top of everything (including dialogue if both exist — order TBD; this menu draws last).
- ESC closes pause from anywhere in this menu. Destructive actions (exit to menu, quit game) always
  need a second confirmation. (Game no longer autosave — only designated save points.)

GAME FREEZE VS AUDIO
- While pause is open: stop gameplay updates (enemies, damage, room logic, etc.).
- Background music keeps playing (do not pause the music track).
- Menu UI still plays small SFX (chimes, navigation, using bread from inventory, etc.).

SETTINGS TAB
- Duplicate the same controls/values as SettingsPane so main-menu and pause never disagree.
- Settings apply immediately when changed (no staged values / no separate Apply for volume etc.).
  If resolution/fullscreen needs a moment to apply, handle that in the same write path as SettingsPane.

INPUT (ship in layers: keyboard first)
- W/A/S/D = move focus up/left/down/right in the grid; no wrap — stays if no neighbor.
- J/K = left tab / right tab (gameplay uses for attack/dodge are disabled while this menu is open).
- SPACEBAR = use selected consumable (e.g. HealingBread) when focus is on a usable item.
- Mouse: clicks should work; keyboard navigation is required for first playable. Later: mouse hover
  moves focus in the inventory list (mouseMoved) — implement after keyboard path is solid.

TABS / CONTENT
- Two tabs: default = Inventory (items + relic display + player sprite facing Player direction),
  other = Settings (volume, window presets + fullscreen, return to game, main menu, quit).
- Inventory: static description area for the focused item; "use" only for consumables; relics read-only.

*/


/**
 * Full-screen dim overlay with a "Paused Game" panel: Settings, Return to Main Menu, Exit Game.
 */
public class PauseModal extends GraphicsPane {

    private GRect dimOverlay;
    private GRoundRect panel;
    private GLabel titleLabel;

    private GRoundRect settingsFrame;
    private GLabel settingsLabel;
    private GRoundRect mainMenuFrame;
    private GLabel mainMenuLabel;

    private GRoundRect exitFrame;
    private GLabel exitLabel;

    private static final Color PANEL_FILL = new Color(28, 32, 62);
    private static final Color PANEL_BORDER = new Color(255, 215, 120);
    private static final Color BTN_FILL = new Color(35, 40, 75);
    private static final Color TEXT_GOLD = new Color(255, 215, 120);
    private static final Color TITLE_CREAM = new Color(255, 248, 220);

    public PauseModal(MainApplication mainScreen) {
        this.mainScreen = mainScreen;
    }

    /**
     * Shows dimmed background and the pause panel on top of the current scene.
     */
    public void showPause() {
        hideContent();

        double fw = mainScreen.getWidth();
        double fh = mainScreen.getHeight();
        dimOverlay = new GRect(0, 0, fw, fh);
        dimOverlay.setFilled(true);
        dimOverlay.setFillColor(new Color(0, 0, 0, 160));
        dimOverlay.setColor(new Color(0, 0, 0, 0));
        addBoth(dimOverlay);

        double lw = mainScreen.getLayoutWidth();
        double ox = originX();
        double panelW = Math.min(scaleX(380) - scaleX(0), lw * 0.82);
        double panelH = scaleY(260) - scaleY(0);
        double px = ox + (lw - panelW) / 2;
        double py = scaleY(100);
        double arc = Math.min(scaleX(14) - scaleX(0), scaleY(14) - scaleY(0));

        panel = new GRoundRect(px, py, panelW, panelH, arc, arc);
        panel.setFilled(true);
        panel.setFillColor(PANEL_FILL);
        panel.setColor(PANEL_BORDER);
        addBoth(panel);

        titleLabel = new GLabel("Paused Game", 0, 0);
        titleLabel.setFont("SansSerif-BOLD-" + Math.max(14, scaleFontSize(22)));
        titleLabel.setColor(TITLE_CREAM);
        titleLabel.setLocation(px + (panelW - titleLabel.getWidth()) / 2, py + scaleY(28));
        addBoth(titleLabel);

        double bw = Math.min(scaleX(280) - scaleX(0), panelW - scaleX(40));
        double bh = scaleY(36) - scaleY(0);
        double gap = scaleY(10) - scaleY(0);
        double btnLeft = px + (panelW - bw) / 2;
        double y0 = py + scaleY(58);

        settingsFrame = new GRoundRect(btnLeft, y0, bw, bh, arc, arc);
        settingsFrame.setFilled(true);
        settingsFrame.setFillColor(BTN_FILL);
        settingsFrame.setColor(PANEL_BORDER);
        addBoth(settingsFrame);
        settingsLabel = new GLabel("Settings", 0, 0);
        settingsLabel.setFont("SansSerif-BOLD-" + Math.max(12, scaleFontSize(16)));
        settingsLabel.setColor(TEXT_GOLD);
        centerLabelInButton(settingsLabel, settingsFrame);
        addBoth(settingsLabel);

        mainMenuFrame = new GRoundRect(btnLeft, y0 + bh + gap, bw, bh, arc, arc);
        mainMenuFrame.setFilled(true);
        mainMenuFrame.setFillColor(BTN_FILL);
        mainMenuFrame.setColor(PANEL_BORDER);
        addBoth(mainMenuFrame);
        mainMenuLabel = new GLabel("Return to Main Menu", 0, 0);
        mainMenuLabel.setFont("SansSerif-BOLD-" + Math.max(12, scaleFontSize(16)));
        mainMenuLabel.setColor(TEXT_GOLD);
        centerLabelInButton(mainMenuLabel, mainMenuFrame);
        addBoth(mainMenuLabel);

        exitFrame = new GRoundRect(btnLeft, y0 + 2 * (bh + gap), bw, bh, arc, arc);
        exitFrame.setFilled(true);
        exitFrame.setFillColor(BTN_FILL);
        exitFrame.setColor(PANEL_BORDER);
        addBoth(exitFrame);
        exitLabel = new GLabel("Exit Game", 0, 0);
        exitLabel.setFont("SansSerif-BOLD-" + Math.max(12, scaleFontSize(16)));
        exitLabel.setColor(TEXT_GOLD);
        centerLabelInButton(exitLabel, exitFrame);
        addBoth(exitLabel);

        restackOnTop();
    }

    /** Rebuild at the new window size while keeping the pause menu open. */
    public void refreshForResize() {
        if (contents.isEmpty()) {
            return;
        }
        showPause();
    }

    private void centerLabelInButton(GLabel g, GRoundRect r) {
        double x = r.getX() + (r.getWidth() - g.getWidth()) / 2;
        double y = r.getY() + (r.getHeight() + g.getAscent()) / 2;
        g.setLocation(x, y);
    }

    private void addBoth(GObject g) {
        contents.add(g);
        mainScreen.add(g);
    }

    /**
     * Re-adds all objects on top after a canvas refresh (resize).
     */
    public void restackOnTop() {
        java.util.ArrayList<GObject> snapshot = new java.util.ArrayList<>(contents);
        for (GObject g : snapshot) {
            mainScreen.remove(g);
            mainScreen.add(g);
        }
    }

    private static boolean containsPoint(GObject g, double x, double y) {
        return g != null && g.contains(x, y);
    }

    /**
     * Left-button release handler (same pattern as {@link CardPlayModal}).
     */
    public void handlePointer(double x, double y) {
        if (containsPoint(settingsFrame, x, y) || containsPoint(settingsLabel, x, y)) {
            hideContent();
            mainScreen.switchToSettingsScreen();
            return;
        }
        if (containsPoint(mainMenuFrame, x, y) || containsPoint(mainMenuLabel, x, y)) {
            hideContent();
            mainScreen.switchToStartMenuScreen();
            return;
        }
        if (containsPoint(exitFrame, x, y) || containsPoint(exitLabel, x, y)) {
            System.exit(0);
        }
    }

    @Override
    public void hideContent() {
        for (GObject item : contents) {
            mainScreen.remove(item);
        }
        contents.clear();
        dimOverlay = null;
        panel = null;
        titleLabel = null;
        settingsFrame = null;
        settingsLabel = null;
        mainMenuFrame = null;
        mainMenuLabel = null;
        exitFrame = null;
        exitLabel = null;
    }
}
