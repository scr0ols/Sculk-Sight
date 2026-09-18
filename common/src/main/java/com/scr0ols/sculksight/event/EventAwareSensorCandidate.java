package com.scr0ols.sculksight.event;

/** One sensor's inputs to the event-layer query; geometry remains an explicit separate input. */
public record EventAwareSensorCandidate(
		double distanceSquared,
		boolean geometryReachable,
		boolean calibrated,
		CalibratedTuning tuning,
		VibrationSourceContext source) {
}
