package com.scr0ols.sculksight.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShellSolverTest {

	private static final int SENSOR_X = 100;
	private static final int SENSOR_Y = 64;
	private static final int SENSOR_Z = -200;

	@Test
	@DisplayName("open air produces exactly the in-range lattice counts for both real radii")
	void openAirCountsMatchTheLatticeExactly() {
		assertEquals(2109, solveOpenAir(8).size());
		assertEquals(17077, solveOpenAir(16).size());
	}

	@Test
	@DisplayName("open air costs one ray per in-range position, not six")
	void openAirCostsOneRayPerPosition() {
		RecordingWorld world = RecordingWorld.allClear();
		DetectionSet set = ShellSolver.solve(world, SENSOR_X, SENSOR_Y, SENSOR_Z, 8);

		assertEquals(2109, set.size());
		assertEquals(2109, world.rayCount());
	}

	@Test
	@DisplayName("the range test is inclusive at exactly radius squared")
	void rangeTestIncludesTheOutermostShell() {
		DetectionSet set = solveOpenAir(5);

		assertTrue(set.contains(5, 0, 0), "axis-aligned position at exactly the radius");
		assertTrue(set.contains(3, 4, 0), "3-4-5 triangle, exactly on the sphere");
		assertTrue(set.contains(0, -3, -4), "same, negative octant");

		assertFalse(set.contains(3, 4, 1));
		assertFalse(set.contains(1, 3, 4));
	}

	@Test
	@DisplayName("the sensor's own position is in the set")
	void sensorPositionIsInRange() {
		assertTrue(solveOpenAir(8).contains(0, 0, 0));
	}

	@Test
	@DisplayName("a fully occluding world produces an empty set")
	void fullyOccludedWorldIsEmpty() {
		DetectionSet set = ShellSolver.solve(RecordingWorld.allBlocked(), SENSOR_X, SENSOR_Y, SENSOR_Z, 4);

		assertEquals(0, set.size());
	}

	@Test
	@DisplayName("a source directly above a vibration-dampening block is occluded")
	void sourceAboveDampeningBlockIsOccluded() {
		WorldView world = new WorldView() {
			@Override
			public boolean occluderOnSegment(double fromX, double fromY, double fromZ,
					double toX, double toY, double toZ) {
				return false;
			}

			@Override
			public boolean dampensVibrationsBelow(int sourceX, int sourceY, int sourceZ) {
				return sourceX == SENSOR_X && sourceY == SENSOR_Y + 2 && sourceZ == SENSOR_Z;
			}
		};

		ShellSolution solution = ShellSolver.solveDetailed(world, SENSOR_X, SENSOR_Y, SENSOR_Z, 2);

		assertTrue(solution.occludedOut().contains(0, 2, 0));
		assertFalse(solution.accepted().contains(0, 2, 0));
	}

	@Test
	@DisplayName("occlusion removes exactly the occluded positions and nothing else")
	void occlusionCarvesTheSphere() {
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
		DetectionSet withoutFilter = ShellSolver.solve(RecordingWorld.allClear(),
				SENSOR_X, SENSOR_Y, SENSOR_Z, 6);
		DetectionSet withAcceptAll = ShellSolver.solve(RecordingWorld.allClear(),
				SENSOR_X, SENSOR_Y, SENSOR_Z, 6, PositionFilter.ACCEPT_ALL);

		assertEquals(withoutFilter.size(), withAcceptAll.size());
	}

	@Test
	@DisplayName("the solved set is independent of where the sensor is in the world")
	void resultIsSensorRelative() {
		DetectionSet atOrigin = ShellSolver.solve(RecordingWorld.allClear(), 0, 0, 0, 6);
		DetectionSet farAway = ShellSolver.solve(RecordingWorld.allClear(), -30000, 200, 12345, 6);

		assertEquals(atOrigin.size(), farAway.size());
		assertTrue(farAway.contains(6, 0, 0));
	}

	private static DetectionSet solveOpenAir(int radius) {
		return ShellSolver.solve(RecordingWorld.allClear(), SENSOR_X, SENSOR_Y, SENSOR_Z, radius);
	}
}
