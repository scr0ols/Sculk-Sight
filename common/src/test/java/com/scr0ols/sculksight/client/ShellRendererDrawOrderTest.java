package com.scr0ols.sculksight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

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

	private static String read() throws IOException {
		if (!Files.isRegularFile(SOURCE)) {
			return fail(SOURCE.toAbsolutePath() + " is not there. If ShellRenderer moved, move "
					+ "this test's path with it - Batch 5 (T7) is what it guards.");
		}

		return Files.readString(SOURCE, StandardCharsets.UTF_8);
	}

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
