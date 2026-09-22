package com.scr0ols.sculksight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.scr0ols.sculksight.config.TrackedSensor;

/** The delay-overlay ("H") selection rule: which rendered sensors get labels, kept pure. */
class DelayOverlaySelectionTest {

	private static TrackedSensor sensorAt(int x, int y, int z, boolean delayOverlayEnabled) {
		return new TrackedSensor(x, y, z, TrackedSensor.defaultName(x, y, z), true, delayOverlayEnabled);
	}

	@Test
	void noFlagsOnSelectsNothing() {
		List<TrackedSensor> tracked = List.of(sensorAt(1, 2, 3, false), sensorAt(4, 5, 6, false));
		Set<SensorKey> rendered = Set.of(new SensorKey(1, 2, 3), new SensorKey(4, 5, 6));

		assertTrue(DelayOverlaySelection.targets(tracked, rendered).isEmpty());
	}

	@Test
	void oneFlagOnSelectsOnlyThatSensor() {
		List<TrackedSensor> tracked = List.of(sensorAt(1, 2, 3, true), sensorAt(4, 5, 6, false));
		Set<SensorKey> rendered = Set.of(new SensorKey(1, 2, 3), new SensorKey(4, 5, 6));

		assertEquals(Set.of(new SensorKey(1, 2, 3)), DelayOverlaySelection.targets(tracked, rendered));
	}

	@Test
	void multipleFlagsOnSelectsAllOfThem() {
		List<TrackedSensor> tracked = List.of(
				sensorAt(1, 2, 3, true), sensorAt(4, 5, 6, true), sensorAt(7, 8, 9, false));
		Set<SensorKey> rendered = Set.of(
				new SensorKey(1, 2, 3), new SensorKey(4, 5, 6), new SensorKey(7, 8, 9));

		assertEquals(Set.of(new SensorKey(1, 2, 3), new SensorKey(4, 5, 6)),
				DelayOverlaySelection.targets(tracked, rendered));
	}

	@Test
	void aFlaggedSensorNoLongerRenderedIsExcludedWithoutCrashing() {
		List<TrackedSensor> tracked = List.of(sensorAt(1, 2, 3, true));
		Set<SensorKey> rendered = Set.of();

		assertTrue(DelayOverlaySelection.targets(tracked, rendered).isEmpty(),
				"a tracked sensor removed from the render set must not be selected");
	}

	@Test
	void aRenderedKeyWithNoMatchingTrackedSensorIsExcludedRegardless() {
		List<TrackedSensor> tracked = List.of();
		Set<SensorKey> rendered = Set.of(new SensorKey(10, 20, 30));

		assertTrue(DelayOverlaySelection.targets(tracked, rendered).isEmpty(),
				"an audited-only entry has no TrackedSensor to carry the flag, so it is out of scope");
	}
}
