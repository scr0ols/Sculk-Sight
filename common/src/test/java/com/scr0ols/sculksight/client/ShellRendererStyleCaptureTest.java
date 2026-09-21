package com.scr0ols.sculksight.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ShellRendererStyleCaptureTest {

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

	private static String read() throws IOException {
		if (!Files.isRegularFile(SOURCE)) {
			return fail(SOURCE.toAbsolutePath() + " is not there. If ShellRenderer moved, move this "
					+ "test's path with it - OPEN-QUESTIONS.md section 22.1 is what it guards.");
		}

		return Files.readString(SOURCE, StandardCharsets.UTF_8);
	}

	private static String signatureOf(String source, String declaration) {
		int start = declarationIn(source, declaration);

		int open = source.indexOf('{', start);

		return source.substring(start, open).strip();
	}

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
