package com.scr0ols.sculksight.config;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
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
 *
 * <p><b>{@link #KEY_RENDER_POLICY} is the one exception to "wrong type throws".</b> It has exactly
 * two legal values and nothing between or beyond them for a player to have meant, unlike a number
 * whose intended magnitude a typo could plausibly be guessing at. So a missing key, a value of the
 * wrong type, and a string that is neither {@link #VALUE_RENDER_POLICY_UNION} nor
 * {@link #VALUE_RENDER_POLICY_PER_SENSOR} - including one from a hypothetical later version this
 * schema does not yet know - are all repaired the same way, to {@link SculkSightConfig#DEFAULT_RENDER_POLICY},
 * rather than any of them throwing. A malformed or unrecognised policy is closer to "not set" than
 * to "a string where a number belongs", and this field carries no rendering effect yet for a fail
 * to be costly against.
 */
public final class ConfigCodec {

	static final String KEY_SHELL_OPACITY_PERCENT = "shellOpacityPercent";

	static final String KEY_RENDER_POLICY = "renderPolicy";

	static final String KEY_TRACKED_SENSORS = "trackedSensors";

	/** {@link RenderPolicy#UNION}, as the string this schema writes and reads. */
	static final String VALUE_RENDER_POLICY_UNION = "union";

	/** {@link RenderPolicy#PER_SENSOR}, as the string this schema writes and reads. */
	static final String VALUE_RENDER_POLICY_PER_SENSOR = "per_sensor";

	private ConfigCodec() {
	}

	/** The configuration as the text of a file, ready to be written verbatim. */
	public static String write(SculkSightConfig config) {
		Map<String, Object> object = new LinkedHashMap<>();

		object.put(KEY_SHELL_OPACITY_PERCENT, Integer.valueOf(config.shellOpacityPercent()));
		object.put(KEY_RENDER_POLICY, writeRenderPolicy(config.renderPolicy()));
		if (!config.trackedSensors().isEmpty()) {
			List<Map<String, Object>> sensors = new ArrayList<>();
			for (TrackedSensor sensor : config.trackedSensors()) {
				Map<String, Object> encoded = new LinkedHashMap<>();
				encoded.put("x", sensor.x());
				encoded.put("y", sensor.y());
				encoded.put("z", sensor.z());
				encoded.put("name", sensor.name());
				encoded.put("enabled", sensor.enabled());
				sensors.add(encoded);
			}
			object.put(KEY_TRACKED_SENSORS, sensors);
		}

		return Json.write(object);
	}

	private static String writeRenderPolicy(RenderPolicy policy) {
		return switch (policy) {
			case UNION -> VALUE_RENDER_POLICY_UNION;
			case PER_SENSOR -> VALUE_RENDER_POLICY_PER_SENSOR;
		};
	}

	/**
	 * The configuration a file's text describes.
	 *
	 * @param repairs told, one message at a time, about every value this method had to substitute
	 *        or move into range. Nothing is reported when the file is exactly what was written.
	 * @throws JsonParseException if the text is not a JSON object, or {@link #KEY_SHELL_OPACITY_PERCENT}
	 *         carries a value of the wrong type - {@link #KEY_RENDER_POLICY} never throws; see this
	 *         class's javadoc
	 */
	public static SculkSightConfig read(String text, Consumer<String> repairs)
			throws JsonParseException {
		Object document = Json.parse(text);

		if (!(document instanceof Map<?, ?> object)) {
			throw new JsonParseException("the configuration file must contain a JSON object");
		}

		int percent = readPercent(object, repairs);
		RenderPolicy policy = readRenderPolicy(object, repairs);
		List<TrackedSensor> sensors = readTrackedSensors(object, repairs);

		return new SculkSightConfig(percent, policy, sensors);
	}

	private static List<TrackedSensor> readTrackedSensors(Map<?, ?> object, Consumer<String> repairs) {
		if (!object.containsKey(KEY_TRACKED_SENSORS)) {
			return List.of();
		}
		Object raw = object.get(KEY_TRACKED_SENSORS);
		if (!(raw instanceof Iterable<?> values)) {
			repairs.accept(KEY_TRACKED_SENSORS + " must be an array, not " + describe(raw)
					+ "; using an empty list");
			return List.of();
		}

		List<TrackedSensor> sensors = new ArrayList<>();
		for (Object value : values) {
			if (!(value instanceof Map<?, ?> entry)) {
				repairs.accept(KEY_TRACKED_SENSORS + " contains a non-object entry; skipping it");
				continue;
			}
			try {
				int x = coordinate(entry, "x");
				int y = coordinate(entry, "y");
				int z = coordinate(entry, "z");
				Object rawName = entry.get("name");
				String name = rawName instanceof String string && !string.strip().isEmpty()
						? string : TrackedSensor.defaultName(x, y, z);
				boolean enabled = !(entry.containsKey("enabled")) || Boolean.TRUE.equals(entry.get("enabled"));
				sensors.add(new TrackedSensor(x, y, z, name, enabled));
			} catch (RuntimeException malformed) {
				repairs.accept(KEY_TRACKED_SENSORS + " contains an invalid entry; skipping it");
			}
		}
		if (sensors.size() > SculkSightConfig.MAX_TRACKED_SENSORS) {
			repairs.accept(KEY_TRACKED_SENSORS + " exceeds the limit of "
					+ SculkSightConfig.MAX_TRACKED_SENSORS + "; extra entries were skipped");
		}
		return sensors;
	}

	private static int coordinate(Map<?, ?> entry, String key) {
		Object value = entry.get(key);
		if (!(value instanceof Number number)) {
			throw new IllegalArgumentException(key + " is not a number");
		}
		double numeric = number.doubleValue();
		if (!Double.isFinite(numeric) || numeric != Math.rint(numeric)
				|| numeric < Integer.MIN_VALUE || numeric > Integer.MAX_VALUE) {
			throw new IllegalArgumentException(key + " is not an integer coordinate");
		}
		return (int) numeric;
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

		double value = number.doubleValue();

		// Bounded as a double, and only then narrowed, which is the order the whole of this fix
		// consists of. Json's number scanner accepts by character shape, so a literal too large for
		// a double is a well-formed document to it and Double.valueOf answers positive infinity
		// rather than throwing; rounding that gives Long.MAX_VALUE and the cast to int wraps it to
		// -1, which a clamp applied afterward would move to zero. The player asked for the densest
		// shell there is and would have got no shell at all. OPEN-QUESTIONS.md section 22.2.
		double bounded = SculkSightConfig.clampShellOpacityPercent(value);

		int percent = (int) Math.round(bounded);

		if (bounded != value) {
			repairs.accept(KEY_SHELL_OPACITY_PERCENT + " must be "
					+ SculkSightConfig.MIN_SHELL_OPACITY_PERCENT + ".."
					+ SculkSightConfig.MAX_SHELL_OPACITY_PERCENT + "; " + value
					+ " was moved to " + percent);
		} else if (percent != value) {
			repairs.accept(KEY_SHELL_OPACITY_PERCENT + " is a whole percentage; "
					+ value + " was rounded to " + percent);
		}

		return percent;
	}

	/**
	 * {@link #KEY_RENDER_POLICY}'s repair rule: missing, wrong-typed, and unrecognised all fail
	 * closed to {@link SculkSightConfig#DEFAULT_RENDER_POLICY} rather than throwing - see this
	 * class's javadoc for why this key alone works this way.
	 */
	private static RenderPolicy readRenderPolicy(Map<?, ?> object, Consumer<String> repairs) {
		RenderPolicy fallback = SculkSightConfig.DEFAULT_RENDER_POLICY;

		if (!object.containsKey(KEY_RENDER_POLICY)) {
			repairs.accept(KEY_RENDER_POLICY + " is missing; using the default, "
					+ writeRenderPolicy(fallback));
			return fallback;
		}

		Object raw = object.get(KEY_RENDER_POLICY);

		if (!(raw instanceof String string)) {
			repairs.accept(KEY_RENDER_POLICY + " must be a string, not " + describe(raw)
					+ "; using the default, " + writeRenderPolicy(fallback));
			return fallback;
		}

		RenderPolicy parsed = parseRenderPolicy(string);

		if (parsed == null) {
			repairs.accept(KEY_RENDER_POLICY + " must be \"" + VALUE_RENDER_POLICY_UNION + "\" or \""
					+ VALUE_RENDER_POLICY_PER_SENSOR + "\", not \"" + string + "\"; using the default, "
					+ writeRenderPolicy(fallback));
			return fallback;
		}

		return parsed;
	}

	/** The policy a stored string names, or {@code null} if it names none of them. */
	private static RenderPolicy parseRenderPolicy(String value) {
		return switch (value) {
			case VALUE_RENDER_POLICY_UNION -> RenderPolicy.UNION;
			case VALUE_RENDER_POLICY_PER_SENSOR -> RenderPolicy.PER_SENSOR;
			default -> null;
		};
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
