package com.scr0ols.sculksight.solver;

/** Whether one position lies within one sensor's effective range. */
public final class SensorDetector {

	private SensorDetector() {
	}

	/** True if a vibration originating at {@code (x, y, z)} could reach the sensor: in range and not occluded. */
	public static boolean isDetectedAt(WorldView world,
			int x, int y, int z,
			int sensorX, int sensorY, int sensorZ,
			int radius) {

		if (!isInRange(x, y, z, sensorX, sensorY, sensorZ, radius)) {
			return false;
		}

		return !OcclusionTest.isOccluded(world, x, y, z, sensorX, sensorY, sensorZ);
	}

	/** The range test on its own: an inclusive squared-distance comparison, touching no world state. */
	public static boolean isInRange(int x, int y, int z,
			int sensorX, int sensorY, int sensorZ,
			int radius) {

		int dx = x - sensorX;
		int dy = y - sensorY;
		int dz = z - sensorZ;

		return dx * dx + dy * dy + dz * dz <= radius * radius;
	}
}
