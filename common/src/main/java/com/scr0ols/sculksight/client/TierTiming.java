package com.scr0ols.sculksight.client;

import java.util.Locale;

/**
 * The clock and the per-frame aggregate for PLAN.md section 3.3's two budget lines.
 * DECISIONS.md ADR-031. Whether any of it runs at all is {@link TimingGate}.
 *
 * <p><b>What is timed is the budget, not the tier table.</b> Section 3.3 budgets two quantities:
 * tiers 1 and 2 combined at 2 ms per tick, and tier 3 alone at 0.5 ms per frame. The first is
 * timed at ADR-017's slot boundary, the encode that produces a {@code MeshData} against the upload
 * that consumes it, which exists in v0.0 whichever thread the producer runs on. That is why this
 * instrument is not blocked on OPEN-QUESTIONS.md section 15: answering it changes what the sum
 * means, not where the clocks sit.
 *
 * <p><b>It measures CPU time.</b> A {@code nanoTime} pair around the draw measures the cost of
 * recording the render pass, not of the GPU executing it. Reporting therefore compares a CPU-side
 * number against the budget and never claims the budget outright; the GPU side needs timer queries
 * nobody here has read for, which is a question rather than an assumption (ADR-031).
 */
final class TierTiming {

	/**
	 * How long a run of tier 3 samples accumulates before a summary is flushed on its own.
	 *
	 * <p><b>Wall time rather than a frame count, and the first run is why.</b> The trigger was 600
	 * frames, which sounded like several seconds and was two thirds of one: with the frame cap off,
	 * as the pass requires, the client drew about 1 600 frames a second, so a four-minute run wrote
	 * 606 blocks and 66 KB. A time trigger reports at the same rate whatever the frame rate is,
	 * which is also what makes two machines' files comparable.
	 */
	static final long FLUSH_INTERVAL_NANOS = 10L * 1_000_000_000L;

	/**
	 * PLAN.md section 3.3's per-frame budget, in nanoseconds, so that a report can say how many
	 * frames missed it instead of leaving that to be inferred from a maximum.
	 *
	 * <p>The plan owns the number; this is a copy of it for reporting, and it moves when the plan's
	 * does.
	 */
	static final long FRAME_BUDGET_NANOS = 500_000L;

	private TierTiming() {
	}

	/** A start stamp, or zero when the instrument is off. Needs a running game, per TimingGate. */
	static long start() {
		return TimingGate.ENABLED ? System.nanoTime() : 0L;
	}

	/** Nanoseconds since a stamp from {@link #start()}, or zero when the instrument is off. */
	static long since(long start) {
		return TimingGate.ENABLED ? System.nanoTime() - start : 0L;
	}

	/** Nanoseconds as milliseconds, three decimals, locale-independent so logs compare. */
	static String millis(long nanos) {
		return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0D);
	}

	/**
	 * Tier 3 samples, accumulated while the shell is up.
	 *
	 * <p>Per-frame numbers cannot be printed per frame, and averaging them into one running figure
	 * would hide the frame that missed. Count, minimum, mean, maximum and how many frames went over
	 * the budget are what a budget question actually needs, and they cost five fields and no
	 * allocation per frame.
	 *
	 * <p><b>The over-budget count is here because the first run showed that a maximum alone is not
	 * an answer.</b> Two blocks out of 606 carried a maximum above the budget while their means sat
	 * at a thirtieth of it, which is what one slow frame looks like and is equally what a pause
	 * elsewhere in the client, caught inside the pair of clocks, looks like. How many frames missed
	 * separates a renderer that is over budget from a machine that hiccuped; a maximum cannot.
	 *
	 * <p>Client thread only, which is also the render thread (RESEARCH-LOG.md R13 point 4), so the
	 * fields need no synchronisation. This does not change when OPEN-QUESTIONS.md section 15 is
	 * answered: tier 3 is the draw, and the draw is on the render thread by definition.
	 */
	static final class Frames {

		private int count;

		private long sum;

		private long min = Long.MAX_VALUE;

		private long max;

		private int overBudget;

		void record(long nanos) {
			count++;
			sum += nanos;

			if (nanos < min) {
				min = nanos;
			}

			if (nanos > max) {
				max = nanos;
			}

			if (nanos > FRAME_BUDGET_NANOS) {
				overBudget++;
			}
		}

		int count() {
			return count;
		}

		boolean isEmpty() {
			return count == 0;
		}

		/** The budgeted quantity is per frame, so the mean is over frames rather than over time. */
		String summary() {
			if (count == 0) {
				return "tier 3: no frames drawn.";
			}

			return "tier 3 over " + count + " frames: min " + millis(min) + " ms, mean "
					+ millis(sum / count) + " ms, max " + millis(max) + " ms of CPU, "
					+ overBudget + " over the " + millis(FRAME_BUDGET_NANOS) + " ms budget.";
		}

		void reset() {
			count = 0;
			sum = 0L;
			min = Long.MAX_VALUE;
			max = 0L;
			overBudget = 0;
		}
	}
}
