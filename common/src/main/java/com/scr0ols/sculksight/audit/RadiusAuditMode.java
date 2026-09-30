package com.scr0ols.sculksight.audit;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

/** What {@code /sculksight find} should do with the sensors it selected. */
public enum RadiusAuditMode {

	/** Stop an active live find and clear its audit renders, touching no tracked or pinned sensor. */
	OFF("off"),

	/** Select once at the command's position and add every selected sensor to the tracked list. */
	STATIC("static"),

	/** Select every tick against the player's current position and draw the result without saving it. */
	LIVE("live");

	/** The words a player types, in this order, for the command's suggestion list. */
	public static final List<String> NAMES = List.of(OFF.name, STATIC.name, LIVE.name);

	private final String name;

	RadiusAuditMode(String name) {
		this.name = name;
	}

	/** The word a player types for this mode. */
	public String modeName() {
		return name;
	}

	/**
	 * The entries of {@link #NAMES} that could still complete {@code remaining}, in {@link #NAMES}'
	 * order. Matching is a case-insensitive prefix check, the same rule the game's own suggestion
	 * matching applies to a word with no underscores.
	 */
	public static List<String> suggestionsMatching(String remaining) {
		String needle = remaining.toLowerCase(Locale.ROOT);
		return NAMES.stream().filter(name -> name.startsWith(needle)).toList();
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
