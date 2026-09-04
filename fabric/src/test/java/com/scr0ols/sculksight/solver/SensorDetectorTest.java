package com.scr0ols.sculksight.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SensorDetector}, mode C's solver-side surface.
 *
 * <p>Scope, as in {@link SixRayRuleTest}: everything here runs against {@link RecordingWorld},
 * so a green run means the range test and the occlusion call are composed as R2 and R4 describe
 * them, not that the mod agrees with the game. That seam is TESTING-STRATEGY.md section 3's,
 * same as it is for the shell solver.
 */
class SensorDetectorTest {

	private static final int SENSOR_X = 100;
	private static final int SENSOR_Y = 64;
	private static final int SENSOR_Z = 200;
	private static final int RADIUS = 8;

	private static boolean run(RecordingWorld world, int x, int y, int z) {
		return SensorDetector.isDetectedAt(world, x, y, z, SENSOR_X, SENSOR_Y, SENSOR_Z, RADIUS);
	}

	@Test
	@DisplayName("in range and unoccluded is detected")
	void inRangeAndClearIsDetected() {
		RecordingWorld world = RecordingWorld.allClear();

		assertTrue(run(world, SENSOR_X + RADIUS, SENSOR_Y, SENSOR_Z));
		assertEquals(1, world.rayCount(), "the conjunction returns at the first clear ray");
	}

	@Test
	@DisplayName("in range and fully occluded is not detected")
	void inRangeButOccludedIsNotDetected() {
		RecordingWorld world = RecordingWorld.allBlocked();

		assertFalse(run(world, SENSOR_X + RADIUS, SENSOR_Y, SENSOR_Z));
		assertEquals(6, world.rayCount(), "a fully enclosed position costs all six rays");
	}

	@Test
	@DisplayName("exactly at the radius passes the range test - R2's comparison is inclusive")
	void exactRadiusIsInRange() {
		RecordingWorld world = RecordingWorld.allClear();

		assertTrue(run(world, SENSOR_X + RADIUS, SENSOR_Y, SENSOR_Z),
				"distSqr == radiusSqr must pass, per R2 points 1-2");
	}

	@Test
	@DisplayName("one block past the radius fails the range test and casts no ray")
	void justOutsideRadiusIsNotDetectedAndCostsNoRay() {
		RecordingWorld world = RecordingWorld.allClear();

		assertFalse(run(world, SENSOR_X + RADIUS + 1, SENSOR_Y, SENSOR_Z));
		assertEquals(0, world.rayCount(), "the cheap range test rejects before any ray is cast");
	}

	@Test
	@DisplayName("the sensor's own position is in range and not occluded by construction")
	void sensorsOwnPositionIsDetected() {
		RecordingWorld world = RecordingWorld.allClear();

		assertTrue(run(world, SENSOR_X, SENSOR_Y, SENSOR_Z));
	}

	@Test
	@DisplayName("the candidate is the source and the sensor is the destination, as in the solver")
	void candidateIsSourceSensorIsDestination() {
		RecordingWorld world = RecordingWorld.allBlocked();

		run(world, SENSOR_X + 1, SENSOR_Y, SENSOR_Z);

		RecordingWorld.Ray firstRay = world.rays().getFirst();

		// Only the source is nudged (R4), so the destination lands exactly on the sensor's
		// centre. This is the same assertion SixRayRuleTest makes for the solver's own call,
		// checking that mode C did not swap the two arguments - an easy mistake that would
		// still compile and would still look plausible.
		assertEquals(SENSOR_X + 0.5, firstRay.toX());
		assertEquals(SENSOR_Y + 0.5, firstRay.toY());
		assertEquals(SENSOR_Z + 0.5, firstRay.toZ());
	}

	// ------------------------------------------------------------------ isInRange
	//
	// The range test on its own, extracted so that mode C's differential verification can tell
	// out-of-range apart from occluded without writing R2's comparison a second time. These
	// tests pin it directly rather than only through isDetectedAt, because it is now public and
	// a caller can reach it without going through the conjunction.

	private static boolean inRange(int x, int y, int z) {
		return SensorDetector.isInRange(x, y, z, SENSOR_X, SENSOR_Y, SENSOR_Z, RADIUS);
	}

	@Test
	@DisplayName("isInRange is inclusive at exactly the radius and exclusive one block beyond it")
	void isInRangeIsInclusiveAtTheBoundary() {
		assertTrue(inRange(SENSOR_X + RADIUS, SENSOR_Y, SENSOR_Z),
				"distSqr == radiusSqr must pass, per R2 points 1-2");
		assertFalse(inRange(SENSOR_X + RADIUS + 1, SENSOR_Y, SENSOR_Z));
	}

	@Test
	@DisplayName("isInRange is spherical, not a bounding box - R2's check is a squared distance")
	void isInRangeIsSphericalRatherThanCubic() {
		// A cube corner at (RADIUS, RADIUS, RADIUS) is inside the bounding box the solver sweeps
		// and far outside the radius. This is the single assertion that separates R2's actual
		// comparison from the shape it is easy to assume.
		assertFalse(inRange(SENSOR_X + RADIUS, SENSOR_Y + RADIUS, SENSOR_Z + RADIUS));
	}

	@Test
	@DisplayName("isDetectedAt agrees with isInRange wherever the world is fully open")
	void isDetectedAtAgreesWithIsInRangeInOpenAir() {
		// With nothing occluding, detection reduces to the range test, so the two methods must
		// answer identically across the whole cube. This is what lets DetectionScan classify a
		// position with one call to each without the two disagreeing about the boundary.
		for (int dx = -RADIUS - 1; dx <= RADIUS + 1; dx++) {
			for (int dy = -RADIUS - 1; dy <= RADIUS + 1; dy++) {
				for (int dz = -RADIUS - 1; dz <= RADIUS + 1; dz++) {
					int x = SENSOR_X + dx;
					int y = SENSOR_Y + dy;
					int z = SENSOR_Z + dz;

					assertEquals(inRange(x, y, z), run(RecordingWorld.allClear(), x, y, z),
							"open air: detection must reduce to the range test at ("
									+ dx + ", " + dy + ", " + dz + ")");
				}
			}
		}
	}
}
