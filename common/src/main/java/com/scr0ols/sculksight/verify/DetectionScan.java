package com.scr0ols.sculksight.verify;

import com.scr0ols.sculksight.solver.DetectionSet;
import com.scr0ols.sculksight.solver.SensorDetector;
import com.scr0ols.sculksight.solver.ShellSolution;
import com.scr0ols.sculksight.solver.ShellSolver;
import com.scr0ols.sculksight.solver.WorldView;

/** Classifies every position in a sensor's cube using the mod's own detection code. */
public final class DetectionScan {

	private DetectionScan() {
	}

	/** Classifies every position in the sensor's bounding cube by asking {@link SensorDetector}. */
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
