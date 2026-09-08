package com.scr0ols.sculksight.config;

import org.jspecify.annotations.Nullable;

import com.scr0ols.sculksight.SculkSight;
import com.scr0ols.sculksight.client.ClientPlatform;

/**
 * The one {@link ConfigStore} a running game has, and the only thing the rest of the mod talks to.
 *
 * <p>A holder rather than a singleton the store itself enforces, for the reason
 * {@code ClientPlatform} gives about its own split: the store is an ordinary object that takes a
 * path, which is what lets the JUnit suite drive it against a temporary directory, and this class
 * is the separate, game-only question of which path that is and when it is read. That division is
 * also why this class alone in the {@code config} package is untestable in a plain JVM: it names
 * {@link SculkSight}, whose logger sits beside a Minecraft type.
 *
 * <p><b>Initialised from each loader's own entrypoint, after {@code ClientPlatform.set} and before
 * anything reads a setting.</b> The renderer reads {@link #get()} when it builds a mesh, which is
 * a keypress at the earliest, so mod initialisation is comfortably early enough - the same
 * ordering argument {@code ClientPlatform} already makes for {@code TimingGate}. Before
 * {@link #load} has run, {@link #get()} answers with the shipped defaults rather than throwing:
 * every value here has a working default by construction, and a settings read is never worth a
 * crash.
 *
 * <p>Problems - an unreadable file, a malformed one, a value repaired on the way in - go to the
 * mod's own logger rather than to the player's chat. None of them stops the game, and a settings
 * file is something a player looks at with the log open, not mid-session.
 */
public final class ClientConfig {

	private static @Nullable ConfigStore store;

	private ClientConfig() {
	}

	/**
	 * Resolves the file under the loader's own config directory and reads it.
	 *
	 * <p>Called once per game start. Calling it again re-reads from disk, which is harmless but
	 * has no caller: nothing outside this mod edits the file while the game is running.
	 */
	public static void load() {
		ConfigStore created = new ConfigStore(
				ClientPlatform.get().configDir().resolve(ConfigStore.FILE_NAME),
				ClientConfig::report);

		created.load();

		store = created;
	}

	/** The current settings. Usable before {@link #load}, where it is the shipped defaults. */
	public static SculkSightConfig get() {
		ConfigStore current = store;

		return current == null ? SculkSightConfig.defaults() : current.get();
	}

	/**
	 * Replaces the settings and writes them out. The config screen's save button is the caller.
	 *
	 * <p>Does nothing but log if {@link #load} has not run, which cannot happen from a screen the
	 * game opened but would otherwise write a file next to the working directory by accident.
	 */
	public static void set(SculkSightConfig config) {
		ConfigStore current = store;

		if (current == null) {
			report("a setting was changed before the configuration file was located; not saved");
			return;
		}

		current.set(config);
	}

	private static void report(String problem) {
		SculkSight.LOGGER.warn("[sculksight] config: {}", problem);
	}
}
