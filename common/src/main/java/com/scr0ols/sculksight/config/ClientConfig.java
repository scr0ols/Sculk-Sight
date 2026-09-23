package com.scr0ols.sculksight.config;

import org.jspecify.annotations.Nullable;

import com.scr0ols.sculksight.SculkSight;
import com.scr0ols.sculksight.client.ClientPlatform;

/** Holds the single {@link ConfigStore} a running game uses. */
public final class ClientConfig {

	private static @Nullable ConfigStore store;

	private ClientConfig() {
	}

	/** Resolves the config file under the loader's config directory and reads it. */
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

	/** Replaces the settings and writes them out. */
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
