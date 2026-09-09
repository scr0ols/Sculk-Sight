package com.scr0ols.sculksight.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import com.scr0ols.sculksight.solver.DetectionSet;
import com.scr0ols.sculksight.solver.ShellSolution;
import com.scr0ols.sculksight.timing.DelayModel;

/**
 * Immutable, sensor-relative data for one delay-label solve.
 *
 * <p>The solver's two sets are deliberately kept separate in the parallel {@code occluded}
 * array. A label is needed for both sets, while the colour tells the player whether the sensor
 * itself can hear that position. Delays and world anchors are built once when the worker publishes
 * the solve, rather than recomputed while the render thread submits text every frame.
 */
final class DelayOverlay {

	private final Vec3[] anchors;
	private final String[] texts;
	private final boolean[] occluded;

	private DelayOverlay(Vec3[] anchors, String[] texts, boolean[] occluded) {
		this.anchors = anchors;
		this.texts = texts;
		this.occluded = occluded;
	}

	static DelayOverlay from(SensorKey sensor, ShellSolution solution) {
		DetectionSet accepted = solution.accepted();
		DetectionSet occludedOut = solution.occludedOut();
		int count = accepted.size() + occludedOut.size();

		Vec3[] anchors = new Vec3[count];
		String[] texts = new String[count];
		boolean[] occluded = new boolean[count];
		BlockPos sensorPos = new BlockPos(sensor.x(), sensor.y(), sensor.z());
		int radius = solution.radius();
		int index = 0;

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					boolean sensorAccepts = accepted.contains(dx, dy, dz);
					boolean sensorOccludes = occludedOut.contains(dx, dy, dz);

					if (!sensorAccepts && !sensorOccludes) {
						continue;
					}

					BlockPos candidate = new BlockPos(sensor.x() + dx, sensor.y() + dy, sensor.z() + dz);
					anchors[index] = Vec3.atCenterOf(candidate);
					texts[index] = Integer.toString(DelayModel.ticksBetween(candidate, sensorPos));
					occluded[index] = sensorOccludes;
					index++;
				}
			}
		}

		if (index != count) {
			throw new IllegalStateException("delay overlay count changed while building: " + index
					+ " instead of " + count);
		}

		return new DelayOverlay(anchors, texts, occluded);
	}

	int size() {
		return anchors.length;
	}

	Vec3 anchor(int index) {
		return anchors[index];
	}

	String text(int index) {
		return texts[index];
	}

	boolean isSensorOccluded(int index) {
		return occluded[index];
	}
}
