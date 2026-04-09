import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;

import acm.graphics.GImage;
import acm.graphics.GLabel;
import acm.graphics.GObject;
import acm.graphics.GOval;
import acm.graphics.GPolygon;
import acm.graphics.GRect;
import acm.graphics.GRoundRect;

public class GraphicsPane {
	protected MainApplication mainScreen;
	protected ArrayList<GObject> contents;

	/** Top-right "×" control — opens the pause menu when clicked (see {@link #addSettingsCornerButton()}). */
	protected GRoundRect settingsCornerFrame;
	protected GLabel settingsCornerLabel;

	public GraphicsPane() {
		contents = new ArrayList<GObject>();
	}


	public void showContent() {
	}

	/**
	 * Re-layout at the current window size without switching screens.
	 * Default rebuilds the pane (stateless menus). Panes with progress override.
	 */
	public void refreshLayout() {
		hideContent();
		showContent();
	}

	public void hideContent() {
	}

	public void mousePressed(MouseEvent e) {
	}

	public void mouseReleased(MouseEvent e) {
	}

	public void mouseClicked(MouseEvent e) {
	}

	/**
	 * Optional top-most UI click handler for pane-owned overlays that should still be clickable
	 * even while another overlay (for example dialogue) is open.
	 *
	 * @return true when the pane consumed the click
	 */
	public boolean tryHandleOverlayClick(MouseEvent e) {
		return false;
	}

	public void mouseDragged(MouseEvent e) {
	}

	public void mouseMoved(MouseEvent e) {
	}

	public void mouseWheelMoved(MouseWheelEvent e) {
	}

	public void keyPressed(KeyEvent e) {
	}

	/**
	 * Optional top-most key handler for pane-owned overlays that should remain available even while
	 * another overlay (for example dialogue) is open.
	 *
	 * @return true when the pane consumed the key press
	 */
	public boolean tryHandleOverlayKeyPressed(KeyEvent e) {
		return false;
	}

	public void keyReleased(KeyEvent e) {
	}

	public void keyTyped(KeyEvent e) {
	}

	/**
	 * Per-tick callback for panes that need continuous updates (movement, AI, cutscenes).
	 * Default no-op keeps menu panes event-driven.
	 */
	public void onTick(double dt) {
	}

	/**
	 * Returns true if this pane needs the 60fps game loop running (player movement, AI, etc.).
	 * Menu/narrative panes return false so the loop doesn't interfere with event-driven rendering.
	 */
	public boolean needsGameLoop() {
		return false;
	}

	/** Horizontal offset so the layout canvas stays centered in the real window while animating. */
	protected double originX() {
		double lw = mainScreen.getLayoutWidth();
		return (mainScreen.getWidth() - lw) / 2.0;
	}

	/** Vertical offset so the layout canvas stays centered in the real window while animating. */
	protected double originY() {
		double lh = mainScreen.getLayoutHeight();
		return (mainScreen.getHeight() - lh) / 2.0;
	}

	protected double scaleX(double logicalX) {
		return originX() + logicalX * mainScreen.getLayoutWidth() / MainApplication.WINDOW_WIDTH;
	}

	protected double scaleY(double logicalY) {
		return originY() + logicalY * mainScreen.getLayoutHeight() / MainApplication.WINDOW_HEIGHT;
	}

	protected int scaleFontSize(int baseSize) {
		double widthRatio = mainScreen.getLayoutWidth() / MainApplication.WINDOW_WIDTH;
		double heightRatio = mainScreen.getLayoutHeight() / MainApplication.WINDOW_HEIGHT;
		double ratio = Math.min(widthRatio, heightRatio);
		return Math.max(12, (int) Math.round(baseSize * ratio));
	}

	protected String scaledFont(int baseSize) {
		return "DialogInput-PLAIN-" + scaleFontSize(baseSize);
	}

	protected double centeredX(GObject object) {
		return originX() + (mainScreen.getLayoutWidth() - object.getWidth()) / 2.0;
	}

	protected double uniformScale() {
		double widthRatio = mainScreen.getLayoutWidth() / MainApplication.WINDOW_WIDTH;
		double heightRatio = mainScreen.getLayoutHeight() / MainApplication.WINDOW_HEIGHT;
		return Math.min(widthRatio, heightRatio);
	}

	/**
	 * Adds a small gold-outlined × button in the top-right (layout coordinates) that opens the pause menu.
	 * Call at the end of {@link #showContent()} on full-screen panes except {@link SettingsPane},
	 * {@link StartMenuPane} (main menu — use Options), {@link LandingPane} (splash), and
	 * {@link SkyTransitionPane} (camera pans every object in {@code contents}).
	 */
	protected void addSettingsCornerButton() {
		double ox = originX();
		double lw = mainScreen.getLayoutWidth();
		double side = Math.max(scaleY(28) - scaleY(0), 24);
		double bx = ox + lw - side - scaleX(22);
		double by = scaleY(24);
		double arc = Math.min(scaleX(8) - scaleX(0), scaleY(8) - scaleY(0));
		settingsCornerFrame = new GRoundRect(bx, by, side, side, arc, arc);
		settingsCornerFrame.setFilled(true);
		settingsCornerFrame.setFillColor(new Color(40, 45, 75));
		settingsCornerFrame.setColor(new Color(255, 215, 120));
		settingsCornerLabel = new GLabel("\u00D7", 0, 0);
		settingsCornerLabel.setFont("SansSerif-BOLD-" + Math.max(11, scaleFontSize(18)));
		settingsCornerLabel.setColor(new Color(255, 215, 120));
		GLabel lab = settingsCornerLabel;
		double cx = bx + (side - lab.getWidth()) / 2;
		double cy = by + (side + lab.getAscent() - lab.getDescent()) / 2;
		lab.setLocation(cx, cy);
		contents.add(settingsCornerFrame);
		contents.add(settingsCornerLabel);
		mainScreen.add(settingsCornerFrame);
		mainScreen.add(settingsCornerLabel);
		settingsCornerFrame.sendToFront();
		settingsCornerLabel.sendToFront();
	}

	// =========================================================
	// SHARED HELPERS — available to all panes
	// =========================================================

	/**
	 * Adds a GObject to both the {@link #contents} tracking list and the
	 * main application canvas. Every visual element should use this method
	 * so that {@link #hideContent()} can remove it later.
	 *
	 * @param obj the graphics object to display
	 */
	protected void place(GObject obj) {
		contents.add(obj);
		mainScreen.add(obj);
	}

	/**
	 * Creates a filled {@link GRect} using <b>raw pixel</b> coordinates.
	 * Use {@link #srect} for logical (700×500) coordinates that auto-scale.
	 *
	 * @param x      pixel X of the left edge
	 * @param y      pixel Y of the top edge
	 * @param w      pixel width
	 * @param h      pixel height
	 * @param fill   fill colour
	 * @param border border / outline colour
	 * @return configured GRect (not yet added to canvas — call {@link #place})
	 */
	protected GRect rect(double x, double y, double w, double h, Color fill, Color border) {
		GRect r = new GRect(x, y, w, h);
		r.setFilled(true);
		r.setFillColor(fill);
		r.setColor(border);
		return r;
	}

	/**
	 * Creates a filled {@link GRect} using <b>logical</b> (700×500 design space)
	 * coordinates that are automatically scaled to pixel coordinates via
	 * {@link #scaleX} / {@link #scaleY}.
	 *
	 * @param lx     logical X of the left edge
	 * @param ly     logical Y of the top edge
	 * @param lw     logical width
	 * @param lh     logical height
	 * @param fill   fill colour
	 * @param border border / outline colour
	 * @return configured GRect (not yet added to canvas — call {@link #place})
	 */
	protected GRect srect(double lx, double ly, double lw, double lh, Color fill, Color border) {
		return rect(
			scaleX(lx),
			scaleY(ly),
			scaleX(lx + lw) - scaleX(lx),
			scaleY(ly + lh) - scaleY(ly),
			fill,
			border
		);
	}

	/**
	 * Creates a {@link GLabel} in the pixel-art monospaced bold font.
	 * The font size is automatically scaled for the current window size.
	 *
	 * @param text  label string
	 * @param size  logical font size (scaled automatically)
	 * @param color text colour
	 * @return configured GLabel at (0, 0) — caller sets location
	 */
	protected GLabel pixelLabel(String text, int size, Color color) {
		GLabel lbl = new GLabel(text, 0, 0);
		lbl.setFont("Monospaced-BOLD-" + scaleFontSize(size));
		lbl.setColor(color);
		return lbl;
	}

	/**
	 * Word-wraps a string into lines of at most {@code maxChars} characters,
	 * breaking only at spaces. Respects embedded {@code \n} newlines.
	 *
	 * @param text     the string to wrap
	 * @param maxChars maximum characters per line
	 * @return list of wrapped lines (never empty)
	 */
	protected List<String> wrapText(String text, int maxChars) {
		List<String> lines   = new ArrayList<>();
		String[]     words   = text.split(" ");
		StringBuilder current = new StringBuilder();

		for (String word : words) {
			if (current.length() + word.length() + 1 > maxChars && current.length() > 0) {
				lines.add(current.toString().trim());
				current = new StringBuilder();
			}
			current.append(word).append(" ");
		}
		if (current.length() > 0) {
			lines.add(current.toString().trim());
		}
		if (lines.isEmpty()) {
			lines.add("");
		}
		return lines;
	}

	// =========================================================
	// PLAYER HUD — portrait + stat bars
	// =========================================================

	private GPolygon hudFrameOuter;
	private GPolygon hudFrameInner;
	private GOval hudPortraitRing;
	private GOval hudPortraitWell;
	private GImage hudPortrait;
	private GLabel hudPortraitFallbackLabel;
	private HeartDisplay hudHeartDisplay;
	private GRect[] hudBarTracks;
	private GRect[] hudBarFills;
	/** Top-right wallet total (persistent during gameplay). */
	private GOval hudCoinIcon;
	private GLabel hudCoinTotalLabel;

	private static final double HUD_X = 18;
	private static final double HUD_Y = 18;
	private static final double HUD_PORTRAIT_D = 58;
	private static final double HUD_PORTRAIT_INSET = 5;
	private static final double HUD_PANEL_OFFSET_X = 40;
	private static final double HUD_PANEL_OFFSET_Y = 5;
	private static final double HUD_PANEL_W = 246;
	private static final double HUD_PANEL_H = 56;
	private static final double HUD_PANEL_TIP = 18;
	private static final double HUD_PANEL_LEFT_CUT = 16;
	private static final double HUD_FRAME_INSET = 4;
	private static final double HUD_BAR_X = 70;
	private static final double HUD_BAR_Y = 14;
	private static final double HUD_BAR_W = 182;
	private static final double HUD_BAR_TOP_H = 10;
	private static final double HUD_BAR_H = 7;
	private static final double HUD_BAR_GAP = 6;
	private static final double HUD_BAR_INSET = 1;
	private static final double HUD_HEART_CELL_SIZE = 2.0;
	private static final double HUD_HEART_LEFT_PAD = 6;

	/** Logical px from layout right edge reserved for pause/settings button — coin cluster sits left of this. */
	private static final double HUD_COIN_PAUSE_RESERVE = 56;
	private static final double HUD_COIN_ICON_SIZE = 12;
	private static final double HUD_COIN_ICON_LABEL_GAP = 6;
	private static final double HUD_COIN_TOP = 18;
	private static final int HUD_COIN_DISPLAY_MAX = 99999;

	private static final Color HUD_FRAME_GOLD = new Color(177, 124, 55);
	private static final Color HUD_FRAME_WOOD = new Color(116, 69, 28);
	private static final Color HUD_FRAME_SHADOW = new Color(78, 43, 14);
	private static final Color HUD_PORTRAIT_BG = new Color(184, 200, 225);
	private static final Color HUD_PORTRAIT_FALLBACK = new Color(255, 245, 214);
	private static final Color HUD_BAR_TRACK = new Color(84, 48, 20);
	private static final Color HUD_BAR_BORDER = new Color(60, 33, 12);
	private static final Color HUD_BAR_RED = new Color(214, 63, 47);
	private static final Color HUD_BAR_BLUE = new Color(53, 132, 215);
	private static final Color HUD_BAR_GREEN = new Color(93, 181, 82);
	private static final Color HUD_BAR_DISABLED = new Color(87, 102, 70);
	protected static final Color SHARED_HEART_FULL = new Color(232, 45, 34);
	protected static final Color SHARED_HEART_EMPTY = new Color(108, 33, 30);
	protected static final Color SHARED_HEART_BORDER = new Color(70, 18, 17);
	protected static final Color SHARED_HEART_SHADOW = new Color(38, 10, 10);

	private static final String HUD_PORTRAIT_PATH =
		"assets/visuals/characters/normalized/player-1-idle-front.gif";
	private static final double HUD_PORTRAIT_ANCHOR_X = 0.50;
	private static final double HUD_PORTRAIT_ANCHOR_Y = 0.56;

	/**
	 * Draws the top-left HUD styled after the provided mockup:
	 * player portrait on the left, three state bars on the right.
	 *
	 * Bars map to:
	 * 1. Health
	 * 2. Attack readiness
	 * 3. Relic ability state / recharge
	 */
	protected void showPlayerHUD(Player player) {
		hidePlayerHUD();

		double sx = mainScreen.getLayoutWidth() / MainApplication.WINDOW_WIDTH;
		double sy = mainScreen.getLayoutHeight() / MainApplication.WINDOW_HEIGHT;
		double scale = uniformScale();

		double baseX = scaleX(HUD_X);
		double baseY = scaleY(HUD_Y);
		double portraitD = HUD_PORTRAIT_D * scale;
		double portraitX = baseX;
		double portraitY = baseY;

		double panelX = baseX + HUD_PANEL_OFFSET_X * sx;
		double panelY = baseY + HUD_PANEL_OFFSET_Y * sy;
		double panelW = HUD_PANEL_W * sx;
		double panelH = HUD_PANEL_H * sy;

		hudFrameOuter = createHudFrame(
			panelX, panelY, panelW, panelH,
			HUD_PANEL_TIP * sx, HUD_PANEL_LEFT_CUT * sx,
			HUD_FRAME_GOLD, HUD_FRAME_SHADOW);
		place(hudFrameOuter);

		hudFrameInner = createHudFrame(
			panelX + HUD_FRAME_INSET * sx,
			panelY + HUD_FRAME_INSET * sy,
			panelW - HUD_FRAME_INSET * 2 * sx,
			panelH - HUD_FRAME_INSET * 2 * sy,
			Math.max(8 * sx, (HUD_PANEL_TIP - HUD_FRAME_INSET) * sx),
			Math.max(8 * sx, (HUD_PANEL_LEFT_CUT - HUD_FRAME_INSET) * sx),
			HUD_FRAME_WOOD, HUD_FRAME_SHADOW);
		place(hudFrameInner);

		hudPortraitRing = new GOval(portraitX, portraitY, portraitD, portraitD);
		hudPortraitRing.setFilled(true);
		hudPortraitRing.setFillColor(HUD_FRAME_GOLD);
		hudPortraitRing.setColor(HUD_FRAME_SHADOW);
		place(hudPortraitRing);

		double portraitInset = HUD_PORTRAIT_INSET * scale;
		hudPortraitWell = new GOval(
			portraitX + portraitInset,
			portraitY + portraitInset,
			portraitD - portraitInset * 2,
			portraitD - portraitInset * 2);
		hudPortraitWell.setFilled(true);
		hudPortraitWell.setFillColor(HUD_PORTRAIT_BG);
		hudPortraitWell.setColor(HUD_FRAME_SHADOW);
		place(hudPortraitWell);

		hudPortrait = createHudPortrait(portraitX, portraitY, portraitD);
		if (hudPortrait != null) {
			place(hudPortrait);
		} else {
			String fallback = "?";
			if (player != null && player.getName() != null && !player.getName().trim().isEmpty()) {
				fallback = String.valueOf(player.getName().trim().charAt(0)).toUpperCase();
			}
			hudPortraitFallbackLabel = pixelLabel(fallback, 16, HUD_PORTRAIT_FALLBACK);
			double labelX = portraitX + (portraitD - hudPortraitFallbackLabel.getWidth()) / 2.0;
			double labelY = portraitY + (portraitD + hudPortraitFallbackLabel.getAscent()) / 2.0
				- hudPortraitFallbackLabel.getDescent() / 2.0;
			hudPortraitFallbackLabel.setLocation(labelX, labelY);
			place(hudPortraitFallbackLabel);
		}

		double barX = baseX + HUD_BAR_X * sx;
		double barY = baseY + HUD_BAR_Y * sy;
		double barW = HUD_BAR_W * sx;
		double topBarH = HUD_BAR_TOP_H * sy;
		double barH = HUD_BAR_H * sy;
		double barGap = HUD_BAR_GAP * sy;
		double barInsetX = HUD_BAR_INSET * sx;
		double barInsetY = HUD_BAR_INSET * sy;

		double heartCellSize = Math.max(2.0, Math.ceil(HUD_HEART_CELL_SIZE * scale));
		double heartHeight = HeartDisplay.heightFor(heartCellSize);
		double heartX = barX + HUD_HEART_LEFT_PAD * sx;
		double heartY = barY + Math.max(0.0, (topBarH - heartHeight) / 2.0);

		hudHeartDisplay = new HeartDisplay(Player.DEFAULT_HEART_COUNT, heartCellSize);
		applySharedHeartPalette(hudHeartDisplay);
		hudHeartDisplay.show(this, heartX, heartY);

		setupHudCoinCluster(player);

		double attackBarY = barY + topBarH + barGap;
		hudBarTracks = new GRect[2];
		hudBarFills = new GRect[2];
		for (int i = 0; i < hudBarTracks.length; i++) {
			double currentY = attackBarY + i * (barH + barGap);

			GRect track = new GRect(barX, currentY, barW, barH);
			track.setFilled(true);
			track.setFillColor(HUD_BAR_TRACK);
			track.setColor(HUD_BAR_BORDER);
			hudBarTracks[i] = track;
			place(track);

			GRect fill = new GRect(
				barX + barInsetX,
				currentY + barInsetY,
				Math.max(1.0, barW - barInsetX * 2),
				Math.max(1.0, barH - barInsetY * 2));
			fill.setFilled(true);
			hudBarFills[i] = fill;
			place(fill);
		}

		updatePlayerHUD(player);
		bringPlayerHudToFront();
	}

	private GPolygon createHudFrame(
		double x, double y, double width, double height, double tipWidth, double leftCutWidth,
		Color fill, Color border) {
		GPolygon frame = new GPolygon();
		frame.addVertex(0, 0);
		frame.addVertex(width - tipWidth, 0);
		frame.addVertex(width, height / 2.0);
		frame.addVertex(width - tipWidth, height);
		frame.addVertex(0, height);
		frame.addVertex(leftCutWidth, height / 2.0);
		frame.setLocation(x, y);
		frame.setFilled(true);
		frame.setFillColor(fill);
		frame.setColor(border);
		return frame;
	}

	private GImage createHudPortrait(double portraitX, double portraitY, double portraitDiameter) {
		GImage portrait = new GImage(HUD_PORTRAIT_PATH, 0, 0);
		double baseWidth = portrait.getWidth() > 0 ? portrait.getWidth() : 32.0;
		double baseHeight = portrait.getHeight() > 0 ? portrait.getHeight() : 32.0;
		double maxWidth = portraitDiameter * 0.66;
		double maxHeight = portraitDiameter * 0.72;
		double scale = Math.min(maxWidth / baseWidth, maxHeight / baseHeight);
		double targetWidth = baseWidth * scale;
		double targetHeight = baseHeight * scale;
		portrait.setSize(targetWidth, targetHeight);

		double centerX = portraitX + portraitDiameter / 2.0;
		double centerY = portraitY + portraitDiameter / 2.0 + portraitDiameter * 0.03;
		portrait.setLocation(
			centerX - targetWidth * HUD_PORTRAIT_ANCHOR_X,
			centerY - targetHeight * HUD_PORTRAIT_ANCHOR_Y);
		return portrait;
	}

	protected void applySharedHeartPalette(HeartDisplay heartDisplay) {
		if (heartDisplay == null) {
			return;
		}
		heartDisplay.setColors(SHARED_HEART_FULL, SHARED_HEART_EMPTY, SHARED_HEART_BORDER);
		heartDisplay.setShadowColor(SHARED_HEART_SHADOW);
	}

	/**
	 * Refreshes the three bars without recreating HUD objects.
	 */
	protected void updatePlayerHUD(Player player) {
		if (player == null) {
			return;
		}
		if (hudHeartDisplay != null) {
			hudHeartDisplay.setFilledHalfHearts(player.getHP());
		}
		if (hudCoinTotalLabel != null) {
			int c = Math.max(0, Math.min(HUD_COIN_DISPLAY_MAX, player.getCoins()));
			hudCoinTotalLabel.setLabel(String.valueOf(c));
			layoutHudCoinCluster();
		}
		if (hudBarTracks == null || hudBarFills == null) {
			bringPlayerHudToFront();
			return;
		}

		double attackFraction = 1.0;
		if (player.getAttackCooldownMax() > 0) {
			attackFraction = 1.0
				- (double) player.getAttackCooldownTicks() / (double) player.getAttackCooldownMax();
		}

		double relicFraction = 0.0;
		Color relicColor = HUD_BAR_DISABLED;
		if (player.hasIntangible()) {
			relicColor = HUD_BAR_GREEN;
			if (player.isIntangibleActive() && player.getIntangibleActiveMax() > 0) {
				relicFraction = (double) player.getIntangibleActiveTicks()
					/ (double) player.getIntangibleActiveMax();
			} else if (player.getIntangibleCooldownMax() > 0) {
				relicFraction = 1.0
					- (double) player.getIntangibleCooldownTicks()
					/ (double) player.getIntangibleCooldownMax();
			} else {
				relicFraction = 1.0;
			}
		}

		setHudBarFill(hudBarFills[0], hudBarTracks[0], attackFraction, HUD_BAR_BLUE);
		setHudBarFill(hudBarFills[1], hudBarTracks[1], relicFraction, relicColor);
		bringPlayerHudToFront();
	}

	private void setHudBarFill(GRect fill, GRect track, double fraction, Color color) {
		if (fill == null || track == null) {
			return;
		}

		double clamped = Math.max(0.0, Math.min(1.0, fraction));
		double insetX = fill.getX() - track.getX();
		double insetY = fill.getY() - track.getY();
		double maxWidth = Math.max(0.0, track.getWidth() - insetX * 2);
		double height = Math.max(1.0, track.getHeight() - insetY * 2);
		double width = maxWidth * clamped;

		fill.setFillColor(color);
		fill.setColor(color.darker());
		fill.setVisible(clamped > 0.0);
		if (clamped > 0.0) {
			fill.setSize(Math.max(1.0, width), height);
		}
	}

	private void bringPlayerHudToFront() {
		if (hudFrameOuter != null) hudFrameOuter.sendToFront();
		if (hudFrameInner != null) hudFrameInner.sendToFront();
		if (hudPortraitRing != null) hudPortraitRing.sendToFront();
		if (hudPortraitWell != null) hudPortraitWell.sendToFront();
		if (hudPortrait != null) hudPortrait.sendToFront();
		if (hudPortraitFallbackLabel != null) hudPortraitFallbackLabel.sendToFront();
		if (hudHeartDisplay != null) hudHeartDisplay.bringToFront();
		if (hudBarTracks != null) {
			for (GRect track : hudBarTracks) {
				if (track != null) track.sendToFront();
			}
		}
		if (hudBarFills != null) {
			for (GRect fill : hudBarFills) {
				if (fill != null) fill.sendToFront();
			}
		}
		if (hudCoinIcon != null) hudCoinIcon.sendToFront();
		if (hudCoinTotalLabel != null) hudCoinTotalLabel.sendToFront();
	}

	/**
	 * Creates the top-right coin icon + total label. Call once from {@link #showPlayerHUD}.
	 */
	private void setupHudCoinCluster(Player player) {
		removeHudCoinCluster();
		hudCoinTotalLabel = new GLabel("0", 0, 0);
		hudCoinTotalLabel.setFont("SansSerif-BOLD-14");
		hudCoinTotalLabel.setColor(new Color(255, 230, 140));
		int c = player == null ? 0 : Math.max(0, Math.min(HUD_COIN_DISPLAY_MAX, player.getCoins()));
		hudCoinTotalLabel.setLabel(String.valueOf(c));

		hudCoinIcon = new GOval(0, 0, HUD_COIN_ICON_SIZE, HUD_COIN_ICON_SIZE);
		hudCoinIcon.setFilled(true);
		hudCoinIcon.setFillColor(Color.YELLOW);
		hudCoinIcon.setColor(Color.BLACK);

		layoutHudCoinCluster();
		place(hudCoinIcon);
		place(hudCoinTotalLabel);
	}

	private void layoutHudCoinCluster() {
		if (hudCoinTotalLabel == null || hudCoinIcon == null) {
			return;
		}
		double ox = originX();
		double lw = mainScreen.getLayoutWidth();
		double clusterRight = ox + lw - scaleX(HUD_COIN_PAUSE_RESERVE);
		double labelW = hudCoinTotalLabel.getWidth();
		double labelX = clusterRight - labelW;
		double baseline = scaleY(HUD_COIN_TOP) + hudCoinTotalLabel.getAscent();
		hudCoinTotalLabel.setLocation(labelX, baseline);
		double iconY =
			scaleY(HUD_COIN_TOP) + (hudCoinTotalLabel.getAscent() + hudCoinTotalLabel.getDescent() - HUD_COIN_ICON_SIZE) / 2;
		hudCoinIcon.setLocation(labelX - HUD_COIN_ICON_LABEL_GAP - HUD_COIN_ICON_SIZE, iconY);
	}

	private void removeHudCoinCluster() {
		if (hudCoinIcon != null) {
			mainScreen.remove(hudCoinIcon);
			contents.remove(hudCoinIcon);
			hudCoinIcon = null;
		}
		if (hudCoinTotalLabel != null) {
			mainScreen.remove(hudCoinTotalLabel);
			contents.remove(hudCoinTotalLabel);
			hudCoinTotalLabel = null;
		}
	}

	/**
	 * Baseline Y for a short "+N" pickup popup above the coin total (gameplay HUD).
	 */
	protected double getCoinGainPopupBaselineY() {
		if (hudCoinTotalLabel == null) {
			return scaleY(HUD_COIN_TOP);
		}
		return hudCoinTotalLabel.getY() - hudCoinTotalLabel.getAscent() - 6;
	}

	/** Right edge X of the coin cluster area (for aligning popups). */
	protected double getHudCoinClusterRightX() {
		double ox = originX();
		double lw = mainScreen.getLayoutWidth();
		return ox + lw - scaleX(HUD_COIN_PAUSE_RESERVE);
	}

	/**
	 * Removes every HUD object from the canvas and tracking list.
	 */
	protected void hidePlayerHUD() {
		if (hudHeartDisplay != null) {
			hudHeartDisplay.remove();
			hudHeartDisplay = null;
		}

		GObject[] hudObjects = {
			hudFrameOuter,
			hudFrameInner,
			hudPortraitRing,
			hudPortraitWell,
			hudPortrait,
			hudPortraitFallbackLabel
		};
		for (GObject obj : hudObjects) {
			if (obj != null) {
				mainScreen.remove(obj);
				contents.remove(obj);
			}
		}
		if (hudBarTracks != null) {
			for (GRect track : hudBarTracks) {
				if (track != null) {
					mainScreen.remove(track);
					contents.remove(track);
				}
			}
		}
		if (hudBarFills != null) {
			for (GRect fill : hudBarFills) {
				if (fill != null) {
					mainScreen.remove(fill);
					contents.remove(fill);
				}
			}
		}

		hudFrameOuter = null;
		hudFrameInner = null;
		hudPortraitRing = null;
		hudPortraitWell = null;
		hudPortrait = null;
		hudPortraitFallbackLabel = null;
		hudHeartDisplay = null;
		hudBarTracks = null;
		hudBarFills = null;
		removeHudCoinCluster();
	}

	protected double playerHudBottomY() {
		double bottom = scaleY(HUD_Y + HUD_PORTRAIT_D);
		if (hudFrameOuter != null) {
			bottom = Math.max(bottom, hudFrameOuter.getY() + hudFrameOuter.getHeight());
		}
		if (hudPortraitRing != null) {
			bottom = Math.max(bottom, hudPortraitRing.getY() + hudPortraitRing.getHeight());
		}
		return bottom;
	}

	/**
	 * @return true if the click was on the settings × button (caller should not forward the event)
	 */
	public boolean tryHandleSettingsCornerClick(MouseEvent e) {
		if (settingsCornerFrame == null) {
			return false;
		}
		double x = e.getX();
		double y = e.getY();
		if (settingsCornerFrame.contains(x, y)) {
			mainScreen.showPauseModal();
			return true;
		}
		if (settingsCornerLabel != null && settingsCornerLabel.contains(x, y)) {
			mainScreen.showPauseModal();
			return true;
		}
		return false;
	}
}
