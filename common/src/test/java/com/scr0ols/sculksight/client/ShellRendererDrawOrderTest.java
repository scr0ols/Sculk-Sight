package com.scr0ols.sculksight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Pins the answer to Batch 5 (T7) sub-problem 3: {@code PER_SENSOR} mode's per-frame draw list is
 * sorted back-to-front by camera distance before anything in it is drawn, so overlapping
 * translucent shells composite with the nearest one on top (ADR-022's two low-alpha tiers are not
 * order-independent). {@link DrawOrderTest} covers the comparator's own arithmetic; this class
 * covers that {@code onRender} actually applies it, freshly each frame, and only to the branch it
 * applies to.
 *
 * <p><b>Why this is a source-text test and not an ordinary one.</b> {@code ShellRenderer} lives in
 * {@code common/src/client/java}, which this module's own build does not compile at all - the same
 * reason {@link ShellRendererDrawCallTest} and {@link ShellRendererSnapshotBudgetTest} read it as
 * text instead of importing it.
 *
 * <p><b>What it does not prove.</b> That the new order reads correctly at a live 20+-sensor scene
 * with overlapping shells - the nearest one on top, no flicker or pop as the camera moves - is a
 * render-thread, visual question this task's PR asks the author to check in-game, not something a
 * source-text test can see.
 */
class ShellRendererDrawOrderTest {

	private static final Path SOURCE = Path.of("src", "client", "java", "com", "scr0ols",
			"sculksight", "client", "ShellRenderer.java");

	private static final String ON_RENDER_METHOD = "public static void onRender(Vec3 cameraPos) {";
	private static final String SORT_CALL = "toDraw.sort(Comparator.comparing(ShellEntry::sensor,";
	private static final String CAMERA_ARGUMENT = "DrawOrder.backToFront(cameraPos.x, cameraPos.y, cameraPos.z)";
	private static final String UNION_GUARD = "if (policy != RenderPolicy.UNION) {";

	@Test
	void onRenderSortsThePerSensorDrawListBackToFrontByTheCurrentCameraPosition() throws IOException {
		String body = bodyOf(read(), ON_RENDER_METHOD);

		assertEquals(1, countOccurrences(body, SORT_CALL),
				SORT_CALL + " should appear exactly once in onRender - the sort belongs to the "
						+ "per-frame draw list this method itself builds, not to a helper called "
						+ "from somewhere else that could drift out of step with it.");

		assertTrue(body.contains(CAMERA_ARGUMENT),
				"the sort must key off cameraPos - the exact position onRender was called with "
						+ "this frame - rather than a cached or stale value. The camera moves every "
						+ "frame this shell is drawn, so a comparator built once and reused would "
						+ "sort correctly on the frame it was captured and silently drift after.");
	}

	@Test
	void theSortIsGuardedToTheNonUnionBranchOnly() throws IOException {
		String body = bodyOf(read(), ON_RENDER_METHOD);
		String guardBody = bodyOf(body, UNION_GUARD);

		assertTrue(guardBody.contains(SORT_CALL),
				"the sort must sit inside the \"policy != RenderPolicy.UNION\" branch - UNION mode "
						+ "draws one merged buffer, so a per-entry draw order does not apply to it, "
						+ "and calling List.sort on the UNION branch's immutable List.of() would "
						+ "throw UnsupportedOperationException.");
	}

	/**
	 * A guard on the guard: if the class is renamed or moved, this test class fails loudly rather
	 * than passing over a file it never found.
	 */
	private static String read() throws IOException {
		if (!Files.isRegularFile(SOURCE)) {
			return fail(SOURCE.toAbsolutePath() + " is not there. If ShellRenderer moved, move "
					+ "this test's path with it - Batch 5 (T7) is what it guards.");
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
