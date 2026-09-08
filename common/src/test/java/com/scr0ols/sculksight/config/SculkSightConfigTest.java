package com.scr0ols.sculksight.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for the settings record, and for its agreement with ADR-022's authored alphas. */
class SculkSightConfigTest {

	/**
	 * The whole reason the slider stores a percentage rather than a float: at the default position
	 * the two derived alphas must be exactly the numbers ADR-022 decided, not near them.
	 */
	@Test
	void theDefaultsAreTheAlphasAdr022Chose() {
		SculkSightConfig config = SculkSightConfig.defaults();

		assertEquals(25, config.shellOpacityPercent());
		assertEquals(0.25F, config.depthTestedAlpha(), 1.0E-6F);
		assertEquals(0.10F, config.seeThroughAlpha(), 1.0E-6F);
	}

	@ParameterizedTest
	@CsvSource({"0, 0.00, 0.00", "10, 0.10, 0.04", "25, 0.25, 0.10", "50, 0.50, 0.20",
			"100, 1.00, 0.40"})
	void bothAlphasFollowTheOneSlider(int percent, float depthTested, float seeThrough) {
		SculkSightConfig config = new SculkSightConfig(percent);

		assertEquals(depthTested, config.depthTestedAlpha(), 1.0E-6F);
		assertEquals(seeThrough, config.seeThroughAlpha(), 1.0E-6F);
	}

	/**
	 * ADR-021's see-through pass is the fainter of the two at every position, which is what makes
	 * one control legitimate rather than a shortcut - a player cannot invert the two passes.
	 */
	@ParameterizedTest
	@ValueSource(ints = {1, 25, 50, 99, 100})
	void theSeeThroughPassIsNeverDenserThanTheDepthTestedOne(int percent) {
		SculkSightConfig config = new SculkSightConfig(percent);

		org.junit.jupiter.api.Assertions.assertTrue(
				config.seeThroughAlpha() < config.depthTestedAlpha(),
				"see-through " + config.seeThroughAlpha() + " should be under depth-tested "
						+ config.depthTestedAlpha());
	}

	@ParameterizedTest
	@ValueSource(ints = {-1, 101, Integer.MIN_VALUE, Integer.MAX_VALUE})
	void refusesAPercentageOutsideItsOwnRange(int percent) {
		assertThrows(IllegalArgumentException.class, () -> new SculkSightConfig(percent));
	}

	@ParameterizedTest
	@CsvSource({"-1, 0", "0, 0", "25, 25", "100, 100", "101, 100", "-2147483648, 0",
			"2147483647, 100"})
	void clampingMovesAValueToTheNearestPermittedOne(int given, int expected) {
		assertEquals(expected, SculkSightConfig.clampShellOpacityPercent(given));
	}

	/**
	 * The {@code double} overload, and the reason it exists: every one of these is a value that
	 * {@code (int) Math.round(...)} would have wrapped before any clamp could see it
	 * (OPEN-QUESTIONS.md section 22.2). The last row is why {@code NaN} is named explicitly rather
	 * than left to {@code Math.max} and {@code Math.min}, which propagate it.
	 */
	@ParameterizedTest
	@CsvSource({"-1.0, 0.0", "0.0, 0.0", "30.4, 30.4", "100.0, 100.0", "100.6, 100.0",
			"1.0E300, 100.0", "-1.0E300, 0.0", "Infinity, 100.0", "-Infinity, 0.0", "NaN, 0.0"})
	void clampingADoubleBoundsItBeforeAnyNarrowingCanWrap(double given, double expected) {
		assertEquals(expected, SculkSightConfig.clampShellOpacityPercent(given));
	}

	/**
	 * The property the {@code double} overload is for, stated as the thing that was wrong: after it
	 * runs, the narrowing cannot lose information, because the value is inside a range an
	 * {@code int} represents exactly.
	 */
	@ParameterizedTest
	@ValueSource(doubles = {1.0E300, Double.MAX_VALUE, Double.POSITIVE_INFINITY,
			Double.NEGATIVE_INFINITY, Double.NaN, 2147483648.0})
	void aClampedDoubleNarrowsToAPercentageTheRecordAccepts(double given) {
		int percent = (int) Math.round(SculkSightConfig.clampShellOpacityPercent(given));

		assertEquals(percent, new SculkSightConfig(percent).shellOpacityPercent(),
				"the record's own constructor is the check: it refuses anything out of range");
	}

	@Test
	void changingASettingLeavesTheOriginalAlone() {
		SculkSightConfig original = SculkSightConfig.defaults();

		SculkSightConfig changed = original.withShellOpacityPercent(60);

		assertEquals(25, original.shellOpacityPercent());
		assertEquals(60, changed.shellOpacityPercent());
	}
}
