package com.scr0ols.sculksight.solver;

/**
 * Reduces a {@link DetectionSet} to its surface.
 *
 * <p>PLAN.md section 3.3's core rendering technique: draw a quad only where the neighbouring
 * position is outside the set. This turns tens of thousands of positions into hundreds of
 * faces, and it reads better - the shell carved out by dampening blocks becomes visible
 * instead of being buried inside a solid mass.
 *
 * <p>Nothing in v0.0 consumes this yet. The renderer is blocked on decisions that have not
 * been taken (ARCHITECTURE.md section 8), and the differential verification mechanism does not
 * draw. It is written now because it is cheap, fully specified, and testable in isolation.
 */
public final class BoundaryFaceExtractor {

	private BoundaryFaceExtractor() {
	}

	/**
	 * Emits one face per (member, direction) pair where the neighbour in that direction is not
	 * a member.
	 *
	 * <p>Members on the outer wall of the cube emit faces outward, and that is correct rather
	 * than an edge case: {@link DetectionSet#contains} returns false outside the cube, and a
	 * position outside the cube is genuinely outside the detection set - its squared distance
	 * is at least {@code (radius + 1)^2}, which exceeds {@code radius^2} by R2's own
	 * comparison. The clamp and the truth agree, so no special handling is needed here.
	 *
	 * <p>Emission order is the sweep order and is not part of the contract. A consumer that
	 * depends on it is relying on an implementation detail.
	 */
	public static void extract(DetectionSet set, BoundaryFaceSink sink) {
		final int radius = set.radius();

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (!set.contains(dx, dy, dz)) {
						continue;
					}

					for (Face face : Face.allWithoutCopy()) {
						if (!set.contains(dx + face.stepX(), dy + face.stepY(), dz + face.stepZ())) {
							sink.accept(dx, dy, dz, face);
						}
					}
				}
			}
		}
	}
}
