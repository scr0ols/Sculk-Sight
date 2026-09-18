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
 * Pins the answer to Batch 4 (T6): does {@code ShellRenderer}'s existing per-entry draw loop
 * already give every audited entry - however it reached {@link ShellRenderer#entries}, tracked or
 * from either half of mode B's find - one buffer and one draw call, per ADR-014, at mode B's scale
 * (20+ sensors)?
 *
 * <p><b>Why this needed checking rather than assuming.</b> ADR-014 fixes the constraint (sensor-
 * relative vertices mean two sensors cannot share a buffer, so mode B gets "one buffer and one
 * draw call per sensor") but was written before mode B's own per-sensor cache and cap existed.
 * What this class confirms, from the code as it stands after T3/T4's cache and per-tick budget:
 * {@code onRender}'s {@code PER_SENSOR} branch iterates every entry currently held in {@link
 * ShellRenderer#entries} with no truncation of its own (a cap, if any, already happened upstream
 * at selection, {@code RadiusAudit}'s own layer - not here), {@code draw(...)} has exactly one
 * call site in the whole file so no second path could double-draw or batch a subset, and the
 * buffer each call binds is the one it was handed as a parameter, not a shared field - so nothing
 * before this loop can leave two sensors pointing at the same buffer.
 *
 * <p><b>What was found: already correct.</b> No production change accompanies this test. Per the
 * task's own framing (Batch 4's row and section 12.5), this is a confirmation of existing wiring,
 * not a new render path, and an honest "already correct" is the valid outcome here the same way
 * it was for Batch 3b (T5)'s snapshot-budget question.
 *
 * <p><b>Why this is a source-text test and not an ordinary one.</b> {@code ShellRenderer} lives in
 * {@code common/src/client/java}, which this module's own build does not compile at all - the same
 * reason {@link ShellRendererSnapshotBudgetTest} and {@link ShellRendererStyleCaptureTest} read it
 * as text instead of importing it. All three guard a property a compiler cannot see and a reader
 * would otherwise only check by eye.
 *
 * <p><b>What it does not prove.</b> That drawing N≈20+ separate buffers stays inside the frame
 * budget is a live, render-thread question (Batch 5 (T7)'s job, and the in-game check this task's
 * PR asks the author to run) - not something a JUnit test can measure. What this class proves is
 * narrower and permanent: that the loop reaching that measurement is structurally one buffer, one
 * draw, per current entry, with no accidental sharing or silent drop between here and the GPU call.
 */
class ShellRendererDrawCallTest {

	/**
	 * Where the class sits relative to this module. A Gradle {@code Test} task runs in its own
	 * project directory, so this resolves from {@code common/}.
	 */
	private static final Path SOURCE = Path.of("src", "client", "java", "com", "scr0ols",
			"sculksight", "client", "ShellRenderer.java");

	private static final String ON_RENDER_METHOD = "public static void onRender(Vec3 cameraPos) {";
	private static final String DRAW_GEOMETRY_METHOD = "private static void drawGeometry(RenderPass pass, ShellBuffer buffer,";
	private static final String PER_ENTRY_LOOP = "for (ShellEntry current : toDraw) {";
	private static final String DRAW_METHOD_DECLARATION =
			"private static void draw(ShellEntry current, ShellBuffer faces, Vec3 camera) {";
	private static final String DRAW_CALL_SITE = "draw(current, faces, cameraPos)";
	private static final String PER_SENSOR_TO_DRAW = "new ArrayList<>(entries.values())";

	@Test
	void onRenderBuildsThePerSensorDrawListFromEveryCurrentEntryWithNoTruncation() throws IOException {
		String body = bodyOf(read(), ON_RENDER_METHOD);

		assertTrue(body.contains(PER_SENSOR_TO_DRAW),
				"the PER_SENSOR branch of onRender's toDraw ternary must be exactly "
						+ "\"" + PER_SENSOR_TO_DRAW + "\" - every entry currently held, snapshotted "
						+ "into a fresh list for this frame. A truncation here (subList, a stream "
						+ "limit, an index bound) would silently drop entries past whatever the "
						+ "limit was, on top of whatever RadiusAudit's own cap already enforced.");

		assertFalse(body.contains(".subList(") || body.contains(".limit("),
				"onRender must not truncate toDraw itself - any cap belongs to RadiusAudit's "
						+ "selection layer (T2), not to how many of the currently-held entries get "
						+ "a draw call.");
	}

	@Test
	void drawHasExactlyOneCallSiteAndItIsOncePerEntryInOnRendersLoop() throws IOException {
		String source = read();

		assertEquals(1, countOccurrences(source, DRAW_METHOD_DECLARATION),
				DRAW_METHOD_DECLARATION + " should have exactly one declaration in this file. If "
						+ "its signature changed, update this test's marker too.");

		// Exactly one call site for the exact call onRender's loop makes - a second occurrence
		// anywhere in the file would mean another path can also issue a shell's draw call, which
		// is exactly the kind of accidental double-draw or cross-sensor batching ADR-014 rules
		// out. (A plain "draw(" substring count would also match the word inside an unrelated
		// comment elsewhere in the file, so the exact call expression is matched instead.)
		assertEquals(1, countOccurrences(source, DRAW_CALL_SITE),
				"\"" + DRAW_CALL_SITE + "\" should appear exactly once in this file. A second "
						+ "occurrence would mean a second, unaccounted-for path can trigger a "
						+ "shell draw.");

		String onRenderBody = bodyOf(source, ON_RENDER_METHOD);
		String loopBody = bodyOf(onRenderBody, PER_ENTRY_LOOP);

		assertTrue(loopBody.contains(DRAW_CALL_SITE),
				"the one draw() call must be inside onRender's per-entry loop, drawing the "
						+ "current iteration's own entry and its own buffer - not a hoisted or "
						+ "shared value from outside the loop.");
	}

	@Test
	void drawGeometryBindsTheBufferItWasHandedNotAStaticOrSharedOne() throws IOException {
		String body = bodyOf(read(), DRAW_GEOMETRY_METHOD);

		assertTrue(body.contains("buffer.buffer().slice()"),
				"drawGeometry must bind the vertex buffer from its own \"buffer\" parameter - the "
						+ "one each caller passes for its own entry - so two sensors calling this "
						+ "method in the same frame can never end up bound to the same GpuBuffer.");
	}

	/**
	 * A guard on the guard: if the class is renamed or moved, this test class fails loudly rather
	 * than passing over a file it never found.
	 */
	private static String read() throws IOException {
		if (!Files.isRegularFile(SOURCE)) {
			return fail(SOURCE.toAbsolutePath() + " is not there. If ShellRenderer moved, move "
					+ "this test's path with it - Batch 4 (T6) is what it guards.");
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
