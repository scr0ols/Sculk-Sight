package com.scr0ols.sculksight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DrawLoopTimingTest {

	private static final long MS = 1_000_000L;

	@Test
	void aFreshAccumulatorIsEmptyAndSaysSo() {
		DrawLoopTiming loop = new DrawLoopTiming();

		assertTrue(loop.isEmpty());
		assertEquals("[sculksight-diag] draw loop: no frames drawn.", loop.summary());
	}

	@Test
	void reportsTheEntryCountRangeAlongsideTheTiming() {
		DrawLoopTiming loop = new DrawLoopTiming();

		loop.record(20, MS);
		loop.record(27, 2 * MS);

		assertFalse(loop.isEmpty());
		assertEquals("[sculksight-diag] draw loop over 2 frames, 20-27 entries: min 1.000 ms, "
				+ "mean 1.500 ms, max 2.000 ms of CPU (upload+draw, all entries), "
				+ "0 over the 5.000 ms stall budget.", loop.summary());
	}

	@Test
	void aConstantEntryCountReportsAsOneNumberRatherThanARange() {
		DrawLoopTiming loop = new DrawLoopTiming();

		loop.record(20, MS);
		loop.record(20, MS);

		assertEquals("[sculksight-diag] draw loop over 2 frames, 20 entries: min 1.000 ms, "
				+ "mean 1.000 ms, max 1.000 ms of CPU (upload+draw, all entries), "
				+ "0 over the 5.000 ms stall budget.", loop.summary());
	}

	@Test
	void aFrameOverTheFiveMillisecondStallBudgetIsCounted() {
		DrawLoopTiming loop = new DrawLoopTiming();

		loop.record(25, 6 * MS);
		loop.record(25, MS);

		assertEquals("[sculksight-diag] draw loop over 2 frames, 25 entries: min 1.000 ms, "
				+ "mean 3.500 ms, max 6.000 ms of CPU (upload+draw, all entries), "
				+ "1 over the 5.000 ms stall budget.", loop.summary());
	}

	@Test
	void resetStartsANewRunRatherThanContinuingTheOldOne() {
		DrawLoopTiming loop = new DrawLoopTiming();

		loop.record(20, 6 * MS);
		loop.reset();

		assertTrue(loop.isEmpty());

		loop.record(5, MS / 10);

		assertEquals("[sculksight-diag] draw loop over 1 frames, 5 entries: min 0.100 ms, "
				+ "mean 0.100 ms, max 0.100 ms of CPU (upload+draw, all entries), "
				+ "0 over the 5.000 ms stall budget.", loop.summary());
	}
}
