/*
Roberto: health HUD class 
RIG: [whoever] calls show / update / hide. 
Does not own Player or combat
*/

import java.awt.Color;
import acm.graphics.GRect;



public class HUDoverlay 
{
    private static final int DEFAULT_HEART_SEGMENTS = 6;
    private static final int UPGRADED_HEART_SEGMENTS = 12;
    private static final int HEARTS_ON_SCREEN = 3;

    private GRect[] hearts;
    //private GImage[] heartIcons;

    private GOval r1background;
    private GImage r1icon;
    private GOval r2background;
    private GImage r2icon;
    private GOval r3background;
    private GImage r3icon;
    private GOval SWDbackground;
    private GImage SWDicon;
    private GLabel SWDbutton;



/*
-I need a way to "heal" the hearts on the HUDoverlay. Probably called by something in 
 the player class.
-We need a way to reduce the hearts on the HUDoverlay. Probably called by something in 
 the player class, when interacted with an Entity that deals damage to the player.
-I need a way to draw the HUD
-I need a way to show the HUD
-I need a way to update the HUD
-I need space in the bottom right corner to show the sword icon with a button to indicate 
the attack button associated with it
-I need the a spot just beneath the hearts where the 3 relics will be shown, WHEN they are obtained,
so there is an indicator of them
-I need a way to operate this class on it's own so that I can test the position of everything from
within this class
*/


    
  private static void placeOnScreen(GraphicsPane pane, GRect r) 
  {
		pane.contents.add(r);
		pane.mainScreen.add(r);
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
				new HUDoverlay().show(new Host(), 6);
			}
		}
		new Sandbox().start(args);
	}
}