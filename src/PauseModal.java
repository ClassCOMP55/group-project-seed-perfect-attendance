import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import acm.graphics.*;

/*
(a lot of this was set up by Charles/Gorge before the pivot on 03/27/26 )
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
- Item list from Player later: stackables (e.g. Healing Bread) show as {@code Name x N}; non-stackables have no {@code x N}.
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
 * Main panel size: {@link #PAUSE_PANEL_WIDTH_RATIO} / {@link #PAUSE_PANEL_HEIGHT_RATIO} of the window (tuned, not fixed aspect).
 * Intent: dim everything, bring up main panel with two tab headers (tight width: label + horizontal pad).
 * (Inventory active, Settings inactive by default)
 * Inventory tab: stats outlines, portrait/relics column, item list + description outlines (right column).
 * Tight stat-cell sizing: {@link #measureTightInventoryOutline} + {@link #addTightInventoryStatOutline}.
 * Legacy three-button pause UI removed. Keyboard navigation only (no pause-menu mouse): <b>J</b>/<b>K</b> tabs,
 * <b>WASD</b> + <b>Space</b>/<b>Enter</b> per plan block; confirm uses <b>A</b>/<b>D</b> + <b>Enter</b>/<b>Space</b>/<b>Esc</b>.
 */
public class PauseModal extends GraphicsPane
{
    //CONSTANTS

  //Layout measurement (window pixels; no scaleX/scaleY).
  //Panel is a fraction of the game window — not a locked aspect ratio. 
  // Kept wider than a strict 3:4 card so the inventory list/description stay readable.
  // narrower than the old ~0.78 width to cut empty horizontal.

  /*
  =====================
  Adjust these two values to change the size of the pause panel.
  =====================
  */
  private static final double PAUSE_PANEL_WIDTH_RATIO = 0.66;
  private static final double PAUSE_PANEL_HEIGHT_RATIO = 0.70;
/*
 =====================
 End of adjustable values.
 =====================
 */

  private static final double PAUSE_PANEL_TOP_MARGIN_RATIO = 0.08;
  private static final double TAB_BUTTON_HEIGHT = 36;
  private static final double TAB_PAD_X_INDENT = 10;
  private static final double TAB_GAP = 6;
  //Horizontal padding inside each tab cell around the label (tabs are only as wide as text + this).
  private static final double TAB_BUTTON_INNER_PAD_X = 14;
  //Pixels for the thick “active tab connects to body” accent under the "in use" tab.
  private static final double ACTIVE_TAB_BOTTOM_BAR = 4;

  //Inventory stats block (matches {@link HUDoverlay} heart/coin visuals; pause-panel-local).
  private static final double PAUSE_STATS_BELOW_TABS = 12;
  private static final double PAUSE_STATS_ROW_GAP = 10;
  private static final double PAUSE_STATS_COL_GAP = 12;
  private static final double PAUSE_ROUND_BOX_PAD = 8;
  private static final double PAUSE_COINS_SAVE_STACK_GAP = 8;
  /** Extra pixels on the outer width beyond the {@link #PAUSE_ROUND_BOX_PAD} inset (horizontal breathing room). */
  private static final double PAUSE_STATS_OUTLINE_LOOSE = 8;
  //Extra pixels on the outer height beyond the pad pair — keeps single-line and heart rows from feeling cramped.
  private static final double PAUSE_STATS_OUTLINE_VERTICAL_EXTRA = 10;
  //Vertical gap after stats strip and before lower inventory content (portrait row, item list, etc.).
  private static final double INVENTORY_SECTION_GAP = 8;
  private static final double PAUSE_PORTRAIT_RELICS_GAP = 10;
  //Portrait placeholder height from inner width; width = height × narrow factor (tall rectangle, not square).
  private static final double PAUSE_PORTRAIT_CONTENT_HEIGHT_RATIO = 0.30;
  private static final double PAUSE_PORTRAIT_WIDTH_OF_HEIGHT = 0.62;
  //Relic placeholders: small squares ~HUD heart scale; gap between squares (not full-width stretch).
  private static final double PAUSE_RELIC_SLOT_SIZE = 12;
  private static final double PAUSE_RELIC_SLOT_GAP = 6;
  //Horizontal gap between item-list outline and description outline (same row).
  private static final double PAUSE_INVENTORY_LIST_TO_DESC_GAP = 10;
  //Share of usable inner width (minus both outlines’ horizontal shells) for the item list; rest is description.
  private static final double PAUSE_INVENTORY_LIST_WIDTH_SHARE = 0.38;
  private static final double PAUSE_INVENTORY_STUB_LINE_STEP = 18;
  private static final double PAUSE_INVENTORY_DESC_FONT_TOP_INSET = 8;
  private static final double PAUSE_INVENTORY_DESC_LINE_GAP = 3;
  // Dummy blurbs per stub row — same order as the stub list in showPause; swap for real item text later.
  private static final String[] PAUSE_INVENTORY_STUB_DESCRIPTIONS =
      new String[] {
        "Healing Bread (dummy): pretend this restores hearts when used. Stackables show “x N” in the list.",
        "Broken Lever (dummy): pretend this is a quest piece — not usable like bread.",
        "Ore (dummy): pretend this is crafting stuff — long text on purpose so you can see wrapping "
            + "still works when the real item descriptions arrive from Player / inventory."
      };
  /** Settings tab: bottom row (Return / Main Menu / Quit). */
  private static final double PAUSE_SETTINGS_ACTION_BAR_H = 48;
  /** Square thumb on each volume slider track. */
  private static final double PAUSE_SETTINGS_SLIDER_THUMB = 12;
  /** Music/SFX change per <b>A</b>/<b>D</b> while that slider row is focused. */
  private static final int PAUSE_SLIDER_KEY_STEP = 5;
  /** Gap between Music and SFX rows. */
  private static final double PAUSE_SETTINGS_AUDIO_ROW_GAP = 40;
  /** Placeholder resolution labels (replace when real window presets exist). */
  private static final String[] PAUSE_SETTINGS_RESOLUTION_STUBS =
      new String[] {"1280x720", "1600x900", "1920x1080"};
  private static final int PAUSE_HUD_HEART_SEGMENT_COUNT = 6;
  private static final double PAUSE_HUD_HEART_SEG_W = 10;
  private static final double PAUSE_HUD_HEART_SEG_H = 12;
  /** Width of the six HUD heart segments including pair gaps (matches {@link #addPauseHeartsLikeHud}). */
  private static final double PAUSE_HEARTS_CLUSTER_WIDTH =
      6 * PAUSE_HUD_HEART_SEG_W + 2 * (PAUSE_HUD_HEART_SEG_W * 2);
  private static final double PAUSE_HUD_COINS_ICON = 10;
  private static final double PAUSE_HUD_COINS_ICON_LABEL_GAP = 4;
  private static final int PAUSE_HUD_COINS_DISPLAY_MAX = 999;
  /** Until Player / wallet is wired into {@link #showPause()}. */
  private static final int PAUSE_STUB_HEART_SEGMENTS_FILLED = 6;
  /** Until Player / wallet is wired into {@link #showPause()}. */
  private static final int PAUSE_STUB_COINS = 125;

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
  //Thick bar under each tab (only the active tab’s bar is visible — widths match tight tab cells).
  private GRect inventoryTabBottomAccent;
  private GRect settingsTabBottomAccent;
  private GLabel inventoryTabLabel;
  private GLabel settingsTabLabel;
  /** Tab row band, flush to panel inner right: WASD / Space / Esc hints (both tabs; no outline). */
  private GLabel pauseMenuInstructionsLabel;
  private GLabel tabKeysHintLabel;

  //Inventory tab: HUD-style hearts + coins + last saved (stubs until Player / SaveManager feed data)
  private GRect[] pauseHeartSegments;
  private GOval pauseCoinsIcon;
  private GLabel pauseCoinsLabel;
  private GLabel inventoryLastSavedLabel;

  private GRoundRect pauseStatsHintBox;
  private GRoundRect pauseStatsHeartsBox;
  private GRoundRect pauseStatsCoinsSaveBox;

  //Inventory middle column: portrait placeholder + one relic strip (until Player / art assets).
  private GRoundRect pausePortraitOutline;
  private GRect pausePlayerPlaceholder;
  private GRoundRect pauseRelicsOutline;
  private GRect[] pauseRelicPlaceholders;

  //Inventory right column: item list + description (same rounded-outline style as stats cells).
  private GRoundRect pauseInventoryListOutline;
  private GRoundRect pauseInventoryDescriptionOutline;
  private GLabel[] pauseInventoryItemStubLabels;
  /** Wrapped lines inside the description outline (ACM has no multiline label). */
  private GLabel[] pauseInventoryDescriptionLines;
  // Cached inner box for the description outline — rebuild wrapped lines when focus moves.
  private double inventoryDescInnerX;
  private double inventoryDescInnerY;
  private double inventoryDescInnerW;
  private double inventoryDescInnerH;

  /** Left X of the padded column inside the main panel (used when switching tab stub position). */
  private double inventoryContentLeftX;
  /** Right column left edge (item list + description); portrait column ends before this. */
  private double inventoryItemStubLeftX;

  /** First open: seed pause sliders from {@link GameSettings}; later opens keep last drag values. */
  private boolean pauseVolumesInitialized;

  /** Settings tab widgets (visibility toggled with tab). */
  private GObject[] settingsTabWidgets;
  private GRoundRect settingsContentBg;
  private GLine settingsDividerAudioDisplay;
  private GLabel musicVolumeTitleLabel;
  private GLabel sfxVolumeTitleLabel;
  private GLine musicSliderTrack;
  private GLine sfxSliderTrack;
  private GRect musicSliderThumb;
  private GRect sfxSliderThumb;
  private GLabel musicVolumePercentLabel;
  private GLabel sfxVolumePercentLabel;
  private double musicTrackLeft;
  private double musicTrackRight;
  private double musicTrackYCenter;
  private double sfxTrackLeft;
  private double sfxTrackRight;
  private double sfxTrackYCenter;
  private int pauseMusicVolumePercent;
  private int pauseSfxVolumePercent;
  private GRoundRect[] pauseResolutionButtons;
  private GLabel[] pauseResolutionLabels;
  /** 0–2 = stub presets; fourth button is empty placeholder. */
  private int selectedResolutionIndex;
  private GRect pauseFullscreenCheckboxOuter;
  private GLabel pauseFullscreenCheckGlyph;
  private GLabel pauseFullscreenLabel;
  private boolean pauseFullscreenStub;
  private GRect settingsActionBarBg;
  private GRect settingsReturnHit;
  private GRect settingsMainMenuHit;
  private GRect settingsQuitHit;
  private GLabel settingsReturnLabel;
  private GLabel settingsMainMenuLabel;
  private GLabel settingsQuitLabel;
  /** Inventory list row index for stub items (0 .. 2). */
  private int pauseInventoryFocusIndex;
  /** Settings: 0 music, 1 SFX, 2 resolution, 3 fullscreen, 4 return, 5 main menu, 6 quit. */
  private int pauseSettingsFocusIndex;
  /** Confirmation dialog: 0 Cancel, 1 Yes (keyboard <b>A</b>/<b>D</b>). */
  private int pauseConfirmFocus;
  /** Confirmation overlay for Main Menu / Quit (see {@link ConfirmKind}). */
  private ArrayList<GObject> confirmDialogObjects;
  private ConfirmKind confirmPending = ConfirmKind.NONE;
  /** Cancel / Yes button rects — keyboard focus highlight only (not mouse). */
  private GRect confirmCancelHit;
  private GRect confirmYesHit;

  private enum ConfirmKind
  {
    NONE,
    MAIN_MENU,
    QUIT
  }

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
    // Full rebuild of dimmer + panel + lists; 
    // resize calls this while pause stays open.
    boolean preserveTabSelection = !contents.isEmpty(); // true => keep tab + focus across rebuild
    hideContent();
    if (!preserveTabSelection)
    {
      settingsTabActive = false;
      pauseInventoryFocusIndex = 0;
      pauseSettingsFocusIndex = 0;
    }
    if (!pauseVolumesInitialized)
    {
      pauseMusicVolumePercent = GameSettings.getVolumePercent();
      pauseSfxVolumePercent = 100;
      pauseVolumesInitialized = true;
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
    double tabRowLeft = px + TAB_PAD_X_INDENT;

    inventoryTabLabel = new GLabel("Inventory", 0, 0);
    inventoryTabLabel.setFont("SansSerif-BOLD-16");
    inventoryTabLabel.setColor(Color.BLACK);
    settingsTabLabel = new GLabel("Settings", 0, 0);
    settingsTabLabel.setFont("SansSerif-BOLD-16");
    settingsTabLabel.setColor(Color.BLACK);

    double inventoryTabW = inventoryTabLabel.getWidth() + 2 * TAB_BUTTON_INNER_PAD_X;
    double settingsTabW = settingsTabLabel.getWidth() + 2 * TAB_BUTTON_INNER_PAD_X;

    inventoryTabBg = new GRect(tabRowLeft, tabY, inventoryTabW, TAB_BUTTON_HEIGHT);
    inventoryTabBg.setFilled(true);
    inventoryTabBg.setFillColor(Color.LIGHT_GRAY);
    inventoryTabBg.setColor(Color.BLACK);
    addBoth(inventoryTabBg);

    double settingsTabLeft = tabRowLeft + inventoryTabW + TAB_GAP;
    settingsTabBg = new GRect(settingsTabLeft, tabY, settingsTabW, TAB_BUTTON_HEIGHT);
    settingsTabBg.setFilled(true);
    settingsTabBg.setFillColor(Color.GRAY);
    settingsTabBg.setColor(Color.BLACK);
    addBoth(settingsTabBg);

    double accentY = tabY + TAB_BUTTON_HEIGHT;
    inventoryTabBottomAccent =
        new GRect(tabRowLeft, accentY, inventoryTabW, ACTIVE_TAB_BOTTOM_BAR);
    inventoryTabBottomAccent.setFilled(true);
    inventoryTabBottomAccent.setFillColor(Color.BLACK);
    addBoth(inventoryTabBottomAccent);

    settingsTabBottomAccent =
        new GRect(settingsTabLeft, accentY, settingsTabW, ACTIVE_TAB_BOTTOM_BAR);
    settingsTabBottomAccent.setFilled(true);
    settingsTabBottomAccent.setFillColor(Color.BLACK);
    addBoth(settingsTabBottomAccent);

    centerLabelInRect(inventoryTabLabel, inventoryTabBg);
    addBoth(inventoryTabLabel);

    centerLabelInRect(settingsTabLabel, settingsTabBg);
    addBoth(settingsTabLabel);

    pauseMenuInstructionsLabel =
        new GLabel("J/K = tabs  WASD = move  Space/Enter = activate  Esc = close", 0, 0);
    pauseMenuInstructionsLabel.setFont("SansSerif-BOLD-12");
    pauseMenuInstructionsLabel.setColor(Color.WHITE);
    double panelInnerRightX = px + panelW - TAB_PAD_X_INDENT;
    double menuHintsLeft = panelInnerRightX - pauseMenuInstructionsLabel.getWidth();
    double menuHintsBaseline =
        tabY + (TAB_BUTTON_HEIGHT + pauseMenuInstructionsLabel.getAscent()) / 2;
    pauseMenuInstructionsLabel.setLocation(menuHintsLeft, menuHintsBaseline);
    addBoth(pauseMenuInstructionsLabel);

    inventoryContentLeftX = px + TAB_PAD_X_INDENT;
    double innerW = panelW - 2 * TAB_PAD_X_INDENT;

    double statsRow1Top = tabY + TAB_BUTTON_HEIGHT + ACTIVE_TAB_BOTTOM_BAR + PAUSE_STATS_BELOW_TABS;

    tabKeysHintLabel = new GLabel("J = inventory, K = settings", 0, 0);
    tabKeysHintLabel.setFont("SansSerif-PLAIN-12");
    tabKeysHintLabel.setColor(Color.BLACK);

    pauseCoinsLabel = new GLabel("", 0, 0);
    pauseCoinsLabel.setFont("SansSerif-BOLD-14");
    pauseCoinsLabel.setColor(Color.BLACK);
    int displayCoins =
        Math.max(0, Math.min(PAUSE_HUD_COINS_DISPLAY_MAX, getPauseCoins()));
    pauseCoinsLabel.setLabel(String.valueOf(displayCoins));

    inventoryLastSavedLabel = new GLabel("Last Saved: --:--", 0, 0);
    inventoryLastSavedLabel.setFont("SansSerif-BOLD-12");
    inventoryLastSavedLabel.setColor(Color.BLACK);

    //Tight inventory-stats outlines: same outer-size rules for hint, hearts, and coins/save (see helpers below).
    TightOutlineDimensions hintOutlineSize =
        measureTightInventoryOutline(tabKeysHintLabel.getWidth(), tabKeysHintLabel.getAscent());
    TightOutlineDimensions heartsOutlineSize =
        measureTightInventoryOutline(PAUSE_HEARTS_CLUSTER_WIDTH, PAUSE_HUD_HEART_SEG_H);

    double coinUnitW =
        PAUSE_HUD_COINS_ICON + PAUSE_HUD_COINS_ICON_LABEL_GAP + pauseCoinsLabel.getWidth();
    double coinRowH = Math.max(PAUSE_HUD_COINS_ICON, pauseCoinsLabel.getAscent());
    double coinSaveStackH =
        coinRowH + PAUSE_COINS_SAVE_STACK_GAP + inventoryLastSavedLabel.getAscent();
    TightOutlineDimensions coinSaveOutlineSize =
        measureTightInventoryOutline(
            Math.max(coinUnitW, inventoryLastSavedLabel.getWidth()), coinSaveStackH);

    double row1BoxH = hintOutlineSize.outerH;
    double row2BoxH = heartsOutlineSize.outerH;
    double statsBlockH = row1BoxH + PAUSE_STATS_ROW_GAP + row2BoxH;
    double leftStackMaxW = Math.max(hintOutlineSize.outerW, heartsOutlineSize.outerW);

    double innerRightX = inventoryContentLeftX + innerW;
    double minCoinSaveLeft = inventoryContentLeftX + leftStackMaxW + PAUSE_STATS_COL_GAP;
    double coinSaveLeft =
        Math.max(minCoinSaveLeft, innerRightX - coinSaveOutlineSize.outerW);

    RoundedDecoration hintDecoration =
        addTightInventoryStatOutline(inventoryContentLeftX, statsRow1Top, hintOutlineSize);
    pauseStatsHintBox = hintDecoration.outline;
    centerLabelInInnerBounds(
        tabKeysHintLabel,
        hintDecoration.innerX,
        hintDecoration.innerY,
        hintDecoration.innerW,
        hintDecoration.innerH);
    addBoth(tabKeysHintLabel);

    double statsRow2Top = statsRow1Top + row1BoxH + PAUSE_STATS_ROW_GAP;
    RoundedDecoration heartsDecoration =
        addTightInventoryStatOutline(inventoryContentLeftX, statsRow2Top, heartsOutlineSize);
    pauseStatsHeartsBox = heartsDecoration.outline;
    double heartsOriginX =
        heartsDecoration.innerX + (heartsDecoration.innerW - PAUSE_HEARTS_CLUSTER_WIDTH) / 2;
    double heartsOriginY =
        heartsDecoration.innerY + (heartsDecoration.innerH - PAUSE_HUD_HEART_SEG_H) / 2;
    addPauseHeartsLikeHud(heartsOriginX, heartsOriginY, getPauseHeartSegmentsFilled());

    RoundedDecoration coinSaveDecoration =
        addTightInventoryStatOutline(coinSaveLeft, statsRow1Top, coinSaveOutlineSize);
    pauseStatsCoinsSaveBox = coinSaveDecoration.outline;
    double stackTop = coinSaveDecoration.innerY + (coinSaveDecoration.innerH - coinSaveStackH) / 2;
    double coinLeft = coinSaveDecoration.innerX + (coinSaveDecoration.innerW - coinUnitW) / 2;
    double coinIconTop = stackTop + (coinRowH - PAUSE_HUD_COINS_ICON) / 2;
    pauseCoinsIcon = new GOval(coinLeft, coinIconTop, PAUSE_HUD_COINS_ICON, PAUSE_HUD_COINS_ICON);
    pauseCoinsIcon.setColor(Color.BLACK);
    pauseCoinsIcon.setFilled(true);
    pauseCoinsIcon.setFillColor(Color.YELLOW);
    double coinLabelBaseline = stackTop + coinRowH - 2;
    pauseCoinsLabel.setLocation(
        coinLeft + PAUSE_HUD_COINS_ICON + PAUSE_HUD_COINS_ICON_LABEL_GAP, coinLabelBaseline);
    addBoth(pauseCoinsIcon);
    addBoth(pauseCoinsLabel);
    double lastSavedBaseline =
        stackTop + coinRowH + PAUSE_COINS_SAVE_STACK_GAP + inventoryLastSavedLabel.getAscent();
    double lastSavedX =
        coinSaveDecoration.innerX
            + (coinSaveDecoration.innerW - inventoryLastSavedLabel.getWidth()) / 2;
    inventoryLastSavedLabel.setLocation(lastSavedX, lastSavedBaseline);
    addBoth(inventoryLastSavedLabel);

    double statsBlockBottom =
        statsRow1Top + Math.max(statsBlockH, coinSaveOutlineSize.outerH);
    double middleSectionTop = statsBlockBottom + INVENTORY_SECTION_GAP;

    double portraitContentH = innerW * PAUSE_PORTRAIT_CONTENT_HEIGHT_RATIO;
    double portraitContentW = portraitContentH * PAUSE_PORTRAIT_WIDTH_OF_HEIGHT;
    TightOutlineDimensions portraitOutlineSize =
        measureTightInventoryOutline(portraitContentW, portraitContentH);
    RoundedDecoration portraitDecoration =
        addTightInventoryStatOutline(
            inventoryContentLeftX, middleSectionTop, portraitOutlineSize);
    pausePortraitOutline = portraitDecoration.outline;
    double phInset = 0.92;
    double greenW = portraitDecoration.innerW * phInset;
    double greenH = portraitDecoration.innerH * phInset;
    double greenX = portraitDecoration.innerX + (portraitDecoration.innerW - greenW) / 2;
    double greenY = portraitDecoration.innerY + (portraitDecoration.innerH - greenH) / 2;
    pausePlayerPlaceholder = new GRect(greenX, greenY, greenW, greenH);
    pausePlayerPlaceholder.setFilled(true);
    pausePlayerPlaceholder.setFillColor(Color.GREEN);
    pausePlayerPlaceholder.setColor(Color.BLACK);
    addBoth(pausePlayerPlaceholder);

    double relicClusterW =
        3 * PAUSE_RELIC_SLOT_SIZE + 2 * PAUSE_RELIC_SLOT_GAP;
    double relicClusterH = PAUSE_RELIC_SLOT_SIZE;
    TightOutlineDimensions relicOutlineSize =
        measureTightInventoryOutline(relicClusterW, relicClusterH);
    double relicsTop =
        middleSectionTop + portraitOutlineSize.outerH + PAUSE_PORTRAIT_RELICS_GAP;
    double relicOutlineLeftX =
        inventoryContentLeftX
            + (portraitOutlineSize.outerW - relicOutlineSize.outerW) / 2;
    RoundedDecoration relicDecoration =
        addTightInventoryStatOutline(relicOutlineLeftX, relicsTop, relicOutlineSize);
    pauseRelicsOutline = relicDecoration.outline;
    double relTop =
        relicDecoration.innerY
            + (relicDecoration.innerH - PAUSE_RELIC_SLOT_SIZE) / 2;
    double startX =
        relicDecoration.innerX
            + (relicDecoration.innerW - relicClusterW) / 2;
    Color[] relicColors = {Color.MAGENTA, Color.ORANGE, Color.CYAN};
    pauseRelicPlaceholders = new GRect[3];
    for (int i = 0; i < 3; i++)
    {
      double sx = startX + i * (PAUSE_RELIC_SLOT_SIZE + PAUSE_RELIC_SLOT_GAP);
      GRect slot = new GRect(sx, relTop, PAUSE_RELIC_SLOT_SIZE, PAUSE_RELIC_SLOT_SIZE);
      slot.setFilled(true);
      slot.setFillColor(relicColors[i]);
      slot.setColor(Color.BLACK);
      pauseRelicPlaceholders[i] = slot;
      addBoth(slot);
    }

    inventoryItemStubLeftX =
        inventoryContentLeftX + portraitOutlineSize.outerW + PAUSE_STATS_COL_GAP;
    double rightColOuterW = innerRightX - inventoryItemStubLeftX;
    double leftBlockBottom = relicsTop + relicOutlineSize.outerH;
    double sharedMiddleH = leftBlockBottom - middleSectionTop;
    double shellH = 2 * PAUSE_ROUND_BOX_PAD + PAUSE_STATS_OUTLINE_VERTICAL_EXTRA;
    double rowContentH = Math.max(80, sharedMiddleH - shellH);
    double shellW = 2 * PAUSE_ROUND_BOX_PAD + PAUSE_STATS_OUTLINE_LOOSE;
    double innerRowW = rightColOuterW - PAUSE_INVENTORY_LIST_TO_DESC_GAP - 2 * shellW;
    if (innerRowW < 80)
    {
      innerRowW = 80;
    }
    double listContentW = Math.max(36, innerRowW * PAUSE_INVENTORY_LIST_WIDTH_SHARE);
    double descContentW = Math.max(36, innerRowW - listContentW);

    TightOutlineDimensions inventoryListOutlineSize =
        measureTightInventoryOutline(listContentW, rowContentH);
    RoundedDecoration inventoryListDecoration =
        addTightInventoryStatOutline(
            inventoryItemStubLeftX, middleSectionTop, inventoryListOutlineSize);
    pauseInventoryListOutline = inventoryListDecoration.outline;

    double descriptionLeftX =
        inventoryItemStubLeftX
            + inventoryListOutlineSize.outerW
            + PAUSE_INVENTORY_LIST_TO_DESC_GAP;
    TightOutlineDimensions inventoryDescOutlineSize =
        measureTightInventoryOutline(descContentW, rowContentH);
    RoundedDecoration inventoryDescDecoration =
        addTightInventoryStatOutline(
            descriptionLeftX, middleSectionTop, inventoryDescOutlineSize);
    pauseInventoryDescriptionOutline = inventoryDescDecoration.outline;
    inventoryDescInnerX = inventoryDescDecoration.innerX;
    inventoryDescInnerY = inventoryDescDecoration.innerY;
    inventoryDescInnerW = inventoryDescDecoration.innerW;
    inventoryDescInnerH = inventoryDescDecoration.innerH;

    List<Item> inventoryItems = getPauseInventoryItems();
    String[] stubItemLines = buildInventoryListLines(inventoryItems);
    if (pauseInventoryFocusIndex >= stubItemLines.length) {
      pauseInventoryFocusIndex = Math.max(0, stubItemLines.length - 1);
    }
    pauseInventoryItemStubLabels = new GLabel[stubItemLines.length];
    double itemLineBaseline = inventoryListDecoration.innerY + 14;
    for (int i = 0; i < stubItemLines.length; i++)
    {
      GLabel row = new GLabel(stubItemLines[i], 0, 0);
      row.setFont("SansSerif-PLAIN-12");
      row.setColor(inventoryItems.isEmpty() ? Color.DARK_GRAY : Color.BLACK);
      row.setLocation(inventoryListDecoration.innerX + 8, itemLineBaseline);
      pauseInventoryItemStubLabels[i] = row;
      addBoth(row);
      itemLineBaseline += PAUSE_INVENTORY_STUB_LINE_STEP;
    }

    // Description lines come from refreshInventoryDescriptionFromFocus() via refreshPauseFrameDecoration.

    double panelInnerBottomY = py + panelH - TAB_PAD_X_INDENT;
    buildSettingsTabContent(inventoryContentLeftX, innerW, statsRow1Top, panelInnerBottomY);

    refreshPauseFrameDecoration();
    refreshPauseMenuFocusVisuals();
    restackOnTop();
  }

  /**
   * Paints tab headers + bottom accent for {@link #settingsTabActive}; updates stub body copy per tab.
   */
  // Tab colors, bottom accent, visibility of inventory vs settings widgets; 
  // also refreshes description text on inventory.
  private void refreshPauseFrameDecoration()
  {
    if (inventoryTabBg == null
        || settingsTabBg == null
        || inventoryTabBottomAccent == null
        || settingsTabBottomAccent == null)
    {
      return;
    }
    if (!settingsTabActive)
    {
      inventoryTabBg.setFillColor(Color.LIGHT_GRAY);
      settingsTabBg.setFillColor(Color.GRAY);
      inventoryTabBottomAccent.setVisible(true);
      settingsTabBottomAccent.setVisible(false);
    }
    else
    {
      inventoryTabBg.setFillColor(Color.GRAY);
      settingsTabBg.setFillColor(Color.LIGHT_GRAY);
      inventoryTabBottomAccent.setVisible(false);
      settingsTabBottomAccent.setVisible(true);
    }
    boolean inventoryTab = !settingsTabActive;
    // Rebuild description text when the inventory tab is showing (focus row or first paint).
    if (inventoryTab)
    {
      refreshInventoryDescriptionFromFocus();
    }
    if (pauseHeartSegments != null)
    {
      for (GRect h : pauseHeartSegments)
      {
        if (h != null)
        {
          h.setVisible(inventoryTab);
        }
      }
    }
    if (pauseCoinsIcon != null)
    {
      pauseCoinsIcon.setVisible(inventoryTab);
    }
    if (pauseCoinsLabel != null)
    {
      pauseCoinsLabel.setVisible(inventoryTab);
    }
    if (inventoryLastSavedLabel != null)
    {
      inventoryLastSavedLabel.setVisible(inventoryTab);
    }
    if (tabKeysHintLabel != null)
    {
      tabKeysHintLabel.setVisible(inventoryTab);
    }
    if (pauseStatsHintBox != null)
    {
      pauseStatsHintBox.setVisible(inventoryTab);
    }
    if (pauseStatsHeartsBox != null)
    {
      pauseStatsHeartsBox.setVisible(inventoryTab);
    }
    if (pauseStatsCoinsSaveBox != null)
    {
      pauseStatsCoinsSaveBox.setVisible(inventoryTab);
    }
    if (pausePortraitOutline != null)
    {
      pausePortraitOutline.setVisible(inventoryTab);
    }
    if (pausePlayerPlaceholder != null)
    {
      pausePlayerPlaceholder.setVisible(inventoryTab);
    }
    if (pauseRelicsOutline != null)
    {
      pauseRelicsOutline.setVisible(inventoryTab);
    }
    if (pauseRelicPlaceholders != null)
    {
      for (GRect r : pauseRelicPlaceholders)
      {
        if (r != null)
        {
          r.setVisible(inventoryTab);
        }
      }
    }
    if (pauseInventoryListOutline != null)
    {
      pauseInventoryListOutline.setVisible(inventoryTab);
    }
    if (pauseInventoryDescriptionOutline != null)
    {
      pauseInventoryDescriptionOutline.setVisible(inventoryTab);
    }
    if (pauseInventoryItemStubLabels != null)
    {
      for (GLabel row : pauseInventoryItemStubLabels)
      {
        if (row != null)
        {
          row.setVisible(inventoryTab);
        }
      }
    }
    if (pauseInventoryDescriptionLines != null)
    {
      for (GLabel ln : pauseInventoryDescriptionLines)
      {
        if (ln != null)
        {
          ln.setVisible(inventoryTab);
        }
      }
    }
    if (settingsTabWidgets != null)
    {
      for (GObject g : settingsTabWidgets)
      {
        if (g != null)
        {
          g.setVisible(!inventoryTab);
        }
      }
    }
    refreshPauseMenuFocusVisuals();
  }

  // White settings body + sliders + resolution stubs + fullscreen stub + black action bar 
  // (keyboard-driven).
  // Placeholders until shared window/audio wiring matches SettingsPane.
  private void buildSettingsTabContent(
      double innerLeftX, double innerW, double contentTop, double panelInnerBottomY)
  {
    ArrayList<GObject> group = new ArrayList<>();
    double pad = 12;
    double actionBarTop = panelInnerBottomY - PAUSE_SETTINGS_ACTION_BAR_H;
    double blockBottom = actionBarTop - 10;
    double blockH = Math.max(120, blockBottom - contentTop);
    double corner = Math.min(12, blockH * 0.08);

    settingsContentBg = new GRoundRect(innerLeftX, contentTop, innerW, blockH, corner, corner);
    settingsContentBg.setFilled(true);
    settingsContentBg.setFillColor(Color.WHITE);
    settingsContentBg.setColor(Color.BLACK);
    addBoth(settingsContentBg);
    group.add(settingsContentBg);

    double row1Baseline = contentTop + pad + 14;
    musicVolumeTitleLabel = new GLabel("Music Volume:", innerLeftX + pad, row1Baseline);
    musicVolumeTitleLabel.setFont("SansSerif-PLAIN-14");
    musicVolumeTitleLabel.setColor(Color.BLACK);
    addBoth(musicVolumeTitleLabel);
    group.add(musicVolumeTitleLabel);

    musicTrackLeft = innerLeftX + innerW * 0.34;
    musicTrackRight = innerLeftX + innerW - pad - 44;
    musicTrackYCenter = row1Baseline - musicVolumeTitleLabel.getAscent() / 2 + 2;
    musicSliderTrack =
        new GLine(musicTrackLeft, musicTrackYCenter, musicTrackRight, musicTrackYCenter);
    musicSliderTrack.setColor(Color.BLACK);
    addBoth(musicSliderTrack);
    group.add(musicSliderTrack);

    musicSliderThumb =
        new GRect(
            musicTrackLeft,
            musicTrackYCenter - PAUSE_SETTINGS_SLIDER_THUMB / 2,
            PAUSE_SETTINGS_SLIDER_THUMB,
            PAUSE_SETTINGS_SLIDER_THUMB);
    musicSliderThumb.setFilled(true);
    musicSliderThumb.setFillColor(Color.DARK_GRAY);
    musicSliderThumb.setColor(Color.BLACK);
    addBoth(musicSliderThumb);
    group.add(musicSliderThumb);

    musicVolumePercentLabel = new GLabel(pauseMusicVolumePercent + "%", 0, 0);
    musicVolumePercentLabel.setFont("SansSerif-PLAIN-12");
    musicVolumePercentLabel.setColor(Color.BLACK);
    addBoth(musicVolumePercentLabel);
    group.add(musicVolumePercentLabel);

    double row2Baseline = row1Baseline + PAUSE_SETTINGS_AUDIO_ROW_GAP;
    sfxVolumeTitleLabel = new GLabel("SFX Volume:", innerLeftX + pad, row2Baseline);
    sfxVolumeTitleLabel.setFont("SansSerif-PLAIN-14");
    sfxVolumeTitleLabel.setColor(Color.BLACK);
    addBoth(sfxVolumeTitleLabel);
    group.add(sfxVolumeTitleLabel);

    sfxTrackLeft = musicTrackLeft;
    sfxTrackRight = musicTrackRight;
    sfxTrackYCenter = row2Baseline - sfxVolumeTitleLabel.getAscent() / 2 + 2;
    sfxSliderTrack = new GLine(sfxTrackLeft, sfxTrackYCenter, sfxTrackRight, sfxTrackYCenter);
    sfxSliderTrack.setColor(Color.BLACK);
    addBoth(sfxSliderTrack);
    group.add(sfxSliderTrack);

    sfxSliderThumb =
        new GRect(
            sfxTrackLeft,
            sfxTrackYCenter - PAUSE_SETTINGS_SLIDER_THUMB / 2,
            PAUSE_SETTINGS_SLIDER_THUMB,
            PAUSE_SETTINGS_SLIDER_THUMB);
    sfxSliderThumb.setFilled(true);
    sfxSliderThumb.setFillColor(Color.DARK_GRAY);
    sfxSliderThumb.setColor(Color.BLACK);
    addBoth(sfxSliderThumb);
    group.add(sfxSliderThumb);

    sfxVolumePercentLabel = new GLabel(pauseSfxVolumePercent + "%", 0, 0);
    sfxVolumePercentLabel.setFont("SansSerif-PLAIN-12");
    sfxVolumePercentLabel.setColor(Color.BLACK);
    addBoth(sfxVolumePercentLabel);
    group.add(sfxVolumePercentLabel);

    updateMusicSliderThumbLayout();
    updateSfxSliderThumbLayout();

    double divY = row2Baseline + 22;
    settingsDividerAudioDisplay =
        new GLine(innerLeftX + pad, divY, innerLeftX + innerW - pad, divY);
    settingsDividerAudioDisplay.setColor(Color.GRAY);
    addBoth(settingsDividerAudioDisplay);
    group.add(settingsDividerAudioDisplay);

    int nRes = PAUSE_SETTINGS_RESOLUTION_STUBS.length + 1;
    double btnGap = 6;
    double resTop = divY + 14;
    double resBtnH = 28;
    double resBtnW = (innerW - 2 * pad - (nRes - 1) * btnGap) / nRes;
    pauseResolutionButtons = new GRoundRect[nRes];
    pauseResolutionLabels = new GLabel[nRes];
    for (int i = 0; i < nRes; i++)
    {
      double bx = innerLeftX + pad + i * (resBtnW + btnGap);
      pauseResolutionButtons[i] =
          new GRoundRect(bx, resTop, resBtnW, resBtnH, 4, 4);
      pauseResolutionButtons[i].setFilled(true);
      pauseResolutionButtons[i].setColor(Color.BLACK);
      addBoth(pauseResolutionButtons[i]);
      group.add(pauseResolutionButtons[i]);
      String lab = i < PAUSE_SETTINGS_RESOLUTION_STUBS.length ? PAUSE_SETTINGS_RESOLUTION_STUBS[i] : "";
      pauseResolutionLabels[i] = new GLabel(lab, 0, 0);
      pauseResolutionLabels[i].setFont("SansSerif-PLAIN-11");
      pauseResolutionLabels[i].setColor(Color.BLACK);
      centerLabelInRect(pauseResolutionLabels[i], pauseResolutionButtons[i]);
      addBoth(pauseResolutionLabels[i]);
      group.add(pauseResolutionLabels[i]);
    }
    selectedResolutionIndex = 0;
    refreshResolutionButtonHighlights();

    double fsTop = resTop + resBtnH + 12;
    pauseFullscreenLabel = new GLabel("Fullscreen:", innerLeftX + pad, fsTop + 14);
    pauseFullscreenLabel.setFont("SansSerif-PLAIN-14");
    pauseFullscreenLabel.setColor(Color.BLACK);
    addBoth(pauseFullscreenLabel);
    group.add(pauseFullscreenLabel);

    double cbSize = 18;
    double cbX = innerLeftX + innerW / 2 - 40;
    double cbY = fsTop;
    pauseFullscreenCheckboxOuter = new GRect(cbX, cbY, cbSize, cbSize);
    pauseFullscreenCheckboxOuter.setFilled(true);
    pauseFullscreenCheckboxOuter.setFillColor(Color.WHITE);
    pauseFullscreenCheckboxOuter.setColor(Color.BLACK);
    addBoth(pauseFullscreenCheckboxOuter);
    group.add(pauseFullscreenCheckboxOuter);

    pauseFullscreenCheckGlyph = new GLabel(pauseFullscreenStub ? "\u2713" : "", 0, 0);
    pauseFullscreenCheckGlyph.setFont("SansSerif-BOLD-14");
    pauseFullscreenCheckGlyph.setColor(Color.BLACK);
    centerLabelInRect(pauseFullscreenCheckGlyph, pauseFullscreenCheckboxOuter);
    addBoth(pauseFullscreenCheckGlyph);
    group.add(pauseFullscreenCheckGlyph);

    settingsActionBarBg = new GRect(innerLeftX, actionBarTop, innerW, PAUSE_SETTINGS_ACTION_BAR_H);
    settingsActionBarBg.setFilled(true);
    settingsActionBarBg.setFillColor(Color.BLACK);
    settingsActionBarBg.setColor(Color.BLACK);
    addBoth(settingsActionBarBg);
    group.add(settingsActionBarBg);

    double segW = innerW / 3.0;
    settingsReturnHit = new GRect(innerLeftX, actionBarTop, segW, PAUSE_SETTINGS_ACTION_BAR_H);
    settingsReturnHit.setFilled(true);
    settingsReturnHit.setFillColor(Color.BLACK);
    settingsReturnHit.setColor(Color.BLACK);
    addBoth(settingsReturnHit);
    group.add(settingsReturnHit);

    settingsMainMenuHit =
        new GRect(innerLeftX + segW, actionBarTop, segW, PAUSE_SETTINGS_ACTION_BAR_H);
    settingsMainMenuHit.setFilled(true);
    settingsMainMenuHit.setFillColor(Color.BLACK);
    settingsMainMenuHit.setColor(Color.BLACK);
    addBoth(settingsMainMenuHit);
    group.add(settingsMainMenuHit);

    double thirdW = innerW - 2 * segW;
    settingsQuitHit =
        new GRect(innerLeftX + 2 * segW, actionBarTop, thirdW, PAUSE_SETTINGS_ACTION_BAR_H);
    settingsQuitHit.setFilled(true);
    settingsQuitHit.setFillColor(Color.BLACK);
    settingsQuitHit.setColor(Color.BLACK);
    addBoth(settingsQuitHit);
    group.add(settingsQuitHit);

    settingsReturnLabel = new GLabel("Return to Game", 0, 0);
    settingsReturnLabel.setFont("SansSerif-BOLD-12");
    settingsReturnLabel.setColor(Color.WHITE);
    centerLabelInRect(settingsReturnLabel, settingsReturnHit);
    addBoth(settingsReturnLabel);
    group.add(settingsReturnLabel);

    settingsMainMenuLabel = new GLabel("Main Menu", 0, 0);
    settingsMainMenuLabel.setFont("SansSerif-BOLD-12");
    settingsMainMenuLabel.setColor(Color.WHITE);
    centerLabelInRect(settingsMainMenuLabel, settingsMainMenuHit);
    addBoth(settingsMainMenuLabel);
    group.add(settingsMainMenuLabel);

    settingsQuitLabel = new GLabel("Quit Game", 0, 0);
    settingsQuitLabel.setFont("SansSerif-BOLD-12");
    settingsQuitLabel.setColor(Color.WHITE);
    centerLabelInRect(settingsQuitLabel, settingsQuitHit);
    addBoth(settingsQuitLabel);
    group.add(settingsQuitLabel);

    settingsTabWidgets = group.toArray(new GObject[0]);
    for (GObject g : settingsTabWidgets)
    {
      g.setVisible(false);
    }
  }

  // Fills which resolution stub looks “selected” (fake list until real presets exist).
  private void refreshResolutionButtonHighlights()
  {
    if (pauseResolutionButtons == null)
    {
      return;
    }
    for (int i = 0; i < pauseResolutionButtons.length; i++)
    {
      boolean sel = i == selectedResolutionIndex && i < PAUSE_SETTINGS_RESOLUTION_STUBS.length;
      pauseResolutionButtons[i].setFillColor(sel ? new Color(200, 210, 255) : Color.WHITE);
    }
  }

  // Drops old wrapped lines so we can swap text when the highlighted stub row changes.
  private void removePauseInventoryDescriptionLines()
  {
    if (pauseInventoryDescriptionLines == null)
    {
      return;
    }
    for (GLabel ln : pauseInventoryDescriptionLines)
    {
      if (ln != null)
      {
        mainScreen.remove(ln);
        contents.remove(ln);
      }
    }
    pauseInventoryDescriptionLines = null;
  }

  // Picks dummy text for the current inventory row; real game will ask the item object instead.
  private void refreshInventoryDescriptionFromFocus()
  {
    if (inventoryDescInnerW <= 1)
    {
      return;
    }
    removePauseInventoryDescriptionLines();
    List<Item> inventoryItems = getPauseInventoryItems();
    String description;
    if (inventoryItems.isEmpty())
    {
      description = "No items in the inventory yet.";
    }
    else
    {
      int idx = pauseInventoryFocusIndex;
      if (idx < 0)
      {
        idx = 0;
      }
      if (idx >= inventoryItems.size())
      {
        idx = inventoryItems.size() - 1;
      }
      pauseInventoryFocusIndex = idx;
      Item item = inventoryItems.get(idx);
      description = item.getDescription();
    }
    pauseInventoryDescriptionLines =
        addWrappedDescriptionLines(
            description,
            "SansSerif-PLAIN-12",
            Color.BLACK,
            inventoryDescInnerX,
            inventoryDescInnerY,
            inventoryDescInnerW,
            inventoryDescInnerH);
    if (pauseInventoryDescriptionLines != null)
    {
      for (GLabel ln : pauseInventoryDescriptionLines)
      {
        if (ln != null)
        {
          ln.setVisible(!settingsTabActive);
        }
      }
    }
  }

  /** Keyboard focus highlights (Inventory + Settings); no mouse hit-testing. */
  private void refreshPauseMenuFocusVisuals()
  {
    if (pauseInventoryItemStubLabels != null)
    {
      for (int i = 0; i < pauseInventoryItemStubLabels.length; i++)
      {
        pauseInventoryItemStubLabels[i].setFont(
            i == pauseInventoryFocusIndex ? "SansSerif-BOLD-12" : "SansSerif-PLAIN-12");
      }
    }
    boolean settingsOn = settingsTabActive;
    if (musicVolumeTitleLabel != null)
    {
      musicVolumeTitleLabel.setFont(
          settingsOn && pauseSettingsFocusIndex == 0
              ? "SansSerif-BOLD-14"
              : "SansSerif-PLAIN-14");
    }
    if (sfxVolumeTitleLabel != null)
    {
      sfxVolumeTitleLabel.setFont(
          settingsOn && pauseSettingsFocusIndex == 1
              ? "SansSerif-BOLD-14"
              : "SansSerif-PLAIN-14");
    }
    if (pauseResolutionLabels != null)
    {
      for (int i = 0; i < PAUSE_SETTINGS_RESOLUTION_STUBS.length; i++)
      {
        boolean rowFocused = settingsOn && pauseSettingsFocusIndex == 2;
        boolean itemSelected = i == selectedResolutionIndex;
        pauseResolutionLabels[i].setFont(
            rowFocused && itemSelected ? "SansSerif-BOLD-11" : "SansSerif-PLAIN-11");
      }
    }
    if (pauseFullscreenLabel != null)
    {
      pauseFullscreenLabel.setFont(
          settingsOn && pauseSettingsFocusIndex == 3
              ? "SansSerif-BOLD-14"
              : "SansSerif-PLAIN-14");
    }
    Color hi = new Color(255, 220, 100);
    if (settingsReturnLabel != null)
    {
      settingsReturnLabel.setColor(
          settingsOn && pauseSettingsFocusIndex == 4 ? hi : Color.WHITE);
    }
    if (settingsMainMenuLabel != null)
    {
      settingsMainMenuLabel.setColor(
          settingsOn && pauseSettingsFocusIndex == 5 ? hi : Color.WHITE);
    }
    if (settingsQuitLabel != null)
    {
      settingsQuitLabel.setColor(
          settingsOn && pauseSettingsFocusIndex == 6 ? hi : Color.WHITE);
    }
  }

  // A/D moves between Cancel and Yes before Enter/Space; this tints the active side.
  private void refreshConfirmFocusVisuals()
  {
    if (confirmCancelHit == null || confirmYesHit == null)
    {
      return;
    }
    confirmCancelHit.setFillColor(
        pauseConfirmFocus == 0 ? new Color(220, 220, 255) : Color.LIGHT_GRAY);
    confirmYesHit.setFillColor(
        pauseConfirmFocus == 1 ? new Color(255, 200, 200) : new Color(220, 200, 200));
  }

  private static int clampVolume(int v)
  {
    if (v < 0)
    {
      return 0;
    }
    if (v > 100)
    {
      return 100;
    }
    return v;
  }

  private void updateMusicSliderThumbLayout()
  {
    if (musicSliderThumb == null || musicVolumePercentLabel == null)
    {
      return;
    }
    double span = musicTrackRight - musicTrackLeft;
    double t = pauseMusicVolumePercent / 100.0;
    double cx = musicTrackLeft + t * span;
    double thumbX = cx - PAUSE_SETTINGS_SLIDER_THUMB / 2;
    double thumbY = musicTrackYCenter - PAUSE_SETTINGS_SLIDER_THUMB / 2;
    musicSliderThumb.setLocation(thumbX, thumbY);
    musicVolumePercentLabel.setLabel(pauseMusicVolumePercent + "%");
    musicVolumePercentLabel.setLocation(
        musicTrackRight + 6, musicTrackYCenter + musicVolumePercentLabel.getAscent() / 2);
  }

  private void updateSfxSliderThumbLayout()
  {
    if (sfxSliderThumb == null || sfxVolumePercentLabel == null)
    {
      return;
    }
    double span = sfxTrackRight - sfxTrackLeft;
    double t = pauseSfxVolumePercent / 100.0;
    double cx = sfxTrackLeft + t * span;
    double thumbX = cx - PAUSE_SETTINGS_SLIDER_THUMB / 2;
    double thumbY = sfxTrackYCenter - PAUSE_SETTINGS_SLIDER_THUMB / 2;
    sfxSliderThumb.setLocation(thumbX, thumbY);
    sfxVolumePercentLabel.setLabel(pauseSfxVolumePercent + "%");
    sfxVolumePercentLabel.setLocation(
        sfxTrackRight + 6, sfxTrackYCenter + sfxVolumePercentLabel.getAscent() / 2);
  }

  // Main menu / quit: second-step overlay; keys handled in keyPressed before normal pause keys.
  private void showConfirmDialog(ConfirmKind kind)
  {
    if (confirmPending != ConfirmKind.NONE || kind == ConfirmKind.NONE)
    {
      return;
    }
    confirmPending = kind;
    confirmDialogObjects = new ArrayList<>();
    double fw = mainScreen.getWidth();
    double fh = mainScreen.getHeight();

    GRect dim = new GRect(0, 0, fw, fh);
    dim.setFilled(true);
    dim.setFillColor(new Color(0, 0, 0, 150));
    dim.setColor(new Color(0, 0, 0, 0));
    addConfirmObject(dim);

    double boxW = Math.min(440, fw * 0.85);
    double boxH = 140;
    double bx = (fw - boxW) / 2;
    double by = (fh - boxH) / 2;
    GRoundRect box = new GRoundRect(bx, by, boxW, boxH, 12, 12);
    box.setFilled(true);
    box.setFillColor(Color.WHITE);
    box.setColor(Color.BLACK);
    addConfirmObject(box);

    String msg =
        kind == ConfirmKind.MAIN_MENU
            ? "Are you sure you want to return to the main menu?"
            : "Are you sure you want to quit the game?";
    GLabel msgLab = new GLabel(msg, 0, 0);
    msgLab.setFont("SansSerif-PLAIN-14");
    msgLab.setColor(Color.BLACK);
    double msgW = msgLab.getWidth();
    msgLab.setLocation(bx + (boxW - msgW) / 2, by + 36);
    addConfirmObject(msgLab);

    double btnW = 100;
    double btnH = 32;
    double btnY = by + boxH - btnH - 20;
    double gap = 16;
    double pairW = 2 * btnW + gap;
    double btnLeft0 = bx + (boxW - pairW) / 2;

    confirmCancelHit = new GRect(btnLeft0, btnY, btnW, btnH);
    confirmCancelHit.setFilled(true);
    confirmCancelHit.setFillColor(Color.LIGHT_GRAY);
    confirmCancelHit.setColor(Color.BLACK);
    addConfirmObject(confirmCancelHit);

    GLabel cancelLab = new GLabel("Cancel", 0, 0);
    cancelLab.setFont("SansSerif-BOLD-13");
    cancelLab.setColor(Color.BLACK);
    centerLabelInRect(cancelLab, confirmCancelHit);
    addConfirmObject(cancelLab);

    confirmYesHit = new GRect(btnLeft0 + btnW + gap, btnY, btnW, btnH);
    confirmYesHit.setFilled(true);
    confirmYesHit.setFillColor(new Color(220, 200, 200));
    confirmYesHit.setColor(Color.BLACK);
    addConfirmObject(confirmYesHit);

    GLabel yesLab = new GLabel("Yes", 0, 0);
    yesLab.setFont("SansSerif-BOLD-13");
    yesLab.setColor(Color.BLACK);
    centerLabelInRect(yesLab, confirmYesHit);
    addConfirmObject(yesLab);

    // TECH DEMO: default to "Yes" so pressing Enter/Space to open the dialog and then
    // Enter/Space again immediately confirms. A/D still let the player switch focus.
    // RIG POINT: consider defaulting to 0 (Cancel) in the shipped game for safety,
    //            so an accidental Enter can't trigger a destructive action.
    pauseConfirmFocus = 1;
    refreshConfirmFocusVisuals();
    restackOnTop();
  }

  // Tracks confirm-layer objects so dismissConfirmDialog 
  // can remove them without hunting the canvas.
  private void addConfirmObject(GObject g)
  {
    contents.add(g);
    mainScreen.add(g);
    confirmDialogObjects.add(g);
  }

  // Strips the dim + box + buttons; leaves the main pause UI underneath.
  private void dismissConfirmDialog()
  {
    if (confirmDialogObjects == null)
    {
      confirmPending = ConfirmKind.NONE;
      return;
    }
    for (GObject g : confirmDialogObjects)
    {
      mainScreen.remove(g);
      contents.remove(g);
    }
    confirmDialogObjects = null;
    confirmPending = ConfirmKind.NONE;
    confirmCancelHit = null;
    confirmYesHit = null;
  }

  // Yes on confirm: go to main menu or exit process; 
  // Cancel never calls this.
  private void runConfirmedAction()
  {
    ConfirmKind k = confirmPending;
    dismissConfirmDialog();
    if (k == ConfirmKind.MAIN_MENU)
    {
      hideContent();
      mainScreen.switchToStartMenuScreen();
    }
    else if (k == ConfirmKind.QUIT)
    {
      System.exit(0);
    }
  }

  // Same heart “pill” layout as HUDoverlay#showHearts; counts still stubbed until Player feeds HP.
  private void addPauseHeartsLikeHud(double originX, double originY, int filledSegments)
  {
    int filled = Math.max(0, Math.min(PAUSE_HUD_HEART_SEGMENT_COUNT, filledSegments));
    pauseHeartSegments = new GRect[PAUSE_HUD_HEART_SEGMENT_COUNT];
    double gapBetweenHearts = PAUSE_HUD_HEART_SEG_W * 2;
    double x = originX;
    for (int i = 0; i < PAUSE_HUD_HEART_SEGMENT_COUNT; i++)
    {
      double segX = x;
      double segY = originY;
      GRect heartsegment = new GRect(segX, segY, PAUSE_HUD_HEART_SEG_W, PAUSE_HUD_HEART_SEG_H);
      heartsegment.setColor(Color.BLACK);
      heartsegment.setFilled(true);
      heartsegment.setFillColor(i < filled ? Color.RED : Color.LIGHT_GRAY);
      pauseHeartSegments[i] = heartsegment;
      addBoth(heartsegment);
      x += PAUSE_HUD_HEART_SEG_W;
      if (i % 2 == 1 && i < PAUSE_HUD_HEART_SEGMENT_COUNT - 1)
      {
        x += gapBetweenHearts;
      }
    }
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

  /** Centers a {@link GLabel} inside an inner rectangle (padded area inside a rounded outline). */
  private void centerLabelInInnerBounds(
      GLabel g, double innerX, double innerY, double innerW, double innerH)
  {
    double lx = innerX + (innerW - g.getWidth()) / 2;
    double ly = innerY + (innerH + g.getAscent()) / 2;
    g.setLocation(lx, ly);
  }

  /**
   * Word-wraps {@code text} to {@code maxWidth} using {@code fontSpec} for measurement. Overlong
   * single words are split so they can still fit (rare for item names; useful for long stubs).
   */
  private static java.util.List<String> wrapInventoryDescriptionText(
      String text, String fontSpec, double maxWidth)
  {
    java.util.ArrayList<String> linesOut = new java.util.ArrayList<>();
    if (text == null || text.trim().isEmpty())
    {
      return linesOut;
    }
    String[] words = text.trim().split("\\s+");
    StringBuilder current = new StringBuilder();
    for (String w : words)
    {
      String trial = current.length() == 0 ? w : current + " " + w;
      GLabel measure = new GLabel(trial, 0, 0);
      measure.setFont(fontSpec);
      if (measure.getWidth() <= maxWidth)
      {
        current = new StringBuilder(trial);
        continue;
      }
      if (current.length() > 0)
      {
        linesOut.add(current.toString());
        current = new StringBuilder();
      }
      GLabel oneWord = new GLabel(w, 0, 0);
      oneWord.setFont(fontSpec);
      if (oneWord.getWidth() <= maxWidth)
      {
        current.append(w);
      }
      else
      {
        int i = 0;
        while (i < w.length())
        {
          int j = i + 1;
          while (j <= w.length())
          {
            GLabel chunkLab = new GLabel(w.substring(i, j), 0, 0);
            chunkLab.setFont(fontSpec);
            if (chunkLab.getWidth() > maxWidth && j > i + 1)
            {
              j--;
              break;
            }
            j++;
          }
          if (j <= i)
          {
            j = i + 1;
          }
          linesOut.add(w.substring(i, j));
          i = j;
        }
      }
    }
    if (current.length() > 0)
    {
      linesOut.add(current.toString());
    }
    return linesOut;
  }

  /**
   * Adds wrapped {@link GLabel} lines inside the description outline; returns them for tab visibility.
   */
  private GLabel[] addWrappedDescriptionLines(
      String text,
      String fontSpec,
      Color color,
      double innerX,
      double innerY,
      double innerW,
      double innerH)
  {
    double pad = PAUSE_INVENTORY_DESC_FONT_TOP_INSET;
    double maxLineW = Math.max(8, innerW - 2 * pad);
    java.util.List<String> lines = wrapInventoryDescriptionText(text, fontSpec, maxLineW);
    GLabel probe = new GLabel("Mg", 0, 0);
    probe.setFont(fontSpec);
    double lineStep = probe.getAscent() + probe.getDescent() + PAUSE_INVENTORY_DESC_LINE_GAP;
    double bottomLimit = innerY + innerH - pad;
    java.util.ArrayList<GLabel> added = new java.util.ArrayList<>();
    double baseline = innerY + pad + probe.getAscent();
    for (String line : lines)
    {
      if (baseline > bottomLimit)
      {
        break;
      }
      GLabel gl = new GLabel(line, 0, 0);
      gl.setFont(fontSpec);
      gl.setColor(color);
      gl.setLocation(innerX + pad, baseline);
      addBoth(gl);
      added.add(gl);
      baseline += lineStep;
    }
    return added.toArray(new GLabel[0]);
  }

  /**
   * Outer width/height for one inventory-tab stats cell after {@link #measureTightInventoryOutline}.
   * (Inner placement uses {@link RoundedDecoration} from {@link #addPauseRoundedDecoration}.)
   */
  private static final class TightOutlineDimensions
  {
    final double outerW;
    final double outerH;

    private TightOutlineDimensions(double outerW, double outerH)
    {
      this.outerW = outerW;
      this.outerH = outerH;
    }
  }

  /**
   * Content size in → outline size out. Used for J/K hint, heart cluster, and coins/save stack so all
   * three cells share one sizing rule ({@link #PAUSE_ROUND_BOX_PAD}, {@link #PAUSE_STATS_OUTLINE_LOOSE},
   * {@link #PAUSE_STATS_OUTLINE_VERTICAL_EXTRA}).
   */
  private static TightOutlineDimensions measureTightInventoryOutline(double contentW, double contentH)
  {
    return new TightOutlineDimensions(
        contentW + 2 * PAUSE_ROUND_BOX_PAD + PAUSE_STATS_OUTLINE_LOOSE,
        contentH + 2 * PAUSE_ROUND_BOX_PAD + PAUSE_STATS_OUTLINE_VERTICAL_EXTRA);
  }

  /**
   * Places one tight inventory-stats rounded box. Call before adding labels/sprites that sit inside
   * the returned {@link RoundedDecoration} inner bounds.
   */
  private RoundedDecoration addTightInventoryStatOutline(
      double outerLeft, double outerTop, TightOutlineDimensions dim)
  {
    return addPauseRoundedDecoration(outerLeft, outerTop, dim.outerW, dim.outerH);
  }

  /**
   * Low-level rounded outline for the pause panel. Prefer {@link #addTightInventoryStatOutline} for
   * inventory stats rows; keep this for any future outline that does not use the tight-stat size rule.
   */
  private RoundedDecoration addPauseRoundedDecoration(double x, double y, double w, double h)
  {
    double corner = Math.min(14, Math.min(w, h) * 0.2);
    GRoundRect outline = new GRoundRect(x, y, w, h, corner, corner);
    outline.setFilled(true);
    outline.setFillColor(Color.LIGHT_GRAY);
    outline.setColor(Color.BLACK);
    addBoth(outline);
    double p = PAUSE_ROUND_BOX_PAD;
    return new RoundedDecoration(outline, x + p, y + p, w - 2 * p, h - 2 * p);
  }

  /** Rounded outline plus inner bounds after {@value #PAUSE_ROUND_BOX_PAD} inset (for centering content). */
  private static final class RoundedDecoration
  {
    private final GRoundRect outline;
    private final double innerX;
    private final double innerY;
    private final double innerW;
    private final double innerH;

    private RoundedDecoration(GRoundRect outline, double ix, double iy, double iw, double ih)
    {
      this.outline = outline;
      this.innerX = ix;
      this.innerY = iy;
      this.innerW = iw;
      this.innerH = ih;
    }
  }

  // Normal add for pause UI (not the small confirm overlay list).
  private void addBoth(GObject g)
  {
    contents.add(g);
    mainScreen.add(g);
  }

  // Re-add everything in contents so the stack order stays correct after canvas quirks / resize.
  public void restackOnTop()
  {
    java.util.ArrayList<GObject> snapshot = new java.util.ArrayList<>(contents);
    for (GObject g : snapshot)
    {
      mainScreen.remove(g);
      mainScreen.add(g);
    }
  }

  // Bottom bar: Return closes pause; 
  // Main menu / Quit open confirm first.
  private void activateSettingsBottomButton(int focusSlot)
  {
    if (focusSlot == 4)
    {
      hideContent();
    }
    else if (focusSlot == 5)
    {
      showConfirmDialog(ConfirmKind.MAIN_MENU);
    }
    else if (focusSlot == 6)
    {
      showConfirmDialog(ConfirmKind.QUIT);
    }
  }

  // Inventory tab: W/S on stub rows; 
  // Space reserved for consumable use once Player is wired.
  private void handleInventoryNavigationKey(int k)
  {
    int rows =
        pauseInventoryItemStubLabels == null ? 0 : pauseInventoryItemStubLabels.length;
    if (rows == 0)
    {
      return;
    }
    if (k == KeyEvent.VK_W)
    {
      if (pauseInventoryFocusIndex > 0)
      {
        pauseInventoryFocusIndex--;
      }
      refreshPauseMenuFocusVisuals();
      refreshInventoryDescriptionFromFocus();
      return;
    }
    if (k == KeyEvent.VK_S)
    {
      if (pauseInventoryFocusIndex < rows - 1)
      {
        pauseInventoryFocusIndex++;
      }
      refreshPauseMenuFocusVisuals();
      refreshInventoryDescriptionFromFocus();
      return;
    }
    if (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_ENTER)
    {
      useFocusedInventoryItem();
    }
  }

  private Player getPausePlayer()
  {
    return mainScreen == null ? null : mainScreen.getPlayer();
  }

  private List<Item> getPauseInventoryItems()
  {
    Player player = getPausePlayer();
    if (player == null)
    {
      return new ArrayList<Item>();
    }
    return player.getInventory();
  }

  private int getPauseCoins()
  {
    Player player = getPausePlayer();
    return player == null ? 0 : player.getCoins();
  }

  private int getPauseHeartSegmentsFilled()
  {
    Player player = getPausePlayer();
    if (player == null)
    {
      return 0;
    }
    return Math.max(0, Math.min(PAUSE_HUD_HEART_SEGMENT_COUNT, player.getHP() * 2));
  }

  private String[] buildInventoryListLines(List<Item> inventoryItems)
  {
    if (inventoryItems == null || inventoryItems.isEmpty())
    {
      return new String[] {"• (empty)"};
    }

    String[] lines = new String[inventoryItems.size()];
    for (int i = 0; i < inventoryItems.size(); i++)
    {
      Item item = inventoryItems.get(i);
      String row = "• " + item.getDisplayName();
      if (item.isStackable())
      {
        row += " x " + item.getStackCount();
      }
      lines[i] = row;
    }
    return lines;
  }

  private void useFocusedInventoryItem()
  {
    Player player = getPausePlayer();
    if (player == null)
    {
      return;
    }
    Item focused = player.getInventoryItem(pauseInventoryFocusIndex);
    if (focused == null || !focused.isUsable())
    {
      return;
    }

    player.useInventoryItem(pauseInventoryFocusIndex);
    int inventorySize = player.getInventory().size();
    if (pauseInventoryFocusIndex >= inventorySize && inventorySize > 0)
    {
      pauseInventoryFocusIndex = inventorySize - 1;
    }
    else if (inventorySize == 0)
    {
      pauseInventoryFocusIndex = 0;
    }
    showPause();
  }

  // Settings tab: see pauseSettingsFocusIndex on fields  
  // W/S, A/D on sliders + resolution + bottom row, etc.
  private void handleSettingsNavigationKey(int k)
  {
    int f = pauseSettingsFocusIndex;
    if (k == KeyEvent.VK_SPACE)
    {
      if (f == 3)
      {
        pauseFullscreenStub = !pauseFullscreenStub;
        if (pauseFullscreenCheckGlyph != null)
        {
          pauseFullscreenCheckGlyph.setLabel(pauseFullscreenStub ? "\u2713" : "");
          centerLabelInRect(pauseFullscreenCheckGlyph, pauseFullscreenCheckboxOuter);
        }
        refreshPauseMenuFocusVisuals();
        return;
      }
      if (f >= 4 && f <= 6)
      {
        activateSettingsBottomButton(f);
      }
      return;
    }
    if (k == KeyEvent.VK_ENTER)
    {
      if (f >= 4 && f <= 6)
      {
        activateSettingsBottomButton(f);
      }
      return;
    }
    if (k == KeyEvent.VK_W)
    {
      if (f == 0)
      {
        refreshPauseMenuFocusVisuals();
        return;
      }
      if (f >= 4 && f <= 6)
      {
        if (f == 4)
        {
          pauseSettingsFocusIndex = 3;
        }
        else
        {
          pauseSettingsFocusIndex--;
        }
      }
      else if (f >= 1 && f <= 3)
      {
        pauseSettingsFocusIndex--;
      }
      refreshPauseMenuFocusVisuals();
      return;
    }
    if (k == KeyEvent.VK_S)
    {
      if (f >= 4 && f <= 6)
      {
        if (f < 6)
        {
          pauseSettingsFocusIndex++;
        }
      }
      else if (f >= 0 && f <= 2)
      {
        pauseSettingsFocusIndex++;
      }
      else if (f == 3)
      {
        pauseSettingsFocusIndex = 4;
      }
      refreshPauseMenuFocusVisuals();
      return;
    }
    if (f == 0 && (k == KeyEvent.VK_A || k == KeyEvent.VK_D))
    {
      pauseMusicVolumePercent =
          clampVolume(
              pauseMusicVolumePercent
                  + (k == KeyEvent.VK_A ? -PAUSE_SLIDER_KEY_STEP : PAUSE_SLIDER_KEY_STEP));
      updateMusicSliderThumbLayout();
      refreshPauseMenuFocusVisuals();
      return;
    }
    if (f == 1 && (k == KeyEvent.VK_A || k == KeyEvent.VK_D))
    {
      pauseSfxVolumePercent =
          clampVolume(
              pauseSfxVolumePercent
                  + (k == KeyEvent.VK_A ? -PAUSE_SLIDER_KEY_STEP : PAUSE_SLIDER_KEY_STEP));
      updateSfxSliderThumbLayout();
      refreshPauseMenuFocusVisuals();
      return;
    }
    if (f == 2 && (k == KeyEvent.VK_A || k == KeyEvent.VK_D))
    {
      int n = PAUSE_SETTINGS_RESOLUTION_STUBS.length;
      if (k == KeyEvent.VK_A)
      {
        selectedResolutionIndex = (selectedResolutionIndex + n - 1) % n;
      }
      else
      {
        selectedResolutionIndex = (selectedResolutionIndex + 1) % n;
      }
      refreshResolutionButtonHighlights();
      refreshPauseMenuFocusVisuals();
      return;
    }
    if (f >= 4 && f <= 6 && (k == KeyEvent.VK_A || k == KeyEvent.VK_D))
    {
      if (k == KeyEvent.VK_A)
      {
        if (f > 4)
        {
          pauseSettingsFocusIndex = f - 1;
        }
      }
      else if (f < 6)
      {
        pauseSettingsFocusIndex = f + 1;
      }
      refreshPauseMenuFocusVisuals();
    }
  }

  // Clears every GObject we added; MainApplication also calls this when switching screens.
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
    inventoryTabBottomAccent = null;
    settingsTabBottomAccent = null;
    inventoryTabLabel = null;
    settingsTabLabel = null;
    pauseMenuInstructionsLabel = null;
    tabKeysHintLabel = null;
    pauseHeartSegments = null;
    pauseCoinsIcon = null;
    pauseCoinsLabel = null;
    inventoryLastSavedLabel = null;
    pauseStatsHintBox = null;
    pauseStatsHeartsBox = null;
    pauseStatsCoinsSaveBox = null;
    pausePortraitOutline = null;
    pausePlayerPlaceholder = null;
    pauseRelicsOutline = null;
    pauseRelicPlaceholders = null;
    pauseInventoryListOutline = null;
    pauseInventoryDescriptionOutline = null;
    pauseInventoryItemStubLabels = null;
    pauseInventoryDescriptionLines = null;
    settingsTabWidgets = null;
    settingsContentBg = null;
    settingsDividerAudioDisplay = null;
    musicVolumeTitleLabel = null;
    sfxVolumeTitleLabel = null;
    musicSliderTrack = null;
    sfxSliderTrack = null;
    musicSliderThumb = null;
    sfxSliderThumb = null;
    musicVolumePercentLabel = null;
    sfxVolumePercentLabel = null;
    pauseResolutionButtons = null;
    pauseResolutionLabels = null;
    pauseFullscreenCheckboxOuter = null;
    pauseFullscreenCheckGlyph = null;
    pauseFullscreenLabel = null;
    settingsActionBarBg = null;
    settingsReturnHit = null;
    settingsMainMenuHit = null;
    settingsQuitHit = null;
    settingsReturnLabel = null;
    settingsMainMenuLabel = null;
    settingsQuitLabel = null;
    confirmDialogObjects = null;
    confirmPending = ConfirmKind.NONE;
    confirmCancelHit = null;
    confirmYesHit = null;
  }

  // Key order: confirm dialog first, then Esc, J/K tabs, then inventory vs settings handlers.
  @Override
  public void keyPressed(KeyEvent e)
  {
    int k = e.getKeyCode();
    if (confirmPending != ConfirmKind.NONE)
    {
      if (k == KeyEvent.VK_ESCAPE)
      {
        dismissConfirmDialog();
        return;
      }
      if (k == KeyEvent.VK_A)
      {
        pauseConfirmFocus = 0;
        refreshConfirmFocusVisuals();
        return;
      }
      if (k == KeyEvent.VK_D)
      {
        pauseConfirmFocus = 1;
        refreshConfirmFocusVisuals();
        return;
      }
      if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE)
      {
        if (pauseConfirmFocus == 0)
        {
          dismissConfirmDialog();
        }
        else
        {
          runConfirmedAction();
        }
      }
      return;
    }
    if (k == KeyEvent.VK_ESCAPE)
    {
      hideContent();
      return;
    }
    if (k == KeyEvent.VK_J)
    {
      if (!settingsTabActive)
      {
        return;
      }
      settingsTabActive = false;
      pauseInventoryFocusIndex = 0;
      refreshPauseFrameDecoration();
      return;
    }
    if (k == KeyEvent.VK_K)
    {
      if (settingsTabActive)
      {
        return;
      }
      settingsTabActive = true;
      pauseSettingsFocusIndex = 0;
      refreshPauseFrameDecoration();
      return;
    }
    if (!settingsTabActive)
    {
      handleInventoryNavigationKey(k);
      return;
    }
    handleSettingsNavigationKey(k);
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
