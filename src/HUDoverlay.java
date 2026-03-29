/*
Roberto: health HUD class 
RIG: [whoever] calls show / update / hide / etc. 
Does not own Player or combat
*/

import java.awt.Color;
import acm.graphics.*;



public class HUDoverlay 
{
  //constants for the HUDoverlay
  private static final int DEFAULT_HEART_SEGMENTS = 6;
  private static final int UPGRADED_HEART_SEGMENTS = 12;
  private static final int HEARTS_ON_SCREEN = 3;
        //size of one half-segment
  private static final double HEART_SEGMENT_WIDTH = 10;
  private static final double HEART_SEGMENT_HEIGHT = 12;
        //location of the start of the 3 hearts on screen
  private static final double HEART_ROW_X = 12;
  private static final double HEART_ROW_Y = 12;

  //Gap from the right edge of the window to the coin cluster 
  //(pixels; matches heart margin distance).
  private static final double COINS_MARGIN_RIGHT = 32;
  //Placeholder “coin” circle until a {@code GImage} is wired.
  private static final double COINS_ICON_SIZE = 10;
  private static final double COINS_ICON_LABEL_GAP = 4;
  //HUD shows at most this many coins (3 digits); higher values are capped for display purposes
  //need to apply the same cap to actual "wallet" wherever that will be kept
  private static final int COINS_DISPLAY_MAX = 999;

  
  //Horizontal gap between relic slots in the row <em>below</em> the hearts 
  //(side by side, left to* right). 
  // //Each relic has a fixed slot index so spacing does not depend on which relics you own.
  private static final double RELIC_SLOT_GAP_X = 6;

  private GRect[] hearts;
  //private GImage[] heartIcons;

  private GOval relicIntangibleBackground;
  private GRect tempRelicIntangibleIcon;
  //private GImage relicIntangibleIcon;

  private GOval relicHalfDamageBackground;
  private GRect tempRelicHalfDamageIcon;
  //private GImage relicHalfDamageIcon;

  private GOval relicReflectBackground;
  private GRect tempRelicReflectIcon;
  //private GImage relicReflectIcon;

  private GOval SWDbackground;
  private GRect tempSWDicon;
  //private GImage SWDicon;
  private GLabel SWDbutton = new GLabel("J");

  private GOval intangibleAbilityBackground;
  private GRect tempIntangibleAbilityIcon;
  //private GImage intangibleAbilityIcon;

  private GOval coins;
  //private GImage coinsicon;
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
    //remove old rects so calling showHearts again does not leave orphans on the screen behind the new hearts
    if (hearts != null) 
    {
      for (GRect h : hearts) 
      {
        if (h != null) 
        {
          removeFromScreen(pane, h);
        }
      }
      hearts = null;
    }

    //How many segments should look “filled” (red). 
    //clamped 0 to 6 so a bad caller cannot break the HUD.
    int filled = Math.max(0, Math.min(DEFAULT_HEART_SEGMENTS, currentHeart));

    hearts = new GRect[DEFAULT_HEART_SEGMENTS];

    double gapBetweenHearts = HEART_SEGMENT_WIDTH * 2;
    double x = HEART_ROW_X;

    for (int i = 0; i < DEFAULT_HEART_SEGMENTS; i++) 
    {
      double px = x;
      double py = HEART_ROW_Y;
      double pw = HEART_SEGMENT_WIDTH;
      double ph = HEART_SEGMENT_HEIGHT;

      //draws the heart segments
      GRect heartsegment = new GRect(px, py, pw, ph);
      heartsegment.setColor(Color.BLACK);
      heartsegment.setFilled(true);
      heartsegment.setFillColor(i < filled ? Color.RED : Color.LIGHT_GRAY);

      placeOnScreen(pane, heartsegment);
      hearts[i] = heartsegment;

      //offset and gap between the heart segments
      x += HEART_SEGMENT_WIDTH;

      if (i % 2 == 1 && i < DEFAULT_HEART_SEGMENTS - 1) 
      {
        x += gapBetweenHearts;
      }
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
      for (GRect h : hearts)
      {
        if (h != null)
        {
          removeFromScreen(pane, h);
        }
      }
      hearts = null;
    }

    int filled = Math.max(0, Math.min(UPGRADED_HEART_SEGMENTS, currentHeart));

    hearts = new GRect[UPGRADED_HEART_SEGMENTS];

    double quarterW = HEART_SEGMENT_WIDTH / 2.0;
    double gapBetweenHearts = HEART_SEGMENT_WIDTH * 2;

    double x = HEART_ROW_X;

    for (int i = 0; i < UPGRADED_HEART_SEGMENTS; i++)
    {
      double px = x;
      double py = HEART_ROW_Y;
      double pw = quarterW;
      double ph = HEART_SEGMENT_HEIGHT;

      GRect heartsegment = new GRect(px, py, pw, ph);
      heartsegment.setColor(Color.BLACK);
      heartsegment.setFilled(true);
      heartsegment.setFillColor(i < filled ? Color.RED : Color.LIGHT_GRAY);

      placeOnScreen(pane, heartsegment);
      hearts[i] = heartsegment;

      //offset and gap between the heart segments
      x += quarterW;

      if (i % 4 == 3 && i < UPGRADED_HEART_SEGMENTS - 1)
      {
        x += gapBetweenHearts;
      }
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

    int filled = Math.max(0, Math.min(hearts.length, currentHeart));

    
    for (int i = 0; i < hearts.length; i++)
    {
      if (hearts[i] != null)
      {
        hearts[i].setFillColor(i < filled ? Color.RED : Color.LIGHT_GRAY);
      }
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
    removeFromScreen(pane, coins);

    int displayCoins = Math.max(0, Math.min(COINS_DISPLAY_MAX, currentCoins));

    coinslabel.setLabel(String.valueOf(displayCoins));
    coinslabel.setFont("SansSerif-BOLD-14");

    double w = pane.mainScreen.getWidth();
    double labelW = coinslabel.getWidth();
    double iconLeft = w - COINS_MARGIN_RIGHT - COINS_ICON_SIZE - COINS_ICON_LABEL_GAP - labelW;
    double iconTop = HEART_ROW_Y + (HEART_SEGMENT_HEIGHT - COINS_ICON_SIZE) / 2;

    coins = new GOval(iconLeft, iconTop, COINS_ICON_SIZE, COINS_ICON_SIZE);
    coins.setColor(Color.BLACK);
    coins.setFilled(true);
    coins.setFillColor(Color.YELLOW);
    

    double labelX = iconLeft + COINS_ICON_SIZE + COINS_ICON_LABEL_GAP;
    double labelBaseline = HEART_ROW_Y + HEART_SEGMENT_HEIGHT - 2;
    coinslabel.setLocation(labelX, labelBaseline);

    placeOnScreen(pane, coins);
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
    removeFromScreen(pane, tempRelicIntangibleIcon);
    relicIntangibleBackground = null;
    tempRelicIntangibleIcon = null;

    if (!ownedIntangible)
    {
      return;
    }

    double slotSize = 16;
    double rowY = HEART_ROW_Y + HEART_SEGMENT_HEIGHT + 6;
    int column = 0;
    double x = HEART_ROW_X + column * (slotSize + RELIC_SLOT_GAP_X);
    double y = rowY;

    relicIntangibleBackground = new GOval(x, y, slotSize, slotSize);
    relicIntangibleBackground.setFilled(true);
    relicIntangibleBackground.setColor(Color.BLACK);
    relicIntangibleBackground.setFillColor(Color.LIGHT_GRAY);

    double inset = 4;
    tempRelicIntangibleIcon =
        new GRect(x + inset, y + inset, slotSize - 2 * inset, slotSize - 2 * inset);
    tempRelicIntangibleIcon.setColor(Color.BLACK);
    tempRelicIntangibleIcon.setFilled(true);
    tempRelicIntangibleIcon.setFillColor(Color.MAGENTA);

    placeOnScreen(pane, relicIntangibleBackground);
    placeOnScreen(pane, tempRelicIntangibleIcon);
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
    removeFromScreen(pane, tempRelicHalfDamageIcon);
    relicHalfDamageBackground = null;
    tempRelicHalfDamageIcon = null;

    if (!ownedHalfDamage)
    {
      return;
    }

    double slotSize = 16;
    double rowY = HEART_ROW_Y + HEART_SEGMENT_HEIGHT + 6;
    int column = 1;
    double x = HEART_ROW_X + column * (slotSize + RELIC_SLOT_GAP_X);
    double y = rowY;

    relicHalfDamageBackground = new GOval(x, y, slotSize, slotSize);
    relicHalfDamageBackground.setFilled(true);
    relicHalfDamageBackground.setColor(Color.BLACK);
    relicHalfDamageBackground.setFillColor(Color.LIGHT_GRAY);

    double inset = 4;
    tempRelicHalfDamageIcon =
        new GRect(x + inset, y + inset, slotSize - 2 * inset, slotSize - 2 * inset);
    tempRelicHalfDamageIcon.setColor(Color.BLACK);
    tempRelicHalfDamageIcon.setFilled(true);
    tempRelicHalfDamageIcon.setFillColor(Color.ORANGE);

    placeOnScreen(pane, relicHalfDamageBackground);
    placeOnScreen(pane, tempRelicHalfDamageIcon);
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
    removeFromScreen(pane, tempRelicReflectIcon);
    relicReflectBackground = null;
    tempRelicReflectIcon = null;

    if (!ownedReflect)
    {
      return;
    }

    double slotSize = 16;
    double rowY = HEART_ROW_Y + HEART_SEGMENT_HEIGHT + 6;
    int column = 2;
    double x = HEART_ROW_X + column * (slotSize + RELIC_SLOT_GAP_X);
    double y = rowY;

    relicReflectBackground = new GOval(x, y, slotSize, slotSize);
    relicReflectBackground.setFilled(true);
    relicReflectBackground.setColor(Color.BLACK);
    relicReflectBackground.setFillColor(Color.LIGHT_GRAY);

    double inset = 4;
    tempRelicReflectIcon =
        new GRect(x + inset, y + inset, slotSize - 2 * inset, slotSize - 2 * inset);
    tempRelicReflectIcon.setColor(Color.BLACK);
    tempRelicReflectIcon.setFilled(true);
    tempRelicReflectIcon.setFillColor(Color.CYAN);

    placeOnScreen(pane, relicReflectBackground);
    placeOnScreen(pane, tempRelicReflectIcon);
  }

  /**
   * Attack hint (e.g. {@code "J"}) and sword icon area, bottom-right per design notes.
   */
  public void showSwordButton(GraphicsPane pane)
  {
    
  }

  /**
   * Intangible <em>ability</em> UI (layout similar to sword button). Distinct from
   * {@link #showRelicIntangible} (small relic icon under hearts).
   */
  public void showIntangibleAbilityButton(GraphicsPane pane)
  {

  }

  /**
   * Cooldown / “faded” state for the Intangible <em>ability</em> button. 
   * Boolean in, visual only out.
   * @param pane         host pane
   * @param onCooldown   {@code true} if Intangible cannot be used yet (show faded / disabled look)
   */
  public void updateIntangibleAbility(GraphicsPane pane, boolean onCooldown)
  {

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

  /** Opposite of {@link #placeOnScreen} — use when rebuilding a piece of the HUD. */
  private static void removeFromScreen(GraphicsPane pane, GObject obj) 
  {
    if (obj != null) 
    {
      pane.mainScreen.remove(obj);
      pane.contents.remove(obj);
    }
  }

  /**
   * One-shot build: calls the {@code show…} pieces in order, then {@code sendToFront} in the
   * correct stacking order (backgrounds under icons under text). Rigger: call when entering a mode
   * that needs the full HUD; pass ownership flags into relic methods from {@code Player} / save.
   */
  public void showAll(GraphicsPane pane)
  {
    
  }

  /**
   * Removes every HUD {@link GObject} this overlay added (hearts, relics, coins, sword, etc.) from
   * {@code pane.contents} and the canvas. Call on transitions where the HUD should disappear.
   */
  public void hideAll(GraphicsPane pane)
  {
    
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
				hud.showHearts(host, 6, false);
        hud.showCoins(host, 42);
				hud.updateHearts(host, 3);
        hud.updateCoins(host, 999);
        hud.showRelicIntangible(host, true);
        hud.showRelicHalfDamage(host, true);
        hud.showRelicReflect(host, true);
			}
		}
		new Sandbox().start();
	}
}