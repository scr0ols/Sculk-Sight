package com.scr0ols.sculksight.event;

import java.util.OptionalInt;

/** Event metadata kept independent of Minecraft holders and listener radius. */
public record VibrationEventMetadata(
		OptionalInt frequency,
		int notificationRadius) {

	public VibrationEventMetadata {
		if (frequency == null) {
			throw new NullPointerException("frequency");
		}
		if (notificationRadius < 0) {
			throw new IllegalArgumentException("notificationRadius must be non-negative");
		}
	}
}
