package com.scr0ols.sculksight.event;

import java.util.OptionalInt;

/** The event-specific reach facts required by the current vibration model. */
public enum VibrationEventType {
	DEFAULT(16, OptionalInt.empty()),
	JUKEBOX_PLAY(10, OptionalInt.empty()),
	JUKEBOX_STOP(10, OptionalInt.empty()),
	SHRIEK(32, OptionalInt.empty()),
	BOUNCE(16, OptionalInt.of(2));

	private final VibrationEventMetadata metadata;

	VibrationEventType(int notificationRadius, OptionalInt frequency) {
		this.metadata = new VibrationEventMetadata(frequency, notificationRadius);
	}

	public VibrationEventMetadata metadata() {
		return metadata;
	}
}
