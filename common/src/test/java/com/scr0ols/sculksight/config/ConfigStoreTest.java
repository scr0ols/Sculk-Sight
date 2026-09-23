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

/** Tests for the persistence layer itself, against a real directory. */
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
		store(directory).set(new SculkSightConfig(71, SculkSightConfig.DEFAULT_RENDER_POLICY));

		ConfigStore reopened = store(directory);
		reopened.load();

		assertEquals(71, reopened.get().shellOpacityPercent());
		assertEquals(List.of(), problems);
	}

	@Test
	void trackedSensorNamesAndTogglesSurviveBeingWrittenAndReadBack(@TempDir Path directory) {
		List<TrackedSensor> sensors = List.of(new TrackedSensor(4, 5, 6, "hallway", false, true));
		store(directory).set(new SculkSightConfig(71, RenderPolicy.PER_SENSOR, sensors));

		ConfigStore reopened = store(directory);
		reopened.load();

		assertEquals(sensors, reopened.get().trackedSensors());
		assertEquals(List.of(), problems);
	}

	@Test
	void savingCreatesTheDirectoryAboveTheFile(@TempDir Path directory) {
		Path nested = directory.resolve("config").resolve("deeper");

		new ConfigStore(nested.resolve(ConfigStore.FILE_NAME), problems::add)
				.set(new SculkSightConfig(40, SculkSightConfig.DEFAULT_RENDER_POLICY));

		assertTrue(Files.exists(nested.resolve(ConfigStore.FILE_NAME)));
		assertEquals(List.of(), problems);
	}

	@Test
	void savingLeavesNoTemporaryFileBehind(@TempDir Path directory) throws IOException {
		store(directory).set(new SculkSightConfig(40, SculkSightConfig.DEFAULT_RENDER_POLICY));

		try (Stream<Path> entries = Files.list(directory)) {
			assertEquals(List.of(ConfigStore.FILE_NAME),
					entries.map(path -> path.getFileName().toString()).toList());
		}
	}

	@Test
	void aFailedSaveLeavesNoTemporaryFileBehindEither(@TempDir Path directory) throws IOException {
		blockTheDestination(directory);

		ConfigStore store = store(directory);
		store.set(new SculkSightConfig(40, SculkSightConfig.DEFAULT_RENDER_POLICY));

		assertEquals(1, problems.size(), problems.toString());
		assertTrue(problems.getFirst().contains("could not write"), problems.getFirst());

		assertFalse(Files.exists(directory.resolve(ConfigStore.FILE_NAME + ".tmp")),
				"the write succeeded and the move failed, which leaves the temporary file as the "
						+ "only thing to clean up. OPEN-QUESTIONS.md section 22.");

		assertEquals("so the move cannot replace it",
				Files.readString(directory.resolve(ConfigStore.FILE_NAME).resolve("occupied"),
						StandardCharsets.UTF_8),
				"nothing may have replaced the blocked destination; if this ever passes because the "
						+ "move succeeded, the test above is no longer about a failed save");

		assertEquals(40, store.get().shellOpacityPercent(),
				"set stores the value in memory whatever the file does");
	}

	private static void blockTheDestination(Path directory) throws IOException {
		Path inTheWay = directory.resolve(ConfigStore.FILE_NAME);

		Files.createDirectory(inTheWay);
		Files.writeString(inTheWay.resolve("occupied"), "so the move cannot replace it");
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
		write(directory,
				"{\"shellOpacityPercent\": 400, \"renderPolicy\": \"union\", \"radiusAuditCap\": 32}");

		ConfigStore store = store(directory);
		store.load();

		assertEquals(SculkSightConfig.MAX_SHELL_OPACITY_PERCENT, store.get().shellOpacityPercent());
		assertEquals(1, problems.size());

		store.save();

		assertEquals("{\n\t\"shellOpacityPercent\": 100,\n\t\"renderPolicy\": \"union\",\n"
						+ "\t\"radiusAuditCap\": 32\n}\n",
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

	@Test
	void aDeeplyNestedFileFallsBackToTheDefaultsLikeAnyOtherDamagedOne(@TempDir Path directory)
			throws IOException {

		write(directory, "[".repeat(100_000));

		ConfigStore store = store(directory);
		store.load();

		assertEquals(SculkSightConfig.defaults(), store.get());
		assertEquals(1, problems.size());
		assertTrue(problems.getFirst().contains("not a valid configuration file"),
				problems.getFirst());
	}

	@Test
	void aDeeplyNestedFileIsLeftOnDisk(@TempDir Path directory) throws IOException {
		String text = "[".repeat(100_000);

		write(directory, text);

		store(directory).load();

		assertEquals(text,
				Files.readString(directory.resolve(ConfigStore.FILE_NAME), StandardCharsets.UTF_8));
	}

	private ConfigStore store(Path directory) {
		return new ConfigStore(directory.resolve(ConfigStore.FILE_NAME), problems::add);
	}

	private static void write(Path directory, String text) throws IOException {
		Files.writeString(directory.resolve(ConfigStore.FILE_NAME), text, StandardCharsets.UTF_8);
	}
}
