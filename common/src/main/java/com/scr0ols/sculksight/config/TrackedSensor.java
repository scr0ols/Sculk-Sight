package com.scr0ols.sculksight.config;

import java.util.Objects;

/** A persisted sensor selection and the player's display preferences for it. */
public record TrackedSensor(int x, int y, int z, String name, boolean enabled, boolean delayOverlayEnabled) {

	public static final int MAX_NAME_LENGTH = 64;

	public TrackedSensor {
		Objects.requireNonNull(name, "name");
		name = name.strip();
		if (name.isEmpty()) {
			throw new IllegalArgumentException("name must not be blank");
		}
		if (name.length() > MAX_NAME_LENGTH) {
			throw new IllegalArgumentException("name is longer than " + MAX_NAME_LENGTH + " characters");
		}
	}

	public static String defaultName(int x, int y, int z) {
		return "Sensor " + x + ", " + y + ", " + z;
	}

	public static TrackedSensor selected(int x, int y, int z) {
		return new TrackedSensor(x, y, z, defaultName(x, y, z), true, false);
	}

	public TrackedSensor withName(String newName) {
		return new TrackedSensor(x, y, z, newName, enabled, delayOverlayEnabled);
	}

	public TrackedSensor withEnabled(boolean newEnabled) {
		return new TrackedSensor(x, y, z, name, newEnabled, delayOverlayEnabled);
	}

	public TrackedSensor withDelayOverlayEnabled(boolean newDelayOverlayEnabled) {
		return new TrackedSensor(x, y, z, name, enabled, newDelayOverlayEnabled);
	}
}
