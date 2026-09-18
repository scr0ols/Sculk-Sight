package com.scr0ols.sculksight.client;

/**
 * ARCHITECTURE.md section 12.4 sub-problem 1: whether the per-sensor draw cost in {@code
 * ShellRenderer.onRender}'s {@code PER_SENSOR} loop - {@code consumePending}'s upload plus {@code
 * draw}, summed across every currently-drawn entry - is additive and unbudgeted at mode B's scale
 * (20+ sensors), and whether that risks the v0.3 exit criterion of no main-thread stall above 5 ms
 * (PLAN.md section 5, TESTING-STRATEGY.md section 4). {@link TierTiming.Frames} already samples
 * the draw call alone, for a single sensor's tier 3 cost; this samples the whole loop, entry count
 * included, which is what a multi-sensor stall question actually needs.
 *
 * <p>This class is the arithmetic only - it takes no clock and touches no game state, the same
 * split {@link TierTiming} and {@link TierTiming.Frames} already use, so it is testable in an
 * ordinary JVM. {@code ShellRenderer.onRender} is what calls {@code System.nanoTime()} around the
 * loop and feeds this the result, gated on {@link TimingGate#ENABLED} exactly as {@link
 * TierTiming.Frames} already is.
 *
 * <p><b>Tagged {@code [sculksight-diag]}</b> in its own summary line, the same grep-friendly
 * convention the project's earlier in-game diagnostic logging used, so the author can pull these
 * lines out of a session's log alongside {@link TierTiming.Frames}'s existing tier-3 numbers.
 *
 * <p><b>Kept rather than removed before merge</b>, unlike that earlier diagnostic logging (which
 * was built to chase one specific bug and torn out once it closed). This measures the exact
 * question the v0.3 exit criteria ask, it is off by default and costs nothing when off - the same
 * {@link TimingGate} the existing tier-3 timing already gates on, one constant condition the JIT
 * folds away - and the per-tick solve budget's own javadoc already names "the profiling pass" as
 * something later work should revisit against measured numbers, not a one-time question a single
 * PR closes. An instrument that only existed for one PR would not survive to be revisited.
 */
final class DrawLoopTiming {

	/** PLAN.md section 5 / TESTING-STRATEGY.md section 4's v0.3 exit criterion, in nanoseconds. */
	static final long STALL_BUDGET_NANOS = 5_000_000L;

	private int frameCount;

	private long sumNanos;

	private long minNanos = Long.MAX_VALUE;

	private long maxNanos;

	private int overBudget;

	private int minEntries = Integer.MAX_VALUE;

	private int maxEntries;

	/** {@code entryCount} is {@code toDraw.size()} for the frame {@code loopNanos} was measured over. */
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
