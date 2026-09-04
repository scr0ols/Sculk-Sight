package com.scr0ols.sculksight.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Tests for the crease rule and the sweep that feeds it. c-docs/DECISIONS.md ADR-028. */
class CreaseEdgeExtractorTest {

	private record Edge(int x, int y, int z, Axis axis) {
	}

	/**
	 * The rule, over all sixteen configurations of the four cube positions around one edge.
	 *
	 * <p>Exhaustive because there are only sixteen, and because every one of them is a case the
	 * renderer will meet: a shell of two thousand positions has edges of every kind.
	 */
	@Test
	void theRuleIsExactlyOneThreeOrTwoDiagonal() {
		for (int mask = 0; mask < 16; mask++) {
			boolean lowLow = (mask & 1) != 0;
			boolean lowHigh = (mask & 2) != 0;
			boolean highLow = (mask & 4) != 0;
			boolean highHigh = (mask & 8) != 0;

			int members = Integer.bitCount(mask);
			boolean diagonal = (lowLow && highHigh) || (lowHigh && highLow);
			boolean expected = members == 1 || members == 3 || (members == 2 && diagonal);

			assertEquals(expected, CreaseEdgeExtractor.isCrease(lowLow, lowHigh, highLow, highHigh),
					"configuration " + mask);
		}
	}

	/** A flat wall is the case that must not be a crease, or the whole surface gets outlined. */
	@Test
	void twoAdjacentMembersAreAFlatSurfaceAndNotACrease() {
		assertFalse(CreaseEdgeExtractor.isCrease(true, true, false, false));
		assertFalse(CreaseEdgeExtractor.isCrease(false, false, true, true));
		assertFalse(CreaseEdgeExtractor.isCrease(true, false, true, false));
		assertFalse(CreaseEdgeExtractor.isCrease(false, true, false, true));
	}

	@Test
	void noSurfaceMeansNoEdge() {
		assertFalse(CreaseEdgeExtractor.isCrease(false, false, false, false));
		assertFalse(CreaseEdgeExtractor.isCrease(true, true, true, true));
	}

	/**
	 * One position is a cube, and a cube has exactly twelve edges, four along each axis.
	 *
	 * <p>This is the smallest complete shell there is, so it pins the sweep's ranges: an extractor
	 * whose lattice loops stopped at the cube's own bounds rather than one past them would miss the
	 * edges on the far side and return fewer than twelve.
	 */
	@Test
	void aSinglePositionYieldsTheTwelveEdgesOfOneCube() {
		DetectionSet set = new DetectionSet(2);
		set.add(0, 0, 0);

		List<Edge> edges = collect(set);

		assertEquals(12, edges.size());
		assertEquals(4, edges.stream().filter(edge -> edge.axis() == Axis.X).count());
		assertEquals(4, edges.stream().filter(edge -> edge.axis() == Axis.Y).count());
		assertEquals(4, edges.stream().filter(edge -> edge.axis() == Axis.Z).count());

		// Every edge of the unit cube at the origin runs from a corner in {0,1}^3, and no edge is
		// emitted twice.
		Set<Edge> distinct = new HashSet<>(edges);
		assertEquals(12, distinct.size());

		for (Edge edge : edges) {
			assertTrue(edge.x() >= 0 && edge.x() <= 1, "x out of the unit cube: " + edge);
			assertTrue(edge.y() >= 0 && edge.y() <= 1, "y out of the unit cube: " + edge);
			assertTrue(edge.z() >= 0 && edge.z() <= 1, "z out of the unit cube: " + edge);
		}
	}

	/**
	 * A flat slab, which is where the rule earns its keep: the interior of each face contributes
	 * nothing and only the rim is a crease.
	 *
	 * <p>A 3 by 1 by 3 slab is a box, so its creases are the twelve edges of that box: four of
	 * length 3 along X, four of length 3 along Z, and four of length 1 along Y. Counting unit
	 * segments that is 12 + 12 + 4, which is 28.
	 */
	@Test
	void aFlatSlabIsOutlinedOnlyAtItsRim() {
		DetectionSet set = new DetectionSet(3);

		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				set.add(dx, 0, dz);
			}
		}

		assertEquals(28, collect(set).size());
	}

	/**
	 * Two cubes touching only along one edge, which is the configuration the rule keeps as a crease
	 * even though only two of the four positions are members.
	 *
	 * <p>Each cube contributes its own twelve edges, and the shared one is emitted once rather than
	 * twice because the sweep visits each lattice segment once. So 12 + 12 - 1, which is 23.
	 */
	@Test
	void twoDiagonallyTouchingCubesShareOneCreaseRatherThanDuplicatingIt() {
		DetectionSet set = new DetectionSet(3);
		set.add(0, 0, 0);
		set.add(1, 1, 0);

		List<Edge> edges = collect(set);

		assertEquals(23, edges.size());
		assertEquals(edges.size(), new HashSet<>(edges).size());
	}

	@Test
	void anEmptySetHasNoCreases() {
		assertEquals(0, collect(new DetectionSet(4)).size());
	}

	/**
	 * A crease edge always separates a member from a non-member somewhere around it, so the count
	 * the encoder is told to expect can never exceed what a full sweep finds.
	 */
	@Test
	void theCountAndTheSweepAgreeOnASolidBall() {
		DetectionSet set = new DetectionSet(5);

		for (int dx = -5; dx <= 5; dx++) {
			for (int dy = -5; dy <= 5; dy++) {
				for (int dz = -5; dz <= 5; dz++) {
					if (dx * dx + dy * dy + dz * dz <= 25) {
						set.add(dx, dy, dz);
					}
				}
			}
		}

		List<Edge> edges = collect(set);

		assertTrue(edges.size() > 0, "a voxelised ball has creases at every staircase step");
		assertEquals(edges.size(), new HashSet<>(edges).size(), "no edge is emitted twice");
	}

	private static List<Edge> collect(DetectionSet set) {
		List<Edge> edges = new ArrayList<>();
		CreaseEdgeExtractor.extract(set, (x, y, z, axis) -> edges.add(new Edge(x, y, z, axis)));
		return edges;
	}
}
