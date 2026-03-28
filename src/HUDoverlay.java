import java.awt.Color;
import acm.graphics.GRect;

/*
Roberto: health HUD class 
RIG: [whoever] calls show / update / hide. 
Does not own Player or combat
*/

public class HUDoverlay 
{
    private static final int HEART_SEGMENTS = 6;
    private GRect[] segments;


    
    private static void placeOnScreen(GraphicsPane pane, GRect r) 
    {
		pane.contents.add(r);
		pane.mainScreen.add(r);
	}
}