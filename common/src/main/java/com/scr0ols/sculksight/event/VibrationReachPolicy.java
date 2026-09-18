package com.scr0ols.sculksight.event;

/** Applies the event's dispatcher sweep radius without changing listener radius semantics. */
public final class VibrationReachPolicy {
	private VibrationReachPolicy() {
	}

	public static VibrationEvaluation evaluate(VibrationEventMetadata event, double distanceSquared) {
		if (event == null || Double.isNaN(distanceSquared) || distanceSquared < 0.0) {
			return VibrationEvaluation.UNAVAILABLE;
		}
		double radius = event.notificationRadius();
		return distanceSquared <= radius * radius
				? VibrationEvaluation.ACCEPTED
				: VibrationEvaluation.OUTSIDE_NOTIFICATION_REACH;
	}
}
