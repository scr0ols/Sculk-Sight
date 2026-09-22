package com.scr0ols.sculksight.client;

final class DrawLoopTiming {

	static final long STALL_BUDGET_NANOS = 5_000_000L;

	private int frameCount;

	private long sumNanos;

	private long minNanos = Long.MAX_VALUE;

	private long maxNanos;

	private int overBudget;

	private int minEntries = Integer.MAX_VALUE;

	private int maxEntries;

	void record(int entryCount, long loopNanos) {
		frameCount++;
		sumNanos += loopNanos;

		if (loopNanos < minNanos) {
			minNanos = loopNanos;
		}
		if (loopNanos > maxNanos) {
			maxNanos = loopNanos;
		}
		if (loopNanos > STALL_BUDGET_NANOS) {
			overBudget++;
		}

		if (entryCount < minEntries) {
			minEntries = entryCount;
		}
		if (entryCount > maxEntries) {
			maxEntries = entryCount;
		}
	}

	boolean isEmpty() {
		return frameCount == 0;
	}

	String summary() {
		if (frameCount == 0) {
			return "[sculksight-diag] draw loop: no frames drawn.";
		}

		String entries = minEntries == maxEntries ? String.valueOf(minEntries) : minEntries + "-" + maxEntries;

		return "[sculksight-diag] draw loop over " + frameCount + " frames, " + entries
				+ " entries: min " + TierTiming.millis(minNanos) + " ms, mean "
				+ TierTiming.millis(sumNanos / frameCount) + " ms, max " + TierTiming.millis(maxNanos)
				+ " ms of CPU (upload+draw, all entries), " + overBudget + " over the "
				+ TierTiming.millis(STALL_BUDGET_NANOS) + " ms stall budget.";
	}

	void reset() {
		frameCount = 0;
		sumNanos = 0L;
		minNanos = Long.MAX_VALUE;
		maxNanos = 0L;
		overBudget = 0;
		minEntries = Integer.MAX_VALUE;
		maxEntries = 0;
	}
}
