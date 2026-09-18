package com.scr0ols.sculksight.event;

/** A calibrated sensor tuning value, including whether the client may safely use it. */
public record CalibratedTuning(CalibrationAvailability availability, int value) {

	public CalibratedTuning {
		if (availability == null) {
			throw new NullPointerException("availability");
		}
		if (availability != CalibrationAvailability.KNOWN && value != 0) {
			throw new IllegalArgumentException("unknown tuning must not carry a value");
		}
		if (value < 0 || value > 15) {
			throw new IllegalArgumentException("tuning must be between 0 and 15");
		}
	}

	public boolean isKnown() {
		return availability == CalibrationAvailability.KNOWN;
	}
}
