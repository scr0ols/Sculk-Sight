package com.scr0ols.sculksight.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.scr0ols.sculksight.SculkSight;

/**
 * Mirrors everything the renderer says into a text file beside the game's own logs.
 * DECISIONS.md ADR-031's 2026-09-02 addendum.
 *
 * <p><b>Why a file at all, when the same lines already go to chat and to {@code latest.log}.</b>
 * A chat line cannot be selected or copied, and {@code latest.log} interleaves these lines with
 * everything else the client writes. The numbers this instrument exists to produce have to leave
 * the machine they were measured on, and a file that holds them and nothing else is the cheapest
 * way for that to happen without anyone retyping a figure.
 *
 * <p><b>Same gate as the rest of the instrument</b>, so an ordinary installation writes no file
 * unless it was launched to do so. See {@link TimingGate}.
 *
 * <p><b>One append per line, and no open handle.</b> Lines arrive once per solve and once per
 * shell, never per frame, so the cost of opening the file each time is irrelevant and what is
 * bought with it is worth having: the file is complete and readable while the game is still
 * running, and a crash cannot lose a buffered line. Nothing has to be closed on shutdown.
 *
 * <p><b>An I/O failure must not cost a frame.</b> The first failure is logged and switches the
 * mirror off for the rest of the run, so a read-only directory produces one error line rather than
 * one per solve.
 *
 * <p><b>Client thread only</b>, which every caller of {@code ShellRenderer.say} is under ADR-026,
 * so the two flags below need no synchronisation. If the producer ever moves to a worker, this
 * becomes one more thing that crosses a thread boundary, alongside the stats and the timings
 * ADR-026 already names.
 *
 * <p><b>Moved here from {@code fabric}'s client source set, DECISIONS.md ADR-043's own
 * "what did not move" consequence.</b> {@link ClientPlatform} replaces the direct
 * {@code FabricLoader.getInstance().getGameDir()} read; everything else is unchanged.
 */
final class TimingLog {

	/** In the game directory, which is the instance folder for an install and {@code run/} in dev. */
	static final String FILE_NAME = "sculksight-timings.txt";

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

	private static final DateTimeFormatter STARTED =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

	private static boolean failed;

	private static boolean headerWritten;

	private TimingLog() {
	}

	/**
	 * One line as it is written. Pure, and separate from the writing so that what the file says can
	 * be tested without a game (RESEARCH-LOG.md E7 is why that separation is worth making).
	 */
	static String format(LocalTime time, String message) {
		return TIME.format(time) + "  " + message + System.lineSeparator();
	}

	/** The line that opens each run's block, so two launches are never read as one session. */
	static String header(LocalDateTime started) {
		return System.lineSeparator() + "=== Sculk Sight timing run, started " + STARTED.format(started)
				+ " ===" + System.lineSeparator();
	}

	/** Appends one message. Does nothing when the instrument is off or has already failed. */
	static void append(String message) {
		if (!TimingGate.ENABLED || failed) {
			return;
		}

		try {
			Path file = ClientPlatform.get().gameDir().resolve(FILE_NAME);

			if (!headerWritten) {
				write(file, header(LocalDateTime.now()));
				headerWritten = true;
				SculkSight.LOGGER.info("[sculksight] timings are being written to {}", file);
			}

			write(file, format(LocalTime.now(), message));
		} catch (IOException e) {
			failed = true;
			SculkSight.LOGGER.error("[sculksight] could not write {}; timings stay in chat and in the "
					+ "game log for the rest of this run.", FILE_NAME, e);
		}
	}

	private static void write(Path file, String text) throws IOException {
		Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
				StandardOpenOption.WRITE, StandardOpenOption.APPEND);
	}
}
