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
		store(directory).set(new SculkSightConfig(71, SculkSightConfig.DEFAULT_RENDER_POLICY));

		ConfigStore reopened = store(directory);
		reopened.load();

		assertEquals(71, reopened.get().shellOpacityPercent());
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

	/**
	 * The other half of the test above, and OPEN-QUESTIONS.md section 22's second smaller finding:
	 * the write can succeed and the move then fail, and the temporary file used to survive that.
	 *
	 * <p><b>How the failure is provoked, since that is the part that needed thought.</b> The
	 * destination is made a directory with something in it. A move onto a non-empty directory
	 * cannot succeed anywhere - {@code Files.move} with {@code REPLACE_EXISTING} is specified to
	 * replace an existing <i>empty</i> directory and to fail for a non-empty one - so the
	 * provocation itself is portable, and the alternatives are not: a read-only parent directory is
	 * enforced differently on Windows than on POSIX and not at all for an administrator, and
	 * holding the destination open so the rename fails is a Windows-only sharing rule.
	 *
	 * <p><b>Which {@code IOException} arrives is <i>not</i> portable, and this test therefore does
	 * not assert it.</b> A first draft asserted {@code DirectoryNotEmptyException}, which is what
	 * {@code Files.move}'s own javadoc names for this input, and it failed here: on Windows,
	 * 2026-09-07, the move reported {@code AccessDeniedException} instead, with the temporary file
	 * as the source and the blocked path as the target. The assertions below are on what
	 * {@code ConfigStore} does about a failed save, which is the finding, rather than on which
	 * subclass the platform picked to tell it.
	 *
	 * <p><b>What it does not prove.</b> That a temporary file ever existed. The move failing tells
	 * us the write before it returned normally, so it did; but the state this test observes is the
	 * one after the cleanup, and no seam in {@code ConfigStore} exposes the moment in between.
	 * Asserting the destination is untouched is what keeps the test honest in the other direction -
	 * a save that had failed at {@code createDirectories} or at the write would leave the same
	 * empty-handed directory listing, and the untouched destination is what says the save got as
	 * far as trying to replace it.
	 */
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

	/** Makes the settings file's own path a non-empty directory, which no move can replace. */
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
		write(directory, "{\"shellOpacityPercent\": 400, \"renderPolicy\": \"union\"}");

		ConfigStore store = store(directory);
		store.load();

		assertEquals(SculkSightConfig.MAX_SHELL_OPACITY_PERCENT, store.get().shellOpacityPercent());
		assertEquals(1, problems.size());

		store.save();

		assertEquals("{\n\t\"shellOpacityPercent\": 100,\n\t\"renderPolicy\": \"union\"\n}\n",
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

	/**
	 * OPEN-QUESTIONS.md section 22.3, asserted where the promise it broke was made. DECISIONS.md
	 * ADR-055 says a damaged file yields the shipped defaults plus a report; a file of thousands of
	 * opening brackets used to yield a {@code StackOverflowError} instead, which is an
	 * {@link Error} and so went straight past {@code load()}'s {@code catch (JsonParseException)}
	 * and out of the mod's startup. This test would not have failed - it would have errored, which
	 * is the point.
	 */
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

	/** And it is left on disk, for the same reason every other damaged file is: it is theirs. */
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
