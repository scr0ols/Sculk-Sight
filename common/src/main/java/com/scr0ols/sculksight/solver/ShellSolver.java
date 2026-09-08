package com.scr0ols.sculksight.solver;

/**
 * Produces the detection set for one sensor: the positions from which a vibration can actually
 * reach it. Specified in ARCHITECTURE.md section 4.2.
 *
 * <p>Runs synchronously, on whatever thread calls it. The threading model of ARCHITECTURE.md
 * section 6.2 puts this on a worker, but nothing in this class knows or cares - it is a pure
 * function of its arguments, so wrapping it in an executor later changes nothing here.
 */
public final class ShellSolver {

	private ShellSolver() {
	}

	/**
	 * Solves with no position filtering, which is v0.0's behaviour: the full geometric truth,
	 * claiming nothing about what can happen at a position.
	 *
	 * <p>A thin wrapper over {@link #solveDetailed(WorldView, int, int, int, int)} that keeps
	 * only the accepted set. Callers that only ever wanted the shell - the render path, chiefly -
	 * are unaffected by {@link ShellSolution} existing at all.
	 */
	public static DetectionSet solve(WorldView world, int sensorX, int sensorY, int sensorZ, int radius) {
		return solveDetailed(world, sensorX, sensorY, sensorZ, radius).accepted();
	}

	/** As {@link #solve(WorldView, int, int, int, int)}, with a position filter. */
	public static DetectionSet solve(WorldView world, int sensorX, int sensorY, int sensorZ, int radius,
			PositionFilter filter) {
		return solveDetailed(world, sensorX, sensorY, sensorZ, radius, filter).accepted();
	}

	/**
	 * As {@link #solveDetailed(WorldView, int, int, int, int, PositionFilter)}, with no position
	 * filtering.
	 */
	public static ShellSolution solveDetailed(WorldView world, int sensorX, int sensorY, int sensorZ, int radius) {
		return solveDetailed(world, sensorX, sensorY, sensorZ, radius, PositionFilter.ACCEPT_ALL);
	}

	/**
	 * Sweeps the bounding cube of the given radius, keeps positions passing the inclusive
	 * squared-distance test (R2) and the filter, and splits those into accepted and
	 * occluded-out depending on the six-ray rule (R4).
	 *
	 * <p><b>The range test is inclusive, on integers, and that is not a design choice.</b> It
	 * is what {@code EuclideanGameEventListenerRegistry#getPostableListenerPosition} compares,
	 * operand types included: it rejects on strictly greater, so a position at exactly
	 * {@code distSqr == radiusSqr} passes (R2 points 1-2, R12 point 1). Writing {@code <}
	 * instead of {@code <=} would silently drop the outermost shell - precisely the surface
	 * this mod exists to draw.
	 *
	 * <p><b>The ordering is for cost, and the cheap test goes first.</b> The squared-distance
	 * test rejects about 57% of the cube at radius 8 and 52% at radius 16 (R2), and it touches
	 * no world state at all. Only survivors pay for rays, and the six-ray rule short-circuits
	 * on the first ray that gets through, so open air costs one ray per position rather than
	 * six. The filter runs between the two because it is cheaper than a ray and dearer than
	 * arithmetic.
	 *
	 * <p><b>{@link ShellSolution} costs nothing extra here.</b> Every candidate that reaches the
	 * occlusion test already produces a boolean this method used to discard on the accepted
	 * path; recording which of the two outcomes it was, instead of throwing away the false one,
	 * is the entire difference from the plain-{@link DetectionSet} form. No additional ray is
	 * cast and no candidate is visited twice.
	 */
	public static ShellSolution solveDetailed(WorldView world, int sensorX, int sensorY, int sensorZ, int radius,
			PositionFilter filter) {

		DetectionSet accepted = new DetectionSet(radius);
		DetectionSet occludedOut = new DetectionSet(radius);
		final int radiusSqr = radius * radius;

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (dx * dx + dy * dy + dz * dz > radiusSqr) {
						continue;
					}

					if (!filter.keep(dx, dy, dz)) {
						continue;
					}

					// The candidate position is the vibration source and the sensor is the
					// destination. The six-ray rule nudges only the source, so this order is
					// part of the rule rather than a convention - see OcclusionTest.
					boolean occluded = OcclusionTest.isOccluded(world,
							sensorX + dx, sensorY + dy, sensorZ + dz,
							sensorX, sensorY, sensorZ);

					if (occluded) {
						occludedOut.add(dx, dy, dz);
					} else {
						accepted.add(dx, dy, dz);
					}
				}
			}
		}

		return new ShellSolution(accepted, occludedOut);
	}
}
