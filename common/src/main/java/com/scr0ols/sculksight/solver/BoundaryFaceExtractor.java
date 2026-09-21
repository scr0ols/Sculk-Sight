package com.scr0ols.sculksight.solver;

/** Reduces a {@link DetectionSet} to its surface. */
public final class BoundaryFaceExtractor {

	private BoundaryFaceExtractor() {
	}

	/** Emits one face per member and direction whose neighbour is not a member. */
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
