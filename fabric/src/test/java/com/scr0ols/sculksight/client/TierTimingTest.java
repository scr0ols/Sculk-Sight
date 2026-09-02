package com.scr0ols.sculksight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

/**
 * The arithmetic behind what a tier report says, and what a mirrored line looks like.
 * DECISIONS.md ADR-031 and its two addenda.
 *
 * <p>The first tests in this project over classes from the {@code client} source set, which the
 * test source set turns out to see. They name no Minecraft class and they deliberately touch
 * neither {@link TimingGate} nor {@code TierTiming.start}: the gate reads {@code FabricLoader} and
 * cannot initialise outside a launched game, which is why it is a separate type (RESEARCH-LOG.md
 * E7).
 *
 * <p>What this proves is that the numbers a budget question is answered with are the numbers the
 * samples support. It proves nothing about the samples themselves, which come from a clock in a
 * running game and are the profiling pass's business, not JUnit's (TESTING-STRATEGY.md §1 and §2).
 */
class TierTimingTest {

	private static final long MS = 1_000_000L;

	@Test
	void aFreshAccumulatorIsEmptyAndSaysSo() {
		TierTiming.Frames frames = new TierTiming.Frames();

		assertTrue(frames.isEmpty());
		assertEquals(0, frames.count());
		assertEquals("tier 3: no frames drawn.", frames.summary());
	}

	@Test
	void minMeanAndMaxAreOverFrames() {
		TierTiming.Frames frames = new TierTiming.Frames();

		frames.record(MS);
		frames.record(2 * MS);
		frames.record(3 * MS);

		assertFalse(frames.isEmpty());
		assertEquals(3, frames.count());
		assertEquals("tier 3 over 3 frames: min 1.000 ms, mean 2.000 ms, max 3.000 ms of CPU, "
				+ "3 over the 0.500 ms budget.", frames.summary());
	}

	@Test
	void oneSlowFrameSurvivesTheMeanAsTheMaximumAndIsCounted() {
		// The failure this shape exists to prevent: a single frame over budget among many under it
		// disappears into an average, and the average is the only thing a running figure would
		// report. The count is what the first live run added, because a maximum on its own cannot
		// tell one slow frame from a pause elsewhere in the client.
		TierTiming.Frames frames = new TierTiming.Frames();

		for (int i = 0; i < 99; i++) {
			frames.record(MS / 10);
		}

		frames.record(20 * MS);

		assertEquals(100, frames.count());
		assertEquals("tier 3 over 100 frames: min 0.100 ms, mean 0.299 ms, max 20.000 ms of CPU, "
				+ "1 over the 0.500 ms budget.", frames.summary());
	}

	@Test
	void aRunEntirelyUnderBudgetSaysSoRatherThanLeavingItToBeInferred() {
		TierTiming.Frames frames = new TierTiming.Frames();

		for (int i = 0; i < 10; i++) {
			frames.record(MS / 100);
		}

		assertEquals("tier 3 over 10 frames: min 0.010 ms, mean 0.010 ms, max 0.010 ms of CPU, "
				+ "0 over the 0.500 ms budget.", frames.summary());
	}

	@Test
	void resetStartsANewRunRatherThanContinuingTheOldOne() {
		TierTiming.Frames frames = new TierTiming.Frames();

		frames.record(5 * MS);
		frames.reset();

		assertTrue(frames.isEmpty());

		frames.record(MS / 100);

		assertEquals("tier 3 over 1 frames: min 0.010 ms, mean 0.010 ms, max 0.010 ms of CPU, "
				+ "0 over the 0.500 ms budget.", frames.summary());
	}

	@Test
	void aSolveReportsBothHalvesAndTheSumTheBudgetIsAbout() {
		ShellTimings timings = new ShellTimings(4 * MS, MS / 4);

		assertEquals(4 * MS + MS / 4, timings.totalNanos());
		assertEquals("tiers 1+2: encode 4.000 ms + upload 0.250 ms = 4.250 ms of CPU "
				+ "(budget 2 ms per tick).", timings.summary());
	}

	@Test
	void theMirroredLineCarriesTheClockTimeAndNothingElse() {
		// The file exists to be copied out of, so a line has to be readable on its own and has to
		// say when it was written. Nothing is reformatted on the way in: what chat said is what the
		// file says (ADR-031's first 2026-09-02 addendum).
		String line = TimingLog.format(LocalTime.of(14, 5, 9), "shell cleared.");

		assertEquals("14:05:09  shell cleared." + System.lineSeparator(), line);
	}

	@Test
	void eachRunOpensItsOwnBlockSoTwoLaunchesAreNotReadAsOne() {
		String header = TimingLog.header(LocalDateTime.of(2026, 9, 2, 14, 5, 9));

		assertEquals(System.lineSeparator() + "=== Sculk Sight timing run, started "
				+ "2026-09-02 14:05:09 ===" + System.lineSeparator(), header);
	}
}
