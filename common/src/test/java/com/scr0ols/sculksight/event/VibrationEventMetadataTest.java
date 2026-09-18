package com.scr0ols.sculksight.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

class VibrationEventMetadataTest {
	@Test
	void defaultEventsUseTheVanillaNotificationRadius() {
		assertEquals(16, VibrationEventType.DEFAULT.metadata().notificationRadius());
		assertFalse(VibrationEventType.DEFAULT.metadata().frequency().isPresent());
	}

	@Test
	void jukeboxEventsHaveTheShorterNotificationRadius() {
		assertEquals(10, VibrationEventType.JUKEBOX_PLAY.metadata().notificationRadius());
		assertEquals(10, VibrationEventType.JUKEBOX_STOP.metadata().notificationRadius());
	}

	@Test
	void shriekHasTheLongerNotificationRadius() {
		assertEquals(32, VibrationEventType.SHRIEK.metadata().notificationRadius());
	}

	@Test
	void bounceHasFrequencyTwoAndTheDefaultRadius() {
		VibrationEventMetadata metadata = VibrationEventType.BOUNCE.metadata();
		assertEquals(OptionalInt.of(2), metadata.frequency());
		assertEquals(16, metadata.notificationRadius());
	}

	@Test
	void reachUsesTheEventRadiusAtTheBoundary() {
		VibrationEventMetadata metadata = VibrationEventType.JUKEBOX_PLAY.metadata();
		assertEquals(VibrationEvaluation.ACCEPTED, VibrationReachPolicy.evaluate(metadata, 100.0));
		assertEquals(VibrationEvaluation.OUTSIDE_NOTIFICATION_REACH,
				VibrationReachPolicy.evaluate(metadata, 100.01));
	}

	@Test
	void reachDoesNotUseTheListenerRadiusAsAProxy() {
		VibrationEventMetadata metadata = VibrationEventType.JUKEBOX_PLAY.metadata();
		assertEquals(VibrationEvaluation.OUTSIDE_NOTIFICATION_REACH,
				VibrationReachPolicy.evaluate(metadata, 16.0 * 16.0));
	}

	@Test
	void invalidDistanceIsUnavailable() {
		assertEquals(VibrationEvaluation.UNAVAILABLE,
				VibrationReachPolicy.evaluate(VibrationEventType.DEFAULT.metadata(), Double.NaN));
		assertEquals(VibrationEvaluation.UNAVAILABLE,
				VibrationReachPolicy.evaluate(VibrationEventType.DEFAULT.metadata(), -1.0));
		assertTrue(VibrationEventType.BOUNCE.metadata().frequency().isPresent());
	}
}
