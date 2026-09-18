package com.scr0ols.sculksight.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class EventAwareQueryCoreTest {
	private static final VibrationSourceContext SOURCE =
			new VibrationSourceContext(false, VibrationSourceKind.NONE, false);

	@Test
	void summaryKeepsGeometrySeparateFromEventEligibility() {
		EventAwareQueryCore.Summary summary = EventAwareQueryCore.summarize(
				VibrationEventType.BOUNCE.metadata(), List.of(
						new EventAwareSensorCandidate(1.0, true, false, null, SOURCE),
						new EventAwareSensorCandidate(1.0, false, false, null, SOURCE)));

		assertEquals(1, summary.accepted());
		assertEquals(1, summary.geometryBlocked());
	}

	@Test
	void calibratedMismatchAndZeroAreDistinct() {
		VibrationEventMetadata bounce = VibrationEventType.BOUNCE.metadata();
		EventAwareQueryCore.Summary summary = EventAwareQueryCore.summarize(bounce, List.of(
				new EventAwareSensorCandidate(1.0, true, true,
						new CalibratedTuning(CalibrationAvailability.KNOWN, 3), SOURCE),
				new EventAwareSensorCandidate(1.0, true, true,
						new CalibratedTuning(CalibrationAvailability.KNOWN, 0), SOURCE),
				new EventAwareSensorCandidate(1.0, true, true,
						new CalibratedTuning(CalibrationAvailability.UNAVAILABLE, 0), SOURCE)));

		assertEquals(1, summary.accepted());
		assertEquals(1, summary.frequencyMismatch());
		assertEquals(1, summary.unavailable());
	}
}
