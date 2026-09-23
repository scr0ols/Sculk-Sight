package com.scr0ols.sculksight.audit;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

/** What {@code /sculksight find} should do with the sensors it selected. */
public enum RadiusAuditMode {

	/** Select once at the command's position and add every selected sensor to the tracked list. */
	STATIC("static"),

	/** Select every tick against the player's current position and draw the result without saving it. */
	LIVE("live");

	/** The words a player types, in this order, for the command's suggestion list. */
	public static final List<String> NAMES = List.of(STATIC.name, LIVE.name);

	private final String name;

	RadiusAuditMode(String name) {
		this.name = name;
	}

	/** The word a player types for this mode. */
	public String modeName() {
		return name;
	}

	/** Returns the mode named by {@code modeName}, ignoring case. */
	public static RadiusAuditMode of(@Nullable String modeName) throws RadiusAuditArgumentException {
		if (modeName == null) {
			throw new RadiusAuditArgumentException(unknownMessage(""));
		}

		String normalised = modeName.toLowerCase(Locale.ROOT);
		for (RadiusAuditMode mode : values()) {
			if (mode.name.equals(normalised)) {
				return mode;
			}
		}

		throw new RadiusAuditArgumentException(unknownMessage(modeName));
	}

	private static String unknownMessage(String modeName) {
		return "Unknown mode '" + modeName + "'. Expected " + String.join(" or ", NAMES) + ".";
	}
}
