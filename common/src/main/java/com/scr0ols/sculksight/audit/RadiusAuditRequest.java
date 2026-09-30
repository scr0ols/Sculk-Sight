package com.scr0ols.sculksight.audit;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;

import com.scr0ols.sculksight.client.DetectorType;

/** The validated arguments of {@code /sculksight find <type> <n> <mode>}. */
public record RadiusAuditRequest(int radius, Optional<DetectorType> detector) {

	/** A radius of zero would audit nothing, so one block is the smallest request with a meaning. */
	public static final int MIN_RADIUS = 1;

	/** The largest accepted radius, in blocks. */
	public static final int MAX_RADIUS = 512;

	private static final String ALL_NAME = "all";
	private static final String NORMAL_NAME = "sensor";
	private static final String CALIBRATED_NAME = "calibrated";
	private static final String SHRIEKER_NAME = "shrieker";

	/** Every value the {@code type} argument accepts, in the order they are offered as completions. */
	public static final List<String> TYPE_NAMES =
			List.of(ALL_NAME, NORMAL_NAME, CALIBRATED_NAME, SHRIEKER_NAME);

	public RadiusAuditRequest {
		if (detector == null) {
			throw new IllegalArgumentException("detector must be an Optional, never null");
		}
		if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
			throw new IllegalArgumentException(
					"radius must be between " + MIN_RADIUS + " and " + MAX_RADIUS + ", was " + radius);
		}
	}

	/** Builds a request from the two raw arguments, or explains why it cannot. */
	public static RadiusAuditRequest of(int radius, @Nullable String detectorName)
			throws RadiusAuditArgumentException {
		if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
			throw new RadiusAuditArgumentException(outOfRangeMessage(radius));
		}
		return new RadiusAuditRequest(radius, parseDetector(detectorName));
	}

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

	private static Component unknownMessage(String detectorName) {
		return Component.translatable("sculksight.command.find.detector.unknown", detectorName,
				String.join(", ", TYPE_NAMES));
	}

	private static Component outOfRangeMessage(int radius) {
		return Component.translatable("sculksight.command.find.radius.out_of_range", radius,
				MIN_RADIUS, MAX_RADIUS);
	}

	/** How this request reads back to the player, so the command and its tests agree on one wording. */
	public Component describe() {
		Component detectorClause = detector
				.<Component>map(type -> Component.translatable(
						"sculksight.command.find.description.detector", nameOf(type)))
				.orElseGet(() -> Component.translatable("sculksight.command.find.description.all"));
		return Component.translatable("sculksight.command.find.description", radius, detectorClause);
	}

	private static String nameOf(DetectorType type) {
		return switch (type) {
			case NORMAL_SENSOR -> NORMAL_NAME;
			case CALIBRATED_SENSOR -> CALIBRATED_NAME;
			case SHRIEKER -> SHRIEKER_NAME;
		};
	}
}
