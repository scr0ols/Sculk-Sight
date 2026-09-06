package com.scr0ols.sculksight.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Turns a {@link SculkSightConfig} into the text of a configuration file and back.
 *
 * <p>The schema is one flat JSON object whose keys are named here and nowhere else. This is the
 * only place that knows what a stored key is called, so renaming one is a single edit and a
 * migration can be added beside the key it migrates.
 *
 * <p><b>Three kinds of bad input, handled three different ways, deliberately.</b> Text that is not
 * JSON at all, or that is JSON but not an object, is a {@link JsonParseException}: nothing can be
 * salvaged and the caller has to decide what to do. A key whose value is the wrong <i>type</i> - a
 * string where a number belongs - is also a {@link JsonParseException}, because guessing what a
 * player meant by {@code "twenty-five"} would be inventing a setting. A key that is <i>missing</i>,
 * or whose value is a number outside the permitted range, is repaired: the default or the nearest
 * permitted value is used, and the repair is reported to the caller's {@code repairs} consumer so
 * that it is logged rather than silently applied. An unknown key is left alone entirely, so a file
 * written by a later version, or carrying a player's own note, still loads.
 */
public final class ConfigCodec {

	static final String KEY_SHELL_OPACITY_PERCENT = "shellOpacityPercent";

	private ConfigCodec() {
	}

	/** The configuration as the text of a file, ready to be written verbatim. */
	public static String write(SculkSightConfig config) {
		Map<String, Object> object = new LinkedHashMap<>();

		object.put(KEY_SHELL_OPACITY_PERCENT, Integer.valueOf(config.shellOpacityPercent()));

		return Json.write(object);
	}

	/**
	 * The configuration a file's text describes.
	 *
	 * @param repairs told, one message at a time, about every value this method had to substitute
	 *        or move into range. Nothing is reported when the file is exactly what was written.
	 * @throws JsonParseException if the text is not a JSON object, or a known key carries a value
	 *         of the wrong type
	 */
	public static SculkSightConfig read(String text, Consumer<String> repairs)
			throws JsonParseException {
		Object document = Json.parse(text);

		if (!(document instanceof Map<?, ?> object)) {
			throw new JsonParseException("the configuration file must contain a JSON object");
		}

		int percent = readPercent(object, repairs);

		return new SculkSightConfig(percent);
	}

	private static int readPercent(Map<?, ?> object, Consumer<String> repairs)
			throws JsonParseException {
		int fallback = SculkSightConfig.DEFAULT_SHELL_OPACITY_PERCENT;

		// containsKey rather than a null check on get: a key written as JSON null is present, and a
		// player who wrote null did not write a number, which is the wrong-type case below rather
		// than the absent-key case here.
		if (!object.containsKey(KEY_SHELL_OPACITY_PERCENT)) {
			repairs.accept(KEY_SHELL_OPACITY_PERCENT + " is missing; using the default, " + fallback);
			return fallback;
		}

		Object raw = object.get(KEY_SHELL_OPACITY_PERCENT);

		if (!(raw instanceof Number number)) {
			throw new JsonParseException(
					KEY_SHELL_OPACITY_PERCENT + " must be a number, not " + describe(raw));
		}

		int rounded = (int) Math.round(number.doubleValue());

		if (rounded != number.doubleValue()) {
			repairs.accept(KEY_SHELL_OPACITY_PERCENT + " is a whole percentage; "
					+ number.doubleValue() + " was rounded to " + rounded);
		}

		int clamped = SculkSightConfig.clampShellOpacityPercent(rounded);

		if (clamped != rounded) {
			repairs.accept(KEY_SHELL_OPACITY_PERCENT + " must be "
					+ SculkSightConfig.MIN_SHELL_OPACITY_PERCENT + ".."
					+ SculkSightConfig.MAX_SHELL_OPACITY_PERCENT + "; " + rounded
					+ " was moved to " + clamped);
		}

		return clamped;
	}

	/** What a wrong-typed value is, in the words a player would recognise from their own file. */
	private static String describe(Object value) {
		return switch (value) {
			case String ignored -> "a string";
			case Boolean ignored -> "a boolean";
			case null -> "null";
			case Map<?, ?> ignored -> "an object";
			case Iterable<?> ignored -> "an array";
			default -> value.getClass().getSimpleName();
		};
	}
}
