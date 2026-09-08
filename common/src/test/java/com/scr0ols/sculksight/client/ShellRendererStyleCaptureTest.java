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
 * The one automated check available for OPEN-QUESTIONS.md section 22.1, and it reads source text
 * rather than running anything.
 *
 * <p><b>Why this is a source test and not an ordinary one.</b> The defect is that
 * {@code ShellRenderer.solveAndEncode}, which DECISIONS.md ADR-046 runs on the worker thread, read
 * the {@code static} {@code style} field that {@code onConfigChanged} writes on the client thread,
 * with no happens-before edge between them. The fix is that the style is captured in
 * {@code runSolve} and handed over as a parameter, the way the snapshot already travels under
 * RESEARCH-LOG.md R16 and ADR-048. That is a property of where a name appears in one method, and
 * there is no way to observe it from JUnit here: {@code ShellRenderer} lives in
 * {@code common/src/client/java}, which this module's own build does not compile at all (ADR-044,
 * and the reason ADR-048 had to move {@code ShellUploadSlot} into {@code src/main/java} before it
 * could be tested). The class is only ever compiled by {@code fabric} and {@code neoforge}, neither
 * of which has a test source set.
 *
 * <p>So the choice was between this and nothing, and nothing is the worse of the two. A data race
 * is not something a passing suite would have caught even with the class on the classpath - it
 * would have needed a stress test that fails by timing rather than by assertion - whereas the
 * property that actually closes the hole is exactly the one a reader checks by eye, and this test
 * checks it every build instead.
 *
 * <p><b>What it does not prove.</b> That the parameter is the style the encode ends up using is
 * still the compiler's job, and the three-module build is what runs it. This test proves only that
 * the worker's method does not reach back for the field, which is the half a compiler cannot see.
 * It also matches text, so a comment inside that method writing {@code style()} would fail it - a
 * false alarm being much the cheaper mistake here than a missed one.
 */
class ShellRendererStyleCaptureTest {

	/**
	 * Where the class sits relative to this module. A Gradle {@code Test} task runs in its own
	 * project directory, so this resolves from {@code common/}.
	 */
	private static final Path SOURCE = Path.of("src", "client", "java", "com", "scr0ols",
			"sculksight", "client", "ShellRenderer.java");

	private static final String WORKER_METHOD = "private static void solveAndEncode(";

	@Test
	void theWorkerMethodTakesTheStyleAsAParameter() throws IOException {
		String signature = signatureOf(read(), WORKER_METHOD);

		assertTrue(signature.contains("ShellStyle style"),
				"solveAndEncode must be handed the style captured on the client thread, not go "
						+ "looking for it; signature was: " + signature);
	}

	@Test
	void theWorkerMethodNeverReadsTheSharedStyleField() throws IOException {
		String body = bodyOf(read(), WORKER_METHOD);

		assertFalse(body.contains("style()"),
				"solveAndEncode runs on the worker thread (ADR-046) and style() reads a field the "
						+ "client thread writes on a settings save (ADR-058). Use the captured "
						+ "parameter. OPEN-QUESTIONS.md section 22.1.");
	}

	/**
	 * A guard on the guard: if the class is renamed or moved, this test class fails loudly rather
	 * than passing over a file it never found.
	 */
	private static String read() throws IOException {
		if (!Files.isRegularFile(SOURCE)) {
			return fail(SOURCE.toAbsolutePath() + " is not there. If ShellRenderer moved, move this "
					+ "test's path with it - OPEN-QUESTIONS.md section 22.1 is what it guards.");
		}

		return Files.readString(SOURCE, StandardCharsets.UTF_8);
	}

	/** The declaration's parameter list, from the opening bracket to the brace that follows it. */
	private static String signatureOf(String source, String declaration) {
		int start = declarationIn(source, declaration);

		int open = source.indexOf('{', start);

		return source.substring(start, open).strip();
	}

	/** The declaration's body, brace-matched from its opening brace to its closing one. */
	private static String bodyOf(String source, String declaration) {
		int open = source.indexOf('{', declarationIn(source, declaration));

		int depth = 0;

		for (int at = open; at < source.length(); at++) {
			char c = source.charAt(at);

			if (c == '{') {
				depth++;
			} else if (c == '}' && --depth == 0) {
				return source.substring(open, at + 1);
			}
		}

		return fail("the body of " + declaration + " has no closing brace");
	}

	private static int declarationIn(String source, String declaration) {
		int at = source.indexOf(declaration);

		if (at < 0) {
			return fail(declaration + " is no longer in " + SOURCE + ". If it was renamed, rename it "
					+ "here too - OPEN-QUESTIONS.md section 22.1 is what this guards.");
		}

		return at;
	}
}
