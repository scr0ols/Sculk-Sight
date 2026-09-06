package com.scr0ols.sculksight.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/**
 * One configuration file, and the current value read from or written to it.
 *
 * <p>This is the whole of PLAN.md section 4's "own JSON persistence layer in common", and it sits
 * beneath Cloth Config rather than inside it: Cloth draws a screen and hands back values, and this
 * class is what those values are read from at startup and written to on save. Nothing here names a
 * Minecraft class or a loader API, so the JUnit suite exercises the persistence layer directly
 * against a temporary directory - the reason the layer is "not wasted work" in the plan's own
 * words rather than a wrapper around a library's own persistence.
 *
 * <p><b>Nothing is thrown at the caller and nothing is swallowed either.</b> A missing file is the
 * ordinary first-run case and produces the defaults. A file that cannot be read or parsed produces
 * the defaults too, because a mod that refuses to start over a damaged settings file is worse than
 * one that starts with the settings it shipped with - but every such fall-back, and every value
 * repaired on the way in, is reported to the {@code problems} consumer the caller supplied, so the
 * player sees it in the log rather than wondering why their slider moved. The damaged file itself
 * is left exactly where it is: it is the only copy of what the player wrote, and this class does
 * not overwrite it until the player saves something new.
 *
 * <p><b>Saving is atomic where the filesystem allows it.</b> The text goes to a sibling temporary
 * file first and is then moved over the real one, so a crash mid-write leaves either the old
 * complete file or the new complete file, never a truncated one. A filesystem that cannot do an
 * atomic move falls back to an ordinary replace, which is the same risk every other mod runs.
 *
 * <p>Not thread-safe, and does not need to be: every caller is the client thread, which is where
 * both mod initialisation and a screen's own save button run.
 */
public final class ConfigStore {

	/** The file name, under whichever directory the loader calls its config directory. */
	public static final String FILE_NAME = "sculksight.json";

	private static final String TEMPORARY_SUFFIX = ".tmp";

	private final Path file;

	private final Consumer<String> problems;

	private SculkSightConfig current = SculkSightConfig.defaults();

	/**
	 * @param file the configuration file, which need not exist
	 * @param problems told about every fall-back and every repaired value, one message at a time
	 */
	public ConfigStore(Path file, Consumer<String> problems) {
		this.file = file;
		this.problems = problems;
	}

	/** The file this store reads and writes. */
	public Path file() {
		return file;
	}

	/** The configuration as it currently stands. Never null; the defaults until {@link #load}. */
	public SculkSightConfig get() {
		return current;
	}

	/**
	 * Replaces the current configuration and writes it out.
	 *
	 * <p>The two happen together because there is no case in this mod where a setting should change
	 * for this session but not the next one: the screen's save button is the only caller.
	 */
	public void set(SculkSightConfig config) {
		current = config;
		save();
	}

	/**
	 * Reads the file, falling back to the defaults if it is absent, unreadable or malformed.
	 *
	 * <p>Called once per game start, from each loader's own entrypoint.
	 */
	public void load() {
		String text;

		try {
			text = Files.readString(file, StandardCharsets.UTF_8);
		} catch (NoSuchFileException e) {
			// The ordinary first run. Not a problem, and not reported as one.
			current = SculkSightConfig.defaults();
			return;
		} catch (IOException e) {
			problems.accept("could not read " + file + " (" + e + "); using the default settings");
			current = SculkSightConfig.defaults();
			return;
		}

		try {
			current = ConfigCodec.read(text, problems);
		} catch (JsonParseException e) {
			problems.accept(file + " is not a valid configuration file (" + e.getMessage()
					+ "); using the default settings, and leaving the file alone");
			current = SculkSightConfig.defaults();
		}
	}

	/** Writes the current configuration, creating the directory above it if it is missing. */
	public void save() {
		Path temporary = file.resolveSibling(file.getFileName() + TEMPORARY_SUFFIX);

		try {
			Path parent = file.getParent();

			if (parent != null) {
				Files.createDirectories(parent);
			}

			Files.writeString(temporary, ConfigCodec.write(current), StandardCharsets.UTF_8);

			replace(temporary, file);
		} catch (IOException e) {
			problems.accept("could not write " + file + " (" + e
					+ "); this session's settings will not survive a restart");
		}
	}

	private static void replace(Path from, Path to) throws IOException {
		try {
			Files.move(from, to, StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
