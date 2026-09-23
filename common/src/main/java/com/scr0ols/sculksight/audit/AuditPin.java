package com.scr0ols.sculksight.audit;

import java.util.List;

import com.scr0ols.sculksight.audit.RadiusAudit.AuditedSensor;
import com.scr0ols.sculksight.config.SculkSightConfig;
import com.scr0ols.sculksight.config.TrackedSensor;

/** Turns a selection the audit made into tracked sensors. */
public final class AuditPin {

	private AuditPin() {
	}

	/** What one pin did: how many were added, already tracked, or rejected at the cap. */
	public record Result(SculkSightConfig config, int added, int alreadyTracked, int rejectedAtCap) {
	}

	/** Adds every position in {@code selected} to {@code config}'s tracked list that will fit. */
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

	/** One player-facing line describing a {@link Result}. */
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
