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

class ShellRendererDrawCallTest {

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

	private static String read() throws IOException {
		if (!Files.isRegularFile(SOURCE)) {
			return fail(SOURCE.toAbsolutePath() + " is not there. If ShellRenderer moved, move "
					+ "this test's path with it - Batch 4 (T6) is what it guards.");
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
