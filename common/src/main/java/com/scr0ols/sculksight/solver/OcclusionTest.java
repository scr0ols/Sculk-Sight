package com.scr0ols.sculksight.solver;

/**
 * The six-ray occlusion rule, reproduced from vanilla.
 *
 * <p>This class is the most correctness-critical code in the mod, and it is also the piece
 * unit tests can genuinely validate: ARCHITECTURE.md section 2.2 draws the {@link WorldView}
 * boundary exactly so that the nudge, the snapping and the conjunction end up on this side
 * of it, where a fake world in a JUnit test exercises real logic.
 */
public final class OcclusionTest {

	/**
	 * Vanilla's nudge, R4.
	 *
	 * <p>Declared {@code float} because that is how R4 recorded it, {@code 1.0E-5F}. Note
	 * that {@link WorldView#occluderOnSegment} takes {@code double}s, so the widening from
	 * float to double happens in the arithmetic below rather than in the game's own code.
	 * A float widened to double keeps its exact value - 1.0E-5F is not 1.0E-5 but the nearest
	 * float to it, and stays that number after widening - so this matches vanilla only if
	 * vanilla also computes the offset in float before widening. That has not been read.
	 * It is almost certainly identical and it is checkable in one line the next time anyone
	 * opens {@code VibrationSystem.Listener#isOccluded}; recorded here rather than assumed
	 * away, per CONVENTIONS.md section 6.
	 */
	static final float NUDGE = 1.0E-5F;

	private OcclusionTest() {
	}

	/**
	 * Reproduces {@code VibrationSystem.Listener#isOccluded} (R4).
	 *
	 * <p><b>The two endpoints are not interchangeable.</b> {@code from} is the vibration
	 * source - the candidate position being tested - and {@code to} is the sensor. Only the
	 * source is nudged, so passing them the other way round tests a different geometry that
	 * vanilla would never produce. The parameter names say which is which and the solver
	 * passes them accordingly.
	 *
	 * <p>The rule: snap both endpoints to their block centres, then for each of the six
	 * {@link Face} directions cast one ray from the nudged source to the un-nudged
	 * destination centre. Return false at the first ray that reaches the destination without
	 * meeting an occluder; return true only if all six are blocked.
	 *
	 * <p>The centre snapping collapses to {@code + 0.5} and that is exact rather than a
	 * shortcut. R4 records vanilla snapping each endpoint to {@code Mth.floor(c) + 0.5}; the
	 * arguments here are already integers because R12 made the domain integral, and the floor
	 * of an integer is itself. The addition is written out so the correspondence with R4 stays
	 * visible in the code.
	 *
	 * <p>Cost: the early return is the reason open air costs one ray per position rather than
	 * six. It is a property of the conjunction, not an optimisation layered on top of it.
	 */
	public static boolean isOccluded(WorldView world,
			int fromX, int fromY, int fromZ,
			int toX, int toY, int toZ) {

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
