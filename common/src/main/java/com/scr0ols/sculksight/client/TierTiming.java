package com.scr0ols.sculksight.client;

import java.util.Locale;

final class TierTiming {

	static final long FLUSH_INTERVAL_NANOS = 10L * 1_000_000_000L;

	static final long FRAME_BUDGET_NANOS = 500_000L;

	private TierTiming() {
	}

	static long start() {
		return TimingGate.ENABLED ? System.nanoTime() : 0L;
	}

	static long since(long start) {
		return TimingGate.ENABLED ? System.nanoTime() - start : 0L;
	}

	static String millis(long nanos) {
		return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0D);
	}

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
