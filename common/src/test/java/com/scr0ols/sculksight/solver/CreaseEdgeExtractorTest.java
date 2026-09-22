package com.scr0ols.sculksight.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CreaseEdgeExtractorTest {

	private record Edge(int x, int y, int z, Axis axis) {
	}

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

	@Test
	void aSinglePositionYieldsTheTwelveEdgesOfOneCube() {
		DetectionSet set = new DetectionSet(2);
		set.add(0, 0, 0);

		List<Edge> edges = collect(set);

		assertEquals(12, edges.size());
		assertEquals(4, edges.stream().filter(edge -> edge.axis() == Axis.X).count());
		assertEquals(4, edges.stream().filter(edge -> edge.axis() == Axis.Y).count());
		assertEquals(4, edges.stream().filter(edge -> edge.axis() == Axis.Z).count());

		Set<Edge> distinct = new HashSet<>(edges);
		assertEquals(12, distinct.size());

		for (Edge edge : edges) {
			assertTrue(edge.x() >= 0 && edge.x() <= 1, "x out of the unit cube: " + edge);
			assertTrue(edge.y() >= 0 && edge.y() <= 1, "y out of the unit cube: " + edge);
			assertTrue(edge.z() >= 0 && edge.z() <= 1, "z out of the unit cube: " + edge);
		}
	}

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
