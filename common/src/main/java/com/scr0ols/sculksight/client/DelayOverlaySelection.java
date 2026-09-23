package com.scr0ols.sculksight.client;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import com.scr0ols.sculksight.config.TrackedSensor;

/**
 * Which currently-rendered sensors should draw delay-overlay labels. Kept as plain data in and
 * out (no {@code ShellEntry}, which lives in the client-only source set the test suite cannot
 * compile against) so the selection rule itself stays covered by plain JUnit.
 */
public final class DelayOverlaySelection {

	private DelayOverlaySelection() {
	}

	/**
	 * The keys among {@code renderedKeys} whose tracked sensor has its delay-overlay flag on.
	 * A rendered key with no matching tracked sensor - an audited-only sensor from a radius
	 * find - is never selected: the flag lives on {@link TrackedSensor} alone, tracked-only for
	 * this phase. A tracked sensor whose key is no longer rendered (removed or out of range) is
	 * likewise excluded, since only {@code renderedKeys} membership is checked.
	 */
	public static Set<SensorKey> targets(Collection<TrackedSensor> tracked, Set<SensorKey> renderedKeys) {
		Set<SensorKey> selected = new LinkedHashSet<>();

		for (TrackedSensor sensor : tracked) {
			if (!sensor.delayOverlayEnabled()) {
				continue;
			}

			SensorKey key = new SensorKey(sensor.x(), sensor.y(), sensor.z());
			if (renderedKeys.contains(key)) {
				selected.add(key);
			}
		}

		return selected;
	}
}
