package com.scr0ols.sculksight.audit;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * What {@code /sculksight find} should do with the sensors it selected - the command's third
 * argument, and the whole of the difference between its two jobs.
 *
 * <p><b>Two jobs, not one command with a surprising habit.</b> Until 2026-09-17 there was only the
 * {@link #LIVE} behaviour and no word for it, so a player who ran the command to look at the
 * sensors around a redstone build got a selection that silently re-derived itself as they walked
 * away from it. That is a genuinely useful thing - it is an x-ray that follows you - but it is not
 * the same thing as "pin these sensors so I can work on them", and conflating them is what made
 * the older command hard to explain. Naming the modes is the fix; neither behaviour is the default,
 * because a player who has not chosen between them has not yet said which job they wanted.
 *
 * <p><b>Validated here rather than by Brigadier</b>, the same trade {@link RadiusAuditRequest}
 * makes for the detector name and for the same reason: the command's argument is a word with a
 * suggestion list, so "is {@code statc} a mode?" is answered by {@link #of} where the JUnit suite
 * can reach it, instead of by a literal child node where it cannot. ARCHITECTURE.md section 12.1's
 * "selection and validation at layer 1".
 */
public enum RadiusAuditMode {

	/**
	 * Select once, at the position the command was run from, and add every selected sensor to
	 * {@code SculkSightConfig.trackedSensors()} - saved, named, and with the same Rename, Enabled
	 * and Remove controls a sensor tracked by keypress gets. No audit stays active afterwards:
	 * the sensors are the result, so there is nothing left to re-select.
	 */
	STATIC("static"),

	/**
	 * Select every tick, against wherever the player is now, and draw the result without saving any
	 * of it - {@link RadiusAuditController}'s active request. The shells follow the player, and the
	 * settings screen lists the current selection with a per-sensor visibility toggle and a control
	 * to pin the lot, so a live find can still become a {@link #STATIC} one after the fact.
	 */
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

	/**
	 * @param modeName the word the player typed; case is not significant, the same latitude
	 *     {@link RadiusAuditRequest} gives a detector name
	 * @throws RadiusAuditArgumentException when it names no mode, with a message that lists the
	 *     ones it could have been
	 */
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
