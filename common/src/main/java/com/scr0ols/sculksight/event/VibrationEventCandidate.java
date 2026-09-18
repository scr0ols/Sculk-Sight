package com.scr0ols.sculksight.event;

/** Loader-neutral event facts; event tables and Minecraft holders belong to a later adapter. */
public record VibrationEventCandidate(
		int frequency,
		int notificationRadius,
		boolean ignoredWhenSneaking) {

	public VibrationEventCandidate {
		if (frequency < 0) {
			throw new IllegalArgumentException("frequency must be non-negative");
		}
		if (notificationRadius < 0) {
			throw new IllegalArgumentException("notificationRadius must be non-negative");
		}
	}
}
