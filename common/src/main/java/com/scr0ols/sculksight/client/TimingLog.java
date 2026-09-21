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

final class TimingLog {

	static final String FILE_NAME = "sculksight-timings.txt";

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

	private static final DateTimeFormatter STARTED =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

	private static boolean failed;

	private static boolean headerWritten;

	private TimingLog() {
	}

	static String format(LocalTime time, String message) {
		return TIME.format(time) + "  " + message + System.lineSeparator();
	}

	static String header(LocalDateTime started) {
		return System.lineSeparator() + "=== Sculk Sight timing run, started " + STARTED.format(started)
				+ " ===" + System.lineSeparator();
	}

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
