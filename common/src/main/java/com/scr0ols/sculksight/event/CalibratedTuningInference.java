package com.scr0ols.sculksight.event;

/** Pure state gate for client-derived calibrated tuning. */
public final class CalibratedTuningInference {
	private CalibratedTuningInference() {
	}

	public static CalibratedTuning read(boolean loaded, boolean synchronizedState,
			CalibrationSignalReader signalReader) {
		if (!synchronizedState) {
			return new CalibratedTuning(CalibrationAvailability.STALE, 0);
		}
		if (!loaded || signalReader == null) {
			return new CalibratedTuning(CalibrationAvailability.UNAVAILABLE, 0);
		}
		int signal = signalReader.readSignal();
		if (signal < 0 || signal > 15) {
			return new CalibratedTuning(CalibrationAvailability.UNAVAILABLE, 0);
		}
		return new CalibratedTuning(CalibrationAvailability.KNOWN, signal);
	}
}
