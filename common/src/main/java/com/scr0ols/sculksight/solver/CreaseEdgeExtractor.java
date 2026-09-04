package com.scr0ols.sculksight.solver;

/**
 * Reduces a {@link DetectionSet} to the edges where its surface turns.
 *
 * <p>c-docs/DECISIONS.md ADR-028. The renderer draws these as black lines over the amber fill so
 * that the seam between the detectable and the non-detectable zone reads clearly, above all where
 * an occluding block has carved the shell.
 *
 * <p><b>Why creases and not every quad edge.</b> Every boundary face is one block square, so
 * outlining all of them puts a one-block grid over the whole surface: a wireframe, not a highlight.
 * A crease is the strictly smaller set where the incident boundary faces are not coplanar, which is
 * the seam a player actually looks for.
 *
 * <p><b>The rule, and why it is purely local.</b> A lattice edge is touched by exactly four cube
 * positions, the four in the plane perpendicular to it. Their membership decides the shape of the
 * surface at that edge and nothing else does:
 *
 * <ul>
 *   <li><b>none or all four members:</b> no boundary face touches the edge at all, so there is
 *       nothing to draw.</li>
 *   <li><b>one or three members:</b> the surface turns a right angle there, convex at one and
 *       concave at three. A crease.</li>
 *   <li><b>two members, adjacent:</b> the surface is flat and the two boundary faces meeting at
 *       the edge are coplanar. Not a crease, and this is the case that keeps the whole flat part of
 *       a wall from being outlined.</li>
 *   <li><b>two members, diagonal:</b> two separate corners touching along one edge. A crease, and
 *       a visually important one, because it is where two lobes of the set meet.</li>
 * </ul>
 *
 * <p>Four bitset lookups per candidate edge, no allocation, and no state carried between edges.
 */
public final class CreaseEdgeExtractor {

	private CreaseEdgeExtractor() {
	}

	/**
	 * Emits one edge per lattice segment that satisfies the crease rule.
	 *
	 * <p>The sweep covers every lattice segment that could touch a member. Along the edge's own
	 * axis the range is the cube's, since only the one cube column at that coordinate touches the
	 * segment; across the other two axes it is one larger at the top end, because a lattice line at
	 * {@code radius + 1} is still the far corner of the members at {@code radius}.
	 * {@link DetectionSet#contains} returns false outside the cube, and that is exact rather than a
	 * convenience (see its own documentation), so the extra ring needs no special case.
	 *
	 * <p>Emission order is the sweep order and is not part of the contract.
	 */
	public static void extract(DetectionSet set, CreaseEdgeSink sink) {
		final int radius = set.radius();
		final int low = -radius;
		final int high = radius;

		for (Axis axis : Axis.allWithoutCopy()) {
			// The two axes the four cube positions spread across. Naming them once here is what
			// lets the three cases share one loop body instead of being written out three times,
			// which is where a transcription error would otherwise live.
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

	/**
	 * The crease rule of ADR-028, over the four cube positions around one lattice edge.
	 *
	 * <p>Package-private rather than private so that the rule itself can be exercised by JUnit over
	 * all sixteen configurations, independently of the sweep that feeds it. The sweep and the rule
	 * fail in different ways and are worth testing apart.
	 */
	static boolean isCrease(boolean lowLow, boolean lowHigh, boolean highLow, boolean highHigh) {
		int members = (lowLow ? 1 : 0) + (lowHigh ? 1 : 0) + (highLow ? 1 : 0) + (highHigh ? 1 : 0);

		if (members == 1 || members == 3) {
			return true;
		}

		if (members != 2) {
			return false;
		}

		// Exactly two. Diagonal is a crease, adjacent is a flat wall. Testing the two diagonal
		// pairs directly is clearer than testing the four adjacent ones and negating.
		return (lowLow && highHigh) || (lowHigh && highLow);
	}

	/** The n-th of the two axes perpendicular to {@code axis}, in the fixed order X, Y, Z. */
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

	/**
	 * Recomposes one world-frame component from the three axis-relative ones.
	 *
	 * <p>Exactly one of the three axes is {@code wanted}, since the three are distinct by
	 * construction, so this returns that one's value.
	 */
	private static int coordinate(Axis wanted, Axis alongAxis, int along,
			Axis firstAxis, int first, Axis secondAxis, int second) {

		if (wanted == alongAxis) {
			return along;
		}

		return wanted == firstAxis ? first : second;
	}
}
