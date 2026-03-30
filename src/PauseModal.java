import java.awt.Color;
import java.awt.event.KeyEvent;

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
 * Legacy three-button pause UI removed. Keyboard: <b>J</b> / <b>K</b> tabs; mouse later.
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
  /** Settings tab body stub only (hidden on Inventory tab). */
  private GLabel bodyPlaceholderLabel;

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

  /** Left X of the padded column inside the main panel (used when switching tab stub position). */
  private double inventoryContentLeftX;
  /** Right column left edge (item list + description); portrait column ends before this. */
  private double inventoryItemStubLeftX;
  /** Baseline for {@link #bodyPlaceholderLabel} on Settings tab (upper area). */
  private double inventorySettingsBodyBaselineY;

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
        new GLabel("WASD = move selection  Space = use  Esc = close", 0, 0);
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
        Math.max(0, Math.min(PAUSE_HUD_COINS_DISPLAY_MAX, PAUSE_STUB_COINS));
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
    addPauseHeartsLikeHud(heartsOriginX, heartsOriginY, PAUSE_STUB_HEART_SEGMENTS_FILLED);

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

    //Stub list — wire Player later; stackables use "· Name x N" (see plan block).
    String[] stubItemLines =
        new String[] {"• Healing Bread x 3", "• Broken Lever", "• Ore"};
    pauseInventoryItemStubLabels = new GLabel[stubItemLines.length];
    double itemLineBaseline = inventoryListDecoration.innerY + 14;
    for (int i = 0; i < stubItemLines.length; i++)
    {
      GLabel row = new GLabel(stubItemLines[i], 0, 0);
      row.setFont(i == 0 ? "SansSerif-BOLD-12" : "SansSerif-PLAIN-12");
      row.setColor(Color.BLACK);
      row.setLocation(inventoryListDecoration.innerX + 8, itemLineBaseline);
      pauseInventoryItemStubLabels[i] = row;
      addBoth(row);
      itemLineBaseline += PAUSE_INVENTORY_STUB_LINE_STEP;
    }

    String descFont = "SansSerif-PLAIN-12";
    String stubDescription =
        "Healing Bread: restores hearts when used from the inventory. "
            + "This paragraph is intentionally long so you can see line wrapping inside the "
            + "description panel. When you wire selection from Player, replace this string and "
            + "call the same wrap helper. Stackable items in the list use x N.";
    pauseInventoryDescriptionLines =
        addWrappedDescriptionLines(
            stubDescription,
            descFont,
            Color.BLACK,
            inventoryDescDecoration.innerX,
            inventoryDescDecoration.innerY,
            inventoryDescDecoration.innerW,
            inventoryDescDecoration.innerH);

    bodyPlaceholderLabel = new GLabel("", 0, 0);
    bodyPlaceholderLabel.setFont("SansSerif-PLAIN-14");
    bodyPlaceholderLabel.setColor(Color.WHITE);
    inventorySettingsBodyBaselineY = statsRow1Top + bodyPlaceholderLabel.getAscent();
    addBoth(bodyPlaceholderLabel);

    refreshPauseFrameDecoration();
    restackOnTop();
  }

  /**
   * Paints tab headers + bottom accent for {@link #settingsTabActive}; updates stub body copy per tab.
   */
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
    if (bodyPlaceholderLabel != null)
    {
      if (inventoryTab)
      {
        bodyPlaceholderLabel.setVisible(false);
      }
      else
      {
        bodyPlaceholderLabel.setLocation(inventoryContentLeftX, inventorySettingsBodyBaselineY);
        bodyPlaceholderLabel.setLabel(
            "Settings tab — volume, window presets, return / main menu / quit (keyboard layout next).");
        bodyPlaceholderLabel.setVisible(true);
      }
    }
  }

  /**
   * Same segment layout/colors as {@link HUDoverlay#showHearts}; origins are pause-panel coordinates.
   */
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
    inventoryTabBottomAccent = null;
    settingsTabBottomAccent = null;
    inventoryTabLabel = null;
    settingsTabLabel = null;
    pauseMenuInstructionsLabel = null;
    tabKeysHintLabel = null;
    bodyPlaceholderLabel = null;
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
