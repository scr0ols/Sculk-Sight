package com.scr0ols.sculksight.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for the schema: what a configuration file says, and what a damaged one is repaired to. */
class ConfigCodecTest {

	private final List<String> repairs = new ArrayList<>();

	@Test
	void writesEveryKeyTheSchemaAlwaysHas() {
		assertEquals("{\n\t\"shellOpacityPercent\": 25,\n\t\"renderPolicy\": \"union\",\n"
						+ "\t\"radiusAuditCap\": 32\n}\n",
				ConfigCodec.write(SculkSightConfig.defaults()));
	}

	@Test
	void whatItWritesItReadsBackUnchanged() throws JsonParseException {
		SculkSightConfig original = new SculkSightConfig(63, RenderPolicy.PER_SENSOR,
				List.of(new TrackedSensor(1, 2, 3, "entrance", false, true),
						new TrackedSensor(-4, 5, 6, "deep hall", true, false)));

		assertEquals(original, ConfigCodec.read(ConfigCodec.write(original), repairs::add));
		assertEquals(List.of(), repairs);
	}

	@Test
	void aFileWrittenByALaterVersionStillLoads() throws JsonParseException {
		SculkSightConfig config = ConfigCodec.read(
				"{\"shellOpacityPercent\": 40, \"renderPolicy\": \"union\", \"radiusAuditCap\": 32, "
						+ "\"somethingFromV04\": [1, 2], \"note\": \"mine\"}",
				repairs::add);

		assertEquals(40, config.shellOpacityPercent());
		assertEquals(List.of(), repairs, "an unknown key is not a repair, it is simply not ours");
	}

	@Test
	void aMissingKeyBecomesTheDefaultAndSaysSo() throws JsonParseException {
		SculkSightConfig config = ConfigCodec.read("{}", repairs::add);

		assertEquals(SculkSightConfig.DEFAULT_SHELL_OPACITY_PERCENT, config.shellOpacityPercent());
		assertEquals(SculkSightConfig.DEFAULT_RENDER_POLICY, config.renderPolicy());
		assertEquals(SculkSightConfig.DEFAULT_RADIUS_AUDIT_CAP, config.radiusAuditCap());
		assertEquals(3, repairs.size());
		assertTrue(repairs.stream().anyMatch(r -> r.contains("shellOpacityPercent") && r.contains("missing")));
		assertTrue(repairs.stream().anyMatch(r -> r.contains("renderPolicy") && r.contains("missing")));
		assertTrue(repairs.stream().anyMatch(r -> r.contains("radiusAuditCap") && r.contains("missing")));
	}

	@Test
	void aLegacyFileWithoutTheRenderPolicyKeyDecodesToTheDefault() throws JsonParseException {
		SculkSightConfig config = ConfigCodec.read(
				"{\"shellOpacityPercent\": 40, \"radiusAuditCap\": 32}", repairs::add);

		assertEquals(RenderPolicy.UNION, config.renderPolicy());
		assertEquals(1, repairs.size());
		assertTrue(repairs.getFirst().contains("renderPolicy") && repairs.getFirst().contains("missing"),
				repairs.getFirst());
	}

	@Test
	void aTrackedSensorFromBeforeTheDelayOverlayFlagExistedDefaultsItToFalse() throws JsonParseException {
		SculkSightConfig config = ConfigCodec.read(
				"{\"shellOpacityPercent\": 25, \"renderPolicy\": \"union\", \"radiusAuditCap\": 32, "
						+ "\"trackedSensors\": [{\"x\": 1, \"y\": 2, \"z\": 3, \"name\": \"old\", "
						+ "\"enabled\": true}]}",
				repairs::add);

		assertFalse(config.trackedSensors().get(0).delayOverlayEnabled());
		assertEquals(List.of(), repairs,
				"a missing key from an older config version is not a repair - it is the schema growing");
	}

	@ParameterizedTest
	@ValueSource(strings = {"union", "per_sensor"})
	void bothRenderPolicyValuesRoundTrip(String value) throws JsonParseException {
		SculkSightConfig config = ConfigCodec.read(
				"{\"shellOpacityPercent\": 25, \"renderPolicy\": \"" + value
						+ "\", \"radiusAuditCap\": 32}", repairs::add);

		assertEquals(value.equals("union") ? RenderPolicy.UNION : RenderPolicy.PER_SENSOR,
				config.renderPolicy());
		assertEquals(List.of(), repairs);
	}

	@ParameterizedTest
	@ValueSource(strings = {"Union", "PER_SENSOR", "unoin", "", "per-sensor", "radius_audit"})
	void anUnrecognisedRenderPolicyStringFailsClosedToTheDefault(String value) throws JsonParseException {
		SculkSightConfig config = ConfigCodec.read(
				"{\"shellOpacityPercent\": 25, \"renderPolicy\": \"" + value
						+ "\", \"radiusAuditCap\": 32}", repairs::add);

		assertEquals(SculkSightConfig.DEFAULT_RENDER_POLICY, config.renderPolicy());
		assertEquals(1, repairs.size());
		assertTrue(repairs.getFirst().contains("renderPolicy"), repairs.getFirst());
	}

	@ParameterizedTest
	@ValueSource(strings = {"25", "true", "null", "[\"union\"]", "{\"value\": \"union\"}"})
	void aWrongTypedRenderPolicyFailsClosedToTheDefaultInsteadOfThrowing(String rawValue)
			throws JsonParseException {

		SculkSightConfig config = ConfigCodec.read(
				"{\"shellOpacityPercent\": 25, \"renderPolicy\": " + rawValue
						+ ", \"radiusAuditCap\": 32}", repairs::add);

		assertEquals(SculkSightConfig.DEFAULT_RENDER_POLICY, config.renderPolicy());
		assertEquals(1, repairs.size());
		assertTrue(repairs.getFirst().contains("renderPolicy"), repairs.getFirst());
	}

	@ParameterizedTest
	@ValueSource(strings = {"-30", "101", "1000"})
	void anOutOfRangeValueIsMovedIntoRangeAndSaysSo(String value) throws JsonParseException {
		SculkSightConfig config = ConfigCodec.read(
				"{\"shellOpacityPercent\": " + value
						+ ", \"renderPolicy\": \"union\", \"radiusAuditCap\": 32}", repairs::add);

		assertEquals(SculkSightConfig.clampShellOpacityPercent(Integer.parseInt(value)),
				config.shellOpacityPercent());
		assertEquals(1, repairs.size());
		assertTrue(repairs.getFirst().contains("moved to"), repairs.getFirst());
	}

	@Test
	void aFractionalValueIsRoundedAndSaysSo() throws JsonParseException {
		SculkSightConfig config = ConfigCodec.read(
				"{\"shellOpacityPercent\": 30.4, \"renderPolicy\": \"union\", \"radiusAuditCap\": 32}",
				repairs::add);

		assertEquals(30, config.shellOpacityPercent());
		assertEquals(1, repairs.size());
		assertTrue(repairs.getFirst().contains("rounded"), repairs.getFirst());
	}

	@ParameterizedTest
	@ValueSource(strings = {"2147483648", "1e300", "1e400"})
	void aValueTooLargeForAnIntIsStillTheMaximumAndNotTheMinimum(String value)
			throws JsonParseException {

		SculkSightConfig config = ConfigCodec.read(
				"{\"shellOpacityPercent\": " + value
						+ ", \"renderPolicy\": \"union\", \"radiusAuditCap\": 32}", repairs::add);

		assertEquals(SculkSightConfig.MAX_SHELL_OPACITY_PERCENT, config.shellOpacityPercent(),
				"an absurdly high opacity is the most opaque shell, not the least");
		assertEquals(1, repairs.size());
		assertTrue(repairs.getFirst().contains("moved to"), repairs.getFirst());
	}

	@ParameterizedTest
	@ValueSource(strings = {"-2147483649", "-1e300", "-1e400"})
	void aValueTooSmallForAnIntIsStillTheMinimum(String value) throws JsonParseException {
		SculkSightConfig config = ConfigCodec.read(
				"{\"shellOpacityPercent\": " + value
						+ ", \"renderPolicy\": \"union\", \"radiusAuditCap\": 32}", repairs::add);

		assertEquals(SculkSightConfig.MIN_SHELL_OPACITY_PERCENT, config.shellOpacityPercent());
		assertEquals(1, repairs.size());
		assertTrue(repairs.getFirst().contains("moved to"), repairs.getFirst());
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{\"shellOpacityPercent\": \"25\"}",
			"{\"shellOpacityPercent\": true}",
			"{\"shellOpacityPercent\": null}",
			"{\"shellOpacityPercent\": [25]}",
			"{\"shellOpacityPercent\": {\"value\": 25}}",
	})
	void aWrongTypedValueIsAnError(String text) {
		assertThrows(JsonParseException.class, () -> ConfigCodec.read(text, repairs::add));
	}

	@Test
	void aMissingRadiusAuditCapBecomesTheDefaultAndSaysSo() throws JsonParseException {
		SculkSightConfig config = ConfigCodec.read(
				"{\"shellOpacityPercent\": 25, \"renderPolicy\": \"union\"}", repairs::add);

		assertEquals(SculkSightConfig.DEFAULT_RADIUS_AUDIT_CAP, config.radiusAuditCap());
		assertEquals(1, repairs.size());
		assertTrue(repairs.getFirst().contains("radiusAuditCap") && repairs.getFirst().contains("missing"),
				repairs.getFirst());
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "257", "1000"})
	void anOutOfRangeRadiusAuditCapIsMovedIntoRangeAndSaysSo(String value) throws JsonParseException {
		SculkSightConfig config = ConfigCodec.read(
				"{\"shellOpacityPercent\": 25, \"renderPolicy\": \"union\", \"radiusAuditCap\": "
						+ value + "}", repairs::add);

		assertEquals(SculkSightConfig.clampRadiusAuditCap(Integer.parseInt(value)), config.radiusAuditCap());
		assertEquals(1, repairs.size());
		assertTrue(repairs.getFirst().contains("radiusAuditCap") && repairs.getFirst().contains("moved to"),
				repairs.getFirst());
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{\"shellOpacityPercent\": 25, \"renderPolicy\": \"union\", \"radiusAuditCap\": \"32\"}",
			"{\"shellOpacityPercent\": 25, \"renderPolicy\": \"union\", \"radiusAuditCap\": true}",
			"{\"shellOpacityPercent\": 25, \"renderPolicy\": \"union\", \"radiusAuditCap\": null}",
	})
	void aWrongTypedRadiusAuditCapIsAnError(String text) {
		assertThrows(JsonParseException.class, () -> ConfigCodec.read(text, repairs::add));
	}

	@ParameterizedTest
	@ValueSource(strings = {"[]", "\"a string\"", "25", "null", "not json at all"})
	void aDocumentThatIsNotAnObjectIsAnError(String text) {
		assertThrows(JsonParseException.class, () -> ConfigCodec.read(text, repairs::add));
	}
}
