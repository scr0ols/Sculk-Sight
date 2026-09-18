package com.scr0ols.sculksight.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CalibratedTuningInferenceTest {
	@Test
	void readsAKnownNonZeroTuningFromLoadedSynchronizedState() {
		CalibratedTuning tuning = CalibratedTuningInference.read(true, true, () -> 5);

		assertEquals(CalibrationAvailability.KNOWN, tuning.availability());
		assertEquals(5, tuning.value());
		assertTrue(tuning.isKnown());
	}

	@Test
	void preservesKnownZeroAsARealTuning() {
		CalibratedTuning tuning = CalibratedTuningInference.read(true, true, () -> 0);

		assertEquals(CalibrationAvailability.KNOWN, tuning.availability());
		assertEquals(0, tuning.value());
	}

	@Test
	void unloadedStateIsUnavailableRatherThanZero() {
		CalibratedTuning tuning = CalibratedTuningInference.read(false, true, () -> 0);

		assertEquals(CalibrationAvailability.UNAVAILABLE, tuning.availability());
		assertEquals(0, tuning.value());
		assertFalse(tuning.isKnown());
	}

	@Test
	void unsynchronizedStateIsStaleRatherThanKnown() {
		CalibratedTuning tuning = CalibratedTuningInference.read(true, false, () -> 7);

		assertEquals(CalibrationAvailability.STALE, tuning.availability());
		assertFalse(tuning.isKnown());
	}

	@Test
	void missingReaderIsUnavailable() {
		assertEquals(CalibrationAvailability.UNAVAILABLE,
				CalibratedTuningInference.read(true, true, null).availability());
	}

	@Test
	void impossibleSignalsAreUnavailable() {
		assertEquals(CalibrationAvailability.UNAVAILABLE,
				CalibratedTuningInference.read(true, true, () -> 16).availability());
		assertEquals(CalibrationAvailability.UNAVAILABLE,
				CalibratedTuningInference.read(true, true, () -> -1).availability());
	}
}
