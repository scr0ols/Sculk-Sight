package com.scr0ols.sculksight.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link IndexReconciliation}, reproducing the captain's 2026-09-08 report at this
 * class's own pure-data seam.
 *
 * <p><b>The actual bug.</b> On NeoForge, a sculk sensor built into a detection box and tested in
 * one continuous session - the chunk never reloading in between - never reached
 * {@code SensorIndex} at all: NeoForge has no live block-entity add/remove event to call
 * {@code SensorIndex#onBlockEntityLoad} from, unlike Fabric. {@code SensorIndex#snapshot()} - what
 * {@code DetectionIndicator#isDetected} reads every tick - therefore had no entry for it. Every
 * reading came back "not detected", and it never changed no matter where the player stood, because
 * the sensor was never there to find: one missing index entry explains both symptoms the captain
 * reported, not two separate bugs. {@code reconcileMissesASensorThisIndexNeverLearnedOfLive} below
 * is that reproduction; the rest establish the boundaries {@code SensorIndex#reconcile}'s own
 * javadoc claims for the fix built on this arithmetic.
 *
 * <p>What a green run here means, the same caveat every verification-mechanism test in this
 * project carries: these are ordinary Java maps, not a live {@code SensorIndex} and not a real
 * chunk sweep, so this establishes that the diff-and-merge arithmetic is correct given two maps.
 * It establishes nothing about whether {@code SensorIndex} or {@code IndexSweep} are themselves
 * built correctly against the game.
 */
class IndexReconciliationTest {

	private static final WorldPosition SENSOR = new WorldPosition(10, 64, 10);
	private static final WorldPosition OTHER = new WorldPosition(0, 64, 0);
	private static final WorldPosition FAR_AWAY = new WorldPosition(1000, 64, 1000);

	@Test
	@DisplayName("before the fix: an index that never learned of a live sensor stays empty forever")
	void beforeTheFixAnEmptyIndexNeverSeesTheSensorOnItsOwn() {
		// This is the bug itself, not the fix: an index with no entry for SENSOR, left alone
		// (an empty truth, everything excluded from the region), never gains one - the same
		// permanently-missing entry DetectionIndicator read on NeoForge, tick after tick, no
		// matter where the player stood.
		Map<WorldPosition, Integer> index = Map.of();

		Map<WorldPosition, Integer> unchanged = IndexReconciliation.apply(index, Map.of(), pos -> false);

		assertTrue(unchanged.isEmpty());
	}

	@Test
	@DisplayName("reconciling adds a sensor the index never learned of live")
	void reconcileAddsASensorThisIndexNeverLearnedOfLive() {
		// The fix: a fresh ground-truth sweep of the region the sensor is actually in supplies
		// what the missing live event never did.
		Map<WorldPosition, Integer> index = Map.of();
		Map<WorldPosition, Integer> truth = Map.of(SENSOR, 16);

		Map<WorldPosition, Integer> reconciled = IndexReconciliation.apply(index, truth, pos -> true);

		assertEquals(16, reconciled.get(SENSOR));
	}

	@Test
	@DisplayName("reconciling removes a stale sensor within the reconciled region")
	void reconcileRemovesAStaleSensorWithinTheReconciledRegion() {
		Map<WorldPosition, Integer> index = Map.of(SENSOR, 8);

		// Ground truth no longer lists it - the sensor was broken - so a fresh sweep of the same
		// region finds nothing there any more.
		Map<WorldPosition, Integer> reconciled = IndexReconciliation.apply(index, Map.of(), pos -> true);

		assertFalse(reconciled.containsKey(SENSOR));
	}

	@Test
	@DisplayName("reconciling leaves a sensor outside the reconciled region alone")
	void reconcileLeavesASensorOutsideTheReconciledRegionAlone() {
		Map<WorldPosition, Integer> index = Map.of(FAR_AWAY, 16);

		// A reconcile call for an unrelated, smaller region must not drop a sensor it never
		// looked at.
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
