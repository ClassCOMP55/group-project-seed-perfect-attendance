/*
Roberto: GameSFX — short, one-shot sound effects (sword swing, coin pickup, etc.)
Who RIGs it: MainApplication calls GameSFX.init() once at startup.
             Individual game classes call GameSFX.play(SFX.xxx) when the moment fires.
Does not own music tracks — see GameMusic for background looping audio.
Does not own volume settings — reads from GameSettings.getSfxVolumePercent().
*/

/*
================================
PLAN OF ACTION
================================

CLASS ROLE
- GameSFX plays short, non-looping sounds (SFX) on top of whatever music is currently running.
- Music and SFX are fully independent; playing an SFX never pauses or interrupts GameMusic.
- Volume is read from GameSettings.getSfxVolumePercent() at the moment each sound plays.
- If init() was never called, or a clip failed to load, play() does nothing (silent fail).

OWNERSHIP + LIFECYCLE
- MainApplication calls GameSFX.init() once at startup, after SettingsIO.loadOrCreate().
- After that, any class can call GameSFX.play(SFX.xxx) at any time — no setup needed at the call site.
- No teardown is needed; clips are small and held in memory for the full session.

CLIP POOLS (why they exist)
- Each entry in the SFX enum is backed by a small array of pre-loaded clips (a "pool").
- A pool of size N means the same sound can overlap with itself N times simultaneously.
- Example: SWORD_SWING with pool size 3 lets you hear 3 swing sounds at once if you attack fast.
- Different sounds (e.g. SWORD_SWING and COIN_PICKUP) always play independently; pools only
  matter for the same sound firing more than once before its previous play finishes.
- Pool sizes are constants below — easy to tune without touching any logic.

ADDING A NEW SOUND (checklist)
1. Add a new entry to the SFX enum below.
2. Add a matching loadPool(...) line inside init().
3. Drop the matching .wav file into assets/audio/sfx/ (or classpath /audio/sfx/).
4. Call GameSFX.play(SFX.YOUR_NEW_SOUND) from wherever it should fire.

FILE FORMAT
- .wav only (same as GameMusic). No .mp3 or .ogg — Java's built-in audio only reads .wav.
- Keep SFX clips short (under ~2 seconds). Longer audio belongs in GameMusic as a looping track.

*/

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.Map;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.BooleanControl;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * One-shot sound effects (SFX) for gameplay events.
 * Background music is handled separately by {@link GameMusic}.
 *
 * <p>Usage pattern:</p>
 * <pre>
 *   // Once, at startup (MainApplication):
 *   GameSFX.init();
 *
 *   // Anywhere a sound should fire:
 *   GameSFX.play(GameSFX.SFX.SWORD_SWING);
 * </pre>
 *
 * <p>See the plan block at the top of this file for how to add new sounds.</p>
 */
public final class GameSFX
{

  // =========================================================
  // SFX CATALOG
  // Add new sounds here and in init(). One entry per sound.
  // =========================================================

  /**
   * Every named sound effect in the game.
   * To add a new one: add an entry here, then add a loadPool line in {@link #init()}.
   */
  public enum SFX
  {
    /** Player swings their sword. Rapid — pool of 3. */
    SWORD_SWING,

    /** Player takes damage. Infrequent — pool of 1. */
    PLAYER_HURT,

    /** Player picks up a coin. Moderate frequency — pool of 2. */
    COIN_PICKUP,

    /** Player cuts grass. Rapid — pool of 3. */
    GRASS_CUT,

    /** Pause menu opens. Infrequent — pool of 1. */
    PAUSE_OPEN,

    /** One character "tick" as dialogue text types onto screen. Rapid — pool of 4. */
    DIALOGUE_TICK,

    /** Enemy melee attack lands. Moderate — pool of 2. */
    ENEMY_ATTACK,

    /** Player uses a healing item (e.g. HealingBread). Infrequent — pool of 1. */
    ITEM_USE,

    /** Coin or item lands in a chest / chest opens. Infrequent — pool of 1. */
    CHEST_OPEN,

    /** Dialogue box appears on screen. Infrequent — pool of 1. */
    DIALOGUE_OPEN,

    /** Dialogue box is dismissed. Infrequent — pool of 1. */
    DIALOGUE_CLOSE,

    /** A push block successfully slides one tile. Moderate — pool of 2. */
    BLOCK_MOVED,

    /** Player confirms a save at a save point. Infrequent — pool of 1. */
    SAVE_POINT,

    /** Calumund's per-line voice blip. Infrequent — pool of 1. */
    GOAT_SFX,
  }

  // =========================================================
  // POOL SIZE CONSTANTS
  // "Pool size" = how many times this sound can overlap with itself.
  // Tune these numbers without touching any logic.
  // =========================================================

  /** Rapid-fire sounds that can stack 3 times (e.g. fast sword combos, grass cutting). */
  private static final int POOL_RAPID   = 3;

  /** Dialogue tick fires very fast — needs a slightly larger pool. */
  private static final int POOL_TICK    = 4;

  /** Moderate sounds that might fire twice close together. */
  private static final int POOL_MEDIUM  = 2;

  /** Infrequent sounds that rarely overlap with themselves. */
  private static final int POOL_SMALL   = 1;

  // =========================================================
  // INTERNAL STATE
  // =========================================================

  /** Clip arrays, one per SFX entry. Null if init() has not been called or load failed. */
  private static Map<SFX, Clip[]> pools;

  /** Round-robin index per SFX: which clip in the pool to use next. */
  private static Map<SFX, int[]>  poolCursors;

  /** Set to true by init() so play() knows it is safe to proceed. */
  private static boolean initialized = false;

  private GameSFX() {}

  // =========================================================
  // PUBLIC API
  // =========================================================

  /**
   * Pre-loads all SFX clips into memory.
   * Call <b>once</b> from {@code MainApplication} after {@code SettingsIO.loadOrCreate()}.
   *
   * <p>If a file is missing, that sound is silently skipped — the rest still load.
   * Calling init() a second time reloads everything (safe but unnecessary).</p>
   *
   * <p>TO RIG: add one {@code loadPool} line here for each new {@link SFX} entry.</p>
   */
  public static void init()
  {
    pools       = new EnumMap<>(SFX.class);
    poolCursors = new EnumMap<>(SFX.class);

    pools.put(SFX.SWORD_SWING,   loadPool("sword-swing.wav",   POOL_RAPID));
    pools.put(SFX.PLAYER_HURT,   loadPool("player-hurt.wav",   POOL_SMALL));
    pools.put(SFX.COIN_PICKUP,   loadPool("coin-pickup.wav",   POOL_MEDIUM));
    pools.put(SFX.GRASS_CUT,     loadPool("grass-cut.wav",     POOL_RAPID));
    pools.put(SFX.PAUSE_OPEN,    loadPool("pause-open.wav",    POOL_SMALL));
    pools.put(SFX.DIALOGUE_TICK, loadPool("dialogue-tick.wav", POOL_TICK));
    pools.put(SFX.ENEMY_ATTACK,  loadPool("enemy-attack.wav",  POOL_MEDIUM));
    pools.put(SFX.ITEM_USE,      loadPool("item-use.wav",      POOL_SMALL));
    pools.put(SFX.CHEST_OPEN,    loadPool("chest_open.wav",    POOL_SMALL));
    pools.put(SFX.DIALOGUE_OPEN, loadPool("dialogue_open.wav", POOL_SMALL));
    pools.put(SFX.DIALOGUE_CLOSE,loadPool("dialogue_close.wav",POOL_SMALL));
    pools.put(SFX.BLOCK_MOVED,   loadPool("block_moved.wav",   POOL_MEDIUM));
    pools.put(SFX.SAVE_POINT,    loadPool("save_point.wav",    POOL_SMALL));
    pools.put(SFX.GOAT_SFX,      loadPool("goat_sfx.wav",      POOL_SMALL));

    // Each cursor starts at 0 — it advances each time that sound plays.
    for (SFX sfx : SFX.values()) {
      poolCursors.put(sfx, new int[]{ 0 });
    }

    initialized = true;
  }

  /**
   * Plays the given sound effect once, at the current SFX volume.
   * Picks the next free clip from that sound's pool (round-robin).
   * Safe to call before init() or if the clip failed to load — does nothing.
   *
   * @param sfx the sound to play; must not be null
   */
  public static void play(SFX sfx)
  {
    if (!initialized || pools == null) return;

    Clip[] pool = pools.get(sfx);
    if (pool == null || pool.length == 0) return;

    // Round-robin: pick the next clip slot, then advance the index.
    int[] cursor = poolCursors.get(sfx);
    int idx = cursor[0];
    cursor[0] = (idx + 1) % pool.length;

    Clip clip = pool[idx];
    clip.stop();
    clip.setFramePosition(0); // rewind to the beginning
    applyVolume(clip);
    clip.start();
  }

  /**
   * Re-applies the SFX volume from {@link GameSettings#getSfxVolumePercent()} to
   * all clips in all pools. Call this after the SFX volume slider changes
   * (same pattern as {@link GameMusic#refreshVolume()}).
   *
   * <p>TO RIG: {@code PauseModal.persistPauseAudioSettings()} and
   * {@code SettingsPane} should call this alongside {@code GameMusic.refreshVolume()}.</p>
   */
  public static void refreshVolume()
  {
    if (!initialized || pools == null) return;

    for (Clip[] pool : pools.values()) {
      for (Clip clip : pool) {
        applyVolume(clip);
      }
    }
  }

  // =========================================================
  // PRIVATE HELPERS
  // =========================================================

  /**
   * Loads {@code poolSize} independent copies of one .wav file and returns them as an array.
   * Returns an empty array if the file cannot be found or fails to open.
   *
   * @param fileName the .wav file name only (e.g. {@code "sword-swing.wav"});
   *                 looked up via classpath {@code /audio/sfx/} then {@code assets/audio/sfx/}
   * @param poolSize number of clip copies to create
   * @return array of ready-to-play {@link Clip} objects (may be length 0 on failure)
   */
  private static Clip[] loadPool(String fileName, int poolSize)
  {
    Clip[] clips = new Clip[poolSize];
    int loaded = 0;

    for (int i = 0; i < poolSize; i++) {
      InputStream raw = openSFXStream(fileName);
      if (raw == null) {
        // Only log once — if the first open fails, the file simply isn't there yet.
        if (i == 0) {
          System.err.println("GameSFX: could not find audio/sfx/" + fileName + " — skipped");
        }
        break;
      }
      try (InputStream in = new BufferedInputStream(raw)) {
        AudioInputStream ais = AudioSystem.getAudioInputStream(in);
        Clip clip = AudioSystem.getClip();
        clip.open(ais); // loads the audio data into memory
        applyVolume(clip);
        clips[loaded++] = clip;
      } catch (IOException | UnsupportedAudioFileException | LineUnavailableException e) {
        System.err.println("GameSFX: failed to load copy " + i + " of " + fileName);
        break;
      }
    }

    // Return only the clips that actually loaded (may be fewer than poolSize).
    if (loaded == poolSize) return clips;
    Clip[] partial = new Clip[loaded];
    System.arraycopy(clips, 0, partial, 0, loaded);
    return partial;
  }

  /**
   * Applies the current SFX volume from {@link GameSettings} to one clip.
   * Mirrors the logic in {@link GameMusic} (tries MUTE → VOLUME → MASTER_GAIN).
   *
   * @param clip the clip to adjust; must not be null
   */
  private static void applyVolume(Clip clip)
  {
    int p = GameSettings.getSfxVolumePercent(); // 0-100

    // Try muting the clip directly (fastest path when volume is 0).
    try {
      BooleanControl mute = (BooleanControl) clip.getControl(BooleanControl.Type.MUTE);
      if (p <= 0) { mute.setValue(true);  return; }
      mute.setValue(false);
    } catch (IllegalArgumentException ex) {
      // this clip has no mute control — that's fine, keep going
    }

    float t = p / 100f; // convert percent to 0.0–1.0

    // Try a simple linear volume control.
    try {
      FloatControl vol = (FloatControl) clip.getControl(FloatControl.Type.VOLUME);
      vol.setValue(Math.max(0f, Math.min(1f, t)));
      return;
    } catch (IllegalArgumentException ex) {
      // no VOLUME control on this system — fall through to MASTER_GAIN
    }

    // Fall back to decibel gain (most common on Java's audio layer).
    try {
      FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
      float min = gain.getMinimum();
      float max = gain.getMaximum();
      if (p <= 0) { gain.setValue(min); return; }
      float db = max + 20f * (float) Math.log10(Math.max(t, 1e-4f));
      gain.setValue(Math.max(min, Math.min(max, db)));
    } catch (IllegalArgumentException ex) {
      // no gain control either — audio system can't adjust volume; ignore
    }
  }

  /**
   * Finds a .wav file for an SFX, first on the classpath ({@code /audio/sfx/fileName}),
   * then on disk ({@code assets/audio/sfx/fileName}).
   * Returns null if neither location has the file.
   *
   * @param fileName the file name only (e.g. {@code "coin-pickup.wav"})
   * @return an open {@link InputStream}, or null if not found
   */
  private static InputStream openSFXStream(String fileName)
  {
    // Check the classpath first (works when the project is built/packaged as a jar).
    InputStream fromClasspath = GameSFX.class.getResourceAsStream("/audio/ui sfx/" + fileName);
    if (fromClasspath != null) return fromClasspath;

    // Fall back to the local assets folder (useful during development).
    Path p = Paths.get("assets", "audio", "ui sfx", fileName);
    try {
      if (Files.isRegularFile(p)) return Files.newInputStream(p);
    } catch (IOException e) {
      return null;
    }
    return null;
  }
}
