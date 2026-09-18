package com.scr0ols.sculksight.event;

/** Whether a client-derived calibrated tuning value is safe to use. */
public enum CalibrationAvailability {
	KNOWN,
	UNAVAILABLE,
	STALE
}
