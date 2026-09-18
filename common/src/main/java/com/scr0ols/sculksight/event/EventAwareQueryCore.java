package com.scr0ols.sculksight.event;

import java.util.List;

/** Composes event-layer rules for a display without pretending they are a vanilla observation. */
public final class EventAwareQueryCore {
	private EventAwareQueryCore() {
	}

	public static Summary summarize(VibrationEventMetadata event, List<EventAwareSensorCandidate> candidates) {
		int accepted = 0;
		int geometryBlocked = 0;
		int invalid = 0;
		int outside = 0;
		int mismatch = 0;
		int unavailable = 0;

		for (EventAwareSensorCandidate candidate : candidates) {
			VibrationEvaluation source = VibrationSourceValidator.evaluate(eventToCandidate(event), candidate.source());
			if (source == VibrationEvaluation.INVALID_SOURCE) {
				invalid++;
				continue;
			}
			if (source == VibrationEvaluation.UNAVAILABLE) {
				unavailable++;
				continue;
			}
			VibrationEvaluation reach = VibrationReachPolicy.evaluate(event, candidate.distanceSquared());
			if (reach == VibrationEvaluation.OUTSIDE_NOTIFICATION_REACH) {
				outside++;
				continue;
			}
			if (reach == VibrationEvaluation.UNAVAILABLE || !candidate.geometryReachable()) {
				if (!candidate.geometryReachable()) {
					geometryBlocked++;
				} else {
					unavailable++;
				}
				continue;
			}
			if (candidate.calibrated()) {
				if (!event.frequency().isPresent() || candidate.tuning() == null
						|| !candidate.tuning().isKnown()) {
					unavailable++;
					continue;
				}
				int tuning = candidate.tuning().value();
				if (tuning != 0 && tuning != event.frequency().getAsInt()) {
					mismatch++;
					continue;
				}
			}
			accepted++;
		}

		return new Summary(accepted, geometryBlocked, invalid, outside, mismatch, unavailable);
	}

	private static VibrationEventCandidate eventToCandidate(VibrationEventMetadata event) {
		return new VibrationEventCandidate(event.frequency().orElse(0), event.notificationRadius(), false);
	}

	public record Summary(int accepted, int geometryBlocked, int invalidSource,
			int outsideNotificationReach, int frequencyMismatch, int unavailable) {
	}
}
