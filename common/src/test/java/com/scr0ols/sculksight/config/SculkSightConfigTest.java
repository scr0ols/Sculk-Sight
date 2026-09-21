package com.scr0ols.sculksight.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for the settings record, and for its agreement with the authored alphas. */
class SculkSightConfigTest {

	@Test
	void theDefaultsAreTheAlphasAdr022Chose() {
		SculkSightConfig config = SculkSightConfig.defaults();

		assertEquals(25, config.shellOpacityPercent());
		assertEquals(0.25F, config.depthTestedAlpha(), 1.0E-6F);
		assertEquals(0.10F, config.seeThroughAlpha(), 1.0E-6F);
	}

	@Test
	void theDefaultRenderPolicyIsUnion() {
		assertEquals(RenderPolicy.UNION, SculkSightConfig.defaults().renderPolicy());
		assertEquals(RenderPolicy.UNION, SculkSightConfig.DEFAULT_RENDER_POLICY);
	}

	@Test
	void theDefaultRadiusAuditCapIsThirtyTwo() {
		assertEquals(32, SculkSightConfig.defaults().radiusAuditCap());
		assertEquals(32, SculkSightConfig.DEFAULT_RADIUS_AUDIT_CAP);
	}

	@Test
	void selectionIsDeduplicatedAndBounded() {
		SculkSightConfig config = SculkSightConfig.defaults();
		for (int index = 0; index < SculkSightConfig.MAX_TRACKED_SENSORS + 2; index++) {
			config = config.track(TrackedSensor.selected(index, 0, 0));
		}
		SculkSightConfig duplicate = config.track(TrackedSensor.selected(0, 0, 0));

		assertEquals(SculkSightConfig.MAX_TRACKED_SENSORS, config.trackedSensors().size());
		assertSame(config, duplicate);
		assertEquals("Sensor 0, 0, 0", config.trackedSensors().getFirst().name());
	}

	@Test
	void untrackingRemovesOnlyTheRequestedPosition() {
		TrackedSensor first = TrackedSensor.selected(1, 2, 3);
		TrackedSensor second = TrackedSensor.selected(4, 5, 6);
		SculkSightConfig config = new SculkSightConfig(25, RenderPolicy.UNION, List.of(first, second));

		SculkSightConfig updated = config.untrack(1, 2, 3);

		assertEquals(List.of(second), updated.trackedSensors());
		assertEquals(List.of(first, second), config.trackedSensors());
		assertSame(updated, updated.untrack(99, 99, 99));
	}

	@ParameterizedTest
	@CsvSource({"0, 0.00, 0.00", "10, 0.10, 0.04", "25, 0.25, 0.10", "50, 0.50, 0.20",
			"100, 1.00, 0.40"})
	void bothAlphasFollowTheOneSlider(int percent, float depthTested, float seeThrough) {
		SculkSightConfig config = new SculkSightConfig(percent, SculkSightConfig.DEFAULT_RENDER_POLICY);

		assertEquals(depthTested, config.depthTestedAlpha(), 1.0E-6F);
		assertEquals(seeThrough, config.seeThroughAlpha(), 1.0E-6F);
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 25, 50, 99, 100})
	void theSeeThroughPassIsNeverDenserThanTheDepthTestedOne(int percent) {
		SculkSightConfig config = new SculkSightConfig(percent, SculkSightConfig.DEFAULT_RENDER_POLICY);

		org.junit.jupiter.api.Assertions.assertTrue(
				config.seeThroughAlpha() < config.depthTestedAlpha(),
				"see-through " + config.seeThroughAlpha() + " should be under depth-tested "
						+ config.depthTestedAlpha());
	}

	@ParameterizedTest
	@ValueSource(ints = {-1, 101, Integer.MIN_VALUE, Integer.MAX_VALUE})
	void refusesAPercentageOutsideItsOwnRange(int percent) {
		assertThrows(IllegalArgumentException.class,
				() -> new SculkSightConfig(percent, SculkSightConfig.DEFAULT_RENDER_POLICY));
	}

	@Test
	void refusesANullRenderPolicy() {
		assertThrows(NullPointerException.class,
				() -> new SculkSightConfig(SculkSightConfig.DEFAULT_SHELL_OPACITY_PERCENT, null));
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 257, Integer.MIN_VALUE, Integer.MAX_VALUE})
	void refusesARadiusAuditCapOutsideItsOwnRange(int cap) {
		assertThrows(IllegalArgumentException.class,
				() -> SculkSightConfig.defaults().withRadiusAuditCap(cap));
	}

	@ParameterizedTest
	@CsvSource({"0, 1", "1, 1", "32, 32", "256, 256", "257, 256", "-2147483648, 1", "2147483647, 256"})
	void clampingARadiusAuditCapMovesItToTheNearestPermittedOne(int given, int expected) {
		assertEquals(expected, SculkSightConfig.clampRadiusAuditCap(given));
	}

	@Test
	void changingTheRadiusAuditCapLeavesTheOriginalAlone() {
		SculkSightConfig original = SculkSightConfig.defaults();

		SculkSightConfig changed = original.withRadiusAuditCap(64);

		assertEquals(32, original.radiusAuditCap());
		assertEquals(64, changed.radiusAuditCap());
	}

	@ParameterizedTest
	@CsvSource({"-1, 0", "0, 0", "25, 25", "100, 100", "101, 100", "-2147483648, 0",
			"2147483647, 100"})
	void clampingMovesAValueToTheNearestPermittedOne(int given, int expected) {
		assertEquals(expected, SculkSightConfig.clampShellOpacityPercent(given));
	}

	@ParameterizedTest
	@CsvSource({"-1.0, 0.0", "0.0, 0.0", "30.4, 30.4", "100.0, 100.0", "100.6, 100.0",
			"1.0E300, 100.0", "-1.0E300, 0.0", "Infinity, 100.0", "-Infinity, 0.0", "NaN, 0.0"})
	void clampingADoubleBoundsItBeforeAnyNarrowingCanWrap(double given, double expected) {
		assertEquals(expected, SculkSightConfig.clampShellOpacityPercent(given));
	}

	@ParameterizedTest
	@ValueSource(doubles = {1.0E300, Double.MAX_VALUE, Double.POSITIVE_INFINITY,
			Double.NEGATIVE_INFINITY, Double.NaN, 2147483648.0})
	void aClampedDoubleNarrowsToAPercentageTheRecordAccepts(double given) {
		int percent = (int) Math.round(SculkSightConfig.clampShellOpacityPercent(given));

		assertEquals(percent,
				new SculkSightConfig(percent, SculkSightConfig.DEFAULT_RENDER_POLICY).shellOpacityPercent(),
				"the record's own constructor is the check: it refuses anything out of range");
	}

	@Test
	void changingASettingLeavesTheOriginalAlone() {
		SculkSightConfig original = SculkSightConfig.defaults();

		SculkSightConfig changed = original.withShellOpacityPercent(60);

		assertEquals(25, original.shellOpacityPercent());
		assertEquals(60, changed.shellOpacityPercent());
	}

	/** {@link SculkSightConfig#withRenderPolicy} is the enum's own copy-with, mirroring opacity's. */
	@Test
	void changingTheRenderPolicyLeavesTheOriginalAlone() {
		SculkSightConfig original = SculkSightConfig.defaults();

		SculkSightConfig changed = original.withRenderPolicy(RenderPolicy.PER_SENSOR);

		assertEquals(RenderPolicy.UNION, original.renderPolicy());
		assertEquals(RenderPolicy.PER_SENSOR, changed.renderPolicy());
	}

	/** Each {@code with*} touches only its own component - the other one survives the copy. */
	@Test
	void withMethodsDoNotDisturbTheOtherComponent() {
		SculkSightConfig original = new SculkSightConfig(60, RenderPolicy.PER_SENSOR);

		assertEquals(RenderPolicy.PER_SENSOR, original.withShellOpacityPercent(80).renderPolicy());
		assertEquals(80, original.withShellOpacityPercent(80).shellOpacityPercent());

		assertEquals(60, original.withRenderPolicy(RenderPolicy.UNION).shellOpacityPercent());
		assertEquals(RenderPolicy.UNION, original.withRenderPolicy(RenderPolicy.UNION).renderPolicy());
	}
}
