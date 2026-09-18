package com.scr0ols.sculksight.audit;

import java.util.List;

import com.scr0ols.sculksight.audit.RadiusAudit.AuditedSensor;
import com.scr0ols.sculksight.config.SculkSightConfig;
import com.scr0ols.sculksight.config.TrackedSensor;

/**
 * Turns a selection the audit made into tracked sensors - the one operation behind both ways a
 * player can ask for that: {@code /sculksight find <type> <radius> static}, which pins as it runs,
 * and the settings screen's own Pin button, which pins a {@link RadiusAuditMode#LIVE} selection
 * after the player has had a look at it.
 *
 * <p><b>Pure, so both callers get identical behaviour and identical wording.</b> It takes a config
 * and a selection and returns a new config plus what happened; it reads nothing, writes nothing,
 * and names no Minecraft type beyond the {@link com.scr0ols.sculksight.client.SensorKey} its input
 * already carries. Each caller does its own {@code ClientConfig} read and write around it - the
 * command through {@code RadiusAuditClient}, the button through {@code ConfigScreens} - which is
 * what keeps this class inside the reach of the JUnit suite while the config I/O stays where it
 * already lived.
 *
 * <p><b>Pinning is {@link SculkSightConfig#track} repeated, deliberately.</b> A pinned sensor is
 * not a new kind of entry: it is exactly what pressing K on that block produces, down to
 * {@link TrackedSensor#defaultName} and the enabled flag, so every control the settings screen
 * already offers a tracked sensor works on it with no special case. That is also why a position
 * already tracked is left completely alone rather than reset - {@code track} preserves an existing
 * name and toggle state on repeat selection, so pinning a find twice does not rename the sensor a
 * player renamed in between, or re-enable one they disabled.
 */
public final class AuditPin {

	private AuditPin() {
	}

	/**
	 * What one pin did, in the three outcomes a player can tell apart.
	 *
	 * @param config the new configuration, or the original instance when nothing changed
	 * @param added positions that became new tracked sensors
	 * @param alreadyTracked positions the tracked list already held, left untouched with their
	 *     existing name and enabled state
	 * @param rejectedAtCap positions that did not fit under
	 *     {@link SculkSightConfig#MAX_TRACKED_SENSORS}; nearest-first order means these are the
	 *     furthest away, which is what makes the truncation the least-bad one available
	 */
	public record Result(SculkSightConfig config, int added, int alreadyTracked, int rejectedAtCap) {
	}

	/**
	 * Adds every position in {@code selected} to {@code config}'s tracked list that will fit.
	 *
	 * <p>{@code selected} is consumed in the order given, which is {@link RadiusAudit#select}'s
	 * nearest-first order: if the cap truncates, it keeps the sensors closest to where the find was
	 * run, for the same reason the audit's own cap truncates that way.
	 */
	public static Result pin(SculkSightConfig config, List<AuditedSensor> selected) {
		SculkSightConfig updated = config;
		int added = 0;
		int alreadyTracked = 0;
		int rejectedAtCap = 0;

		for (AuditedSensor sensor : selected) {
			if (isTracked(updated, sensor)) {
				alreadyTracked++;
				continue;
			}
			if (updated.trackedSensors().size() >= SculkSightConfig.MAX_TRACKED_SENSORS) {
				rejectedAtCap++;
				continue;
			}
			updated = updated.track(TrackedSensor.selected(
					sensor.position().x(), sensor.position().y(), sensor.position().z()));
			added++;
		}

		return new Result(updated, added, alreadyTracked, rejectedAtCap);
	}

	/**
	 * One player-facing line about a {@link Result}, so the command and the Pin button say the same
	 * thing rather than each inventing its own phrasing.
	 *
	 * <p>Only the outcomes that actually happened are mentioned: the common case - a find in fresh
	 * territory - reads as one clause, and the counts that are zero stay out of it rather than
	 * padding it with "0 already tracked, 0 skipped".
	 */
	public static String describe(Result result) {
		if (result.added() == 0 && result.alreadyTracked() == 0 && result.rejectedAtCap() == 0) {
			return "Nothing to pin.";
		}

		StringBuilder line = new StringBuilder();
		line.append(result.added() == 1 ? "1 sensor added to tracked sensors"
				: result.added() + " sensors added to tracked sensors");

		if (result.alreadyTracked() > 0) {
			line.append("; ").append(result.alreadyTracked()).append(" already tracked");
		}
		if (result.rejectedAtCap() > 0) {
			line.append("; ").append(result.rejectedAtCap())
					.append(" did not fit the tracked limit of ")
					.append(SculkSightConfig.MAX_TRACKED_SENSORS);
		}

		return line.append('.').toString();
	}

	private static boolean isTracked(SculkSightConfig config, AuditedSensor sensor) {
		for (TrackedSensor tracked : config.trackedSensors()) {
			if (tracked.x() == sensor.position().x() && tracked.y() == sensor.position().y()
					&& tracked.z() == sensor.position().z()) {
				return true;
			}
		}
		return false;
	}
}
