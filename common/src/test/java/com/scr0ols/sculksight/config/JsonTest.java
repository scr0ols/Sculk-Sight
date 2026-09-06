package com.scr0ols.sculksight.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for the hand-written JSON layer PLAN.md section 4's persistence layer is built on. */
class JsonTest {

	/** One backslash, named rather than escaped, so these tests stay readable. */
	private static final String SLASH = "\\";

	@Test
	void readsAFlatObjectOfNumbers() throws JsonParseException {
		Map<?, ?> object = assertInstanceOf(Map.class, Json.parse("{\"a\": 1, \"b\": -2.5}"));

		assertEquals(Double.valueOf(1.0), object.get("a"));
		assertEquals(Double.valueOf(-2.5), object.get("b"));
	}

	@Test
	void readsEveryValueType() throws JsonParseException {
		Map<?, ?> object = assertInstanceOf(Map.class, Json.parse(
				"{\"s\": \"x\", \"t\": true, \"f\": false, \"n\": null, \"a\": [1, 2], \"o\": {\"k\": 3}}"));

		assertEquals("x", object.get("s"));
		assertEquals(Boolean.TRUE, object.get("t"));
		assertEquals(Boolean.FALSE, object.get("f"));
		assertNull(object.get("n"));
		assertEquals(List.of(Double.valueOf(1.0), Double.valueOf(2.0)), object.get("a"));
		assertEquals(Map.of("k", Double.valueOf(3.0)), object.get("o"));
	}

	@Test
	void readsAnEmptyObjectAndAnEmptyArray() throws JsonParseException {
		assertEquals(Map.of(), Json.parse("{}"));
		assertEquals(List.of(), Json.parse("[]"));
	}

	@Test
	void ignoresWhitespaceAroundEverything() throws JsonParseException {
		assertEquals(Map.of("a", Double.valueOf(1.0)), Json.parse("\n\t {  \"a\"  :  1  }  \n"));
	}

	@Test
	void readsEveryEscapeSequenceJsonDefines() throws JsonParseException {
		String document = "{\"k\": \"" + SLASH + "\"" + SLASH + SLASH + SLASH + "/" + SLASH + "b"
				+ SLASH + "f" + SLASH + "n" + SLASH + "r" + SLASH + "t" + SLASH + "u0041\"}";

		Map<?, ?> object = assertInstanceOf(Map.class, Json.parse(document));

		assertEquals("\"" + SLASH + "/\b\f\n\r\tA", object.get("k"));
	}

	@Test
	void keepsTheKeyOrderTheDocumentUsed() throws JsonParseException {
		Map<?, ?> object = assertInstanceOf(Map.class, Json.parse("{\"z\": 1, \"a\": 2, \"m\": 3}"));

		assertEquals(List.of("z", "a", "m"), List.copyOf(object.keySet()));
	}

	/**
	 * The extensions a permissive parser would accept. Rejecting them is the decision: a file this
	 * mod cannot read is reported to the player, where one it silently half-reads is not.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
			"{\"a\": 1,}",
			"{a: 1}",
			"{'a': 1}",
			"{\"a\": 1} // trailing",
			"{\"a\": 1",
			"{\"a\" 1}",
			"{\"a\": }",
			"",
			"   ",
			"{\"a\": tru}",
			"[1, 2",
			"{\"a\": \"unterminated}",
	})
	void rejectsWhatIsNotJson(String text) {
		assertThrows(JsonParseException.class, () -> Json.parse(text));
	}

	@Test
	void rejectsAnUnknownEscapeSequence() {
		String document = "{\"a\": \"" + SLASH + "q\"}";

		assertThrows(JsonParseException.class, () -> Json.parse(document));
	}

	@Test
	void rejectsAUnicodeEscapeThatIsNotFourHexDigits() {
		String document = "{\"a\": \"" + SLASH + "u00zz\"}";

		assertThrows(JsonParseException.class, () -> Json.parse(document));
	}

	@Test
	void rejectsARawControlCharacterInsideAString() {
		assertThrows(JsonParseException.class, () -> Json.parse("{\"a\": \"two\nlines\"}"));
	}

	@Test
	void writesAWholeNumberWithoutADecimalPoint() {
		Map<String, Object> object = new LinkedHashMap<>();
		object.put("a", Integer.valueOf(25));

		assertEquals("{\n\t\"a\": 25\n}\n", Json.write(object));
	}

	@Test
	void writesAFractionalNumberAsOne() {
		Map<String, Object> object = new LinkedHashMap<>();
		object.put("a", Double.valueOf(2.5));

		assertEquals("{\n\t\"a\": 2.5\n}\n", Json.write(object));
	}

	@Test
	void writesAnEmptyObjectOnOneLine() {
		assertEquals("{}\n", Json.write(new LinkedHashMap<>()));
	}

	@Test
	void escapesWhatItWrites() {
		Map<String, Object> object = new LinkedHashMap<>();
		object.put("a\"b", "c\nd");

		String expected = "{\n\t\"a" + SLASH + "\"b\": \"c" + SLASH + "nd" + SLASH + "u0001\"\n}\n";

		assertEquals(expected, Json.write(object));
	}

	@Test
	void whatItWritesItCanReadBack() throws JsonParseException {
		String awkward = "a \"quoted\" " + SLASH + " value";

		Map<String, Object> object = new LinkedHashMap<>();
		object.put("number", Integer.valueOf(7));
		object.put("text", awkward);
		object.put("flag", Boolean.TRUE);
		object.put("nothing", null);
		object.put("list", List.of(Double.valueOf(1.0), "two"));
		object.put("nested", Map.of("inner", Double.valueOf(3.0)));

		Map<?, ?> read = assertInstanceOf(Map.class, Json.parse(Json.write(object)));

		assertEquals(Double.valueOf(7.0), read.get("number"));
		assertEquals(awkward, read.get("text"));
		assertEquals(Boolean.TRUE, read.get("flag"));
		assertNull(read.get("nothing"));
		assertEquals(List.of(Double.valueOf(1.0), "two"), read.get("list"));
		assertEquals(Map.of("inner", Double.valueOf(3.0)), read.get("nested"));
	}

	@Test
	void refusesToWriteAValueJsonCannotRepresent() {
		Map<String, Object> object = new LinkedHashMap<>();
		object.put("a", Double.valueOf(Double.NaN));

		assertThrows(IllegalArgumentException.class, () -> Json.write(object));
	}
}
