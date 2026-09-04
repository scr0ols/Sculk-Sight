package com.scr0ols.sculksight.client;

/**
 * How long one solve took, split at the boundary that already exists rather than at the one
 * PLAN.md section 3.3's tier table describes. DECISIONS.md ADR-031.
 *
 * <p>{@code encodeNanos} covers the solve, the boundary extraction and the vertex build, which is
 * everything {@code ShellRenderer.runSolve} does before offering into the slot. {@code uploadNanos}
 * covers the single {@code createBuffer} the render thread does when it takes the mesh out again.
 * Section 3.3 budgets the two together at 2 ms per tick, so {@link #totalNanos()} is the number the
 * budget is about; they are kept apart because the split is free once the call sites are separate,
 * and because it is what survives OPEN-QUESTIONS.md section 15 moving the producer off-thread.
 *
 * <p>A type of its own rather than fields on {@link ShellStats}, whose purpose is the second v0.0
 * exit criterion and whose vertex arithmetic is load-bearing in {@code ShellRenderer}. Giving that
 * type a second purpose is the cost OPEN-QUESTIONS.md section 18 named, and two fields here are a
 * cheaper way to avoid it than any argument for merging them.
 */
record ShellTimings(long encodeNanos, long uploadNanos) {

	long totalNanos() {
		return encodeNanos + uploadNanos;
	}

	String summary() {
		return "tiers 1+2: encode " + TierTiming.millis(encodeNanos) + " ms + upload "
				+ TierTiming.millis(uploadNanos) + " ms = " + TierTiming.millis(totalNanos())
				+ " ms of CPU (budget 2 ms per tick).";
	}
}
