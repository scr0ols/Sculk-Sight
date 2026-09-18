package com.scr0ols.sculksight.event;

/** The event-layer outcome; geometry occlusion is intentionally evaluated elsewhere. */
public enum VibrationEvaluation {
	INVALID_SOURCE,
	OUTSIDE_NOTIFICATION_REACH,
	FREQUENCY_MISMATCH,
	ACCEPTED,
	UNAVAILABLE
}
