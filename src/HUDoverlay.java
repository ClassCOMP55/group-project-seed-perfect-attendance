/*
Roberto: health HUD class 
RIG: [whoever] calls show / update / hide. 
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

  /** Intangible <em>ability</em> button (like sword key hint), not the small relic-row icon. */
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
*/

  /**
   * Draws the normal heart bar (6 half-segments for 3 hearts). Call from {@link #showAll} or when
   * building the HUD the first time.
   *
   * @param pane          host pane (canvas + {@code contents} list)
   * @param currentHeart  how many segments are filled (0–{@value #DEFAULT_HEART_SEGMENTS}); game passes current value, not damage delt
   * @param relicBool     reserved for pivot “upgraded heart” / quarter-step mode when implemented
   */
  public void showHearts(GraphicsPane pane, int currentHeart, boolean relicBool)
  {
    GRect tempHeart = new GRect(0,0,10,12);
    tempHeart.setColor(Color.BLACK);
    tempHeart.setFilled(true);
    tempHeart.setFillColor(Color.RED);
    placeOnScreen(pane, tempHeart);

  }


  /**
   * Draws the upgraded heart bar when the pivot uses 12 segments (quarter steps). Same “numbers in,
   * pictures out” idea as {@link #showHearts}.
   *
   * @param pane          host pane
   * @param currentHeart  filled segment count (0–{@value #UPGRADED_HEART_SEGMENTS})
   * @param relicBool     gate or extra flag when design locks how upgrade is unlocked
   */
  public void showUpgradedHearts(GraphicsPane pane, int currentHeart, boolean relicBool)
{

}

  /**
   * Updates heart segment colors only (no full rebuild). Use for both heal and damage: pass the
   * <em>new</em> filled segment count. Does not own combat logic—whoever owns {@code Player} / game
   * state computes the number and calls this.
   *
   * @param pane          host pane
   * @param currentHeart  filled segments after the change (0–max for current mode)
   */
  public void updateHearts(GraphicsPane pane, int currentHeart)
  {

  }
  /**
   * Places the coin display (top-right area per pivot). First-time setup; pair with {@link #updateCoins}.
   *
   * @param pane         host pane
   * @param currentCoins wallet value to show when the HUD appears
   */
  public void showCoins(GraphicsPane pane, int currentCoins)
  {
    
  }
  /**
   * Refreshes the coin label (or icon) when the count changes. Numbers in, display only out.
   *
   * @param pane         host pane
   * @param currentCoins new total coins
   */
  public void updateCoins(GraphicsPane pane, int currentCoins)
  {

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
   * Cooldown / “faded” state for the Intangible <em>ability</em> button. Boolean in, visual only out.
   *
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

  	/** LOCAL TEST: Run this class from the IDE to preview the HUD. 
     * Remove before turn-in if required. */
	public static void main(String[] args) 
  {
		class Sandbox extends MainApplication 
    {
			@Override
			public void run() 
      {
				setSize(1280, 720);
				setupInteractions();
				class Host extends GraphicsPane 
        {
					Host() 
          {
						mainScreen = Sandbox.this;
					}
				}
				new HUDoverlay().showAll(new Host());
			}
		}
		new Sandbox().start();
	}
}