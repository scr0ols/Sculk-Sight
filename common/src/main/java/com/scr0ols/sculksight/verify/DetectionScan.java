package com.scr0ols.sculksight.verify;

import com.scr0ols.sculksight.solver.DetectionSet;
import com.scr0ols.sculksight.solver.SensorDetector;
import com.scr0ols.sculksight.solver.ShellSolution;
import com.scr0ols.sculksight.solver.ShellSolver;
import com.scr0ols.sculksight.solver.WorldView;

/**
 * Mode C's prediction, in the shape {@link DifferentialVerifier} already consumes.
 *
 * <p><b>Why this exists at all, when {@link ShellSolver#solveDetailed} produces the same type.</b>
 * TESTING-STRATEGY.md section 4's v0.1 phase gate names "both modes", and mode A's own passing
 * run says nothing about mode C, because mode C does not call the shell solver. It calls
 * {@link SensorDetector}, a separate piece of code that ADR-039 point 1 deliberately made
 * separate. Two implementations of the same rule can agree with the documents and disagree with
 * each other, and only something that runs mode C's own code against the game would notice. This
 * class runs mode C's own code: every position in the cube is classified by the two methods
 * {@code DetectionIndicator} itself calls, and nothing here reimplements either test.
 *
 * <p><b>What this therefore verifies, and what it does not.</b> A clean run establishes that
 * {@link SensorDetector}'s answer agrees with the running game across the sampled positions - the
 * geometry half of mode C, which is the half TESTING-STRATEGY.md section 1's circularity problem
 * applies to and the half that reaches the {@code traverseBlocks} seam of ARCHITECTURE.md section
 * 2.2. It establishes nothing about {@code SensorIndex}: whether the index holds the sensors it
 * should, and whether the indicator's short-circuiting loop over the index aggregates them
 * correctly, are separate claims this mechanism does not test and does not pretend to. The same
 * boundary mode A's command already draws between the solver and the renderer, drawn in the same
 * place for the same reason.
 *
 * <p><b>Eager rather than lazy, and R16 is why.</b> {@link DifferentialVerifier} probes on the
 * server thread, in one hop for a whole run (see {@code VerificationCommand}). A predictor that
 * answered by reading the {@code ClientLevel} when asked would therefore be reading the live
 * client level from the server thread, which R16 establishes is not safe: no synchronisation
 * exists between a reader and a writer anywhere on that path. Scanning the cube up front, on the
 * client thread, leaves the verifier a value rather than a world reference, and the question does
 * not arise. This is the same reason mode A's command solves before it submits.
 */
public final class DetectionScan {

	private DetectionScan() {
	}

	/**
	 * Classifies every position in the sensor's bounding cube by asking {@link SensorDetector}.
	 *
	 * <p>The partition is the one {@link ShellSolution} documents: accepted, occluded-out, and
	 * an out-of-range third class that needs no storage because it is whatever is in neither.
	 * The ordering is {@link SensorDetector}'s own - the cheap range test first, so a corner of
	 * the cube costs one comparison rather than up to six rays - and it is preserved here rather
	 * than reordered, because a scan that visited the two tests in a different order from the
	 * indicator would be verifying something the indicator does not do.
	 *
	 * <p>The result is directly comparable with {@link ShellSolver#solveDetailed}'s over the same
	 * world and sensor, and {@code DetectionScanTest} asserts exactly that. That comparison is a
	 * JUnit-level check of two of this project's own components against each other, which is what
	 * TESTING-STRATEGY.md section 2 says unit tests are for and is not evidence about the game.
	 */
	public static ShellSolution scan(WorldView world, int sensorX, int sensorY, int sensorZ, int radius) {
		DetectionSet accepted = new DetectionSet(radius);
		DetectionSet occludedOut = new DetectionSet(radius);

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					int x = sensorX + dx;
					int y = sensorY + dy;
					int z = sensorZ + dz;

					if (!SensorDetector.isInRange(x, y, z, sensorX, sensorY, sensorZ, radius)) {
						continue;
					}

					if (SensorDetector.isDetectedAt(world, x, y, z, sensorX, sensorY, sensorZ, radius)) {
						accepted.add(dx, dy, dz);
					} else {
						occludedOut.add(dx, dy, dz);
					}
				}
			}
		}

		return new ShellSolution(accepted, occludedOut);
	}
}
