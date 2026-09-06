package com.scr0ols.sculksight.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal JSON reader and writer, owned by this project rather than borrowed from a library.
 *
 * <p><b>Why this exists at all, since writing a parser is normally the wrong answer.</b>
 * PLAN.md section 4 puts an "own JSON persistence layer in common" beneath Cloth Config, and this
 * module's classpath is what makes that literal rather than stylistic. Gson is on the compile
 * classpath here, because Minecraft itself ships it, but it is <b>not</b> on this module's test
 * runtime classpath - verified 2026-09-06 by compiling and then running a probe against it, not
 * assumed. Reaching for Gson would therefore have put the persistence layer out of reach of the
 * JUnit suite that is this project's only automated check, or added a third-party test dependency
 * with a version to pin, for a document whose whole schema is a flat object of numbers.
 *
 * <p><b>Scope: all of JSON's value types, none of its extensions.</b> Objects, arrays, strings,
 * numbers, {@code true}, {@code false} and {@code null} are read; comments, trailing commas,
 * unquoted keys and single-quoted strings are not, and are reported as errors rather than
 * tolerated. The parser is general rather than schema-specific so that a document carrying keys
 * this version does not know - a newer version's, or a player's own note - is read rather than
 * rejected. {@link ConfigCodec} is what turns the resulting values into a configuration.
 *
 * <p>Parsed values are {@link Map} (insertion-ordered), {@link List}, {@link String},
 * {@link Double}, {@link Boolean}, or {@code null}. Every JSON number becomes a {@code Double},
 * including an integral one: JSON itself does not distinguish the two, so pretending to would be
 * inventing information the document does not carry. {@link ConfigCodec} narrows where its own
 * schema says a value is a whole number.
 */
public final class Json {

	private static final String INDENT = "\t";

	private Json() {
	}

	/**
	 * Reads one complete JSON document.
	 *
	 * @return a {@link Map}, {@link List}, {@link String}, {@link Double}, {@link Boolean}, or
	 *         {@code null}
	 * @throws JsonParseException if the text is not one well-formed JSON value, or carries
	 *         anything but whitespace after it
	 */
	public static Object parse(String text) throws JsonParseException {
		Parser parser = new Parser(text);

		Object value = parser.readValue();

		parser.skipWhitespace();

		if (!parser.atEnd()) {
			throw parser.error("trailing content after the end of the document");
		}

		return value;
	}

	/** Writes one object as an indented document, with a trailing newline. */
	public static String write(Map<String, Object> object) {
		StringBuilder out = new StringBuilder();

		writeValue(out, object, 0);
		out.append('\n');

		return out.toString();
	}

	private static void writeValue(StringBuilder out, Object value, int depth) {
		switch (value) {
			case null -> out.append("null");
			case Map<?, ?> map -> writeObject(out, map, depth);
			case List<?> list -> writeArray(out, list, depth);
			case String string -> writeString(out, string);
			case Boolean bool -> out.append(bool.booleanValue() ? "true" : "false");
			case Number number -> out.append(writeNumber(number));
			default -> throw new IllegalArgumentException(
					"not a JSON value: " + value.getClass().getName());
		}
	}

	private static void writeObject(StringBuilder out, Map<?, ?> map, int depth) {
		if (map.isEmpty()) {
			out.append("{}");
			return;
		}

		out.append("{\n");

		boolean first = true;

		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (!first) {
				out.append(",\n");
			}

			first = false;

			out.append(INDENT.repeat(depth + 1));
			writeString(out, String.valueOf(entry.getKey()));
			out.append(": ");
			writeValue(out, entry.getValue(), depth + 1);
		}

		out.append('\n').append(INDENT.repeat(depth)).append('}');
	}

	private static void writeArray(StringBuilder out, List<?> list, int depth) {
		if (list.isEmpty()) {
			out.append("[]");
			return;
		}

		out.append("[\n");

		for (int i = 0; i < list.size(); i++) {
			if (i > 0) {
				out.append(",\n");
			}

			out.append(INDENT.repeat(depth + 1));
			writeValue(out, list.get(i), depth + 1);
		}

		out.append('\n').append(INDENT.repeat(depth)).append(']');
	}

	/**
	 * A whole number is written without a decimal point, so that a hand-editing player sees
	 * {@code 25} rather than {@code 25.0} in a field whose schema says it is an integer.
	 */
	private static String writeNumber(Number number) {
		double value = number.doubleValue();

		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException("JSON has no representation for " + value);
		}

		if (value == Math.rint(value) && Math.abs(value) < 1.0E15) {
			return Long.toString((long) value);
		}

		return Double.toString(value);
	}

	private static void writeString(StringBuilder out, String value) {
		out.append('"');

		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);

			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				case '\b' -> out.append("\\b");
				case '\f' -> out.append("\\f");
				default -> appendPlain(out, c);
			}
		}

		out.append('"');
	}

	private static void appendPlain(StringBuilder out, char c) {
		if (c < 0x20) {
			out.append(String.format("\\u%04x", (int) c));
			return;
		}

		out.append(c);
	}

	/** One pass over one document. Not reusable, and not thread-safe; each parse makes its own. */
	private static final class Parser {

		private final String text;

		private int at;

		private Parser(String text) {
			this.text = text;
		}

		private Object readValue() throws JsonParseException {
			skipWhitespace();

			if (atEnd()) {
				throw error("a value was expected");
			}

			return switch (text.charAt(at)) {
				case '{' -> readObject();
				case '[' -> readArray();
				case '"' -> readString();
				case 't' -> readKeyword("true", Boolean.TRUE);
				case 'f' -> readKeyword("false", Boolean.FALSE);
				case 'n' -> readKeyword("null", null);
				default -> readNumber();
			};
		}

		private Map<String, Object> readObject() throws JsonParseException {
			expect('{');
			skipWhitespace();

			Map<String, Object> object = new LinkedHashMap<>();

			if (peekIs('}')) {
				at++;
				return object;
			}

			while (true) {
				skipWhitespace();

				String key = readString();

				expect(':');

				object.put(key, readValue());

				skipWhitespace();

				if (peekIs(',')) {
					at++;
					continue;
				}

				expect('}');
				return object;
			}
		}

		private List<Object> readArray() throws JsonParseException {
			expect('[');
			skipWhitespace();

			List<Object> array = new ArrayList<>();

			if (peekIs(']')) {
				at++;
				return array;
			}

			while (true) {
				array.add(readValue());

				skipWhitespace();

				if (peekIs(',')) {
					at++;
					continue;
				}

				expect(']');
				return array;
			}
		}

		private String readString() throws JsonParseException {
			expect('"');

			StringBuilder value = new StringBuilder();

			while (true) {
				if (atEnd()) {
					throw error("the document ended inside a string");
				}

				char c = text.charAt(at++);

				if (c == '"') {
					return value.toString();
				}

				if (c == '\\') {
					value.append(readEscape());
					continue;
				}

				if (c < 0x20) {
					throw error("a raw control character is not allowed inside a string");
				}

				value.append(c);
			}
		}

		private char readEscape() throws JsonParseException {
			if (atEnd()) {
				throw error("the document ended inside an escape sequence");
			}

			char c = text.charAt(at++);

			return switch (c) {
				case '"', '\\', '/' -> c;
				case 'b' -> '\b';
				case 'f' -> '\f';
				case 'n' -> '\n';
				case 'r' -> '\r';
				case 't' -> '\t';
				case 'u' -> readUnicodeEscape();
				default -> throw error("unknown escape sequence \\" + c);
			};
		}

		private char readUnicodeEscape() throws JsonParseException {
			if (at + 4 > text.length()) {
				throw error("a \\u escape needs four hexadecimal digits");
			}

			String digits = text.substring(at, at + 4);

			try {
				char value = (char) Integer.parseInt(digits, 16);
				at += 4;
				return value;
			} catch (NumberFormatException e) {
				throw error("\\u" + digits + " is not four hexadecimal digits");
			}
		}

		private Double readNumber() throws JsonParseException {
			int start = at;

			if (peekIs('-')) {
				at++;
			}

			skipDigits();

			if (peekIs('.')) {
				at++;
				skipDigits();
			}

			if (peekIs('e') || peekIs('E')) {
				at++;

				if (peekIs('+') || peekIs('-')) {
					at++;
				}

				skipDigits();
			}

			String span = text.substring(start, at);

			try {
				return Double.valueOf(span);
			} catch (NumberFormatException e) {
				throw error("'" + span + "' is not a number");
			}
		}

		private Object readKeyword(String keyword, Object value) throws JsonParseException {
			if (!text.startsWith(keyword, at)) {
				throw error("'" + keyword + "' was expected");
			}

			at += keyword.length();
			return value;
		}

		private void skipDigits() {
			while (!atEnd() && text.charAt(at) >= '0' && text.charAt(at) <= '9') {
				at++;
			}
		}

		private void skipWhitespace() {
			while (!atEnd() && isWhitespace(text.charAt(at))) {
				at++;
			}
		}

		private static boolean isWhitespace(char c) {
			return c == ' ' || c == '\t' || c == '\n' || c == '\r';
		}

		private void expect(char expected) throws JsonParseException {
			skipWhitespace();

			if (atEnd() || text.charAt(at) != expected) {
				throw error("'" + expected + "' was expected");
			}

			at++;
		}

		private boolean peekIs(char c) {
			return !atEnd() && text.charAt(at) == c;
		}

		private boolean atEnd() {
			return at >= text.length();
		}

		private JsonParseException error(String what) {
			return new JsonParseException(what + ", at offset " + at);
		}
	}
}
