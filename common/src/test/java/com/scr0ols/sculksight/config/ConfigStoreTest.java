package com.scr0ols.sculksight.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the persistence layer itself, against a real directory.
 *
 * <p>This is what PLAN.md section 4 buys by putting the layer in common rather than leaning on
 * Cloth: the whole of saving and loading is exercised with no game, no loader and no Cloth Config.
 */
class ConfigStoreTest {

	private final List<String> problems = new ArrayList<>();

	@Test
	void aMissingFileIsTheDefaultsAndIsNotAProblem(@TempDir Path directory) {
		ConfigStore store = store(directory);

		store.load();

		assertEquals(SculkSightConfig.defaults(), store.get());
		assertEquals(List.of(), problems, "a first run is not something to warn about");
	}

	@Test
	void aSettingSurvivesBeingWrittenAndReadBack(@TempDir Path directory) {
		store(directory).set(new SculkSightConfig(71));

		ConfigStore reopened = store(directory);
		reopened.load();

		assertEquals(71, reopened.get().shellOpacityPercent());
		assertEquals(List.of(), problems);
	}

	@Test
	void savingCreatesTheDirectoryAboveTheFile(@TempDir Path directory) {
		Path nested = directory.resolve("config").resolve("deeper");

		new ConfigStore(nested.resolve(ConfigStore.FILE_NAME), problems::add)
				.set(new SculkSightConfig(40));

		assertTrue(Files.exists(nested.resolve(ConfigStore.FILE_NAME)));
		assertEquals(List.of(), problems);
	}

	@Test
	void savingLeavesNoTemporaryFileBehind(@TempDir Path directory) throws IOException {
		store(directory).set(new SculkSightConfig(40));

		try (Stream<Path> entries = Files.list(directory)) {
			assertEquals(List.of(ConfigStore.FILE_NAME),
					entries.map(path -> path.getFileName().toString()).toList());
		}
	}

	@Test
	void aMalformedFileFallsBackToTheDefaultsAndSaysSo(@TempDir Path directory) throws IOException {
		write(directory, "{ this is not json");

		ConfigStore store = store(directory);
		store.load();

		assertEquals(SculkSightConfig.defaults(), store.get());
		assertEquals(1, problems.size());
		assertTrue(problems.getFirst().contains("not a valid configuration file"),
				problems.getFirst());
	}

	@Test
	void aMalformedFileIsLeftOnDisk(@TempDir Path directory) throws IOException {
		write(directory, "{ this is not json");

		store(directory).load();

		assertEquals("{ this is not json",
				Files.readString(directory.resolve(ConfigStore.FILE_NAME), StandardCharsets.UTF_8));
	}

	@Test
	void aRepairedValueIsReportedAndThenPersistedOnTheNextSave(@TempDir Path directory)
			throws IOException {
		write(directory, "{\"shellOpacityPercent\": 400}");

		ConfigStore store = store(directory);
		store.load();

		assertEquals(SculkSightConfig.MAX_SHELL_OPACITY_PERCENT, store.get().shellOpacityPercent());
		assertEquals(1, problems.size());

		store.save();

		assertEquals("{\n\t\"shellOpacityPercent\": 100\n}\n",
				Files.readString(directory.resolve(ConfigStore.FILE_NAME), StandardCharsets.UTF_8));
	}

	@Test
	void theSettingsAreTheDefaultsBeforeAnythingIsLoaded(@TempDir Path directory) {
		assertEquals(SculkSightConfig.defaults(), store(directory).get());
	}

	@Test
	void aFileWithAWrongTypedValueDoesNotStopTheMod(@TempDir Path directory) throws IOException {
		write(directory, "{\"shellOpacityPercent\": \"a quarter\"}");

		ConfigStore store = store(directory);
		store.load();

		assertEquals(SculkSightConfig.defaults(), store.get());
		assertFalse(problems.isEmpty());
	}

	private ConfigStore store(Path directory) {
		return new ConfigStore(directory.resolve(ConfigStore.FILE_NAME), problems::add);
	}

	private static void write(Path directory, String text) throws IOException {
		Files.writeString(directory.resolve(ConfigStore.FILE_NAME), text, StandardCharsets.UTF_8);
	}
}
