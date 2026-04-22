/*
Roberto: Dialogue overlay (text box for signs, NPCs, scripted lines)
Who RIGs it: MainApplication — creates one instance, calls open/close; does not own Player or combat
Extends GraphicsPane

===============
PLAN OF ACTION 
===============

- CLASS ROLE (UI ONLY)
- Dialogue draws and controls the dialogue overlay UI only.
- Dialogue does not own world/combat/player freeze logic.
- Dialogue does not own quest progression, rewards, or inventory mutation.
- Dialogue receives content from other sources (NPC/sign/script/save flow) and reports completion.

- OWNERSHIP + LIFECYCLE
- MainApplication (or game loop owner) creates and owns one Dialogue instance.
- Open/close is triggered externally (e.g., on interact with NPC/sign/save point).
- Only one dialogue overlay should be active at a time.

- INPUT RULES
- Advance dialogue with J or Space only (forward-only, no backward navigation).
- Mouse advance is allowed only when click is inside the dialogue panel bounds.
- ESC does not advance and does not close dialogue; ESC is reserved for pause handling outside this class.

- LAYOUT + VISUALS
- Dialogue controls panel placement, size, colors, text labels, and speaker portrait placement.
- Dialogue may cover underlying world/HUD visuals while active; gameplay state freeze is handled elsewhere.
- Support text wrapping for long lines and include typewriter effect behavior.

- RESIZE BEHAVIOR
- Game is fixed 1280x720; no resize logic needed. refreshLayout() is a no-op stub.

- DATA + GAMEPLAY HOOKS
- Progressive dialogue/reset points are owned by NPC/script/save-state systems, not by this UI class.
- End-of-dialogue should provide a clear signal (callback/event/flag) so external systems can trigger follow-up actions.
- Reward/item safety (e.g., no duplicate BrokenLever, no duplicate MarkOfHero) must be enforced by external state checks.

- SAVE DIALOGUE USE CASE
- Save interaction (Inn & Crystal) uses the same Dialogue box, but displayed with two selectable options (Yes / No).
- Actual save execution is handled by SaveManager/SaveData owner after player confirms Yes.
- Dialogue reports the selection via the end-of-dialogue signal; save logic lives outside this class.

*/

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import acm.graphics.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Panel overlay for in-world dialogue lines (signs, NPCs, events).
 * Draws a box at the bottom of the screen and shows lines one at a time
 * with a typewriter effect. J or Space advance; last line fires onComplete.
 */
public class Dialogue extends GraphicsPane
{

	// =========================================================
	// LAYOUT CONSTANTS  (fixed 1280×720 game resolution)
	// =========================================================

	/** Dialogue panel rectangle. */
	private static final int PANEL_X      = 20;
	private static final int PANEL_Y      = 530;
	private static final int PANEL_W      = 1240;
	private static final int PANEL_H      = 160;

	/** Portrait placeholder inside the panel (shown when hasPortrait = true). */
	private static final int PORTRAIT_X   = 36;
	private static final int PORTRAIT_Y   = PANEL_Y + 20;
	private static final int PORTRAIT_W   = 100;
	private static final int PORTRAIT_H   = 120;

	/** X position where text starts (shifts right when portrait is present). */
	private static final int TEXT_X_NO_PORTRAIT  = PANEL_X + 20;
	private static final int TEXT_X_WITH_PORTRAIT = PORTRAIT_X + PORTRAIT_W + 20;

	/** Y positions for speaker label and text body inside the panel. */
	private static final int SPEAKER_Y    = PANEL_Y + 28;
	private static final int TEXT_Y       = PANEL_Y + 58;

	/** Max characters per wrapped line (tuned for font size 18 in the panel). Pass to wrapText(). */
	public static final int MAX_CHARS    = 85;

	/** Vertical gap in pixels between wrapped text rows. */
	private static final int LINE_HEIGHT = 24;

	/** Typewriter speed: characters revealed per game tick. */
	private static final int CHARS_PER_TICK = 2;

	/** Save prompt option positions. */
	private static final int OPTION_Y     = PANEL_Y + 100;
	private static final int OPTION0_X    = PANEL_X + 500;
	private static final int OPTION1_X    = PANEL_X + 620;
	private static final int CHOICE_OPTION_X = PANEL_X + 165;
	private static final int CHOICE_OPTION_START_Y = PANEL_Y + 92;
	private static final int CHOICE_OPTION_GAP_Y = 19;

	// =========================================================
	// COLORS
	// =========================================================

	private static final Color PANEL_BG     = new Color(10, 8, 20, 220);
	private static final Color PANEL_BORDER = new Color(180, 140, 80);
	private static final Color SPEAKER_COLOR = new Color(255, 215, 120);
	private static final Color TEXT_COLOR   = new Color(255, 215, 120);
	private static final Color HINT_COLOR   = new Color(160, 160, 180);
	private static final Color PORTRAIT_BG  = new Color(40, 35, 60);
	private static final Color PORTRAIT_BORDER = new Color(120, 100, 60);
	private static final Color OPTION_NORMAL   = new Color(200, 200, 220);
	private static final Color OPTION_SELECTED = new Color(255, 215, 120);

	// =========================================================
	// FIELDS
	// =========================================================

	/** Full set of lines for the current conversation. */
	private String[] lines;

	/** Index of the line currently being shown. */
	private int currentLine;

	/** True while the typewriter effect is still printing characters. */
	private boolean isTyping;

	/** How many characters of the current line have been revealed so far. */
	private int charIndex;

	/** True when this is a Yes/No save confirmation instead of normal lines. */
	private boolean isSavePrompt;

	/** True when this is a generic multi-choice prompt (used by the Trial of Wisdom riddles). */
	private boolean isChoicePrompt;

	/** For save prompt: 0 = Yes highlighted, 1 = No highlighted. */
	private int selectedOption;

	/** Prompt question shown above the selectable choices. */
	private String choiceQuestion;

	/** Choice labels presented for the current prompt (supports up to 3 answers). */
	private String[] choiceOptions;

	/** Fired when the last line is dismissed (or save option confirmed). */
	private Runnable onComplete;

	/** The visible dialogue box. */
	private GRect panel;

	/** Speaker name label shown above the text. */
	private GLabel speakerLabel;

	/** One label per wrapped row of the current line (rebuilt each typewriter tick). */
	private List<GLabel> textRows;

	/** X position of the text area — stored so update() knows where to place rows. */
	private int textX;

	/** Portrait placeholder rectangle (null if hasPortrait = false). */
	private GRect portraitBox;

	/** "▼ [J]" hint shown once typing is done and more lines remain. */
	private GLabel continueHint;

	/** "Yes" label — save prompt only. */
	private GLabel option0Label;

	/** "No" label — save prompt only. */
	private GLabel option1Label;

	/** Third answer label — choice prompt only. */
	private GLabel option2Label;

	// =========================================================
	// CONSTRUCTOR
	// =========================================================

	public Dialogue(MainApplication mainScreen)
	{
		this.mainScreen = mainScreen;
	}

	// =========================================================
	// OPEN
	// =========================================================

	/**
	 * Opens the dialogue box with a set of lines.
	 *
	 * @param lines       lines to show, one at a time
	 * @param speakerName name shown above the text (null or empty = no label)
	 * @param hasPortrait true to draw a portrait placeholder on the left
	 * @param onComplete  called when the last line is dismissed
	 */
	public void open(String[] lines, String speakerName, boolean hasPortrait, Runnable onComplete)
	{
		if (lines == null || lines.length == 0) return;
		close();
		this.lines       = lines;
		this.currentLine = 0;
		this.charIndex   = 0;
		this.isTyping    = true;
		this.isSavePrompt = false;
		this.onComplete  = onComplete;
		buildPanel(speakerName, hasPortrait);
	}

	/**
	 * Opens the dialogue box in save-prompt mode (Yes / No choice).
	 * The onComplete callback is fired when the player confirms either option.
	 * Which option was chosen (0 = Yes, 1 = No) is available via {@link #getSelectedOption()}.
	 *
	 * @param onComplete called when the player confirms their choice
	 */
	public void openSavePrompt(Runnable onComplete)
	{
		close();
		this.isSavePrompt   = true;
		this.isChoicePrompt = false;
		this.selectedOption = 0;
		this.onComplete     = onComplete;
		buildPanel(null, false);
	}

	/**
	 * Opens the dialogue box in 3-choice prompt mode.
	 * The selected option index is available through {@link #getSelectedOption()} when confirmed.
	 */
	public void openChoicePrompt(String speakerName, String question, String[] options, Runnable onComplete)
	{
		if (question == null || options == null || options.length < 2 || options.length > 3) return;
		close();
		this.isSavePrompt   = false;
		this.isChoicePrompt = true;
		this.selectedOption = 0;
		this.choiceQuestion = question;
		this.choiceOptions  = options.clone();
		this.onComplete     = onComplete;
		buildPanel(speakerName, false);
	}

	// =========================================================
	// CLOSE
	// =========================================================

	/**
	 * Removes all dialogue graphics from the canvas and resets state.
	 * Safe to call even when the dialogue is already closed.
	 */
	public void close()
	{
		for (GObject obj : contents)
		{
			mainScreen.remove(obj);
		}
		contents.clear();

		lines         = null;
		currentLine   = 0;
		charIndex     = 0;
		isTyping      = false;
		isSavePrompt  = false;
		isChoicePrompt = false;
		selectedOption = 0;
		choiceQuestion = null;
		choiceOptions = null;
		onComplete    = null;

		panel         = null;
		speakerLabel  = null;
		textRows      = null;
		textX         = 0;
		portraitBox   = null;
		continueHint  = null;
		option0Label  = null;
		option1Label  = null;
		option2Label  = null;
	}

	// =========================================================
	// ADVANCE
	// =========================================================

	/**
	 * Called when the player presses J, Space, or clicks inside the panel.
	 *
	 * First press while typing: reveals the full line instantly.
	 * Second press (or first if not typing): moves to the next line,
	 * or fires onComplete and closes if this was the last line.
	 */
	public void advance()
	{
		if (!isOpen()) return;

		if (isTyping)
		{
			// Skip typewriter — reveal the full current line immediately
			isTyping = false;
			if (!isSavePrompt && lines != null)
			{
				rebuildTextRows(lines[currentLine]);
			}
			if (continueHint != null)
			{
				continueHint.setVisible(true);
			}
			return;
		}

		if (isSavePrompt || isChoicePrompt)
		{
			// Confirm the highlighted choice and close
			Runnable callback = onComplete;
			int confirmedOption = selectedOption;
			close();
			selectedOption = confirmedOption;
			if (callback != null)
			{
				callback.run();
			}
			return;
		}

		// Move to the next line
		currentLine++;
		if (currentLine >= lines.length)
		{
			Runnable callback = onComplete;
			close();
			if (callback != null)
			{
				callback.run();
			}
			return;
		}

		// Start the next line's typewriter
		charIndex = 0;
		isTyping  = true;
		removeTextRows();
		if (continueHint != null)
		{
			continueHint.setVisible(false);
		}
	}

	// =========================================================
	// UPDATE  (called each game tick — drives typewriter)
	// =========================================================

	/**
	 * Advances the typewriter effect by one tick.
	 * Call this from the game loop every frame while dialogue is open.
	 *
	 * @param dt delta-time in seconds (not used for speed here; tick-based)
	 */
	public void update(double dt)
	{
		if (!isOpen()) return;
		if (isSavePrompt || isChoicePrompt) return;
		if (!isTyping) return;
		if (lines == null) return;

		String current = lines[currentLine];
		charIndex = Math.min(charIndex + CHARS_PER_TICK, current.length());
		GameSFX.play(GameSFX.SFX.DIALOGUE_TICK);
		rebuildTextRows(current.substring(0, charIndex));

		if (charIndex >= current.length())
		{
			isTyping = false;
			if (continueHint != null)
			{
				continueHint.setVisible(true);
			}
		}
	}

	/**
	 * Removes all current text row labels from the canvas and the contents list.
	 */
	private void removeTextRows()
	{
		if (textRows == null) return;
		for (GLabel row : textRows)
		{
			mainScreen.remove(row);
			contents.remove(row);
		}
		textRows.clear();
	}

	/**
	 * Clears existing text rows and rebuilds them from the given partial string.
	 * One GLabel is placed per wrapped line, stacked vertically inside the panel.
	 *
	 * @param partial the text to display (may be a partial line during typewriter)
	 */
	private void rebuildTextRows(String partial)
	{
		removeTextRows();
		if (textRows == null) textRows = new ArrayList<>();
		List<String> rows = wrapText(partial, MAX_CHARS);
		for (int i = 0; i < rows.size(); i++)
		{
			GLabel lbl = new GLabel(rows.get(i), textX, TEXT_Y + i * LINE_HEIGHT);
			lbl.setFont("Monospaced-PLAIN-18");
			lbl.setColor(TEXT_COLOR);
			place(lbl);
			textRows.add(lbl);
		}
	}

	// =========================================================
	// INPUT HANDLING
	// =========================================================

	@Override
	public void keyPressed(KeyEvent e)
	{
		if (!isOpen()) return;
		int key = e.getKeyCode();

		if (key == KeyEvent.VK_J || key == KeyEvent.VK_SPACE)
		{
			advance();
			return;
		}

		// Save prompt: Left/A moves highlight to Yes, Right/D moves to No
		if (isSavePrompt)
		{
			if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A)
			{
				setSelectedOption(0);
			}
			else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D)
			{
				setSelectedOption(1);
			}
			return;
		}

		if (isChoicePrompt)
		{
			int optionCount = choiceOptions == null ? 0 : choiceOptions.length;
			if (optionCount <= 0) return;
			if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A)
			{
				setSelectedOption((selectedOption - 1 + optionCount) % optionCount);
			}
			else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D)
			{
				setSelectedOption((selectedOption + 1) % optionCount);
			}
		}
	}

	@Override
	public void mouseClicked(MouseEvent e)
	{
		if (!isOpen() || panel == null) return;

		double x = e.getX();
		double y = e.getY();

		if (isSavePrompt || isChoicePrompt)
		{
			// Click on an option label selects it and confirms immediately
			if (option0Label != null && option0Label.contains(x, y))
			{
				setSelectedOption(0);
				advance();
				return;
			}
			if (option1Label != null && option1Label.contains(x, y))
			{
				setSelectedOption(1);
				advance();
				return;
			}
			if (option2Label != null && option2Label.contains(x, y))
			{
				setSelectedOption(2);
				advance();
				return;
			}
		}

		if (panel.contains(x, y))
		{
			advance();
		}
	}

	// =========================================================
	// BUILD PANEL
	// =========================================================

	/**
	 * Draws all GObjects for the current open state.
	 * Called by open() and openSavePrompt() after state is set.
	 */
	private void buildPanel(String speakerName, boolean hasPortrait)
	{
		// Background panel
		panel = new GRect(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
		panel.setFilled(true);
		panel.setFillColor(isChoicePrompt ? new Color(10, 8, 20, 242) : PANEL_BG);
		panel.setColor(PANEL_BORDER);
		place(panel);

		// Portrait placeholder
		if (hasPortrait)
		{
			portraitBox = new GRect(PORTRAIT_X, PORTRAIT_Y, PORTRAIT_W, PORTRAIT_H);
			portraitBox.setFilled(true);
			portraitBox.setFillColor(PORTRAIT_BG);
			portraitBox.setColor(PORTRAIT_BORDER);
			place(portraitBox);
		}

		this.textX = hasPortrait ? TEXT_X_WITH_PORTRAIT : TEXT_X_NO_PORTRAIT;

		// Speaker name label
		if (speakerName != null && !speakerName.isEmpty())
		{
			speakerLabel = new GLabel(speakerName, textX, SPEAKER_Y);
			speakerLabel.setFont("Monospaced-BOLD-18");
			speakerLabel.setColor(SPEAKER_COLOR);
			place(speakerLabel);
		}

		if (isSavePrompt)
		{
			// Save prompt: static question + two option labels
			GLabel question = new GLabel("Save your progress?", textX, TEXT_Y);
			question.setFont("Monospaced-PLAIN-18");
			question.setColor(TEXT_COLOR);
			place(question);

			option0Label = new GLabel("Yes", OPTION0_X, OPTION_Y);
			option0Label.setFont("Monospaced-BOLD-18");
			option0Label.setColor(OPTION_SELECTED);
			place(option0Label);

			option1Label = new GLabel("No", OPTION1_X, OPTION_Y);
			option1Label.setFont("Monospaced-BOLD-18");
			option1Label.setColor(OPTION_NORMAL);
			place(option1Label);
		}
		else if (isChoicePrompt)
		{
			textRows = new ArrayList<>();
			List<String> rows = wrapText(choiceQuestion, 74);
			for (int i = 0; i < rows.size(); i++)
			{
				GLabel lbl = new GLabel(rows.get(i), textX, TEXT_Y + i * LINE_HEIGHT);
				lbl.setFont("Monospaced-PLAIN-18");
				lbl.setColor(TEXT_COLOR);
				place(lbl);
				textRows.add(lbl);
			}

			int optionY = Math.max(
				CHOICE_OPTION_START_Y,
				TEXT_Y + rows.size() * LINE_HEIGHT + 2
			);

			option0Label = buildChoiceLabel(choiceOptions[0], CHOICE_OPTION_X, optionY);
			option1Label = buildChoiceLabel(choiceOptions[1], CHOICE_OPTION_X, optionY + CHOICE_OPTION_GAP_Y);
			place(option0Label);
			place(option1Label);
			if (choiceOptions.length > 2)
			{
				option2Label = buildChoiceLabel(choiceOptions[2], CHOICE_OPTION_X, optionY + CHOICE_OPTION_GAP_Y * 2);
				place(option2Label);
			}
			setSelectedOption(0);
		}
		else
		{
			// Normal dialogue: rows populated by update() on the first tick
			textRows = new ArrayList<>();

			// Continue hint — hidden until typing finishes
			continueHint = new GLabel("\u25BC [J]", PANEL_X + PANEL_W - 80, PANEL_Y + PANEL_H - 16);
			continueHint.setFont("SansSerif-PLAIN-13");
			continueHint.setColor(HINT_COLOR);
			continueHint.setVisible(false);
			place(continueHint);
		}
	}

	// =========================================================
	// WRAP TEXT
	// =========================================================

	/**
	 * Splits a line of text into sub-lines of at most maxCharsPerRow characters,
	 * breaking only at word boundaries where possible.
	 * Overrides GraphicsPane.wrapText with dialogue-specific behavior
	 * (handles words longer than the limit by force-breaking them).
	 *
	 * @param line           the full text to wrap
	 * @param maxCharsPerRow maximum characters allowed per line
	 * @return list of wrapped sub-lines
	 */
	@Override
	public List<String> wrapText(String line, int maxCharsPerRow)
	{
		List<String> result = new ArrayList<>();

		if (line == null || line.isEmpty())
		{
			result.add("");
			return result;
		}

		String[] words = line.split(" ");
		StringBuilder current = new StringBuilder();

		for (String word : words)
		{
			// Word alone is longer than the limit — force-break it
			if (word.length() > maxCharsPerRow)
			{
				if (current.length() > 0)
				{
					result.add(current.toString().trim());
					current = new StringBuilder();
				}
				result.add(word.substring(0, maxCharsPerRow));
				current.append(word.substring(maxCharsPerRow)).append(" ");
				continue;
			}

			if (current.length() + word.length() + 1 > maxCharsPerRow && current.length() > 0)
			{
				result.add(current.toString().trim());
				current = new StringBuilder();
			}
			current.append(word).append(" ");
		}

		if (current.length() > 0)
		{
			result.add(current.toString().trim());
		}
		if (result.isEmpty())
		{
			result.add("");
		}

		return result;
	}

	// =========================================================
	// HELPERS
	// =========================================================

	/**
	 * Returns true if the dialogue overlay is currently visible.
	 * Uses the contents list as the source of truth (same pattern as PauseModal).
	 */
	public boolean isOpen()
	{
		return !contents.isEmpty();
	}

	/**
	 * Returns the currently selected save-prompt option (0 = Yes, 1 = No).
	 * Only meaningful when opened via openSavePrompt().
	 */
	public int getSelectedOption()
	{
		return selectedOption;
	}

	/**
	 * No-op. Game is fixed 1280×720; no resize handling needed.
	 * Kept so MainApplication can call refreshLayout() safely on all panes.
	 */
	@Override
	public void refreshLayout()
	{
		// intentional no-op
	}

	// =========================================================
	// PRIVATE HELPERS
	// =========================================================

	/** Updates the highlight color on the Yes/No option labels. */
	private void setSelectedOption(int option)
	{
		selectedOption = option;
		if (option0Label != null)
		{
			option0Label.setColor(option == 0 ? OPTION_SELECTED : OPTION_NORMAL);
		}
		if (option1Label != null)
		{
			option1Label.setColor(option == 1 ? OPTION_SELECTED : OPTION_NORMAL);
		}
		if (option2Label != null)
		{
			option2Label.setColor(option == 2 ? OPTION_SELECTED : OPTION_NORMAL);
		}
	}

	private GLabel buildChoiceLabel(String text, double x, double y)
	{
		GLabel label = new GLabel(text, x, y);
		label.setFont("Monospaced-BOLD-18");
		label.setColor(OPTION_NORMAL);
		return label;
	}

	// =========================================================
	// LOCAL TEST  —  run this class directly to preview Dialogue
	// =========================================================

	/**
	 * Local test harness. Run this class from the IDE to preview the dialogue box.
	 *
	 * Controls:
	 *   F1         — open a multi-page dummy dialogue (with portrait)
	 *   F2         — open a dummy save prompt (Yes / No)
	 *   J or Space — advance / confirm
	 *   Up / Down  — move highlight in save prompt
	 *   Mouse click inside panel — also advances
	 */
	public static void main(String[] args)
	{
		class Sandbox extends MainApplication
		{
			private static final int TEST_W = 1280;
			private static final int TEST_H = 720;

			private Dialogue sandboxDialogue;
			private javax.swing.Timer tickTimer;

			@Override
			public void run()
			{
				setSize(TEST_W, TEST_H);
				setupInteractions();

				// White background
				GRect bg = new GRect(0, 0, TEST_W, TEST_H);
				bg.setFilled(true);
				bg.setFillColor(Color.WHITE);
				add(bg);

				// HUD (same as PauseModal local test)
				class Host extends GraphicsPane
				{
					Host() { mainScreen = Sandbox.this; }
				}
				Host host = new Host();
				HUDoverlay hud = new HUDoverlay();
				HUDoverlay.HudSnapshot snap =
					new HUDoverlay.HudSnapshot(3, false, false, 99, true, true, true, false);
				hud.showAll(host, snap);

				// Hint label so testers know the keys
				GLabel hint = new GLabel(
					"F1 = open dialogue   F2 = save prompt   J / Space = advance   Left/Right = save option",
					20, TEST_H - 12);
				hint.setFont("SansSerif-PLAIN-13");
				hint.setColor(Color.DARK_GRAY);
				add(hint);

				sandboxDialogue = new Dialogue(this);

				// Timer drives the typewriter effect (~60 fps)
				tickTimer = new javax.swing.Timer(16, e -> sandboxDialogue.update(0.016));
				tickTimer.start();

				requestFocus();
			}

			@Override
			public void keyPressed(java.awt.event.KeyEvent e)
			{
				// Forward all input to dialogue while it is open
				if (sandboxDialogue != null && sandboxDialogue.isOpen())
				{
					sandboxDialogue.keyPressed(e);
					return;
				}

				int key = e.getKeyCode();

				if (key == java.awt.event.KeyEvent.VK_F1)
				{
					sandboxDialogue.open(
						new String[]{
							"Caelomund: Hoof there! You look like someone who can hold a line. The market is about to get very lively — keep your sword hand ready.",
							"There are things moving in the thicket. Things with too many teeth. I cannot tell you everything right now, but stay close and listen when I signal.",
							"Take this mark. The gate will know you now. When the dust settles, find me at the north path. Ready?"
						},
						"Caelomund",
						true,
						() -> System.out.println("[TEST] Dialogue finished.")
					);
				}
				else if (key == java.awt.event.KeyEvent.VK_F2)
				{
					sandboxDialogue.openSavePrompt(
						() -> System.out.println("[TEST] Save choice: " +
							(sandboxDialogue.getSelectedOption() == 0 ? "Yes" : "No"))
					);
				}
			}

			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				if (sandboxDialogue != null && sandboxDialogue.isOpen())
				{
					sandboxDialogue.mouseClicked(e);
				}
			}
		}
		new Sandbox().start();
	}
}
