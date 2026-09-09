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
		List<TrackedSensor> trackedSensors) {

	/** ADR-022's depth-tested alpha of 0.25, as the percentage this record stores. */
	public static final int DEFAULT_SHELL_OPACITY_PERCENT = 25;

	/** ADR-051's fixed default: union, not per-sensor. */
	public static final RenderPolicy DEFAULT_RENDER_POLICY = RenderPolicy.UNION;

	/** Safety bound for selection, solving, and the union mesh. */
	public static final int MAX_TRACKED_SENSORS = 8;

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
		return new SculkSightConfig(DEFAULT_SHELL_OPACITY_PERCENT, DEFAULT_RENDER_POLICY, List.of());
	}

	/** Compatibility constructor for callers that only set the appearance. */
	public SculkSightConfig(int shellOpacityPercent, RenderPolicy renderPolicy) {
		this(shellOpacityPercent, renderPolicy, List.of());
	}

	public SculkSightConfig {
		if (shellOpacityPercent < MIN_SHELL_OPACITY_PERCENT
				|| shellOpacityPercent > MAX_SHELL_OPACITY_PERCENT) {
			throw new IllegalArgumentException("shellOpacityPercent must be "
					+ MIN_SHELL_OPACITY_PERCENT + ".." + MAX_SHELL_OPACITY_PERCENT
					+ ", got " + shellOpacityPercent);
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
		return new SculkSightConfig(percent, renderPolicy, trackedSensors);
	}

	/** A copy with a different render policy, since a record component cannot be assigned in place. */
	public SculkSightConfig withRenderPolicy(RenderPolicy policy) {
		return new SculkSightConfig(shellOpacityPercent, policy, trackedSensors);
	}

	public SculkSightConfig withTrackedSensors(List<TrackedSensor> sensors) {
		return new SculkSightConfig(shellOpacityPercent, renderPolicy, sensors);
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
