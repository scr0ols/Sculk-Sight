package com.scr0ols.sculksight.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
	void writesTheOneKeyTheSchemaHas() {
		assertEquals("{\n\t\"shellOpacityPercent\": 25\n}\n",
				ConfigCodec.write(SculkSightConfig.defaults()));
	}

	@Test
	void whatItWritesItReadsBackUnchanged() throws JsonParseException {
		SculkSightConfig original = new SculkSightConfig(63);

		assertEquals(original, ConfigCodec.read(ConfigCodec.write(original), repairs::add));
		assertEquals(List.of(), repairs);
	}

	@Test
	void aFileWrittenByALaterVersionStillLoads() throws JsonParseException {
		SculkSightConfig config = ConfigCodec.read(
				"{\"shellOpacityPercent\": 40, \"somethingFromV02\": [1, 2], \"note\": \"mine\"}",
				repairs::add);

		assertEquals(40, config.shellOpacityPercent());
		assertEquals(List.of(), repairs, "an unknown key is not a repair, it is simply not ours");
	}

	@Test
	void aMissingKeyBecomesTheDefaultAndSaysSo() throws JsonParseException {
		SculkSightConfig config = ConfigCodec.read("{}", repairs::add);

		assertEquals(SculkSightConfig.DEFAULT_SHELL_OPACITY_PERCENT, config.shellOpacityPercent());
		assertEquals(1, repairs.size());
		assertTrue(repairs.getFirst().contains("missing"), repairs.getFirst());
	}

	@ParameterizedTest
	@ValueSource(strings = {"-30", "101", "1000"})
	void anOutOfRangeValueIsMovedIntoRangeAndSaysSo(String value) throws JsonParseException {
		SculkSightConfig config =
				ConfigCodec.read("{\"shellOpacityPercent\": " + value + "}", repairs::add);

		assertEquals(SculkSightConfig.clampShellOpacityPercent(Integer.parseInt(value)),
				config.shellOpacityPercent());
		assertEquals(1, repairs.size());
		assertTrue(repairs.getFirst().contains("moved to"), repairs.getFirst());
	}

	@Test
	void aFractionalValueIsRoundedAndSaysSo() throws JsonParseException {
		SculkSightConfig config = ConfigCodec.read("{\"shellOpacityPercent\": 30.4}", repairs::add);

		assertEquals(30, config.shellOpacityPercent());
		assertEquals(1, repairs.size());
		assertTrue(repairs.getFirst().contains("rounded"), repairs.getFirst());
	}

	/**
	 * OPEN-QUESTIONS.md section 22.2. Each of these is a number a player could type into the file
	 * by hand and each used to come back as 0 - a fully transparent shell for someone who asked for
	 * the densest one there is. {@code Json} accepts them by character shape and {@code Double}
	 * maps the last two to positive infinity, so the round gave {@code Long.MAX_VALUE}, the cast to
	 * {@code int} wrapped it to -1, and the clamp that ran afterward moved that to the minimum.
	 */
	@ParameterizedTest
	@ValueSource(strings = {"2147483648", "1e300", "1e400"})
	void aValueTooLargeForAnIntIsStillTheMaximumAndNotTheMinimum(String value)
			throws JsonParseException {

		SculkSightConfig config =
				ConfigCodec.read("{\"shellOpacityPercent\": " + value + "}", repairs::add);

		assertEquals(SculkSightConfig.MAX_SHELL_OPACITY_PERCENT, config.shellOpacityPercent(),
				"an absurdly high opacity is the most opaque shell, not the least");
		assertEquals(1, repairs.size());
		assertTrue(repairs.getFirst().contains("moved to"), repairs.getFirst());
	}

	/** The same defect at the other end: a hugely negative value belongs at the minimum. */
	@ParameterizedTest
	@ValueSource(strings = {"-2147483649", "-1e300", "-1e400"})
	void aValueTooSmallForAnIntIsStillTheMinimum(String value) throws JsonParseException {
		SculkSightConfig config =
				ConfigCodec.read("{\"shellOpacityPercent\": " + value + "}", repairs::add);

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

	@ParameterizedTest
	@ValueSource(strings = {"[]", "\"a string\"", "25", "null", "not json at all"})
	void aDocumentThatIsNotAnObjectIsAnError(String text) {
		assertThrows(JsonParseException.class, () -> ConfigCodec.read(text, repairs::add));
	}
}
