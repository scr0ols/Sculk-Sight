package com.scr0ols.sculksight.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Source-text regression coverage for the two shell pipelines' depth state. {@link ShellPipelines}
 * builds real {@code RenderPipeline} objects backed by GPU-facing Minecraft classes at class-load
 * time and cannot be safely instantiated in a unit test - like the {@code ShellRenderer*Test}
 * classes in this package, this reads the source file directly.
 *
 * <p>This covers the "complementary depth tests" change: the see-through pass used to draw with no
 * depth test at all (so it painted over the whole screen, including where the depth-tested pass had
 * already drawn), and now draws with the exact complement of the depth-tested pass's comparison, so
 * every pixel the shell touches is drawn by exactly one of the two passes.
 */
class ShellPipelinesDepthTest {

	private static final Path SOURCE = Path.of("src", "client", "java", "com", "scr0ols",
			"sculksight", "client", "ShellPipelines.java");

	private static final String DEPTH_TESTED_FIELD =
			"public static final RenderPipeline FACES_DEPTH_TESTED = RenderPipeline";

	private static final String SEE_THROUGH_FIELD =
			"public static final RenderPipeline FACES_SEE_THROUGH = RenderPipeline";

	@Test
	void depthTestedPassUsesTheOrdinaryInFrontOrEqualComparison() throws IOException {
		String body = fieldInitializerOf(read(), DEPTH_TESTED_FIELD);

		assertTrue(body.contains("CompareOp.GREATER_THAN_OR_EQUAL"),
				"the depth-tested pass must keep the ordinary reverse-Z \"in front of or equal to "
						+ "what's already there\" comparison - it is the visible half of the "
						+ "complementary partition.");
	}

	@Test
	void seeThroughPassUsesTheExactComplementOfTheDepthTestedComparison() throws IOException {
		String body = fieldInitializerOf(read(), SEE_THROUGH_FIELD);

		assertFalse(body.contains("Optional.empty()"),
				"the see-through pass must no longer draw with no depth test at all - that painted "
						+ "over the whole screen, including pixels the depth-tested pass had already "
						+ "drawn, which is exactly the over-composited-alpha bug this change fixes.");

		assertTrue(body.contains("CompareOp.LESS_THAN"),
				"the see-through pass must use CompareOp.LESS_THAN, the complement of "
						+ "GREATER_THAN_OR_EQUAL under reverse-Z: a fragment is either in front of or "
						+ "equal to what's already drawn (GREATER_THAN_OR_EQUAL, the depth-tested "
						+ "pass), or strictly behind it (LESS_THAN, the see-through pass) - never "
						+ "both, never neither.");
	}

	@Test
	void neitherPassWritesDepth() throws IOException {
		String source = read();

		String depthTestedBody = fieldInitializerOf(source, DEPTH_TESTED_FIELD);
		String seeThroughBody = fieldInitializerOf(source, SEE_THROUGH_FIELD);

		assertTrue(depthTestedBody.contains("false, DEPTH_BIAS_SCALE"),
				"the depth-tested pass must keep depth write off - the shell never occludes world "
						+ "geometry.");
		assertTrue(seeThroughBody.contains("false, DEPTH_BIAS_SCALE"),
				"the see-through pass must not write depth either, for the same reason.");
	}

	@Test
	void bothPassesShareTheSameDepthBiasSoTheComplementHoldsExactlyAtCoplanarFaces() throws IOException {
		String source = read();

		String depthTestedBody = fieldInitializerOf(source, DEPTH_TESTED_FIELD);
		String seeThroughBody = fieldInitializerOf(source, SEE_THROUGH_FIELD);

		assertTrue(depthTestedBody.contains("DEPTH_BIAS_SCALE, DEPTH_BIAS_CONSTANT"),
				"the depth-tested pass must use the shared bias constants.");
		assertTrue(seeThroughBody.contains("DEPTH_BIAS_SCALE, DEPTH_BIAS_CONSTANT"),
				"the see-through pass must use the same shared bias constants as the depth-tested "
						+ "pass - a mismatched bias would shift the two comparisons apart and let a "
						+ "sliver of near-coplanar fragments be drawn by both passes (or neither).");
	}

	private static String read() throws IOException {
		if (!Files.isRegularFile(SOURCE)) {
			return fail(SOURCE.toAbsolutePath() + " is not there. If ShellPipelines moved, move "
					+ "this test's path with it.");
		}

		return Files.readString(SOURCE, StandardCharsets.UTF_8);
	}

	private static String fieldInitializerOf(String source, String marker) {
		int start = markerIn(source, marker);
		int end = source.indexOf(".build()", start);

		if (end < 0) {
			return fail("no \".build()\" found after " + marker + " in " + SOURCE);
		}

		return source.substring(start, end);
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
