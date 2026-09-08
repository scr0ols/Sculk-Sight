package com.scr0ols.sculksight.config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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
 * file first, is flushed to the disk rather than only to the operating system's cache, and is then
 * moved over the real one, so a crash mid-write leaves either the old complete file or the new
 * complete file, never a truncated one. A filesystem that cannot do an atomic move falls back to an
 * ordinary replace, which is the same risk every other mod runs.
 *
 * <p>That sentence used to be slightly stronger than the code, which did not flush and therefore
 * held only against a process crash and not against a power loss; {@code OPEN-QUESTIONS.md}
 * section 22 recorded the gap and {@code DECISIONS.md} ADR-055's 2026-09-07 second addendum closed
 * it by moving the code rather than the sentence. What the flush covers and what it does not is in
 * {@code writeDurably}'s own javadoc, and the honest short version is that a lost <i>rename</i> is
 * still possible and still lands inside the promise, because what it leaves is the old file whole.
 *
 * <p>A save that fails part way through leaves no temporary file behind either - also section 22.
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

	/**
	 * Writes the current configuration, creating the directory above it if it is missing.
	 *
	 * <p><b>A failed save leaves nothing behind.</b> The write can succeed and the move then fail -
	 * the destination being a directory, or a permission that applies to the real file and not to a
	 * sibling - and the temporary file was left on disk in that case, sitting beside the settings
	 * as a {@code sculksight.json.tmp} nobody would delete and nothing would ever read.
	 * {@code OPEN-QUESTIONS.md} section 22 is the finding. Cleaning up on the failure path costs
	 * one {@code deleteIfExists} and keeps the directory saying only what is true: the file that is
	 * there is the settings, and there is no other. The player still hears about the failure, which
	 * is the part that matters, and their previous settings file is untouched.
	 */
	public void save() {
		Path temporary = file.resolveSibling(file.getFileName() + TEMPORARY_SUFFIX);

		try {
			Path parent = file.getParent();

			if (parent != null) {
				Files.createDirectories(parent);
			}

			writeDurably(temporary, ConfigCodec.write(current));

			replace(temporary, file);
		} catch (IOException e) {
			problems.accept("could not write " + file + " (" + e
					+ "); this session's settings will not survive a restart");

			discard(temporary);
		}
	}

	/**
	 * Writes the text and does not return until the filesystem says the bytes are down.
	 *
	 * <p><b>This is {@code fsync} and it is what makes the class javadoc's atomicity sentence
	 * true.</b> Without it {@code Files.writeString} returns once the data is in the operating
	 * system's page cache, and the rename that follows can reach the disk before that data does -
	 * so a power loss between the two leaves the settings file renamed into place over content that
	 * was never written, which is the truncated file that sentence promises cannot happen. A
	 * process crash was never at risk, because the kernel owns the cache and outlives the process;
	 * only a power loss or a kernel panic was, and that is the gap {@code DECISIONS.md} ADR-055's
	 * 2026-09-07 second addendum closes.
	 *
	 * <p><b>What it does not promise, said plainly rather than left to be assumed.</b> The
	 * <i>rename</i> is not synced, because Java has no portable way to sync a directory - opening
	 * one as a {@link FileChannel} works on some platforms and throws on others. So a power loss
	 * immediately after the move can still lose the move, and the player then finds their previous
	 * settings file, complete and readable. That is inside what the sentence promises - "either the
	 * old complete file or the new complete file" - rather than outside it, which is why the file
	 * sync alone is enough here and a directory sync is not being reached for. Beyond that,
	 * {@code force} can only ask; a drive whose own write cache lies about completion defeats every
	 * caller of it equally, and no code in this class can do anything about that.
	 */
	private static void writeDurably(Path target, String text) throws IOException {
		ByteBuffer bytes = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));

		try (FileChannel channel = FileChannel.open(target, StandardOpenOption.CREATE,
				StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

			// write is not obliged to take the whole buffer in one call, and a partial write here
			// would be exactly the truncation this method exists to rule out.
			while (bytes.hasRemaining()) {
				channel.write(bytes);
			}

			// true rather than false: the file length changed, so the metadata has to go down with
			// the content for the content to be findable.
			channel.force(true);
		}
	}

	/**
	 * Removes a temporary file a failed save left behind, and says nothing if it cannot.
	 *
	 * <p>The caller has already reported the failure that got here, and a second message about the
	 * cleanup of the first would tell the player nothing they can act on - the settings did not
	 * save either way. A stray temporary file is untidy, not harmful: nothing reads it, and the
	 * next successful save overwrites it.
	 */
	private static void discard(Path temporary) {
		try {
			Files.deleteIfExists(temporary);
		} catch (IOException ignored) {
			// Deliberately swallowed; see above for why this is not a silent error.
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
