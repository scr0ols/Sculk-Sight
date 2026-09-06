package com.scr0ols.sculksight.client;

/**
 * How long one solve took, split by the thread each phase runs on. DECISIONS.md ADR-031, and its
 * 2026-09-06 addendum for the split below.
 *
 * <p><b>The split is by thread now, not by ADR-017's slot boundary, and DECISIONS.md ADR-048 is
 * why.</b> This record used to carry two fields, {@code encodeNanos} and {@code uploadNanos}, and
 * sum them against PLAN.md section 3.3's 2 ms per tick - correct while the encode ran on the client
 * thread, which is where ADR-026 had left it. ADR-046 and ADR-048 moved the solve, the boundary
 * extraction and the encode onto {@code ShellWorkerExecutor}'s own thread, and from that moment the
 * old sum added a number that costs the player a frame to one that does not. The first live run of
 * the wired pipeline (2026-09-06) reported {@code 16.144 ms of CPU (budget 2 ms per tick)} for work
 * almost none of which was on the frame path, which is the reading this split exists to prevent.
 *
 * <p><b>{@code snapshotNanos} is new and it is the point of the change.</b> ARCHITECTURE.md
 * section 6.2's first phase, {@code VolumeSnapshot.of}, is the only part of a solve still on the
 * client thread, and until this field existed nothing timed it: it sat above the old instrument's
 * own start stamp in {@code ShellRenderer.runSolve}. RESEARCH-LOG.md R16 is why the phase has to be
 * there at all, so it cannot be moved off the budget - which makes measuring it the only way to
 * know what the budget is actually spending.
 *
 * <p><b>{@link #clientNanos()} is the budgeted quantity, and it is a sum of two rather than three.</b>
 * The snapshot runs on the client thread and the upload on the render thread, which
 * RESEARCH-LOG.md R13 point 4 established are the same thread; both therefore cost a frame and both
 * belong to section 3.3's per-tick budget. The encode is reported beside them and deliberately not
 * added in: it is a real cost with real consequences - a solve in flight is a shell not yet drawn -
 * but it is latency rather than frame budget, and adding it to a frame-budget figure is the
 * misreading above.
 *
 * <p>A type of its own rather than fields on {@link ShellStats}, whose purpose is the second v0.0
 * exit criterion and whose vertex arithmetic is load-bearing in {@code ShellRenderer}. Giving that
 * type a second purpose is the cost OPEN-QUESTIONS.md section 18 named, and three fields here are a
 * cheaper way to avoid it than any argument for merging them.
 */
record ShellTimings(long snapshotNanos, long encodeNanos, long uploadNanos) {

	/**
	 * The two phases that run on the client thread, which is the quantity PLAN.md section 3.3
	 * budgets at 2 ms per tick.
	 *
	 * <p>Replaces the former {@code totalNanos()}, which summed the encode into the same figure.
	 * That sum stopped describing anything a player can feel when ADR-048 moved the encode to a
	 * worker, and no caller wanted the three-way total for its own sake.
	 */
	long clientNanos() {
		return snapshotNanos + uploadNanos;
	}

	String summary() {
		return "client thread: snapshot " + TierTiming.millis(snapshotNanos) + " ms + upload "
				+ TierTiming.millis(uploadNanos) + " ms = " + TierTiming.millis(clientNanos())
				+ " ms of CPU (budget 2 ms per tick); worker: encode "
				+ TierTiming.millis(encodeNanos) + " ms, off the frame path.";
	}
}
