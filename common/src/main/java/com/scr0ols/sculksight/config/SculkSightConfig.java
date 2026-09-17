package com.scr0ols.sculksight.config;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

/**
 * Every player-settable value this mod has, as one immutable record.
 *
 * <p><b>Three entries.</b> `VISUAL-SPEC.md`'s 2026-09-06 status line closed the last four questions
 * that blocked v0.1, and of the answers only Q2's is a setting: ADR-049 makes the render-distance
 * fade an implementation constant rather than a config entry, ADR-050 makes shader-pack support a
 * documentation sentence, and ADR-052 ships no edge treatment at v0.1 at all. What is left from
 * that round is ADR-022's opacity, which that ADR itself already calls "the numbers the v0.1
 * slider will move". {@link #renderPolicy} joined it at v0.2 (ADR-034's M1): ADR-051 fixed union
 * as the multi-sensor default back at v0.1 and identified this as the seam the setting would
 * eventually live in, once a bounded multi-sensor selection existed for the policy to govern -
 * see {@link RenderPolicy} for what this field does. {@link #trackedSensors} is that selection: a
 * bounded, {@link #MAX_TRACKED_SENSORS}-deep list of the positions the player has chosen to track,
 * each with its own name and enabled toggle - see {@link TrackedSensor}.
 *
 * <p><b>One slider, two alphas.</b> ADR-021 draws the shell in two passes and ADR-022 gives them
 * different alphas, 0.25 depth-tested and 0.10 see-through; every document that mentions the
 * control says "the v0.1 opacity slider", singular. So the stored value is the depth-tested alpha,
 * as a whole percentage, and the see-through alpha follows it at {@link #SEE_THROUGH_RATIO} - the
 * ratio the two authored numbers already stand in. Moving one control therefore moves both passes
 * and preserves the relationship ADR-021 designed, instead of letting a player set a see-through
 * pass denser than the depth-tested one it is supposed to sit under.
 *
 * <p>Stored as a percentage rather than as a float because that is what the screen shows and what
 * a player hand-editing the file would expect to type. The alpha arithmetic is derived on demand
 * by {@link #depthTestedAlpha()} and {@link #seeThroughAlpha()}.
 *
 * <p><b>Validating, not clamping.</b> The canonical constructor rejects a percentage outside the
 * permitted range, and a {@code null} policy, rather than quietly moving either, so a bug that
 * computes one cannot hide. Repairing a hand-edited file is a separate, deliberate act with its
 * own report - see {@link ConfigCodec#read}.
 */
public record SculkSightConfig(int shellOpacityPercent, RenderPolicy renderPolicy,
		List<TrackedSensor> trackedSensors, int radiusAuditCap) {

	/** ADR-022's depth-tested alpha of 0.25, as the percentage this record stores. */
	public static final int DEFAULT_SHELL_OPACITY_PERCENT = 25;

	/** ADR-051's fixed default: union, not per-sensor. */
	public static final RenderPolicy DEFAULT_RENDER_POLICY = RenderPolicy.UNION;

	/**
	 * Safety bound for selection, solving, and the union mesh.
	 *
	 * <p><b>Was 8 until 2026-09-17, and raised to match {@link #DEFAULT_RADIUS_AUDIT_CAP} when
	 * {@code /sculksight find ... static} gave the audit a way to write into this list.</b> A static
	 * find pins what it selected, and the audit's own cap already bounds that at 32 - so a list
	 * capped at 8 would have silently dropped most of a typical find, which is the one thing the
	 * feature exists to avoid. Raising it makes "a full find always fits" true by construction
	 * rather than by the player happening to search a sparse area.
	 *
	 * <p><b>8 was not load-bearing for rendering by the time it moved</b>, which is what made this
	 * safe rather than hopeful. It predates mode B: the renderer already drew a 27-sensor selection
	 * through {@code ShellRenderer}'s per-tick solve budget and per-sensor cache, and the 2026-09-17
	 * live run measured that union's draw at mean 0.005-0.028 ms against a 0.5 ms budget, with 0-2
	 * frames over it across roughly 2000 sampled frames. The real bound on how much is solved and
	 * drawn is that budget plus {@link #radiusAuditCap}, not this number; this one bounds how much a
	 * player may curate by hand and how large the settings screen's own list may grow.
	 */
	public static final int MAX_TRACKED_SENSORS = 32;

	/**
	 * ARCHITECTURE.md section 12.4's cap, enforced in {@code RadiusAudit} before anything is solved
	 * or uploaded. Plan section 5 sizes mode B's scale estimate at 20+ sensors within radius 64;
	 * this default sits comfortably above that so a typical audit is never truncated, while still
	 * bounding the worst case the cap exists for.
	 */
	public static final int DEFAULT_RADIUS_AUDIT_CAP = 32;

	/** The audit must select at least one sensor to be worth running. */
	public static final int MIN_RADIUS_AUDIT_CAP = 1;

	/** An arbitrary but generous ceiling; nothing in plan section 5's scale estimate approaches it. */
	public static final int MAX_RADIUS_AUDIT_CAP = 256;

	/**
	 * Fully transparent. Permitted: a player may turn the fill off and keep the mod loaded.
	 *
	 * <p><b>That promise is kept by the renderer rather than by this constant</b>, and it is worth
	 * saying where. At zero the style's encoded alpha is zero, and ADR-022's modulation scheme
	 * reaches every other alpha by dividing by it - so the drawing code cannot represent this
	 * setting at all, and threw once per frame at it. {@code OPEN-QUESTIONS.md} section 23 is the
	 * finding and ADR-022's 2026-09-07 addendum the decision: {@code ShellRenderer.onRender}
	 * returns before its draw when the fill is off, so the toggle key still solves, still uploads
	 * and still reports "solved N positions", and simply draws nothing. Raising this constant to 1
	 * was the alternative considered, and it was rejected precisely because it would have withdrawn
	 * the sentence above.
	 */
	public static final int MIN_SHELL_OPACITY_PERCENT = 0;

	/** Fully opaque, the ceiling alpha itself has. */
	public static final int MAX_SHELL_OPACITY_PERCENT = 100;

	/**
	 * ADR-022's two alphas as a ratio, 0.10 / 0.25. The see-through pass keeps this proportion to
	 * the depth-tested one at every slider position, which is what makes one control enough.
	 */
	public static final float SEE_THROUGH_RATIO = 0.40F;

	/** The authored configuration: what ADR-022 and ADR-023 decided, with nothing overridden. */
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

	/**
	 * The nearest permitted percentage to the given one, <b>before</b> it is narrowed to an
	 * {@code int}. Used when repairing a value read out of a JSON document, where every number is
	 * a {@code double} ({@link Json}).
	 *
	 * <p><b>This overload exists because clamping after the narrowing is not the same operation.</b>
	 * {@code Math.round(double)} returns a {@code long}, so casting its result to {@code int} wraps:
	 * {@code (int) Math.round(1.0E300)} is -1, and clamping <i>that</i> moves it to zero rather than
	 * to one hundred - a player who hand-edited the file to an absurd opacity would get a fully
	 * transparent shell, the opposite of what they asked for. Bounding the {@code double} first
	 * leaves the cast unable to lose anything, because the value it narrows is already inside a
	 * range an {@code int} represents exactly. {@code OPEN-QUESTIONS.md} section 22.2 is the
	 * finding this answers.
	 *
	 * <p>A {@code NaN} is moved to the minimum rather than left as one: {@code Math.max} and
	 * {@code Math.min} both propagate it, so it is caught here instead of reaching a cast that
	 * would quietly make it zero regardless. Nothing this project parses produces one - {@link Json}
	 * has no {@code NaN} literal - but this method takes a {@code double} and says what it does
	 * with every one of them.
	 *
	 * @return a value in {@link #MIN_SHELL_OPACITY_PERCENT}..{@link #MAX_SHELL_OPACITY_PERCENT},
	 *         never infinite and never {@code NaN}
	 */
	public static double clampShellOpacityPercent(double percent) {
		if (Double.isNaN(percent)) {
			return MIN_SHELL_OPACITY_PERCENT;
		}

		return Math.max(MIN_SHELL_OPACITY_PERCENT, Math.min(MAX_SHELL_OPACITY_PERCENT, percent));
	}

	/** ADR-021's depth-tested pass alpha, 0..1. At the default percentage this is ADR-022's 0.25. */
	public float depthTestedAlpha() {
		return shellOpacityPercent / 100.0F;
	}

	/** ADR-021's see-through pass alpha, 0..1. At the default percentage this is ADR-022's 0.10. */
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
