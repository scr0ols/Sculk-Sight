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

/** One configuration file, and the current value read from or written to it. */
public final class ConfigStore {

	/** The file name, under whichever directory the loader calls its config directory. */
	public static final String FILE_NAME = "sculksight.json";

	private static final String TEMPORARY_SUFFIX = ".tmp";

	private final Path file;

	private final Consumer<String> problems;

	private SculkSightConfig current = SculkSightConfig.defaults();

	/** Creates a store for the given file, reporting fall-backs and repairs to {@code problems}. */
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

	/** Replaces the current configuration and writes it out. */
	public void set(SculkSightConfig config) {
		current = config;
		save();
	}

	/** Reads the file, falling back to the defaults if it is absent, unreadable or malformed. */
	public void load() {
		String text;

		try {
			text = Files.readString(file, StandardCharsets.UTF_8);
		} catch (NoSuchFileException e) {
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

			writeDurably(temporary, ConfigCodec.write(current));

			replace(temporary, file);
		} catch (IOException e) {
			problems.accept("could not write " + file + " (" + e
					+ "); this session's settings will not survive a restart");

			discard(temporary);
		}
	}

	private static void writeDurably(Path target, String text) throws IOException {
		ByteBuffer bytes = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));

		try (FileChannel channel = FileChannel.open(target, StandardOpenOption.CREATE,
				StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

			while (bytes.hasRemaining()) {
				channel.write(bytes);
			}

			channel.force(true);
		}
	}

	private static void discard(Path temporary) {
		try {
			Files.deleteIfExists(temporary);
		} catch (IOException ignored) {
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
