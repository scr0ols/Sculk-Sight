package com.scr0ols.sculksight.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OcclusionTest}, the six-ray rule of R4.
 *
 * <p>Named for the rule rather than for the class because the class is already called
 * "...Test" while being production code.
 *
 * <p><b>Scope, stated so these tests are not over-read.</b> Every test here runs against
 * {@link RecordingWorld}, so what is under test is the composition of the rule and nothing
 * below it. A green run here means the nudge, the snapping, the conjunction and the early
 * return are as R4 describes them. It does not mean the mod agrees with the game.
 */
class SixRayRuleTest {

	private static final int SOURCE_X = 3;
	private static final int SOURCE_Y = 4;
	private static final int SOURCE_Z = 5;

	private static final int SENSOR_X = 10;
	private static final int SENSOR_Y = 4;
	private static final int SENSOR_Z = 5;

	private static boolean run(RecordingWorld world) {
		return OcclusionTest.isOccluded(world,
				SOURCE_X, SOURCE_Y, SOURCE_Z,
				SENSOR_X, SENSOR_Y, SENSOR_Z);
	}

	@Test
	@DisplayName("all six rays blocked means occluded")
	void allSixBlockedIsOccluded() {
		RecordingWorld world = RecordingWorld.allBlocked();

		assertTrue(run(world));
		assertEquals(6, world.rayCount(), "a fully enclosed position costs all six rays");
	}

	@Test
	@DisplayName("no ray blocked means not occluded, and costs exactly one ray")
	void openAirCostsOneRay() {
		RecordingWorld world = RecordingWorld.allClear();

		assertFalse(run(world));
		assertEquals(1, world.rayCount(),
				"the conjunction returns at the first ray that reaches the destination");
	}

	@Test
	@DisplayName("one clear ray out of six is enough to be not occluded, wherever it falls")
	void anySingleClearRayDefeatsOcclusion() {
		// The rule is a conjunction, so the position of the clear ray must not matter. Running
		// all six placements is what distinguishes a real conjunction from code that happens
		// to work for the first one.
		for (int clearIndex = 0; clearIndex < 6; clearIndex++) {
			RecordingWorld world = RecordingWorld.allBlockedExceptCall(clearIndex);

			assertFalse(run(world), "clear ray at index " + clearIndex + " should defeat occlusion");
			assertEquals(clearIndex + 1, world.rayCount(),
					"should stop at the clear ray, not continue past it");
		}
	}

	@Test
	@DisplayName("both endpoints are snapped to block centres")
	void endpointsAreBlockCentres() {
		RecordingWorld world = RecordingWorld.allBlocked();
		run(world);

		for (RecordingWorld.Ray ray : world.rays()) {
			// The destination is the sensor's centre, exactly, with no nudge applied to it.
			assertEquals(SENSOR_X + 0.5, ray.toX());
			assertEquals(SENSOR_Y + 0.5, ray.toY());
			assertEquals(SENSOR_Z + 0.5, ray.toZ());
		}
	}

	@Test
	@DisplayName("the source is nudged along each of the six faces, and only the source")
	void sourceIsNudgedOncePerFace() {
		RecordingWorld world = RecordingWorld.allBlocked();
		run(world);

		List<RecordingWorld.Ray> rays = world.rays();
		assertEquals(6, rays.size());

		// Asserted structurally rather than by recomputing the same expression the production
		// code uses, which would only prove the expression equals itself. The claims checked
		// are the ones R4 actually makes: exactly one axis moves, by the nudge distance, and
		// the six displacements are six distinct directions.
		Set<String> displacements = new HashSet<>();

		for (RecordingWorld.Ray ray : rays) {
			double dx = ray.fromX() - (SOURCE_X + 0.5);
			double dy = ray.fromY() - (SOURCE_Y + 0.5);
			double dz = ray.fromZ() - (SOURCE_Z + 0.5);

			int movedAxes = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
			assertEquals(1, movedAxes, "exactly one axis is nudged per ray, got " + ray);

			double magnitude = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
			assertEquals(OcclusionTest.NUDGE, magnitude, 1.0E-12,
					"the displacement is the nudge distance");

			displacements.add(Math.signum(dx) + "," + Math.signum(dy) + "," + Math.signum(dz));
		}

		assertEquals(6, displacements.size(), "the six nudges are six distinct directions");
	}

	@Test
	@DisplayName("source and destination are not interchangeable")
	void endpointOrderMatters() {
		// Only the source is nudged (R4), so the rule is asymmetric. This test exists because
		// swapping the arguments is an easy mistake that produces a plausible-looking shell,
		// and nothing else here would catch it.
		RecordingWorld forwards = RecordingWorld.allBlocked();
		OcclusionTest.isOccluded(forwards, SOURCE_X, SOURCE_Y, SOURCE_Z, SENSOR_X, SENSOR_Y, SENSOR_Z);

		RecordingWorld backwards = RecordingWorld.allBlocked();
		OcclusionTest.isOccluded(backwards, SENSOR_X, SENSOR_Y, SENSOR_Z, SOURCE_X, SOURCE_Y, SOURCE_Z);

		assertEquals(SENSOR_X + 0.5, forwards.rays().getFirst().toX());
		assertEquals(SOURCE_X + 0.5, backwards.rays().getFirst().toX());
	}

	@Test
	@DisplayName("a position coincident with the sensor is still tested by the rule")
	void sourceEqualToSensorIsNotSpecialCased() {
		// The sensor's own position is inside its radius, so the solver will ask about it.
		// Vanilla applies the same rule there rather than short-cutting, and so does this:
		// the point of the assertion is that the code contains no special case, not that any
		// particular answer is right.
		RecordingWorld world = RecordingWorld.allClear();

		assertFalse(OcclusionTest.isOccluded(world, SENSOR_X, SENSOR_Y, SENSOR_Z, SENSOR_X, SENSOR_Y, SENSOR_Z));
		assertEquals(1, world.rayCount());
	}
}
