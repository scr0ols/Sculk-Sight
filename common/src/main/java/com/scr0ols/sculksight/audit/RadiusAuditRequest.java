package com.scr0ols.sculksight.audit;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.scr0ols.sculksight.client.DetectorType;

/**
 * The validated arguments of {@code /sculksight find <type> <n> <mode>}. ARCHITECTURE.md section 12.1.
 *
 * <p><b>Validation lives in this record, not in the command.</b> Brigadier's own argument types
 * reject a great deal before this class is reached - a non-integer radius never gets here at all -
 * but what counts as a usable radius and what the three detector names are is this mod's
 * knowledge, not Brigadier's, and it belongs where a JUnit test can reach it. The two loader-side
 * command classes are registration and nothing else; both call {@link #of}.
 *
 * <p><b>{@code all} is a name like any other, not an absent argument.</b>
 * {@code /sculksight find all 32 live} audits all three types in {@link DetectorType};
 * {@code /sculksight find calibrated 32 live} narrows it to one. Both spell the choice out, because
 * the {@code type} argument is required - the mode argument beside it has no default either, and a
 * command whose middle argument may be omitted would have to tell {@code sensor} apart from a
 * radius by inspection. An empty {@link #detector} is therefore what {@code all} parses <em>to</em>,
 * not a stand-in for something the player left out, and it is an {@code Optional} rather than a
 * nullable field with a sentinel so the "every type" case cannot be read as "no type".
 *
 * <p>This record does not perform the audit. Selection over the sensor index, its ordering and
 * its cap are ARCHITECTURE.md section 12.1's {@code RadiusAudit}, which is not built yet; section
 * 12.4 is where the reason that is a separate piece of work is written down.
 */
public record RadiusAuditRequest(int radius, Optional<DetectorType> detector) {

	/** A radius of zero would audit nothing, so one block is the smallest request with a meaning. */
	public static final int MIN_RADIUS = 1;

	/**
	 * <b>512 blocks, which is vanilla's largest view distance of 32 chunks.</b> The sensor index
	 * holds loaded chunks only (ADR-038), so a radius beyond what the client has loaded cannot
	 * find a sensor the same query at 512 would miss: past this point a larger number is not a
	 * wider search, it is a longer wait for the same answer. The limit that will actually bind in
	 * practice is the sensor cap of ARCHITECTURE.md section 12.4, which is later work.
	 */
	public static final int MAX_RADIUS = 512;

	private static final String ALL_NAME = "all";
	private static final String NORMAL_NAME = "sensor";
	private static final String CALIBRATED_NAME = "calibrated";
	private static final String SHRIEKER_NAME = "shrieker";

	/**
	 * Every value the {@code type} argument accepts, in the order they are offered as completions:
	 * the widest first, then the three detectors narrowest-use last.
	 *
	 * <p>One list rather than a "detector names" list and a suggestion list beside it, because the
	 * two would have to stay equal to be correct and nothing would make them. What the parser accepts
	 * is what the command offers is what a rejection message lists.
	 */
	public static final List<String> TYPE_NAMES =
			List.of(ALL_NAME, NORMAL_NAME, CALIBRATED_NAME, SHRIEKER_NAME);

	public RadiusAuditRequest {
		if (detector == null) {
			throw new IllegalArgumentException("detector must be an Optional, never null");
		}
		if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
			throw new IllegalArgumentException(outOfRangeMessage(radius));
		}
	}

	/**
	 * Builds a request from the two raw arguments, or explains why it cannot.
	 *
	 * @param radius the radius in blocks, as Brigadier parsed it
	 * @param detectorName the type name the player typed; required, and {@code null} is rejected
	 *     rather than read as {@code all} - see {@link #parseDetector}
	 * @throws RadiusAuditArgumentException if the radius is outside {@link #MIN_RADIUS}..{@link
	 *     #MAX_RADIUS} or the name is not one of {@link #TYPE_NAMES}
	 */
	public static RadiusAuditRequest of(int radius, @Nullable String detectorName)
			throws RadiusAuditArgumentException {
		if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
			throw new RadiusAuditArgumentException(outOfRangeMessage(radius));
		}
		return new RadiusAuditRequest(radius, parseDetector(detectorName));
	}

	/**
	 * Maps a typed name onto a {@link DetectorType}, or empty for {@link #ALL_NAME}.
	 *
	 * <p>Matching is case-insensitive under {@link Locale#ROOT} rather than the default locale:
	 * the names are ASCII identifiers in this mod's own vocabulary, not text in the player's
	 * language, and a Turkish default locale would otherwise fold {@code I} somewhere else.
	 *
	 * <p><b>{@code null} is rejected, not treated as {@code all}.</b> Brigadier cannot reach this
	 * with a missing argument - the command declares all three as required - so a {@code null} here
	 * is a caller that forgot one, and quietly widening it into an every-detector audit would hide
	 * that behind a plausible-looking result. {@link RadiusAuditMode#of} rejects its own {@code null}
	 * for the same reason.
	 */
	private static Optional<DetectorType> parseDetector(@Nullable String detectorName)
			throws RadiusAuditArgumentException {
		if (detectorName == null) {
			throw new RadiusAuditArgumentException(unknownMessage(""));
		}
		String normalised = detectorName.toLowerCase(Locale.ROOT);
		return switch (normalised) {
			case ALL_NAME -> Optional.empty();
			case NORMAL_NAME -> Optional.of(DetectorType.NORMAL_SENSOR);
			case CALIBRATED_NAME -> Optional.of(DetectorType.CALIBRATED_SENSOR);
			case SHRIEKER_NAME -> Optional.of(DetectorType.SHRIEKER);
			default -> throw new RadiusAuditArgumentException(unknownMessage(detectorName));
		};
	}

	private static String unknownMessage(String detectorName) {
		return "Unknown detector type '" + detectorName + "'. Expected one of "
				+ String.join(", ", TYPE_NAMES) + ".";
	}

	private static String outOfRangeMessage(int radius) {
		return "Radius " + radius + " is out of range. Expected " + MIN_RADIUS + " to "
				+ MAX_RADIUS + " blocks.";
	}

	/** How this request reads back to the player, so the command and its tests agree on one wording. */
	public String describe() {
		return "radius " + radius + ", "
				+ detector.map(type -> "detector " + nameOf(type)).orElse("all detectors");
	}

	private static String nameOf(DetectorType type) {
		return switch (type) {
			case NORMAL_SENSOR -> NORMAL_NAME;
			case CALIBRATED_SENSOR -> CALIBRATED_NAME;
			case SHRIEKER -> SHRIEKER_NAME;
		};
	}
}
