package com.scr0ols.sculksight.solver;

/**
 * Mode C's whole solver-side surface: is one position within one sensor's effective range.
 *
 * <p>PLAN.md section 3.4 fixes this as "one raycast per sensor - player to sensor - not the
 * thousands a full shell requires", and ARCHITECTURE.md section 3.3 agrees: mode C needs no
 * {@link DetectionSet}, no {@link BoundaryFaceExtractor}, no mesh. It needs exactly the two
 * tests {@link ShellSolver} already applies to every candidate position, run once against the
 * single position that matters - the one asking "am I detected".
 *
 * <p><b>This is not a new rule.</b> The range test is R2's inclusive squared-distance
 * comparison, written identically to {@link ShellSolver#solveDetailed}'s inner loop. The
 * occlusion test is {@link OcclusionTest#isOccluded}, called in the same argument order the
 * solver uses: the candidate position is the vibration source, the sensor is the destination,
 * and only the source is nudged (R4). A caller here plays the same role a boundary-face
 * candidate plays there - nothing about the geometry changes because the candidate happens to
 * be a player rather than a lattice point.
 */
public final class SensorDetector {

	private SensorDetector() {
	}

	/**
	 * True if a vibration originating at {@code (x, y, z)} could reach the sensor at
	 * {@code (sensorX, sensorY, sensorZ)} with the given radius: in range (R2, inclusive) and
	 * not occluded (R4, the six-ray rule).
	 *
	 * <p>The range test runs first and touches no world state, exactly as
	 * {@link ShellSolver#solveDetailed} orders its own two tests and for the same reason: it is
	 * the cheap rejection, so a sensor far outside its own radius costs one comparison rather
	 * than up to six rays.
	 */
	public static boolean isDetectedAt(WorldView world,
			int x, int y, int z,
			int sensorX, int sensorY, int sensorZ,
			int radius) {

		if (!isInRange(x, y, z, sensorX, sensorY, sensorZ, radius)) {
			return false;
		}

		return !OcclusionTest.isOccluded(world, x, y, z, sensorX, sensorY, sensorZ);
	}

	/**
	 * R2's range test on its own: the inclusive squared-distance comparison, touching no world
	 * state and casting no ray.
	 *
	 * <p><b>This was extracted from {@link #isDetectedAt} rather than copied out of it, and the
	 * distinction is the point.</b> Differential verification for mode C has to tell the two
	 * ways a position can fail apart, out of range against in range but occluded, because
	 * {@code PredictedClass} distinguishes them and `OPEN-QUESTIONS.md` section 13 is why it
	 * does. A caller could recover that distinction by writing the comparison a second time, and
	 * mode C would then hold two copies of R2 free to drift apart without anything noticing. It
	 * asks this method instead, and {@link #isDetectedAt} is defined in terms of the same one,
	 * so the formula has exactly one home on this path.
	 *
	 * <p>Inclusive, per R2 points 1-2: rejection is on strictly greater, so a position at
	 * exactly {@code distSqr == radiusSqr} is in range.
	 */
	public static boolean isInRange(int x, int y, int z,
			int sensorX, int sensorY, int sensorZ,
			int radius) {

		int dx = x - sensorX;
		int dy = y - sensorY;
		int dz = z - sensorZ;

		return dx * dx + dy * dy + dz * dz <= radius * radius;
	}
}
