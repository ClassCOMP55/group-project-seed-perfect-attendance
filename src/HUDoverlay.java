/*
Roberto: health HUD class 
RIG: [whoever] calls show / update / hide / etc. 
Does not own Player or combat
*/

import acm.graphics.*;



public class HUDoverlay 
{
  // In-game HUD: hearts, coins, relic slots, sword + ability buttons. 
  // Caller passes HudSnapshot / updates;
  // this class only draws — it does not own Player or combat math.

  //constants for the HUDoverlay
  private static final int DEFAULT_HEART_SEGMENTS = 6;
  private static final int UPGRADED_HEART_SEGMENTS = 12;
  //private static final int HEARTS_ON_SCREEN = 3; wasn't used lol
        //size of one half-segment
  private static final double HEART_SEGMENT_WIDTH = 14;
  private static final double HEART_SEGMENT_HEIGHT = 28;
        //location of the start of the 3 hearts on screen
  private static final double HEART_ROW_X = 12;
  private static final double HEART_ROW_Y = 12;

  //Gap from the right edge of the window to the coin cluster 
  //(pixels; matches heart margin distance).
  private static final double COINS_MARGIN_RIGHT = 32;
  //Placeholder “coin” circle until a {@code GImage} is wired.
  private static final double COINS_ICON_SIZE = 24;
  private static final double COINS_ICON_LABEL_GAP = 4;
  //HUD shows at most this many coins (3 digits); higher values are capped for display purposes
  //need to apply the same cap to actual "wallet" wherever that will be kept
  private static final int COINS_DISPLAY_MAX = 999;

  
  //Horizontal gap between relic slots in the row below the hearts 
  //(side by side, left to right). 
  // //Each relic has a fixed slot index so spacing does not depend on which relics you own.
  private static final double RELIC_SLOT_GAP_X = 6;

  //Relic slot size is 16px; ability buttons use 3× that.
  private static final double ABILITY_BUTTON_SIZE = 42;
  private static final double ABILITY_BUTTON_GAP = 12;
  private static final double ABILITY_HUD_FROM_EDGE = 32;
  private static final double ABILITY_BUTTON_FROM_BOTTOM = 16;

  /** 
   * THIS CAN BE IT'S OWN CLASS. I LEAVE IT UP TO THE GROUP
   * Values needed to draw the full HUD in one pass. The game / {@code Player} / save builds this;
   * {@link HUDoverlay} only reads it. Rename or move to its own file later if the team prefers.
   */
  public static final class HudSnapshot
  {
    public final int currentHeartSegments;
    /** {@code true} → {@link HUDoverlay#showUpgradedHearts}; {@code false} → {@link HUDoverlay#showHearts}. */
    public final boolean useUpgradedHeartBar;
    /** Passed through to heart {@code show*} (reserved for relic-gated heart mode). */
    public final boolean relicBoolHeart;
    public final int currentCoins;
    public final boolean ownedIntangible;
    public final boolean ownedHalfDamage;
    public final boolean ownedReflect;
    public final boolean intangibleAbilityOnCooldown;

    public HudSnapshot(
        int currentHeartSegments,
        boolean useUpgradedHeartBar,
        boolean relicBoolHeart,
        int currentCoins,
        boolean ownedIntangible,
        boolean ownedHalfDamage,
        boolean ownedReflect,
        boolean intangibleAbilityOnCooldown)
    {
      this.currentHeartSegments = currentHeartSegments;
      this.useUpgradedHeartBar = useUpgradedHeartBar;
      this.relicBoolHeart = relicBoolHeart;
      this.currentCoins = currentCoins;
      this.ownedIntangible = ownedIntangible;
      this.ownedHalfDamage = ownedHalfDamage;
      this.ownedReflect = ownedReflect;
      this.intangibleAbilityOnCooldown = intangibleAbilityOnCooldown;
    }
  }

  // Heart PNG asset paths
  private static final String HEART_FULL         = "assets/visuals/hearts/pixel heart full single.png";
  private static final String HEART_HALF         = "assets/visuals/hearts/pixel heart half.png";
  private static final String HEART_QUARTER      = "assets/visuals/hearts/pixel heart quarter.png";
  private static final String HEART_LAST_QUARTER = "assets/visuals/hearts/pixel heart last quarter.png";
  private static final String HEART_EMPTY        = "assets/visuals/hearts/pixel heart empty.png";

  // Relic slot icon PNG paths
  private static final String RELIC_INTANGIBLE_ICON  = "assets/visuals/png's/fade_relic_icon.png";
  private static final String RELIC_HALF_DAMAGE_ICON = "assets/visuals/png's/health_relic_icon.png";
  private static final String RELIC_REFLECT_ICON     = "assets/visuals/png's/reflect_attack_relic_icon.png";

  // Coin icon PNG path
  private static final String COIN_ICON_PATH = "assets/visuals/png's/coin.png";

  // Ability button PNG paths
  private static final String ATTACK_BUTTON_PATH          = "assets/visuals/png's/attack_button.png";
  private static final String INTANGIBLE_BUTTON_PATH      = "assets/visuals/png's/fade_relic_button.png";
  private static final String INTANGIBLE_BUTTON_COOLDOWN_PATH = "assets/visuals/png's/fade_relic_button_cooldown.png";

  private GImage[] hearts;
  private boolean upgradeMode = false;

  private GOval relicIntangibleBackground;
  private GImage relicIntangibleIcon;

  private GOval relicHalfDamageBackground;
  private GImage relicHalfDamageIcon;

  private GOval relicReflectBackground;
  private GImage relicReflectIcon;

  // private GOval SWDbackground;      // replaced by GImage
  // private GRect tempSWDicon;        // replaced by GImage
  private GImage SWDicon;
  // private GLabel SWDbutton;         // key label is baked into attack_button.png

  // private GOval intangibleAbilityBackground;   // replaced by GImage
  // private GRect tempIntangibleAbilityIcon;     // replaced by GImage
  private GImage intangibleAbilityIcon;
  private GImage intangibleAbilityCooldownIcon;

  // private GOval coins; // replaced by GImage
  private GImage coinsicon;
  private GLabel coinslabel = new GLabel("0");





/*
-I need a way to draw the HUD
-I need a way to show the HUD
-I need a way to update the HUD. Certain elements need to be updated, but not the entire HUD.
-I need a way to hide or clear the HUD for transitions into dungeons or other areas of the game
not the overworld.
- I need a way to send the HUD to the front of the screen; keep in mind the order that 
things are sent to the front.
-I need space in the bottom right corner to show the sword icon with a button to indicate 
the attack button associated with it
-I need the a spot just beneath the hearts where the 3 relics will be shown, WHEN they are obtained,
so there is an indicator for each of them. When not obtained, they will not be shown. 
-I need the coins on the HUD, top right corner, and a way to update them. 
Numbers in, numbers/picture out.
-For the intagible relic, I need a button with design similar to the attack button, and I need
a way to "fade the color" to show it's on cooldown. 
Boolean in, image adjustments out. 
-I need a way to operate this class on it's own so that I can test the position of everything from
within this class
-we will not have a pause icon on screen at this time. pause will be triggered by pressing ESC.
*/

  /**
   * Draws the normal heart bar (6 half-segments for 3 hearts). Call from {@link #showAll} or when
   * building the HUD the first time.
   *
   * @param pane          host pane (canvas + {@code contents} list)
   * @param currentHeart  how many segments are filled (0–{@value #DEFAULT_HEART_SEGMENTS}); 
   * game passes current value, not damage dealt with a hit
   * @param relicBool     reserved for pivot “upgraded heart” / quarter-step mode when implemented
   */
  public void showHearts(GraphicsPane pane, int currentHeart, boolean relicBool)
  {
    if (hearts != null)
    {
      for (GImage h : hearts)
      {
        if (h != null)
        {
          removeFromScreen(pane, h);
        }
      }
      hearts = null;
    }

    upgradeMode = false;
    int numHearts = DEFAULT_HEART_SEGMENTS / 2;  // 3 hearts
    int filled = Math.max(0, Math.min(DEFAULT_HEART_SEGMENTS, currentHeart));
    hearts = new GImage[numHearts];

    double heartW = HEART_SEGMENT_WIDTH * 2;
    double gapBetweenHearts = HEART_SEGMENT_WIDTH * 0.4;

    for (int i = 0; i < numHearts; i++)
    {
      int halfUnits = Math.max(0, Math.min(2, filled - i * 2));
      String path = halfUnits == 2 ? HEART_FULL
                  : halfUnits == 1 ? HEART_HALF
                  :                  HEART_EMPTY;
      double x = HEART_ROW_X + i * (heartW + gapBetweenHearts);
      GImage img = new GImage(path, x, HEART_ROW_Y);
      placeOnScreen(pane, img);
      img.setSize(heartW, heartW);
      hearts[i] = img;
    }
  }


  /**
   * Draws the upgraded heart bar when the pivot uses 12 segments (quarter steps). Same “numbers in,
   * pictures out” idea as {@link #showHearts}.
   *
   * @param pane          host pane
   * @param currentHeart  filled segment count (0–{@value #UPGRADED_HEART_SEGMENTS})
   * @param relicBool     gate or extra flag when design locks how upgrade is unlocked (unused for now)
   */
  public void showUpgradedHearts(GraphicsPane pane, int currentHeart, boolean relicBool)
  {
    if (hearts != null)
    {
      for (GImage h : hearts)
      {
        if (h != null)
        {
          removeFromScreen(pane, h);
        }
      }
      hearts = null;
    }

    upgradeMode = true;
    int numHearts = UPGRADED_HEART_SEGMENTS / 4;  // 3 hearts
    int filled = Math.max(0, Math.min(UPGRADED_HEART_SEGMENTS, currentHeart));
    hearts = new GImage[numHearts];

    double heartW = HEART_SEGMENT_WIDTH * 2;
    double gapBetweenHearts = HEART_SEGMENT_WIDTH * 0.4;

    for (int i = 0; i < numHearts; i++)
    {
      int quarterUnits = Math.max(0, Math.min(4, filled - i * 4));
      String path;
      if      (quarterUnits == 4) path = HEART_FULL;
      else if (quarterUnits == 3) path = HEART_QUARTER;
      else if (quarterUnits == 2) path = HEART_HALF;
      else if (quarterUnits == 1) path = HEART_LAST_QUARTER;
      else                        path = HEART_EMPTY;
      double x = HEART_ROW_X + i * (heartW + gapBetweenHearts);
      GImage img = new GImage(path, x, HEART_ROW_Y);
      placeOnScreen(pane, img);
      img.setSize(heartW, heartW);
      hearts[i] = img;
    }
  }

  /**
   * Updates heart segment colors only (no full rebuild). Use for both heal and damage: pass the
   * <em>new</em> filled segment count. Does not own combat logic—whoever owns {@code Player} / game
   * state computes the number and calls this.
   * <p>
   * Uses {@link #hearts}{@code .length} (6 or 12) so this matches whichever bar
   * {@link #showHearts} or {@link #showUpgradedHearts} last built—no separate mode flag.
   *
   * @param pane          host pane (reserved for future use, e.g. repaint hints)
   * @param currentHeart  filled segments after the change (0–{@code hearts.length})
   */
  public void updateHearts(GraphicsPane pane, int currentHeart)
  {
    if (hearts == null)
    {
      return;
    }
    if (upgradeMode)
    {
      showUpgradedHearts(pane, currentHeart, false);
    }
    else
    {
      showHearts(pane, currentHeart, false);
    }
  }
  
  /**
   * Places the coin display (top-right area per pivot). 
   * First-time setup; pair with {@link #updateCoins}.
   *
   * @param pane         host pane
   * @param currentCoins wallet value to show when the HUD appears
   */
  public void showCoins(GraphicsPane pane, int currentCoins)
  {
    removeFromScreen(pane, coinslabel);
    removeFromScreen(pane, coinsicon);

    int displayCoins = Math.max(0, Math.min(COINS_DISPLAY_MAX, currentCoins));

    coinslabel.setLabel(String.valueOf(displayCoins));
    coinslabel.setFont("SansSerif-BOLD-14");

    double w = pane.mainScreen.getWidth();
    double labelW = coinslabel.getWidth();
    double iconLeft = w - COINS_MARGIN_RIGHT - COINS_ICON_SIZE - COINS_ICON_LABEL_GAP - labelW;
    double iconTop = HEART_ROW_Y + (HEART_SEGMENT_HEIGHT - COINS_ICON_SIZE) / 2;

    coinsicon = new GImage(COIN_ICON_PATH, iconLeft, iconTop);

    double labelX = iconLeft + COINS_ICON_SIZE + COINS_ICON_LABEL_GAP;
    double labelBaseline = HEART_ROW_Y + HEART_SEGMENT_HEIGHT - 2;
    coinslabel.setLocation(labelX, labelBaseline);

    placeOnScreen(pane, coinsicon);
    coinsicon.setSize(COINS_ICON_SIZE, COINS_ICON_SIZE);
    placeOnScreen(pane, coinslabel);
  }
  
  /**
   * Refreshes the coin label (or icon) when the count changes. Numbers in, display only out.
   *
   * @param pane         host pane
   * @param currentCoins new total coins
   */
  public void updateCoins(GraphicsPane pane, int currentCoins)
  {
    int displayCoins = Math.max(0, Math.min(COINS_DISPLAY_MAX, currentCoins));
    coinslabel.setLabel(String.valueOf(displayCoins));
  }

  /**
   * Relic row — <b>Intangible</b> (pivot). Show only when {@code ownedIntangible}; otherwise no-op.
   * Rigger: pass from save / {@code Player} when the player owns Intangible.
   *
   * @param pane              host pane
   * @param ownedIntangible   whether the player has the Intangible relic
   */
  public void showRelicIntangible(GraphicsPane pane, boolean ownedIntangible)
  {
    removeFromScreen(pane, relicIntangibleBackground);
    removeFromScreen(pane, relicIntangibleIcon);
    relicIntangibleBackground = null;
    relicIntangibleIcon = null;

    if (!ownedIntangible)
    {
      return;
    }

    double slotSize = 24;
    double rowY = HEART_ROW_Y + HEART_SEGMENT_HEIGHT + 4;
    double heartW = HEART_SEGMENT_WIDTH * 2;
    double heartGap = HEART_SEGMENT_WIDTH * 0.4;
    double heartsRowW = 3 * heartW + 2 * heartGap;
    double relicsTotalW = 3 * slotSize + 2 * RELIC_SLOT_GAP_X;
    double relicStartX = HEART_ROW_X + (heartsRowW - relicsTotalW) / 2;
    int column = 0;
    double x = relicStartX + column * (slotSize + RELIC_SLOT_GAP_X);
    double y = rowY;

    relicIntangibleIcon = new GImage(RELIC_INTANGIBLE_ICON, x, y);
    placeOnScreen(pane, relicIntangibleIcon);
    scaleToFit(relicIntangibleIcon, slotSize);
  }

  /**
   * Relic row — <b>Half-Damage</b> (pivot). Show only when {@code ownedHalfDamage}.
   * Rigger: tie to save / {@code Player} for Half-Damage.
   *
   * @param pane               host pane
   * @param ownedHalfDamage    whether the player has Half-Damage
   */
  public void showRelicHalfDamage(GraphicsPane pane, boolean ownedHalfDamage)
  {
    removeFromScreen(pane, relicHalfDamageBackground);
    removeFromScreen(pane, relicHalfDamageIcon);
    relicHalfDamageBackground = null;
    relicHalfDamageIcon = null;

    if (!ownedHalfDamage)
    {
      return;
    }

    double slotSize = 24;
    double rowY = HEART_ROW_Y + HEART_SEGMENT_HEIGHT + 4;
    double heartW = HEART_SEGMENT_WIDTH * 2;
    double heartGap = HEART_SEGMENT_WIDTH * 0.4;
    double heartsRowW = 3 * heartW + 2 * heartGap;
    double relicsTotalW = 3 * slotSize + 2 * RELIC_SLOT_GAP_X;
    double relicStartX = HEART_ROW_X + (heartsRowW - relicsTotalW) / 2;
    int column = 1;
    double x = relicStartX + column * (slotSize + RELIC_SLOT_GAP_X);
    double y = rowY;

    // background circle not needed — icon PNG has it baked in
    relicHalfDamageIcon = new GImage(RELIC_HALF_DAMAGE_ICON, x, y);
    placeOnScreen(pane, relicHalfDamageIcon);
    scaleToFit(relicHalfDamageIcon, slotSize);
  }

  /**
   * Relic row — <b>Reflect</b> (pivot). Show only when {@code ownedReflect}.
   * Rigger: tie to save / {@code Player} for Reflect.
   *
   * @param pane           host pane
   * @param ownedReflect   whether the player has Reflect
   */
  public void showRelicReflect(GraphicsPane pane, boolean ownedReflect)
  {
    removeFromScreen(pane, relicReflectBackground);
    removeFromScreen(pane, relicReflectIcon);
    relicReflectBackground = null;
    relicReflectIcon = null;

    if (!ownedReflect)
    {
      return;
    }

    double slotSize = 24;
    double rowY = HEART_ROW_Y + HEART_SEGMENT_HEIGHT + 4;
    double heartW = HEART_SEGMENT_WIDTH * 2;
    double heartGap = HEART_SEGMENT_WIDTH * 0.4;
    double heartsRowW = 3 * heartW + 2 * heartGap;
    double relicsTotalW = 3 * slotSize + 2 * RELIC_SLOT_GAP_X;
    double relicStartX = HEART_ROW_X + (heartsRowW - relicsTotalW) / 2;
    int column = 2;
    double x = relicStartX + column * (slotSize + RELIC_SLOT_GAP_X);
    double y = rowY;

    // background circle not needed — icon PNG has it baked in
    relicReflectIcon = new GImage(RELIC_REFLECT_ICON, x, y);
    placeOnScreen(pane, relicReflectIcon);
    scaleToFit(relicReflectIcon, slotSize);
  }

  /**
   * Attack hint (e.g. {@code "J"}) and sword icon area, bottom-right per design notes.
   */
  public void showSwordButton(GraphicsPane pane)
  {
    removeFromScreen(pane, SWDicon);
    SWDicon = null;

    double w = pane.mainScreen.getWidth();
    double h = pane.mainScreen.getHeight();
    double intangibleX = w - ABILITY_HUD_FROM_EDGE - ABILITY_BUTTON_SIZE;
    double x = intangibleX - ABILITY_BUTTON_GAP - ABILITY_BUTTON_SIZE;
    double y = h - ABILITY_HUD_FROM_EDGE - ABILITY_BUTTON_SIZE - ABILITY_BUTTON_FROM_BOTTOM;

    SWDicon = new GImage(ATTACK_BUTTON_PATH, x, y);
    placeOnScreen(pane, SWDicon);
    scaleToFit(SWDicon, ABILITY_BUTTON_SIZE);
  }

  /**
   * Intangible <em>ability</em> UI (layout similar to sword button). Distinct from
   * {@link #showRelicIntangible} (small relic icon under hearts).
   */
  public void showIntangibleAbilityButton(GraphicsPane pane)
  {
    removeFromScreen(pane, intangibleAbilityIcon);
    removeFromScreen(pane, intangibleAbilityCooldownIcon);
    intangibleAbilityIcon = null;
    intangibleAbilityCooldownIcon = null;

    double w = pane.mainScreen.getWidth();
    double h = pane.mainScreen.getHeight();
    double x = w - ABILITY_HUD_FROM_EDGE - ABILITY_BUTTON_SIZE;
    double y = h - ABILITY_HUD_FROM_EDGE - ABILITY_BUTTON_SIZE - ABILITY_BUTTON_FROM_BOTTOM;

    intangibleAbilityIcon = new GImage(INTANGIBLE_BUTTON_PATH, x, y);
    placeOnScreen(pane, intangibleAbilityIcon);
    scaleToFit(intangibleAbilityIcon, ABILITY_BUTTON_SIZE);

    intangibleAbilityCooldownIcon = new GImage(INTANGIBLE_BUTTON_COOLDOWN_PATH, x, y);
    placeOnScreen(pane, intangibleAbilityCooldownIcon);
    scaleToFit(intangibleAbilityCooldownIcon, ABILITY_BUTTON_SIZE);
    intangibleAbilityCooldownIcon.setVisible(false);
  }

  /**
   * Cooldown / “faded” state for the Intangible <em>ability</em> button. Boolean in, visual only out.
   *
   * @param pane         host pane
   * @param onCooldown   {@code true} if Intangible cannot be used yet (show faded / disabled look)
   */
  public void updateIntangibleAbilityButton(GraphicsPane pane, boolean onCooldown)
  {
    if (intangibleAbilityIcon == null || intangibleAbilityCooldownIcon == null)
    {
      return;
    }

    intangibleAbilityIcon.setVisible(!onCooldown);
    intangibleAbilityCooldownIcon.setVisible(onCooldown);
  }

  /**
   * Adds any {@link GObject} to the pane’s {@code contents} list and the live canvas so
   * {@link #hideAll} can remove it later.
   */
  private static void placeOnScreen(GraphicsPane pane, GObject obj) 
  {
		pane.contents.add(obj);
		pane.mainScreen.add(obj);
	}

  /**
   * Scales a GImage to fit inside a box of {@code maxSide} pixels, keeping the original
   * aspect ratio so the image never looks squished.
   */
  private static void scaleToFit(GImage img, double maxSide)
  {
    double w = img.getWidth();
    double h = img.getHeight();
    if (w <= 0 || h <= 0) return;
    double scale = Math.min(maxSide / w, maxSide / h);
    img.setSize(w * scale, h * scale);
  }

  /** Opposite of {@link #placeOnScreen}
   *  use when rebuilding a piece of the HUD. 
  */
  private static void removeFromScreen(GraphicsPane pane, GObject obj) 
  {
    if (obj != null) 
    {
      pane.mainScreen.remove(obj);
      pane.contents.remove(obj);
    }
  }

  /**
   * One-shot build: draws the full HUD from {@link HudSnapshot}. Call when entering a mode that
   * needs the HUD (rigger builds the snapshot from {@code Player} / save).
   */
  public void showAll(GraphicsPane pane, HudSnapshot data)
  {
    if (data.useUpgradedHeartBar)
    {
      showUpgradedHearts(pane, data.currentHeartSegments, data.relicBoolHeart);
    }
    else
    {
      showHearts(pane, data.currentHeartSegments, data.relicBoolHeart);
    }
    showCoins(pane, data.currentCoins);
    showRelicIntangible(pane, data.ownedIntangible);
    showRelicHalfDamage(pane, data.ownedHalfDamage);
    showRelicReflect(pane, data.ownedReflect);
    showSwordButton(pane);
    if (data.ownedIntangible)
    {
      showIntangibleAbilityButton(pane);
      updateIntangibleAbilityButton(pane, data.intangibleAbilityOnCooldown);
    }
  }

  /**
   * Lifts every live HUD element to the top of the drawing stack so room art,
   * enemies, and player sprites cannot cover it. Call each tick after other
   * bring-to-front operations (e.g. room foreground, controls card).
   */
  public void bringToFront()
  {
    if (hearts != null)
    {
      for (GImage h : hearts)
      {
        if (h != null) h.sendToFront();
      }
    }
    if (coinsicon != null) coinsicon.sendToFront();
    coinslabel.sendToFront();
    if (relicIntangibleIcon != null) relicIntangibleIcon.sendToFront();
    if (relicHalfDamageIcon != null) relicHalfDamageIcon.sendToFront();
    if (relicReflectIcon != null) relicReflectIcon.sendToFront();
    if (SWDicon != null) SWDicon.sendToFront();
    if (intangibleAbilityIcon != null) intangibleAbilityIcon.sendToFront();
    if (intangibleAbilityCooldownIcon != null) intangibleAbilityCooldownIcon.sendToFront();
  }

  /**
   * Removes every HUD {@link GObject} this overlay added (hearts, relics, coins, sword, etc.) from
   * {@code pane.contents} and the canvas. Call on transitions where the HUD should disappear.
   * Clears field references so shapes can be garbage-collected.
   */
  public void hideAll(GraphicsPane pane)
  {
    if (hearts != null)
    {
      for (GImage h : hearts)
      {
        if (h != null)
        {
          removeFromScreen(pane, h);
        }
      }
      hearts = null;
    }

    removeFromScreen(pane, coinslabel);
    removeFromScreen(pane, coinsicon);
    coinsicon = null;

    removeFromScreen(pane, relicIntangibleBackground);
    removeFromScreen(pane, relicIntangibleIcon);
    relicIntangibleBackground = null;
    relicIntangibleIcon = null;

    removeFromScreen(pane, relicHalfDamageBackground);
    removeFromScreen(pane, relicHalfDamageIcon);
    relicHalfDamageBackground = null;
    relicHalfDamageIcon = null;

    removeFromScreen(pane, relicReflectBackground);
    removeFromScreen(pane, relicReflectIcon);
    relicReflectBackground = null;
    relicReflectIcon = null;

    removeFromScreen(pane, SWDicon);
    SWDicon = null;

    removeFromScreen(pane, intangibleAbilityIcon);
    removeFromScreen(pane, intangibleAbilityCooldownIcon);
    intangibleAbilityIcon = null;
    intangibleAbilityCooldownIcon = null;
  }

	/**
	 * Local TEST only Run this class from the IDE to preview the HUD. 
   * assumes the window is exactly {@code TEST_W}×{@code TEST_H} and that
	 * {@code HEART_*} constants are authored as pixels for that size. 
   * Not for resize or other resolutions.
	 */
	public static void main(String[] args) 
  {
		final int TEST_W = 1280;
		final int TEST_H = 720;
		class Sandbox extends MainApplication 
    {
			@Override
			public void run() 
      {
				setSize(TEST_W, TEST_H);
				setupInteractions();
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
            new HUDoverlay.HudSnapshot(
                3,
                false,
                false,
                999,
                true,
                true,
                true,
                true);
        hud.showAll(host, snap);
        //hud.hideAll(host);
			}
		}
		new Sandbox().start();
	}
}