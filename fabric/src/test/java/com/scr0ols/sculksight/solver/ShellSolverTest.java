package com.scr0ols.sculksight.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ShellSolver}.
 *
 * <p><b>One test in this file is not circular, and the rest are.</b> That distinction is the
 * point of TESTING-STRATEGY.md section 1 and is worth keeping visible rather than assumed.
 * {@link #openAirCountsMatchTheLatticeExactly()} compares against numbers produced by counting
 * integer lattice points satisfying {@code x^2 + y^2 + z^2 <= r^2}, which is arithmetic and
 * owes nothing to anyone's model of Minecraft. Everything else here checks that the solver
 * does what this project believes the game does, using a fake world - and if that belief is
 * wrong, these tests are wrong in the same direction and still green.
 */
class ShellSolverTest {

	private static final int SENSOR_X = 100;
	private static final int SENSOR_Y = 64;
	private static final int SENSOR_Z = -200;

	@Test
	@DisplayName("open air produces exactly the in-range lattice counts for both real radii")
	void openAirCountsMatchTheLatticeExactly() {
		// 2 109 of 4 913 at radius 8, and 17 077 of 35 937 at radius 16. These four figures are
		// exact rather than approximate: they are the count of integer lattice points satisfying
		// dx^2 + dy^2 + dz^2 <= r^2 inclusively, which is precisely what R2 records the game as
		// comparing. They appear in PLAN.md section 3.2's cost model and were recomputed
		// independently on 2026-08-31 before being written here.
		//
		// Radius 8 and 16 are the only two the mod needs - normal sensor and shrieker 8,
		// calibrated sensor 16 (R1).
		assertEquals(2109, solveOpenAir(8).size());
		assertEquals(17077, solveOpenAir(16).size());
	}

	@Test
	@DisplayName("open air costs one ray per in-range position, not six")
	void openAirCostsOneRayPerPosition() {
		// The cost model in PLAN.md section 3.3 rests on this. It follows from the six-ray rule
		// being a conjunction that returns at the first ray reaching the destination (R4), and
		// it is asserted here rather than argued because it is the figure the frame budget uses.
		RecordingWorld world = RecordingWorld.allClear();
		DetectionSet set = ShellSolver.solve(world, SENSOR_X, SENSOR_Y, SENSOR_Z, 8);

		assertEquals(2109, set.size());
		assertEquals(2109, world.rayCount());
	}

	@Test
	@DisplayName("the range test is inclusive at exactly radius squared")
	void rangeTestIncludesTheOutermostShell() {
		// The single most consequential off-by-one available in this project. Writing < instead
		// of <= drops the outermost surface, which is the surface the mod exists to draw
		// (R2, implications). Radius 5 is used because it has clean Pythagorean members.
		DetectionSet set = solveOpenAir(5);

		// distSqr == 25 == radius^2, so these must be in.
		assertTrue(set.contains(5, 0, 0), "axis-aligned position at exactly the radius");
		assertTrue(set.contains(3, 4, 0), "3-4-5 triangle, exactly on the sphere");
		assertTrue(set.contains(0, -3, -4), "same, negative octant");

		// distSqr == 26 > 25, so these must be out.
		assertFalse(set.contains(3, 4, 1));
		assertFalse(set.contains(1, 3, 4));
	}

	@Test
	@DisplayName("the sensor's own position is in the set")
	void sensorPositionIsInRange() {
		// Squared distance zero passes the test, and there is no special case anywhere in the
		// path that removes it (R2 point 4). Whether it is useful to draw is a different
		// question, and belongs to the position filter, not to the range test.
		assertTrue(solveOpenAir(8).contains(0, 0, 0));
	}

	@Test
	@DisplayName("a fully occluding world produces an empty set")
	void fullyOccludedWorldIsEmpty() {
		DetectionSet set = ShellSolver.solve(RecordingWorld.allBlocked(), SENSOR_X, SENSOR_Y, SENSOR_Z, 4);

		assertEquals(0, set.size());
	}

	@Test
	@DisplayName("occlusion removes exactly the occluded positions and nothing else")
	void occlusionCarvesTheSphere() {
		// A fake that occludes every ray whose source sits at a negative x offset from the
		// sensor. This is not how occlusion works in the game - no traversal is being modelled -
		// but it is a clean way to check that the solver removes precisely the positions the
		// occlusion test rejects, and keeps precisely the rest.
		int radius = 5;
		WorldView halfBlocked = (fromX, fromY, fromZ, toX, toY, toZ) ->
				Math.floor(fromX) - SENSOR_X < 0;

		DetectionSet carved = ShellSolver.solve(halfBlocked, SENSOR_X, SENSOR_Y, SENSOR_Z, radius);
		DetectionSet open = solveOpenAir(radius);

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					boolean inRange = open.contains(dx, dy, dz);
					boolean expected = inRange && dx >= 0;

					assertEquals(expected, carved.contains(dx, dy, dz),
							"offset (" + dx + ", " + dy + ", " + dz + ")");
				}
			}
		}

		assertTrue(carved.size() > 0 && carved.size() < open.size(), "the carve is not degenerate");
	}

	@Test
	@DisplayName("a position filter removes positions, and does so before any ray is cast")
	void filterAppliesAndRunsBeforeTheRays() {
		// Both halves matter. That the filter removes positions is the feature; that it removes
		// them before the expensive part is the reason ARCHITECTURE.md section 4.4 puts it
		// alongside the range test rather than after the occlusion test.
		RecordingWorld unfilteredWorld = RecordingWorld.allClear();
		DetectionSet unfiltered = ShellSolver.solve(unfilteredWorld, SENSOR_X, SENSOR_Y, SENSOR_Z, 5,
				PositionFilter.ACCEPT_ALL);

		RecordingWorld filteredWorld = RecordingWorld.allClear();
		PositionFilter upperHalfOnly = (dx, dy, dz) -> dy >= 0;
		DetectionSet filtered = ShellSolver.solve(filteredWorld, SENSOR_X, SENSOR_Y, SENSOR_Z, 5, upperHalfOnly);

		assertTrue(filtered.size() < unfiltered.size());
		assertEquals(filtered.size(), filteredWorld.rayCount(),
				"no ray may be cast for a position the filter rejected");

		for (int dy = -5; dy < 0; dy++) {
			assertFalse(filtered.contains(0, dy, 0), "filtered out at dy=" + dy);
		}

		assertTrue(filtered.contains(0, 5, 0));
	}

	@Test
	@DisplayName("the four-argument overload behaves as the five-argument one with ACCEPT_ALL")
	void overloadDelegatesToAcceptAll() {
		// ARCHITECTURE.md section 4.2 writes the signature without a filter and section 4.4
		// describes it with one. Both are kept, and this pins the relationship between them.
		DetectionSet withoutFilter = ShellSolver.solve(RecordingWorld.allClear(),
				SENSOR_X, SENSOR_Y, SENSOR_Z, 6);
		DetectionSet withAcceptAll = ShellSolver.solve(RecordingWorld.allClear(),
				SENSOR_X, SENSOR_Y, SENSOR_Z, 6, PositionFilter.ACCEPT_ALL);

		assertEquals(withoutFilter.size(), withAcceptAll.size());
	}

	@Test
	@DisplayName("the solved set is independent of where the sensor is in the world")
	void resultIsSensorRelative() {
		// The set is expressed as offsets (ADR-014, ADR-016), so moving the sensor must not
		// change it. This is what lets the mesh encoder use the same offsets as coordinates.
		DetectionSet atOrigin = ShellSolver.solve(RecordingWorld.allClear(), 0, 0, 0, 6);
		DetectionSet farAway = ShellSolver.solve(RecordingWorld.allClear(), -30000, 200, 12345, 6);

		assertEquals(atOrigin.size(), farAway.size());
		assertTrue(farAway.contains(6, 0, 0));
	}

	private static DetectionSet solveOpenAir(int radius) {
		return ShellSolver.solve(RecordingWorld.allClear(), SENSOR_X, SENSOR_Y, SENSOR_Z, radius);
	}
}
