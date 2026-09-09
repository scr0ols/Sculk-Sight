package com.scr0ols.sculksight.solver;

import java.util.HashSet;
import java.util.Set;

/**
 * A bounded union of sensor-relative detection sets in world coordinates.
 *
 * <p>Each individual solve remains a {@link DetectionSet} over its own bounded cube. This type
 * only translates accepted members into a common coordinate space, so overlapping sensors are
 * represented once and the boundary extractor can remove internal faces instead of drawing them
 * twice. It deliberately stores positions rather than allocating one giant cube: sensors may be
 * far apart, and the union's cost must be bounded by selected members, not by their separation.
 */
public final class WorldDetectionSet {

	private final Set<Position> positions = new HashSet<>();

	private int minX = Integer.MAX_VALUE;
	private int minY = Integer.MAX_VALUE;
	private int minZ = Integer.MAX_VALUE;
	private int maxX = Integer.MIN_VALUE;
	private int maxY = Integer.MIN_VALUE;
	private int maxZ = Integer.MIN_VALUE;

	public void add(DetectionSet set, int originX, int originY, int originZ) {
		int radius = set.radius();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (set.contains(dx, dy, dz)) {
						add(originX + dx, originY + dy, originZ + dz);
					}
				}
			}
		}
	}

	public void add(int x, int y, int z) {
		if (!positions.add(new Position(x, y, z))) {
			return;
		}
		minX = Math.min(minX, x);
		minY = Math.min(minY, y);
		minZ = Math.min(minZ, z);
		maxX = Math.max(maxX, x);
		maxY = Math.max(maxY, y);
		maxZ = Math.max(maxZ, z);
	}

	public boolean isEmpty() {
		return positions.isEmpty();
	}

	public int size() {
		return positions.size();
	}

	public boolean contains(int x, int y, int z) {
		return positions.contains(new Position(x, y, z));
	}

	public Bounds bounds() {
		return new Bounds(minX, minY, minZ, maxX, maxY, maxZ, positions.isEmpty());
	}

	/** Emits world-coordinate members whose neighbour in the given direction is absent. */
	public void extractBoundaryFaces(BoundaryFaceSink sink) {
		for (Position position : positions) {
			for (Face face : Face.allWithoutCopy()) {
				if (!contains(position.x + face.stepX(), position.y + face.stepY(), position.z + face.stepZ())) {
					sink.accept(position.x, position.y, position.z, face);
				}
			}
		}
	}

	public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean empty) {

		public int spanX() {
			return empty ? 0 : maxX - minX + 1;
		}

		public int spanY() {
			return empty ? 0 : maxY - minY + 1;
		}

		public int spanZ() {
			return empty ? 0 : maxZ - minZ + 1;
		}
	}

	private record Position(int x, int y, int z) {
	}
}
