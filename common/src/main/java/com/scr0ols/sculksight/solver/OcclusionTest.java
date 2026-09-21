package com.scr0ols.sculksight.solver;

/** Vanilla's vibration-occlusion decision: the source-below dampening check and the six-ray rule. */
public final class OcclusionTest {

	static final float NUDGE = 1.0E-5F;

	private OcclusionTest() {
	}

	/** True if either vanilla rule suppresses a vibration from the source {@code from} to the sensor {@code to}. */
	public static boolean isOccluded(WorldView world,
			int fromX, int fromY, int fromZ,
			int toX, int toY, int toZ) {
		if (world.dampensVibrationsBelow(fromX, fromY, fromZ)) {
			return true;
		}

		final double sourceX = fromX + 0.5;
		final double sourceY = fromY + 0.5;
		final double sourceZ = fromZ + 0.5;

		final double destX = toX + 0.5;
		final double destY = toY + 0.5;
		final double destZ = toZ + 0.5;

		for (Face face : Face.allWithoutCopy()) {
			boolean blocked = world.occluderOnSegment(
					sourceX + face.stepX() * NUDGE,
					sourceY + face.stepY() * NUDGE,
					sourceZ + face.stepZ() * NUDGE,
					destX, destY, destZ);

			if (!blocked) {
				return false;
			}
		}

		return true;
	}
}
