package com.scr0ols.sculksight.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IndexReconciliationTest {

	private static final WorldPosition SENSOR = new WorldPosition(10, 64, 10);
	private static final WorldPosition OTHER = new WorldPosition(0, 64, 0);
	private static final WorldPosition FAR_AWAY = new WorldPosition(1000, 64, 1000);

	@Test
	@DisplayName("before the fix: an index that never learned of a live sensor stays empty forever")
	void beforeTheFixAnEmptyIndexNeverSeesTheSensorOnItsOwn() {
		Map<WorldPosition, Integer> index = Map.of();

		Map<WorldPosition, Integer> unchanged = IndexReconciliation.apply(index, Map.of(), pos -> false);

		assertTrue(unchanged.isEmpty());
	}

	@Test
	@DisplayName("reconciling adds a sensor the index never learned of live")
	void reconcileAddsASensorThisIndexNeverLearnedOfLive() {
		Map<WorldPosition, Integer> index = Map.of();
		Map<WorldPosition, Integer> truth = Map.of(SENSOR, 16);

		Map<WorldPosition, Integer> reconciled = IndexReconciliation.apply(index, truth, pos -> true);

		assertEquals(16, reconciled.get(SENSOR));
	}

	@Test
	@DisplayName("reconciling removes a stale sensor within the reconciled region")
	void reconcileRemovesAStaleSensorWithinTheReconciledRegion() {
		Map<WorldPosition, Integer> index = Map.of(SENSOR, 8);

		Map<WorldPosition, Integer> reconciled = IndexReconciliation.apply(index, Map.of(), pos -> true);

		assertFalse(reconciled.containsKey(SENSOR));
	}

	@Test
	@DisplayName("reconciling leaves a sensor outside the reconciled region alone")
	void reconcileLeavesASensorOutsideTheReconciledRegionAlone() {
		Map<WorldPosition, Integer> index = Map.of(FAR_AWAY, 16);

		Map<WorldPosition, Integer> reconciled = IndexReconciliation.apply(index, Map.of(OTHER, 8),
				pos -> pos.x() < 100 && pos.z() < 100);

		assertEquals(16, reconciled.get(FAR_AWAY));
		assertEquals(8, reconciled.get(OTHER));
	}

	@Test
	@DisplayName("reconciling updates a radius change for an already-indexed sensor")
	void reconcileUpdatesARadiusChangeForAnAlreadyIndexedSensor() {
		Map<WorldPosition, Integer> index = Map.of(SENSOR, 8);

		Map<WorldPosition, Integer> reconciled = IndexReconciliation.apply(index, Map.of(SENSOR, 16), pos -> true);

		assertEquals(16, reconciled.get(SENSOR));
	}
}
