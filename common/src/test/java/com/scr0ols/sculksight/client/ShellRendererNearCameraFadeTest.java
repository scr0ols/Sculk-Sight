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
 * Source-text regression coverage for how {@code draw} wires {@link NearCameraFade} into the
 * see-through pass's alpha. {@link ShellRenderer} draws on the render thread with real GPU state
 * and cannot be instantiated in a unit test, so - like the other {@code ShellRenderer*Test}
 * classes in this package - this reads the source file directly. {@code NearCameraFade}'s own
 * math and raycast are covered directly in {@code NearCameraFadeTest}.
 */
class ShellRendererNearCameraFadeTest {

	private static final Path SOURCE = Path.of("src", "client", "java", "com", "scr0ols",
			"sculksight", "client", "ShellRenderer.java");

	private static final String DRAW_METHOD_DECLARATION =
			"private static void draw(ShellEntry current, ShellBuffer faces, Vec3 camera) {";

	@Test
	void theFadeFactorIsOnlyComputedWhileTheCameraIsInsideTheShell() throws IOException {
		String body = bodyOf(read(), DRAW_METHOD_DECLARATION);

		assertTrue(body.contains("inside ? nearCameraFadeFactor(camera) : 1.0F"),
				"draw() must only compute the near-camera fade while inside the shell being drawn "
						+ "- outside that case it must be the neutral multiplier 1.0F, so the shell "
						+ "seen dimly through a distant wall from outside is completely unaffected.");
	}

	@Test
	void theFadeFactorMultipliesOnlyTheSeeThroughModulationNotTheDepthTestedOne() throws IOException {
		String body = bodyOf(read(), DRAW_METHOD_DECLARATION);

		assertTrue(body.contains("detectorStyle.faceModulation(true, inside) * nearCameraFade"),
				"the near-camera fade must multiply the see-through modulation (the first, "
						+ "seeThrough=true, call) - it must never touch the depth-tested pass, which "
						+ "already only draws where the shell has direct line of sight.");

		assertFalse(body.contains("detectorStyle.faceModulation(false, inside) * nearCameraFade"),
				"the depth-tested modulation (seeThrough=false) must not be multiplied by the "
						+ "near-camera fade - that pass is unrelated to the see-through wash-out this "
						+ "fades.");
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
