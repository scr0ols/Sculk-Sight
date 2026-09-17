package com.scr0ols.sculksight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Pins the answer to the async-solver investigation: the only client-thread work a solve still
 * does - taking a {@link VolumeSnapshot} of the sensor's bounding cube, since a worker may not
 * read the live level - is reachable only through {@code dispatchBudgetedSolves}'s per-tick
 * budget, never for the full audited set at once.
 *
 * <p><b>Why this needed checking rather than assuming.</b> The solve itself, the boundary
 * extraction and the mesh encode already run off the client thread (they are submitted to
 * {@code ShellRenderer.WORKER}); that was true before mode B's per-sensor cache and per-tick
 * budget were built. What was genuinely still open was whether the one remaining client-thread
 * step - the snapshot - could still be forced to run for every selected sensor in a single tick
 * at mode B's scale (20+ sensors), which would reintroduce the same kind of stall the worker
 * thread exists to avoid. It cannot: {@code dispatchBudgetedSolves} takes at most
 * {@code PER_TICK_AUDIT_SOLVE_BUDGET} keys off the pending queue before calling
 * {@code runSolves}, and {@code runSolves} only snapshots that budgeted list - every other
 * current entry is folded into a rebuilt union from its already-solved, cached detection set
 * (see {@code ShellRenderer.CachedContribution}), with no second snapshot taken.
 *
 * <p><b>Why this is a source-text test and not an ordinary one.</b> {@code ShellRenderer} lives
 * in {@code common/src/client/java}, which this module's own build does not compile at all - the
 * same reason {@link ShellRendererStyleCaptureTest} reads it as text rather than importing it.
 * That test guards a happens-before property; this one guards a scheduling property, but both are
 * things a compiler cannot see and a reader checks by eye, so both are checked here instead on
 * every build.
 *
 * <p><b>What it does not prove.</b> That the budgeted snapshot cost itself stays inside the
 * frame-budget and 5&nbsp;ms stall targets at a live 20+-sensor scale is not something a JUnit
 * test can establish - that is measured in the running game, not read from source. What this
 * class proves is narrower and permanent: that the mechanism which bounds the cost per tick
 * cannot silently stop applying to the one client-thread step that remains.
 */
class ShellRendererSnapshotBudgetTest {

	/**
	 * Where the class sits relative to this module. A Gradle {@code Test} task runs in its own
	 * project directory, so this resolves from {@code common/}.
	 */
	private static final Path SOURCE = Path.of("src", "client", "java", "com", "scr0ols",
			"sculksight", "client", "ShellRenderer.java");

	private static final String DISPATCH_METHOD = "private static void dispatchBudgetedSolves(";
	private static final String RUN_SOLVES_METHOD = "private static void runSolves(";
	private static final String TO_SOLVE_LOOP = "for (ShellEntry target : toSolve) {";
	private static final String ALREADY_SOLVED_LOOP = "for (ShellEntry entry : alreadySolved) {";
	private static final String SNAPSHOT_CALL = "VolumeSnapshot.of(";
	private static final String RUN_SOLVES_CALL_MARKER = "runSolves(";

	@Test
	void dispatchBudgetedSolvesBoundsHowManyKeysAreTakenOffTheQueue() throws IOException {
		String body = bodyOf(read(), DISPATCH_METHOD);

		assertTrue(body.contains("toSolve.size() < PER_TICK_AUDIT_SOLVE_BUDGET"),
				"dispatchBudgetedSolves must stop moving keys from pendingSolve into toSolve once "
						+ "PER_TICK_AUDIT_SOLVE_BUDGET is reached - that bound is what keeps a full "
						+ "mode B recompute from snapshotting every selected sensor in one tick.");
	}

	@Test
	void runSolvesOnlySnapshotsTheBudgetedToSolveListNotEveryCurrentEntry() throws IOException {
		String runSolvesBody = bodyOf(read(), RUN_SOLVES_METHOD);

		assertEquals(1, countOccurrences(runSolvesBody, SNAPSHOT_CALL),
				"runSolves must take exactly one snapshot per call to the budgeted list - a second "
						+ "call site would mean some other path is being snapshotted too, defeating "
						+ "the per-tick budget.");

		String toSolveLoop = bodyOf(runSolvesBody, TO_SOLVE_LOOP);
		assertTrue(toSolveLoop.contains(SNAPSHOT_CALL),
				"the snapshot must be taken inside the loop over toSolve - the budgeted subset - "
						+ "not somewhere unbounded.");

		String alreadySolvedLoop = bodyOf(runSolvesBody, ALREADY_SOLVED_LOOP);
		assertFalse(alreadySolvedLoop.contains(SNAPSHOT_CALL),
				"alreadySolved entries must be folded into the union from their cached "
						+ "DetectionSet, per ARCHITECTURE.md section 12.3's per-sensor cache - "
						+ "re-snapshotting them here would reintroduce exactly the unbounded cost "
						+ "the per-tick budget exists to prevent.");
	}

	@Test
	void runSolvesHasExactlyOneCallSiteAndItIsTheBudgetedDispatch() throws IOException {
		String source = read();

		assertEquals(2, countOccurrences(source, RUN_SOLVES_CALL_MARKER),
				"runSolves should appear exactly twice in this file - its own declaration and the "
						+ "one call inside dispatchBudgetedSolves. A third occurrence would mean a "
						+ "second, unbudgeted path can trigger a snapshot - the exact regression "
						+ "this test exists to catch.");

		String dispatchBody = bodyOf(source, DISPATCH_METHOD);
		assertTrue(dispatchBody.contains("runSolves(level, toSolve, alreadySolved)"),
				"the one call to runSolves must come from dispatchBudgetedSolves, passing the "
						+ "already-budgeted toSolve list.");
	}

	/**
	 * A guard on the guard: if the class is renamed or moved, this test class fails loudly rather
	 * than passing over a file it never found.
	 */
	private static String read() throws IOException {
		if (!Files.isRegularFile(SOURCE)) {
			return fail(SOURCE.toAbsolutePath() + " is not there. If ShellRenderer moved, move "
					+ "this test's path with it.");
		}

		return Files.readString(SOURCE, StandardCharsets.UTF_8);
	}

	/** The text from a marker's own opening brace to the brace that closes it, brace-matched. */
	private static String bodyOf(String source, String marker) {
		int open = source.indexOf('{', markerIn(source, marker));

		int depth = 0;

		for (int at = open; at < source.length(); at++) {
			char c = source.charAt(at);

			if (c == '{') {
				depth++;
			} else if (c == '}' && --depth == 0) {
				return source.substring(open, at + 1);
			}
		}

		return fail("the body starting at " + marker + " has no closing brace");
	}

	private static int markerIn(String source, String marker) {
		int at = source.indexOf(marker);

		if (at < 0) {
			return fail(marker + " is no longer in " + SOURCE + ". If it was renamed, rename it "
					+ "here too.");
		}

		return at;
	}

	private static int countOccurrences(String haystack, String needle) {
		int count = 0;
		int from = 0;

		while ((from = haystack.indexOf(needle, from)) != -1) {
			count++;
			from += needle.length();
		}

		return count;
	}
}
