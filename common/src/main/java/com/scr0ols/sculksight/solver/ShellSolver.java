package com.scr0ols.sculksight.solver;

/** Produces the detection set for one sensor: the positions from which a vibration can reach it. */
public final class ShellSolver {

	private ShellSolver() {
	}

	/** Solves with no position filtering and keeps only the accepted set. */
	public static DetectionSet solve(WorldView world, int sensorX, int sensorY, int sensorZ, int radius) {
		return solveDetailed(world, sensorX, sensorY, sensorZ, radius).accepted();
	}

	/** As {@link #solve(WorldView, int, int, int, int)}, with a position filter. */
	public static DetectionSet solve(WorldView world, int sensorX, int sensorY, int sensorZ, int radius,
			PositionFilter filter) {
		return solveDetailed(world, sensorX, sensorY, sensorZ, radius, filter).accepted();
	}

	/** As {@link #solveDetailed(WorldView, int, int, int, int, PositionFilter)}, with no position filtering. */
	public static ShellSolution solveDetailed(WorldView world, int sensorX, int sensorY, int sensorZ, int radius) {
		return solveDetailed(world, sensorX, sensorY, sensorZ, radius, PositionFilter.ACCEPT_ALL);
	}

	/** Sweeps the bounding cube and splits the positions that pass range and filter into accepted and occluded-out. */
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
