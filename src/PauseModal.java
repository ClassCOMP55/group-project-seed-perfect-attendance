import java.awt.Color;
import java.awt.event.KeyEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import acm.graphics.*;

/*
Roberto: PauseModal — art-based pause overlay (inventory + settings tabs).
Replaces all code-drawn shapes with PNG art assets from
  assets/visuals/Pause screen stuff/
Dynamic content (hearts, grid items, coins, relics, sliders) is placed on top
in code using coordinates measured from Photoshop.

INPUT ISOLATION (do not break these guards):
  Guard 1 — GameplayPane.java checks isPauseModalOpen() before every player key.
  Guard 2 — MainApplication.keyPressed() routes exclusively to pauseModal when open.
  Guard 3 — this class: confirmPending guard is the FIRST check in keyPressed().
*/
public class PauseModal extends GraphicsPane
{
  // ================================================================
  // PANEL GEOMETRY — image is displayed at native size, no scaling
  // ================================================================
  private static final int PANEL_W = 766;
  private static final int PANEL_H = 431;
  private static final int PANEL_X = (1280 - PANEL_W) / 2;  // 257
  private static final int PANEL_Y = (int)(720 * 0.08);      // 57

  // ================================================================
  // ASSET PATHS
  // ================================================================
  private static final String ASSET_INV_BG       = "assets/visuals/Pause screen stuff/inventory_menu.png";
  private static final String ASSET_SET_BG        = "assets/visuals/Pause screen stuff/setting_menu.png";
  private static final String ASSET_CONFIRM       = "assets/visuals/Pause screen stuff/confirm menu.png";
  private static final String ASSET_GRID_HL       = "assets/visuals/Pause screen stuff/grid cell highlight.png";
  private static final String ASSET_ACTION_HL     = "assets/visuals/Pause screen stuff/action bar button highlight.png";
  private static final String ASSET_CONFIRM_HL    = "assets/visuals/Pause screen stuff/confirm highlight.png";
  private static final String ASSET_SLIDER_THUMB  = "assets/visuals/Pause screen stuff/slider thumb.png";
  private static final String ASSET_PORTRAIT      = "assets/visuals/characters/player-in-inventory-1.png";
  private static final String ASSET_COIN          = "assets/visuals/png's/coin.png";
  private static final String ASSET_RELIC_HEALTH  = "assets/visuals/png's/health_relic_icon.png";
  private static final String ASSET_RELIC_FADE    = "assets/visuals/png's/fade_relic_icon.png";
  private static final String ASSET_RELIC_REFLECT = "assets/visuals/png's/reflect_attack_relic_icon.png";
  private static final String HEART_FULL          = "assets/visuals/hearts/pixel heart full single.png";
  private static final String HEART_HALF          = "assets/visuals/hearts/pixel heart half.png";
  private static final String HEART_EMPTY         = "assets/visuals/hearts/pixel heart empty.png";

  // ================================================================
  // INVENTORY TAB — offsets within inventory_menu.png
  // Add PANEL_X / PANEL_Y to get game-window coordinates.
  // Values marked "tune" should be adjusted by eye after first run.
  // ================================================================
  private static final int INV_HEARTS_X      = 62;
  private static final int INV_HEARTS_Y      = 88;
  // Portrait sits below the hearts strip inside the left column box:
  private static final int INV_PORTRAIT_X    = 63;   // tune
  private static final int INV_PORTRAIT_Y    = 130;  // tune
  private static final int INV_PORTRAIT_W    = 155;  // tune
  private static final int INV_PORTRAIT_H    = 185;  // tune
  // Relic slots (three small squares at bottom of portrait column):
  private static final int INV_RELIC1_X      = 64;
  private static final int INV_RELIC2_X      = 108;
  private static final int INV_RELIC3_X      = 154;
  private static final int INV_RELIC_Y       = 336;
  private static final int INV_RELIC_SIZE    = 36;   // tune
  // Item grid (3 cols × 4 rows = 12 slots):
  private static final int INV_GRID_ORIGIN_X = 244;
  private static final int INV_GRID_ORIGIN_Y = 59;
  private static final int INV_GRID_CELL_W   = 55;   // matches grid cell highlight image width
  private static final int INV_GRID_CELL_H   = 57;   // matches grid cell highlight image height
  private static final int INV_GRID_GAP      = 8;    // tune
  private static final int INV_GRID_COLS     = 3;
  private static final int INV_GRID_ROWS     = 4;
  // Coins + last-saved box (top right of panel):
  private static final int INV_COINS_BOX_X   = 559;
  private static final int INV_COINS_BOX_Y   = 49;
  private static final int INV_COIN_ICON_SIZE = 16;
  // Description box (right side, below coins box):
  private static final int INV_DESC_X        = 519;
  private static final int INV_DESC_Y        = 142;
  private static final int INV_DESC_W        = 230;  // tune
  private static final int INV_DESC_H        = 270;  // tune
  private static final int INV_DESC_PAD      = 8;
  private static final double INV_DESC_LINE_GAP = 3;
  // Heart display cell size — tune to match the hearts strip height in art:
  private static final double PAUSE_HEART_CELL_SIZE = 4.0;

  // ================================================================
  // SETTINGS TAB — offsets within setting_menu.png
  // ================================================================
  private static final int SET_MUSIC_TRACK_X = 241;
  private static final int SET_MUSIC_TRACK_Y = 139;
  private static final int SET_SFX_TRACK_X   = 242;
  private static final int SET_SFX_TRACK_Y   = 235;
  private static final int SET_TRACK_RIGHT_X = 660;  // tune
  private static final int SET_RESUME_X      = 75;
  private static final int SET_RESUME_Y      = 367;
  private static final int SET_MENU_X        = 289;
  private static final int SET_MENU_Y        = 367;
  private static final int SET_QUIT_X        = 502;
  private static final int SET_QUIT_Y        = 367;
  private static final int SLIDER_STEP       = 5;

  // ================================================================
  // CONFIRM DIALOG — confirm menu.png (540×256) centered on screen
  // ================================================================
  private static final int CONFIRM_W      = 540;
  private static final int CONFIRM_H      = 256;
  private static final int CONFIRM_X      = (1280 - CONFIRM_W) / 2;  // 370
  private static final int CONFIRM_Y      = (720  - CONFIRM_H) / 2;  // 232
  private static final int CONFIRM_YES_X  = 102;
  private static final int CONFIRM_YES_Y  = 166;
  private static final int CONFIRM_NO_X   = 296;
  private static final int CONFIRM_NO_Y   = 166;

  // Actual pixel sizes of each highlight / thumb image (read from files):
  private static final int GRID_HL_W      = 55;
  private static final int GRID_HL_H      = 57;
  private static final int ACTION_HL_W    = 199;
  private static final int ACTION_HL_H    = 40;
  private static final int CONFIRM_HL_W   = 153;
  private static final int CONFIRM_HL_H   = 55;
  private static final int SLIDER_THUMB_W = 13;
  private static final int SLIDER_THUMB_H = 50;

  private static final DateTimeFormatter LAST_SAVED_FMT =
      DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.systemDefault());

  // ================================================================
  // STATE
  // ================================================================
  /** false = Inventory tab active (default on open), true = Settings tab active. */
  private boolean settingsTabActive;

  /**
   * Flat grid index 0..(COLS*ROWS-1) for the inventory grid.
   * row = index / COLS, col = index % COLS.
   */
  private int pauseInventoryFocusIndex;

  /**
   * Settings focus: 0=Music slider, 1=SFX slider,
   * 2=RESUME button, 3=MENU button, 4=QUIT button.
   */
  private int pauseSettingsFocusIndex;

  /**
   * Confirm dialog focus: 0=NO (safe default), 1=YES.
   * Default NO prevents accidental destructive action on Enter.
   */
  private int pauseConfirmFocus;

  private int pauseMusicVolumePercent;
  private int pauseSfxVolumePercent;

  private ConfirmKind confirmPending = ConfirmKind.NONE;
  private ArrayList<GObject> confirmDialogObjects;

  private enum ConfirmKind { NONE, MAIN_MENU, QUIT }

  // ================================================================
  // LIVE GRAPHICS OBJECTS
  // ================================================================
  private GRect  dimOverlay;
  private GImage panelBgImage;
  private GLabel pauseNavHintLabel;

  // Inventory tab:
  private HeartDisplay pauseHeartDisplay;
  private GImage       pausePortraitImage;
  private GImage[]     pauseRelicImages;        // [3], null slot = relic not equipped
  private GImage[]     pauseGridItemImages;     // [COLS*ROWS], null = empty slot
  private GLabel[]     pauseGridStackLabels;    // [COLS*ROWS], null = not stackable / empty
  private GImage       pauseGridHighlight;
  private GImage       pauseCoinIconImage;
  private GLabel       pauseCoinsLabel;
  private GLabel       pauseLastSavedLabel;
  private GLabel[]     pauseDescriptionLines;

  // Settings tab:
  private GImage pauseMusicThumb;
  private GImage pauseSfxThumb;
  private GLabel pauseMusicPercentLabel;
  private GLabel pauseSfxPercentLabel;
  private GImage pauseActionBarHighlight;

  // Confirm dialog (tracked separately so A/D can reposition the highlight):
  private GImage confirmHighlightImage;

  // ================================================================
  // CONSTRUCTOR
  // ================================================================
  public PauseModal(MainApplication mainScreen)
  {
    this.mainScreen = mainScreen;
  }

  // ================================================================
  // OPEN
  // ================================================================
  /**
   * Builds the full pause overlay. Safe to call while pause is already open
   * (e.g. tab switch, item use) — tab selection and focus are preserved.
   */
  public void showPause()
  {
    boolean preserveState = !contents.isEmpty();
    hideContent();
    if (!preserveState)
    {
      settingsTabActive = false;
      pauseInventoryFocusIndex = 0;
      pauseSettingsFocusIndex = 0;
      loadPauseVolumesFromSettings();
    }

    // 1. Full-window dim (same pattern as how pause dims the game world).
    double fw = mainScreen.getWidth();
    double fh = mainScreen.getHeight();
    dimOverlay = new GRect(0, 0, fw, fh);
    dimOverlay.setFilled(true);
    dimOverlay.setFillColor(new Color(0, 0, 0, 160));
    dimOverlay.setColor(new Color(0, 0, 0, 0));
    addBoth(dimOverlay);

    // 2. Panel background image (switches based on active tab).
    panelBgImage = loadImage(settingsTabActive ? ASSET_SET_BG : ASSET_INV_BG);
    if (panelBgImage != null)
    {
      panelBgImage.setSize(PANEL_W, PANEL_H);
      panelBgImage.setLocation(PANEL_X, PANEL_Y);
      addBoth(panelBgImage);
    }

    // 3. Navigation hint — right-aligned in tab strip, visible on both tabs.
    buildNavHint();

    // 4. Tab-specific content on top.
    if (!settingsTabActive)
    {
      buildInventoryContent();
    }
    else
    {
      buildSettingsContent();
    }

    restackOnTop();
  }

  private void buildNavHint()
  {
    pauseNavHintLabel = new GLabel(
        "J: Inventory  K: Settings  WASD: Navigate  Space/E: Use Item", 0, 0);
    pauseNavHintLabel.setFont("Courier New-BOLD-10");
    pauseNavHintLabel.setColor(Color.WHITE);
    double hintX = PANEL_X + PANEL_W - pauseNavHintLabel.getWidth() - 10;
    double hintY = PANEL_Y + 24;
    pauseNavHintLabel.setLocation(hintX, hintY);
    addBoth(pauseNavHintLabel);
  }

  // ================================================================
  // INVENTORY TAB
  // ================================================================
  private void buildInventoryContent()
  {
    buildPortrait();
    buildHearts();
    buildRelics();
    buildCoinsSave();
    buildItemGrid();
    buildDescription();
  }

  private void buildPortrait()
  {
    pausePortraitImage = loadImage(ASSET_PORTRAIT);
    if (pausePortraitImage == null) return;
    pausePortraitImage.setSize(INV_PORTRAIT_W, INV_PORTRAIT_H);
    pausePortraitImage.setLocation(PANEL_X + INV_PORTRAIT_X, PANEL_Y + INV_PORTRAIT_Y);
    addBoth(pausePortraitImage);
  }

  private void buildHearts()
  {
    pauseHeartDisplay = new HeartDisplay(Player.DEFAULT_HEART_COUNT, PAUSE_HEART_CELL_SIZE);
    pauseHeartDisplay.setImages(HEART_FULL, HEART_HALF, HEART_EMPTY);
    pauseHeartDisplay.show(this, PANEL_X + INV_HEARTS_X, PANEL_Y + INV_HEARTS_Y);
    pauseHeartDisplay.setFilledHalfHearts(getPauseHeartSegmentsFilled());
  }

  private void buildRelics()
  {
    Player player = getPausePlayer();
    pauseRelicImages = new GImage[3];
    // Slot order: health relic, fade/intangible relic, reflect relic.
    if (player != null && player.hasHalfDamage())
    {
      pauseRelicImages[0] = placeRelicIcon(ASSET_RELIC_HEALTH, INV_RELIC1_X);
    }
    if (player != null && player.hasIntangible())
    {
      pauseRelicImages[1] = placeRelicIcon(ASSET_RELIC_FADE, INV_RELIC2_X);
    }
    if (player != null && player.hasReflect())
    {
      pauseRelicImages[2] = placeRelicIcon(ASSET_RELIC_REFLECT, INV_RELIC3_X);
    }
  }

  private GImage placeRelicIcon(String path, int offsetX)
  {
    GImage img = loadImage(path);
    if (img == null) return null;
    img.setSize(INV_RELIC_SIZE, INV_RELIC_SIZE);
    img.setLocation(PANEL_X + offsetX, PANEL_Y + INV_RELIC_Y);
    addBoth(img);
    return img;
  }

  private void buildCoinsSave()
  {
    double iconX = PANEL_X + INV_COINS_BOX_X + 6;
    double iconY = PANEL_Y + INV_COINS_BOX_Y + 8;

    pauseCoinIconImage = loadImage(ASSET_COIN);
    if (pauseCoinIconImage != null)
    {
      pauseCoinIconImage.setSize(INV_COIN_ICON_SIZE, INV_COIN_ICON_SIZE);
      pauseCoinIconImage.setLocation(iconX, iconY);
      addBoth(pauseCoinIconImage);
    }

    int coins = Math.min(999, Math.max(0, getPauseCoins()));
    pauseCoinsLabel = new GLabel(String.valueOf(coins), 0, 0);
    pauseCoinsLabel.setFont("Courier New-BOLD-12");
    pauseCoinsLabel.setColor(Color.BLACK);
    pauseCoinsLabel.setLocation(
        iconX + INV_COIN_ICON_SIZE + 4,
        iconY + pauseCoinsLabel.getAscent());
    addBoth(pauseCoinsLabel);

    pauseLastSavedLabel = new GLabel(getInventoryLastSavedText(), 0, 0);
    pauseLastSavedLabel.setFont("Courier New-PLAIN-9");
    pauseLastSavedLabel.setColor(Color.BLACK);
    pauseLastSavedLabel.setLocation(
        iconX,
        iconY + INV_COIN_ICON_SIZE + 6 + pauseLastSavedLabel.getAscent());
    addBoth(pauseLastSavedLabel);
  }

  private void buildItemGrid()
  {
    List<Item> items = getPauseInventoryItems();
    int total = INV_GRID_COLS * INV_GRID_ROWS;
    pauseGridItemImages = new GImage[total];
    pauseGridStackLabels = new GLabel[total];

    for (int slot = 0; slot < total; slot++)
    {
      int row = slot / INV_GRID_COLS;
      int col = slot % INV_GRID_COLS;
      double sx = PANEL_X + INV_GRID_ORIGIN_X + col * (INV_GRID_CELL_W + INV_GRID_GAP);
      double sy = PANEL_Y + INV_GRID_ORIGIN_Y + row * (INV_GRID_CELL_H + INV_GRID_GAP);

      if (slot >= items.size()) continue;
      Item item = items.get(slot);

      GImage icon = item.getIcon();
      if (icon != null)
      {
        icon.setSize(INV_GRID_CELL_W - 8, INV_GRID_CELL_H - 8);
        icon.setLocation(sx + 4, sy + 4);
        addBoth(icon);
        pauseGridItemImages[slot] = icon;
      }

      if (item.isStackable() && item.getStackCount() > 1)
      {
        GLabel count = new GLabel("x" + item.getStackCount(), 0, 0);
        count.setFont("Courier New-BOLD-9");
        count.setColor(Color.WHITE);
        count.setLocation(
            sx + INV_GRID_CELL_W - count.getWidth() - 3,
            sy + INV_GRID_CELL_H - 3);
        addBoth(count);
        pauseGridStackLabels[slot] = count;
      }
    }

    placeGridHighlight();
  }

  /**
   * Removes the old grid highlight and places a new one at the currently focused slot.
   * Safe to call at any time while the inventory tab is open.
   * Slot position formula: x = ORIGIN_X + col * (CELL_W + GAP), same for y.
   */
  private void placeGridHighlight()
  {
    if (pauseGridHighlight != null)
    {
      mainScreen.remove(pauseGridHighlight);
      contents.remove(pauseGridHighlight);
      pauseGridHighlight = null;
    }
    int row = pauseInventoryFocusIndex / INV_GRID_COLS;
    int col = pauseInventoryFocusIndex % INV_GRID_COLS;
    double hx = PANEL_X + INV_GRID_ORIGIN_X + col * (INV_GRID_CELL_W + INV_GRID_GAP);
    double hy = PANEL_Y + INV_GRID_ORIGIN_Y + row * (INV_GRID_CELL_H + INV_GRID_GAP);
    pauseGridHighlight = loadImage(ASSET_GRID_HL);
    if (pauseGridHighlight == null) return;
    pauseGridHighlight.setSize(GRID_HL_W, GRID_HL_H);
    pauseGridHighlight.setLocation(hx, hy);
    addBoth(pauseGridHighlight);
  }

  private void buildDescription()
  {
    List<Item> items = getPauseInventoryItems();
    String text;
    if (items.isEmpty() || pauseInventoryFocusIndex >= items.size())
    {
      text = "No items.";
    }
    else
    {
      Item focused = items.get(pauseInventoryFocusIndex);
      String desc = focused.getDescription();
      text = focused.getDisplayName()
          + (desc != null && !desc.isEmpty() ? "\n" + desc : "");
    }

    pauseDescriptionLines = addWrappedLines(
        text,
        "Courier New-PLAIN-11",
        Color.BLACK,
        PANEL_X + INV_DESC_X + INV_DESC_PAD,
        PANEL_Y + INV_DESC_Y + INV_DESC_PAD,
        INV_DESC_W - 2 * INV_DESC_PAD,
        INV_DESC_H - 2 * INV_DESC_PAD);
  }

  /** Removes old description lines and rebuilds them for the newly focused slot. */
  private void refreshDescription()
  {
    if (pauseDescriptionLines != null)
    {
      for (GLabel ln : pauseDescriptionLines)
      {
        if (ln != null)
        {
          mainScreen.remove(ln);
          contents.remove(ln);
        }
      }
      pauseDescriptionLines = null;
    }
    buildDescription();
    restackOnTop();
  }

  // ================================================================
  // SETTINGS TAB
  // ================================================================
  private void buildSettingsContent()
  {
    buildSlider(true);
    buildSlider(false);
    placeActionBarHighlight();
  }

  private void buildSlider(boolean isMusic)
  {
    GImage thumb = loadImage(ASSET_SLIDER_THUMB);
    if (thumb == null) return;
    thumb.setSize(SLIDER_THUMB_W, SLIDER_THUMB_H);
    addBoth(thumb);

    GLabel pct = new GLabel("", 0, 0);
    pct.setFont("Courier New-BOLD-12");
    pct.setColor(Color.BLACK);
    addBoth(pct);

    if (isMusic)
    {
      pauseMusicThumb = thumb;
      pauseMusicPercentLabel = pct;
      updateMusicSliderLayout();
    }
    else
    {
      pauseSfxThumb = thumb;
      pauseSfxPercentLabel = pct;
      updateSfxSliderLayout();
    }
  }

  private void updateMusicSliderLayout()
  {
    if (pauseMusicThumb == null) return;
    double span = SET_TRACK_RIGHT_X - SET_MUSIC_TRACK_X;
    double cx   = PANEL_X + SET_MUSIC_TRACK_X + (pauseMusicVolumePercent / 100.0) * span;
    pauseMusicThumb.setLocation(cx - SLIDER_THUMB_W / 2.0,
        PANEL_Y + SET_MUSIC_TRACK_Y - SLIDER_THUMB_H / 2.0);
    if (pauseMusicPercentLabel != null)
    {
      pauseMusicPercentLabel.setLabel(pauseMusicVolumePercent + "%");
      pauseMusicPercentLabel.setLocation(
          PANEL_X + SET_TRACK_RIGHT_X + 6,
          PANEL_Y + SET_MUSIC_TRACK_Y + pauseMusicPercentLabel.getAscent() / 2.0);
    }
  }

  private void updateSfxSliderLayout()
  {
    if (pauseSfxThumb == null) return;
    double span = SET_TRACK_RIGHT_X - SET_SFX_TRACK_X;
    double cx   = PANEL_X + SET_SFX_TRACK_X + (pauseSfxVolumePercent / 100.0) * span;
    pauseSfxThumb.setLocation(cx - SLIDER_THUMB_W / 2.0,
        PANEL_Y + SET_SFX_TRACK_Y - SLIDER_THUMB_H / 2.0);
    if (pauseSfxPercentLabel != null)
    {
      pauseSfxPercentLabel.setLabel(pauseSfxVolumePercent + "%");
      pauseSfxPercentLabel.setLocation(
          PANEL_X + SET_TRACK_RIGHT_X + 6,
          PANEL_Y + SET_SFX_TRACK_Y + pauseSfxPercentLabel.getAscent() / 2.0);
    }
  }

  private void placeActionBarHighlight()
  {
    if (pauseActionBarHighlight != null)
    {
      mainScreen.remove(pauseActionBarHighlight);
      contents.remove(pauseActionBarHighlight);
      pauseActionBarHighlight = null;
    }
    // Sliders (focus 0-1) have no action-bar highlight.
    if (pauseSettingsFocusIndex < 2) return;

    int btnX, btnY;
    if      (pauseSettingsFocusIndex == 2) { btnX = SET_RESUME_X; btnY = SET_RESUME_Y; }
    else if (pauseSettingsFocusIndex == 3) { btnX = SET_MENU_X;   btnY = SET_MENU_Y;   }
    else if (pauseSettingsFocusIndex == 4) { btnX = SET_QUIT_X;   btnY = SET_QUIT_Y;   }
    else return;

    pauseActionBarHighlight = loadImage(ASSET_ACTION_HL);
    if (pauseActionBarHighlight == null) return;
    pauseActionBarHighlight.setSize(ACTION_HL_W, ACTION_HL_H);
    pauseActionBarHighlight.setLocation(PANEL_X + btnX, PANEL_Y + btnY);
    addBoth(pauseActionBarHighlight);
  }

  // ================================================================
  // CONFIRM DIALOG
  // Dims the pause menu the same way the pause menu dims the game world.
  // Draw order: full-screen dim → confirm image → confirm highlight.
  // Dismissed cleanly; leaves pause menu visible and undimmed underneath.
  // ================================================================
  private void showConfirmDialog(ConfirmKind kind)
  {
    if (confirmPending != ConfirmKind.NONE || kind == ConfirmKind.NONE) return;
    confirmPending = kind;
    confirmDialogObjects = new ArrayList<>();

    double fw = mainScreen.getWidth();
    double fh = mainScreen.getHeight();

    // Dim layer over the pause panel (preserved from original — do not remove).
    GRect dim = new GRect(0, 0, fw, fh);
    dim.setFilled(true);
    dim.setFillColor(new Color(0, 0, 0, 150));
    dim.setColor(new Color(0, 0, 0, 0));
    addConfirmObject(dim);

    // Confirm dialog background image.
    GImage confirmBg = loadImage(ASSET_CONFIRM);
    if (confirmBg != null)
    {
      confirmBg.setSize(CONFIRM_W, CONFIRM_H);
      confirmBg.setLocation(CONFIRM_X, CONFIRM_Y);
      addConfirmObject(confirmBg);
    }

    // Default to NO so an accidental Enter/Space does not destroy game state.
    pauseConfirmFocus = 0;
    placeConfirmHighlight();
    restackOnTop();
  }

  private void placeConfirmHighlight()
  {
    if (confirmHighlightImage != null)
    {
      mainScreen.remove(confirmHighlightImage);
      contents.remove(confirmHighlightImage);
      if (confirmDialogObjects != null) confirmDialogObjects.remove(confirmHighlightImage);
      confirmHighlightImage = null;
    }
    int offX = (pauseConfirmFocus == 1) ? CONFIRM_YES_X : CONFIRM_NO_X;
    int offY = (pauseConfirmFocus == 1) ? CONFIRM_YES_Y : CONFIRM_NO_Y;
    confirmHighlightImage = loadImage(ASSET_CONFIRM_HL);
    if (confirmHighlightImage == null) return;
    confirmHighlightImage.setSize(CONFIRM_HL_W, CONFIRM_HL_H);
    confirmHighlightImage.setLocation(CONFIRM_X + offX, CONFIRM_Y + offY);
    addConfirmObject(confirmHighlightImage);
    restackOnTop();
  }

  private void addConfirmObject(GObject g)
  {
    contents.add(g);
    mainScreen.add(g);
    confirmDialogObjects.add(g);
  }

  private void dismissConfirmDialog()
  {
    if (confirmDialogObjects != null)
    {
      for (GObject g : confirmDialogObjects)
      {
        mainScreen.remove(g);
        contents.remove(g);
      }
      confirmDialogObjects = null;
    }
    confirmHighlightImage = null;
    confirmPending = ConfirmKind.NONE;
  }

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

  // ================================================================
  // KEYBOARD INPUT
  // Guard 3 — confirm dialog is checked FIRST. Keep it first.
  // ================================================================
  @Override
  public void keyPressed(KeyEvent e)
  {
    int k = e.getKeyCode();

    // Guard 3: confirm dialog intercepts ALL keys while open.
    if (confirmPending != ConfirmKind.NONE)
    {
      handleConfirmKey(k);
      return;
    }

    if (k == KeyEvent.VK_ESCAPE)
    {
      hideContent();
      return;
    }

    // Tab switching — J = inventory, K = settings.
    if (k == KeyEvent.VK_J && settingsTabActive)
    {
      settingsTabActive = false;
      pauseInventoryFocusIndex = 0;
      showPause();
      return;
    }
    if (k == KeyEvent.VK_K && !settingsTabActive)
    {
      settingsTabActive = true;
      pauseSettingsFocusIndex = 0;
      showPause();
      return;
    }

    if (!settingsTabActive)
    {
      handleInventoryKey(k);
    }
    else
    {
      handleSettingsKey(k);
    }
  }

  private void handleConfirmKey(int k)
  {
    if (k == KeyEvent.VK_ESCAPE)
    {
      dismissConfirmDialog();
      return;
    }
    if (k == KeyEvent.VK_A)
    {
      pauseConfirmFocus = 0; // NO
      placeConfirmHighlight();
      return;
    }
    if (k == KeyEvent.VK_D)
    {
      pauseConfirmFocus = 1; // YES
      placeConfirmHighlight();
      return;
    }
    if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE)
    {
      if (pauseConfirmFocus == 0) dismissConfirmDialog();
      else runConfirmedAction();
    }
  }

  private void handleInventoryKey(int k)
  {
    int row = pauseInventoryFocusIndex / INV_GRID_COLS;
    int col = pauseInventoryFocusIndex % INV_GRID_COLS;
    boolean moved = false;

    if (k == KeyEvent.VK_W && row > 0)
    {
      pauseInventoryFocusIndex -= INV_GRID_COLS;
      moved = true;
    }
    else if (k == KeyEvent.VK_S && row < INV_GRID_ROWS - 1)
    {
      pauseInventoryFocusIndex += INV_GRID_COLS;
      moved = true;
    }
    else if (k == KeyEvent.VK_A && col > 0)
    {
      pauseInventoryFocusIndex--;
      moved = true;
    }
    else if (k == KeyEvent.VK_D && col < INV_GRID_COLS - 1)
    {
      pauseInventoryFocusIndex++;
      moved = true;
    }
    else if (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_E || k == KeyEvent.VK_ENTER)
    {
      useFocusedItem();
      return;
    }

    if (moved)
    {
      placeGridHighlight();
      refreshDescription();
    }
  }

  /**
   * Calls onUse() on the focused inventory slot via Player.useInventoryItem().
   * After a consumable is eaten the inventory list may shrink, so focus is
   * clamped to the new size before rebuilding the screen.
   */
  private void useFocusedItem()
  {
    Player player = getPausePlayer();
    if (player == null) return;
    List<Item> items = getPauseInventoryItems();
    if (pauseInventoryFocusIndex >= items.size()) return;
    Item focused = items.get(pauseInventoryFocusIndex);
    if (focused == null || !focused.isUsable()) return;

    player.useInventoryItem(pauseInventoryFocusIndex);

    // Clamp focus if the list shrank after consuming the item.
    int newSize = player.getInventory().size();
    if (newSize == 0)
    {
      pauseInventoryFocusIndex = 0;
    }
    else if (pauseInventoryFocusIndex >= newSize)
    {
      pauseInventoryFocusIndex = newSize - 1;
    }

    showPause(); // full rebuild: updated HP, updated inventory
  }

  private void handleSettingsKey(int k)
  {
    int f = pauseSettingsFocusIndex;

    if (k == KeyEvent.VK_W)
    {
      if      (f == 1) pauseSettingsFocusIndex = 0;
      else if (f >= 2) pauseSettingsFocusIndex = 1;
      placeActionBarHighlight();
      return;
    }
    if (k == KeyEvent.VK_S)
    {
      if      (f == 0) pauseSettingsFocusIndex = 1;
      else if (f == 1) pauseSettingsFocusIndex = 2;
      else if (f < 4)  pauseSettingsFocusIndex++;
      placeActionBarHighlight();
      return;
    }
    if (k == KeyEvent.VK_A || k == KeyEvent.VK_D)
    {
      int delta = (k == KeyEvent.VK_A) ? -SLIDER_STEP : SLIDER_STEP;
      if (f == 0)
      {
        pauseMusicVolumePercent = clampVolume(pauseMusicVolumePercent + delta);
        updateMusicSliderLayout();
        persistPauseAudioSettings();
      }
      else if (f == 1)
      {
        pauseSfxVolumePercent = clampVolume(pauseSfxVolumePercent + delta);
        updateSfxSliderLayout();
        persistPauseAudioSettings();
      }
      else if (f == 2 && k == KeyEvent.VK_D)
      {
        pauseSettingsFocusIndex = 3;
        placeActionBarHighlight();
      }
      else if (f == 3)
      {
        pauseSettingsFocusIndex = (k == KeyEvent.VK_A) ? 2 : 4;
        placeActionBarHighlight();
      }
      else if (f == 4 && k == KeyEvent.VK_A)
      {
        pauseSettingsFocusIndex = 3;
        placeActionBarHighlight();
      }
      return;
    }
    if (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_ENTER)
    {
      if      (f == 2) hideContent();
      else if (f == 3) showConfirmDialog(ConfirmKind.MAIN_MENU);
      else if (f == 4) showConfirmDialog(ConfirmKind.QUIT);
    }
  }

  // ================================================================
  // HIDE / RESIZE
  // ================================================================
  @Override
  public void hideContent()
  {
    for (GObject item : contents)
    {
      mainScreen.remove(item);
    }
    contents.clear();

    dimOverlay            = null;
    panelBgImage          = null;
    pauseNavHintLabel     = null;
    pauseHeartDisplay     = null;
    pausePortraitImage    = null;
    pauseRelicImages      = null;
    pauseGridItemImages   = null;
    pauseGridStackLabels  = null;
    pauseGridHighlight    = null;
    pauseCoinIconImage    = null;
    pauseCoinsLabel       = null;
    pauseLastSavedLabel   = null;
    pauseDescriptionLines = null;
    pauseMusicThumb       = null;
    pauseSfxThumb         = null;
    pauseMusicPercentLabel = null;
    pauseSfxPercentLabel  = null;
    pauseActionBarHighlight = null;
    confirmHighlightImage = null;
    confirmDialogObjects  = null;
    confirmPending        = ConfirmKind.NONE;
  }

  /** Rebuilds at the current window size while keeping pause open. */
  public void refreshForResize()
  {
    if (contents.isEmpty()) return;
    showPause();
  }

  // ================================================================
  // HELPERS — DATA
  // ================================================================
  private Player getPausePlayer()
  {
    return mainScreen == null ? null : mainScreen.getPlayer();
  }

  private List<Item> getPauseInventoryItems()
  {
    Player player = getPausePlayer();
    if (player == null) return new ArrayList<>();
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
    if (player == null) return 0;
    return Math.max(0, Math.min(player.getMaxHealth(), player.getHP()));
  }

  private String getInventoryLastSavedText()
  {
    long ts = mainScreen == null ? 0L : mainScreen.getLastSavedAtMillis();
    if (ts <= 0L) return "Last saved: Never";
    return "Last saved: " + LAST_SAVED_FMT.format(Instant.ofEpochMilli(ts));
  }

  private static int clampVolume(int v)
  {
    return Math.max(0, Math.min(100, v));
  }

  private void loadPauseVolumesFromSettings()
  {
    pauseMusicVolumePercent = clampVolume(GameSettings.getMusicVolumePercent());
    pauseSfxVolumePercent   = clampVolume(GameSettings.getSfxVolumePercent());
  }

  /**
   * Writes current slider values to GameSettings, flushes them to disk via
   * SettingsIO, and immediately tells the audio systems to apply the new volumes.
   * Called on every A/D key press while a slider is focused.
   */
  private void persistPauseAudioSettings()
  {
    GameSettings.setMusicVolumePercent(pauseMusicVolumePercent);
    GameSettings.setSfxVolumePercent(pauseSfxVolumePercent);
    SettingsIO.persist();
    GameMusic.refreshVolume();
    GameSFX.refreshVolume();
  }

  // ================================================================
  // HELPERS — GRAPHICS
  // ================================================================
  /**
   * Loads a GImage from a file path. Returns null (and prints a warning)
   * if the file is missing so the rest of the UI still draws.
   */
  private static GImage loadImage(String path)
  {
    try
    {
      return new GImage(path);
    }
    catch (Exception e)
    {
      System.err.println("PauseModal: could not load image: " + path);
      return null;
    }
  }

  /** Tracks a graphics object in our contents list and places it on the canvas. */
  private void addBoth(GObject g)
  {
    contents.add(g);
    mainScreen.add(g);
  }

  /**
   * Re-adds every pause object to the canvas in order so they always render
   * above the game world. ACM draws in add order, so anything added later
   * (like a new room tile) would appear on top unless we re-stack.
   * Called after every build and after placing highlights / dialog layers.
   */
  public void restackOnTop()
  {
    ArrayList<GObject> snapshot = new ArrayList<>(contents);
    for (GObject g : snapshot)
    {
      mainScreen.remove(g);
      mainScreen.add(g);
    }
  }

  /**
   * Renders wrapped GLabel lines inside a bounding box.
   * Handles explicit '\n' breaks (for item name vs description separation).
   */
  private GLabel[] addWrappedLines(
      String text, String font, Color color,
      double x, double y, double maxW, double maxH)
  {
    if (text == null || text.trim().isEmpty()) return new GLabel[0];

    List<String> allLines = new ArrayList<>();
    for (String para : text.split("\n"))
    {
      allLines.addAll(wrapParagraph(para.trim(), font, maxW));
    }

    GLabel probe = new GLabel("Mg", 0, 0);
    probe.setFont(font);
    double lineStep = probe.getAscent() + probe.getDescent() + INV_DESC_LINE_GAP;
    double bottomLimit = y + maxH - probe.getDescent();
    List<GLabel> added = new ArrayList<>();
    double baseline = y + probe.getAscent();

    for (String line : allLines)
    {
      if (baseline > bottomLimit) break;
      GLabel gl = new GLabel(line, x, baseline);
      gl.setFont(font);
      gl.setColor(color);
      addBoth(gl);
      added.add(gl);
      baseline += lineStep;
    }
    return added.toArray(new GLabel[0]);
  }

  private static List<String> wrapParagraph(String text, String font, double maxW)
  {
    List<String> out = new ArrayList<>();
    if (text.isEmpty()) { out.add(""); return out; }
    String[] words = text.split("\\s+");
    StringBuilder current = new StringBuilder();
    for (String w : words)
    {
      String trial = current.length() == 0 ? w : current + " " + w;
      GLabel m = new GLabel(trial, 0, 0);
      m.setFont(font);
      if (m.getWidth() <= maxW)
      {
        current = new StringBuilder(trial);
      }
      else
      {
        if (current.length() > 0) out.add(current.toString());
        current = new StringBuilder(w);
      }
    }
    if (current.length() > 0) out.add(current.toString());
    return out;
  }

  // ================================================================
  // SANDBOX — local test (ESC to open/close pause over a blank window)
  // ================================================================
  public static void main(String[] args)
  {
    class Sandbox extends MainApplication
    {
      private final PauseModal sandboxPause = new PauseModal(this);

      @Override
      public void run()
      {
        setSize(1280, 720);
        setupInteractions();
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

      @Override public void keyReleased(KeyEvent e) {}
      @Override public void keyTyped(KeyEvent e) {}
    }
    new Sandbox().start();
  }
}
