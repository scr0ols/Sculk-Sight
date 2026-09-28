package com.scr0ols.sculksight.client;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Source-text regression coverage for how {@code draw} and {@code drawGeometry} skip the
 * see-through pass. {@link ShellRenderer} draws on the render thread with real GPU state and
 * cannot be instantiated in a unit test, so - like the other {@code ShellRenderer*Test} classes
 * in this package - this reads the source file directly. The see-through pass is permanently
 * disabled - the shell must never render anything through geometry that blocks direct line of
 * sight - and {@code ShellStyleTest} covers directly that {@code skipsSeeThroughPass()} always
 * returns {@code true}.
 */
class ShellRendererSeeThroughSkipTest {

	private static final Path SOURCE = Path.of("src", "client", "java", "com", "scr0ols",
			"sculksight", "client", "ShellRenderer.java");

	private static final String DRAW_METHOD_DECLARATION =
			"private static void draw(ShellEntry current, ShellBuffer faces, Vec3 camera) {";

	private static final String DRAW_GEOMETRY_METHOD =
			"private static void drawGeometry(RenderPass pass, ShellBuffer buffer,";

	@Test
	void drawAsksTheStyleWhetherTheSeeThroughPassCanBeSkipped() throws IOException {
		String body = bodyOf(read(), DRAW_METHOD_DECLARATION);

		assertTrue(body.contains("skipsSeeThroughPass("),
				"draw() must ask the style whether the see-through pass can be skipped, so the "
						+ "permanently-disabled pass actually avoids the GPU draw call instead of "
						+ "just zeroing its alpha.");
	}

	@Test
	void theSkipDecisionTakesNoCameraStateSinceItIsUnconditional() throws IOException {
		String body = bodyOf(read(), DRAW_METHOD_DECLARATION);

		assertTrue(body.contains("skipsSeeThroughPass()"),
				"skipsSeeThroughPass() must be called with no arguments: the see-through pass is "
						+ "permanently disabled regardless of camera position, so the decision no "
						+ "longer depends on \"inside\" or any other per-frame state.");
	}

	@Test
	void drawGeometryTakesTheSkipDecisionAsItsOwnParameter() throws IOException {
		String body = bodyOf(read(), DRAW_GEOMETRY_METHOD);

		assertTrue(body.contains("skipFirstPass"),
				"drawGeometry must take the skip decision as a parameter, rather than deciding for "
						+ "itself - the decision belongs to ShellStyle, which already knows the "
						+ "configured mode and the camera-inside state.");
	}

	@Test
	void onlyTheFirstOfTheTwoDrawIndexedCallsIsEverSkipped() throws IOException {
		String body = bodyOf(read(), DRAW_GEOMETRY_METHOD);

		int firstDraw = body.indexOf("drawIndexed");
		int secondDraw = body.indexOf("drawIndexed", firstDraw + 1);

		assertTrue(firstDraw >= 0 && secondDraw > firstDraw,
				"drawGeometry should still issue exactly two drawIndexed calls in its body.");

		int skipCheck = body.indexOf("skipFirstPass");

		assertTrue(skipCheck >= 0 && skipCheck < firstDraw,
				"the skip check must guard the first drawIndexed call (the see-through pass), not "
						+ "be introduced after it.");

		String betweenTheTwoDraws = body.substring(firstDraw, secondDraw);

		assertTrue(!betweenTheTwoDraws.contains("skipFirstPass")
						|| betweenTheTwoDraws.indexOf("skipFirstPass") > betweenTheTwoDraws.indexOf("}"),
				"the depth-tested pass (the second drawIndexed call) must run unconditionally: "
						+ "skipping only ever applies to the see-through pass.");
	}

	private static String read() throws IOException {
		if (!Files.isRegularFile(SOURCE)) {
			return fail(SOURCE.toAbsolutePath() + " is not there. If ShellRenderer moved, move "
					+ "this test's path with it.");
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
}
