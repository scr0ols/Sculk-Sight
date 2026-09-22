package com.scr0ols.sculksight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

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
	void aSolveSumsOnlyTheTwoPhasesThatCostAFrame() {
		ShellTimings timings = new ShellTimings(3 * MS / 2, 16 * MS, MS / 4);

		assertEquals(3 * MS / 2 + MS / 4, timings.clientNanos());
	}

	@Test
	void aSolveReportsTheClientThreadAndTheWorkerAsSeparateFigures() {
		ShellTimings timings = new ShellTimings(3 * MS / 2, 16 * MS, MS / 4);

		assertEquals("client thread: snapshot 1.500 ms + upload 0.250 ms = 1.750 ms of CPU "
				+ "(budget 2 ms per tick); worker: encode 16.000 ms, off the frame path.",
				timings.summary());
	}

	@Test
	void theMirroredLineCarriesTheClockTimeAndNothingElse() {
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
