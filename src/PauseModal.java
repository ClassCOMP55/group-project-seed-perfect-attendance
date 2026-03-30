import java.awt.Color;
import java.awt.event.KeyEvent;

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
- PauseModal displays from Player + settings: inventory, relic flags, facing for portrait,
  coins, health/hearts for the pause panel. Single source of truth for health is Player — HUD
  reads the same; using HealingBread here updates Player once, then refresh hearts in this menu;
  after unpause HUD matches because it also reads Player.
- Current settings: same source as SettingsPane / SettingsIO (one source of truth).
- PauseModal does not own combat or world update rules. Consumable "use" delegates to Player
  (or a thin helper), not a duplicate inventory/hearts copy.

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
- Active tab indicator (mockup): combine (1) light rectangle fill behind the active tab label and
  (2) a thicker bottom border on the active tab so it visually connects to the panel body.

HEALTH IN PAUSE
- Show hearts (or equivalent) in the inventory tab; values always from Player — same truth as HUDoverlay.

SAVE REMINDER (soft nudge)
- Include UI space for "last saved" / periodic reminder to save (see mockup). Full timer + persistence
  wiring comes with SaveManager + SaveData + SavePoint (next major Person 4 chunk per pivot doc).
  Until then, stub text or placeholder is OK.

BUILD ORDER (pivot doc)
- Finish PauseMenu + InventoryMenu + DialogueBox before SaveManager + SaveData + SavePoint.
  This file is the pause menu piece; keep save-heavy logic out until that milestone unless stubbing.

*/


/**
 * Full-screen dim pause overlay. inventory + settings tabs (see plan block above).
 * Intent: dim everything, bring up main panel with two tab headers 
 * (Inventory active, Settings inactive by default)
 * and a single body placeholder. Legacy three-button pause UI removed.
 * Keyboard: <b>J</b> = Inventory tab, <b>K</b> = Settings tab; mouse tab hits come later.
 */
public class PauseModal extends GraphicsPane
{
    //CONSTANTS

    //Layout measurement 
    //(pixels from current window size; pivot targets fixed 16:9 presets, no more scaleX/scaleY at this time) 
  private static final double PAUSE_PANEL_WIDTH_RATIO = 0.78;
  private static final double PAUSE_PANEL_HEIGHT_RATIO = 0.72;
  private static final double PAUSE_PANEL_TOP_MARGIN_RATIO = 0.08;
  private static final double TAB_BUTTON_HEIGHT = 36;
  private static final double TAB_PAD_X_INDENT = 10;
  private static final double TAB_GAP = 6;
  //Pixels for the thick “active tab connects to body” accent under the "in use" tab.
  private static final double ACTIVE_TAB_BOTTOM_BAR = 4;

  /**
   * {@code false} = Inventory tab selected; 
   * {@code true} = Settings tab selected.
   * Reset when pause opens fresh; kept when {@link #refreshForResize()} rebuilds the layout.
   */
  private boolean settingsTabActive;

  private GRect dimOverlay;
  private GRoundRect panel;

  //Background for the Inventory tab (active on open).
  private GRect inventoryTabBg;
  //Background for the Settings tab (inactive on open).
  private GRect settingsTabBg;
  //Thick line under whichever tab is active (J/K moves it).
  private GRect activeTabBottomAccent;
  private GLabel inventoryTabLabel;
  private GLabel settingsTabLabel;
  private GLabel tabKeysHintLabel;
  //Stub until inventory/settings bodies are built.
  private GLabel bodyPlaceholderLabel;

  public PauseModal(MainApplication mainScreen)
  {
    this.mainScreen = mainScreen;
  }

  /**
   * Builds dim overlay & main pause panel, then tab strip (Inventory / Settings) and stub body text.
   * Opens on Inventory; {@code J} / {@code K} switch tabs while pause is open. Uses window size from
   * {@code mainScreen} only — no {@code scaleX}/{@code scaleY}.
   */
  public void showPause()
  {
    // If pause UI already exists, we are rebuilding (e.g. resize) — keep which tab was selected.
    boolean preserveTabSelection = !contents.isEmpty();
    hideContent();
    if (!preserveTabSelection)
    {
      settingsTabActive = false;
    }

    double fw = mainScreen.getWidth();
    double fh = mainScreen.getHeight();
    dimOverlay = new GRect(0, 0, fw, fh);
    dimOverlay.setFilled(true);
    dimOverlay.setFillColor(new Color(0, 0, 0, 160));
    dimOverlay.setColor(new Color(0, 0, 0, 0));
    addBoth(dimOverlay);

    double panelW = fw * PAUSE_PANEL_WIDTH_RATIO;
    double panelH = fh * PAUSE_PANEL_HEIGHT_RATIO;
    double px = (fw - panelW) / 2.0;
    double py = fh * PAUSE_PANEL_TOP_MARGIN_RATIO;
    double arc = Math.min(18, panelW * 0.02);

    panel = new GRoundRect(px, py, panelW, panelH, arc, arc);
    panel.setFilled(true);
    panel.setFillColor(Color.DARK_GRAY);
    panel.setColor(Color.WHITE);
    addBoth(panel);

    double tabY = py + 8;
    double tabInnerW = (panelW - 2 * TAB_PAD_X_INDENT - TAB_GAP) / 2.0;

    inventoryTabBg = new GRect(px + TAB_PAD_X_INDENT, tabY, tabInnerW, TAB_BUTTON_HEIGHT);
    inventoryTabBg.setFilled(true);
    inventoryTabBg.setFillColor(Color.LIGHT_GRAY);
    inventoryTabBg.setColor(Color.BLACK);
    addBoth(inventoryTabBg);

    settingsTabBg = new GRect(px + TAB_PAD_X_INDENT + tabInnerW + TAB_GAP, tabY, tabInnerW, TAB_BUTTON_HEIGHT);
    settingsTabBg.setFilled(true);
    settingsTabBg.setFillColor(Color.GRAY);
    settingsTabBg.setColor(Color.BLACK);
    addBoth(settingsTabBg);

    activeTabBottomAccent =
        new GRect(px + TAB_PAD_X_INDENT, tabY + TAB_BUTTON_HEIGHT, tabInnerW, ACTIVE_TAB_BOTTOM_BAR);
    activeTabBottomAccent.setFilled(true);
    activeTabBottomAccent.setFillColor(Color.BLACK);
    addBoth(activeTabBottomAccent);

    inventoryTabLabel = new GLabel("Inventory", 0, 0);
    inventoryTabLabel.setFont("SansSerif-BOLD-16");
    inventoryTabLabel.setColor(Color.BLACK);
    centerLabelInRect(inventoryTabLabel, inventoryTabBg);
    addBoth(inventoryTabLabel);

    settingsTabLabel = new GLabel("Settings", 0, 0);
    settingsTabLabel.setFont("SansSerif-BOLD-16");
    settingsTabLabel.setColor(Color.BLACK);
    centerLabelInRect(settingsTabLabel, settingsTabBg);
    addBoth(settingsTabLabel);

    tabKeysHintLabel = new GLabel("J = Inventory tab   K = Settings tab", 0, 0);
    tabKeysHintLabel.setFont("SansSerif-PLAIN-12");
    tabKeysHintLabel.setColor(Color.LIGHT_GRAY);
    tabKeysHintLabel.setLocation(px + TAB_PAD_X_INDENT, tabY + TAB_BUTTON_HEIGHT + ACTIVE_TAB_BOTTOM_BAR + 18);
    addBoth(tabKeysHintLabel);

    bodyPlaceholderLabel = new GLabel("", 0, 0);
    bodyPlaceholderLabel.setFont("SansSerif-PLAIN-14");
    bodyPlaceholderLabel.setColor(Color.WHITE);
    bodyPlaceholderLabel.setLocation(px + TAB_PAD_X_INDENT, py + TAB_BUTTON_HEIGHT + ACTIVE_TAB_BOTTOM_BAR + 48);
    addBoth(bodyPlaceholderLabel);

    refreshPauseFrameDecoration();
    restackOnTop();
  }

  /**
   * Paints tab headers + bottom accent for {@link #settingsTabActive}; updates stub body copy per tab.
   */
  private void refreshPauseFrameDecoration()
  {
    if (inventoryTabBg == null || settingsTabBg == null || activeTabBottomAccent == null)
    {
      return;
    }
    if (!settingsTabActive)
    {
      inventoryTabBg.setFillColor(Color.LIGHT_GRAY);
      settingsTabBg.setFillColor(Color.GRAY);
      activeTabBottomAccent.setLocation(inventoryTabBg.getX(), tabBottomAccentY(inventoryTabBg));
    }
    else
    {
      inventoryTabBg.setFillColor(Color.GRAY);
      settingsTabBg.setFillColor(Color.LIGHT_GRAY);
      activeTabBottomAccent.setLocation(settingsTabBg.getX(), tabBottomAccentY(settingsTabBg));
    }
    if (bodyPlaceholderLabel != null)
    {
      if (!settingsTabActive)
      {
        bodyPlaceholderLabel.setLabel(
            "Inventory tab — item row, description, player panel, hearts/relics (keyboard layout next).");
      }
      else
      {
        bodyPlaceholderLabel.setLabel(
            "Settings tab — volume, window presets, return / main menu / quit (keyboard layout next).");
      }
    }
  }

  private double tabBottomAccentY(GRect tabBg)
  {
    return tabBg.getY() + TAB_BUTTON_HEIGHT;
  }

  /** Rebuild at the new window size while keeping the pause menu open. */
  public void refreshForResize()
  {
    if (contents.isEmpty())
    {
      return;
    }
    showPause();
  }

  /** Centers {@code g} inside {@code r} (tab cell). */
  private void centerLabelInRect(GLabel g, GRect r)
  {
    double lx = r.getX() + (r.getWidth() - g.getWidth()) / 2;
    double ly = r.getY() + (r.getHeight() + g.getAscent()) / 2;
    g.setLocation(lx, ly);
  }

  private void addBoth(GObject g)
  {
    contents.add(g);
    mainScreen.add(g);
  }

  /**
   * Re-adds all objects on top after a canvas refresh (resize).
   */
  public void restackOnTop()
  {
    java.util.ArrayList<GObject> snapshot = new java.util.ArrayList<>(contents);
    for (GObject g : snapshot)
    {
      mainScreen.remove(g);
      mainScreen.add(g);
    }
  }

  /**
   * Mouse release on pause UI. Step 1: no click targets wired (tabs + buttons come next).
   */
  public void handlePointer(double x, double y)
  {
    // Intentionally empty until tab hit-tests and settings/inventory actions are added.
  }

  @Override
  public void hideContent()
  {
    for (GObject item : contents)
    {
      mainScreen.remove(item);
    }
    contents.clear();
    dimOverlay = null;
    panel = null;
    inventoryTabBg = null;
    settingsTabBg = null;
    activeTabBottomAccent = null;
    inventoryTabLabel = null;
    settingsTabLabel = null;
    tabKeysHintLabel = null;
    bodyPlaceholderLabel = null;
  }

  /**
   * ESC closes the pause overlay.
   * J / K switch tabs when that would change the selection.
   * Trying to open the same tab twice does nothing.
   */
  @Override
  public void keyPressed(KeyEvent e)
  {
    if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
    {
      hideContent();
      return;
    }
    if (e.getKeyCode() == KeyEvent.VK_J)
    {
        //pressing J when the Inventory tab is already active does nothing
      if (!settingsTabActive)
      {
        return;
      }
      settingsTabActive = false;
      refreshPauseFrameDecoration();
      return;
    }
    if (e.getKeyCode() == KeyEvent.VK_K)
    {
      //pressing K when the Settings tab is already active does nothing
      if (settingsTabActive)
      {
        return;
      }
      settingsTabActive = true;
      refreshPauseFrameDecoration();
    }
  }

  /**
   * Local TEST only. Run this class from the IDE: 1280×720 window with a fake {@link HUDoverlay}
   * (same stub as {@link HUDoverlay#main(String[])}), then <b>ESC</b> opens/closes {@link PauseModal}
   * on top so you can check dimming over the HUD. Nothing here is wired into the real game loop.
   */
  public static void main(String[] args)
  {
    class Sandbox extends MainApplication
    {
      private static final int TEST_PREVIEW_W = 1280;
      private static final int TEST_PREVIEW_H = 720;

      private final PauseModal sandboxPause = new PauseModal(this);

      @Override
      public void run()
      {
        setSize(TEST_PREVIEW_W, TEST_PREVIEW_H);
        setupInteractions();
        // Preview HUD only inside this test harness (mirrors HUDoverlay#main Host + showAll).
        class Host extends GraphicsPane
        {
          Host()
          {
            mainScreen = Sandbox.this;
          }
        }
        Host host = new Host();
        HUDoverlay hud = new HUDoverlay();
        HUDoverlay.HudSnapshot snap =
            new HUDoverlay.HudSnapshot(3, false, false, 999, true, true, true, true);
        hud.showAll(host, snap);
        requestFocus();
      }

      @Override
      public void keyPressed(KeyEvent e)
      {
        if (!sandboxPause.contents.isEmpty())
        {
          sandboxPause.keyPressed(e);
          return;
        }
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
        {
          sandboxPause.showPause();
        }
      }

      @Override
      public void keyReleased(KeyEvent e)
      {
        if (!sandboxPause.contents.isEmpty())
        {
          return;
        }
      }

      @Override
      public void keyTyped(KeyEvent e)
      {
        if (!sandboxPause.contents.isEmpty())
        {
          return;
        }
      }
    }
    new Sandbox().start();
  }

}
