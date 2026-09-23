package com.scr0ols.sculksight.solver;

/** Reduces a {@link DetectionSet} to the edges where its surface turns. */
public final class CreaseEdgeExtractor {

	private CreaseEdgeExtractor() {
	}

	/** Emits one edge per lattice segment that satisfies the crease rule. */
	public static void extract(DetectionSet set, CreaseEdgeSink sink) {
		final int radius = set.radius();
		final int low = -radius;
		final int high = radius;

		for (Axis axis : Axis.allWithoutCopy()) {
			Axis first = across(axis, 0);
			Axis second = across(axis, 1);

			for (int along = low; along <= high; along++) {
				for (int u = low; u <= high + 1; u++) {
					for (int v = low; v <= high + 1; v++) {
						boolean lowLow = member(set, axis, along, first, u - 1, second, v - 1);
						boolean lowHigh = member(set, axis, along, first, u - 1, second, v);
						boolean highLow = member(set, axis, along, first, u, second, v - 1);
						boolean highHigh = member(set, axis, along, first, u, second, v);

						if (!isCrease(lowLow, lowHigh, highLow, highHigh)) {
							continue;
						}

						int x = coordinate(Axis.X, axis, along, first, u, second, v);
						int y = coordinate(Axis.Y, axis, along, first, u, second, v);
						int z = coordinate(Axis.Z, axis, along, first, u, second, v);

						sink.accept(x, y, z, axis);
					}
				}
			}
		}
	}

	static boolean isCrease(boolean lowLow, boolean lowHigh, boolean highLow, boolean highHigh) {
		int members = (lowLow ? 1 : 0) + (lowHigh ? 1 : 0) + (highLow ? 1 : 0) + (highHigh ? 1 : 0);

		if (members == 1 || members == 3) {
			return true;
		}

		if (members != 2) {
			return false;
		}

		return (lowLow && highHigh) || (lowHigh && highLow);
	}

	private static Axis across(Axis axis, int n) {
		return switch (axis) {
			case X -> n == 0 ? Axis.Y : Axis.Z;
			case Y -> n == 0 ? Axis.X : Axis.Z;
			case Z -> n == 0 ? Axis.X : Axis.Y;
		};
	}

	private static boolean member(DetectionSet set, Axis alongAxis, int along,
			Axis firstAxis, int first, Axis secondAxis, int second) {

		int dx = coordinate(Axis.X, alongAxis, along, firstAxis, first, secondAxis, second);
		int dy = coordinate(Axis.Y, alongAxis, along, firstAxis, first, secondAxis, second);
		int dz = coordinate(Axis.Z, alongAxis, along, firstAxis, first, secondAxis, second);

		return set.contains(dx, dy, dz);
	}

	private static int coordinate(Axis wanted, Axis alongAxis, int along,
			Axis firstAxis, int first, Axis secondAxis, int second) {

		if (wanted == alongAxis) {
			return along;
		}

		return wanted == firstAxis ? first : second;
	}
}
