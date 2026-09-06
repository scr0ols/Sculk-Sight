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
