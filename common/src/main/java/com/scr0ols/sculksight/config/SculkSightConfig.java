package com.scr0ols.sculksight.config;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

/** Every player-settable value this mod has, as one immutable record. */
public record SculkSightConfig(int shellOpacityPercent, RenderPolicy renderPolicy,
		List<TrackedSensor> trackedSensors, int radiusAuditCap) {

	/** The default depth-tested shell alpha, as the percentage this record stores. */
	public static final int DEFAULT_SHELL_OPACITY_PERCENT = 25;

	/** The default render policy. */
	public static final RenderPolicy DEFAULT_RENDER_POLICY = RenderPolicy.UNION;

	/** Safety bound for selection, solving, and the union mesh. */
	public static final int MAX_TRACKED_SENSORS = 32;

	/** The default cap on how many sensors a radius audit may select. */
	public static final int DEFAULT_RADIUS_AUDIT_CAP = 32;

	/** The audit must select at least one sensor to be worth running. */
	public static final int MIN_RADIUS_AUDIT_CAP = 1;

	/** An arbitrary but generous ceiling on the radius audit cap. */
	public static final int MAX_RADIUS_AUDIT_CAP = 256;

	/** Fully transparent; a player may turn the fill off and keep the mod loaded. */
	public static final int MIN_SHELL_OPACITY_PERCENT = 0;

	/** Fully opaque, the ceiling alpha itself has. */
	public static final int MAX_SHELL_OPACITY_PERCENT = 100;

	/** The proportion the see-through alpha keeps to the depth-tested one. */
	public static final float SEE_THROUGH_RATIO = 0.40F;

	/** The authored configuration, with nothing overridden. */
	public static SculkSightConfig defaults() {
		return new SculkSightConfig(DEFAULT_SHELL_OPACITY_PERCENT, DEFAULT_RENDER_POLICY, List.of(),
				DEFAULT_RADIUS_AUDIT_CAP);
	}

	/** Compatibility constructor for callers that only set the appearance. */
	public SculkSightConfig(int shellOpacityPercent, RenderPolicy renderPolicy) {
		this(shellOpacityPercent, renderPolicy, List.of());
	}

	/** Compatibility constructor for callers that predate the radius audit cap. */
	public SculkSightConfig(int shellOpacityPercent, RenderPolicy renderPolicy,
			List<TrackedSensor> trackedSensors) {
		this(shellOpacityPercent, renderPolicy, trackedSensors, DEFAULT_RADIUS_AUDIT_CAP);
	}

	public SculkSightConfig {
		if (shellOpacityPercent < MIN_SHELL_OPACITY_PERCENT
				|| shellOpacityPercent > MAX_SHELL_OPACITY_PERCENT) {
			throw new IllegalArgumentException("shellOpacityPercent must be "
					+ MIN_SHELL_OPACITY_PERCENT + ".." + MAX_SHELL_OPACITY_PERCENT
					+ ", got " + shellOpacityPercent);
		}

		if (radiusAuditCap < MIN_RADIUS_AUDIT_CAP || radiusAuditCap > MAX_RADIUS_AUDIT_CAP) {
			throw new IllegalArgumentException("radiusAuditCap must be "
					+ MIN_RADIUS_AUDIT_CAP + ".." + MAX_RADIUS_AUDIT_CAP
					+ ", got " + radiusAuditCap);
		}

		Objects.requireNonNull(renderPolicy, "renderPolicy");
		Objects.requireNonNull(trackedSensors, "trackedSensors");
		List<TrackedSensor> normalised = new ArrayList<>();
		for (TrackedSensor sensor : trackedSensors) {
			if (sensor == null) {
				throw new NullPointerException("trackedSensors contains null");
			}
			boolean duplicate = normalised.stream().anyMatch(existing -> samePosition(existing, sensor));
			if (!duplicate && normalised.size() < MAX_TRACKED_SENSORS) {
				normalised.add(sensor);
			}
		}
		trackedSensors = List.copyOf(normalised);
	}

	private static boolean samePosition(TrackedSensor first, TrackedSensor second) {
		return first.x() == second.x() && first.y() == second.y() && first.z() == second.z();
	}

	/** The nearest permitted percentage to the given one. Used when repairing a read value. */
	public static int clampShellOpacityPercent(int percent) {
		return Math.max(MIN_SHELL_OPACITY_PERCENT, Math.min(MAX_SHELL_OPACITY_PERCENT, percent));
	}

	/** The nearest permitted cap to the given one. Used when repairing a read value. */
	public static int clampRadiusAuditCap(int cap) {
		return Math.max(MIN_RADIUS_AUDIT_CAP, Math.min(MAX_RADIUS_AUDIT_CAP, cap));
	}

	/** The nearest permitted percentage to the given one, before it is narrowed to an int. */
	public static double clampShellOpacityPercent(double percent) {
		if (Double.isNaN(percent)) {
			return MIN_SHELL_OPACITY_PERCENT;
		}

		return Math.max(MIN_SHELL_OPACITY_PERCENT, Math.min(MAX_SHELL_OPACITY_PERCENT, percent));
	}

	/** The depth-tested pass alpha, 0..1. */
	public float depthTestedAlpha() {
		return shellOpacityPercent / 100.0F;
	}

	/** The see-through pass alpha, 0..1. */
	public float seeThroughAlpha() {
		return depthTestedAlpha() * SEE_THROUGH_RATIO;
	}

	/** A copy with a different opacity, since a record component cannot be assigned in place. */
	public SculkSightConfig withShellOpacityPercent(int percent) {
		return new SculkSightConfig(percent, renderPolicy, trackedSensors, radiusAuditCap);
	}

	/** A copy with a different render policy, since a record component cannot be assigned in place. */
	public SculkSightConfig withRenderPolicy(RenderPolicy policy) {
		return new SculkSightConfig(shellOpacityPercent, policy, trackedSensors, radiusAuditCap);
	}

	public SculkSightConfig withTrackedSensors(List<TrackedSensor> sensors) {
		return new SculkSightConfig(shellOpacityPercent, renderPolicy, sensors, radiusAuditCap);
	}

	/** A copy with a different cap, since a record component cannot be assigned in place. */
	public SculkSightConfig withRadiusAuditCap(int cap) {
		return new SculkSightConfig(shellOpacityPercent, renderPolicy, trackedSensors, cap);
	}

	/** Adds a position once, preserving an existing name and toggle state on repeat selection. */
	public SculkSightConfig track(TrackedSensor sensor) {
		for (TrackedSensor existing : trackedSensors) {
			if (samePosition(existing, sensor)) {
				return this;
			}
		}
		if (trackedSensors.size() >= MAX_TRACKED_SENSORS) {
			return this;
		}
		List<TrackedSensor> updated = new ArrayList<>(trackedSensors);
		updated.add(sensor);
		return withTrackedSensors(updated);
	}

	/** Removes a tracked position, preserving the list order of all remaining sensors. */
	public SculkSightConfig untrack(int x, int y, int z) {
		List<TrackedSensor> updated = new ArrayList<>();
		boolean removed = false;
		for (TrackedSensor sensor : trackedSensors) {
			if (samePosition(sensor, x, y, z)) {
				removed = true;
			} else {
				updated.add(sensor);
			}
		}
		return removed ? withTrackedSensors(updated) : this;
	}

	private static boolean samePosition(TrackedSensor sensor, int x, int y, int z) {
		return sensor.x() == x && sensor.y() == y && sensor.z() == z;
	}
}
