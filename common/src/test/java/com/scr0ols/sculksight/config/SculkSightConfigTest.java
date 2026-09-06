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

	@Test
	void changingASettingLeavesTheOriginalAlone() {
		SculkSightConfig original = SculkSightConfig.defaults();

		SculkSightConfig changed = original.withShellOpacityPercent(60);

		assertEquals(25, original.shellOpacityPercent());
		assertEquals(60, changed.shellOpacityPercent());
	}
}
