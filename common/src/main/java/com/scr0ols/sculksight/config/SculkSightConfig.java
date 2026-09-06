package com.scr0ols.sculksight.config;

/**
 * Every player-settable value this mod has, as one immutable record.
 *
 * <p><b>One entry, and that is the whole of v0.1's screen rather than a stub.</b>
 * `VISUAL-SPEC.md`'s 2026-09-06 status line closed the last four questions that blocked v0.1, and
 * of the answers only Q2's is a setting: ADR-049 makes the render-distance fade an implementation
 * constant rather than a config entry, ADR-050 makes shader-pack support a documentation sentence,
 * ADR-051 fixes union as the multi-sensor policy with the per-sensor alternative arriving at v0.2,
 * and ADR-052 ships no edge treatment at v0.1 at all. What is left is ADR-022's opacity, which
 * that ADR itself already calls "the numbers the v0.1 slider will move".
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
 * permitted range rather than quietly moving it, so a bug that computes one cannot hide. Repairing
 * a hand-edited file is a separate, deliberate act with its own report - see
 * {@link ConfigCodec#read}.
 */
public record SculkSightConfig(int shellOpacityPercent) {

	/** ADR-022's depth-tested alpha of 0.25, as the percentage this record stores. */
	public static final int DEFAULT_SHELL_OPACITY_PERCENT = 25;

	/** Fully transparent. Permitted: a player may turn the fill off and keep the mod loaded. */
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
		return new SculkSightConfig(DEFAULT_SHELL_OPACITY_PERCENT);
	}

	public SculkSightConfig {
		if (shellOpacityPercent < MIN_SHELL_OPACITY_PERCENT
				|| shellOpacityPercent > MAX_SHELL_OPACITY_PERCENT) {
			throw new IllegalArgumentException("shellOpacityPercent must be "
					+ MIN_SHELL_OPACITY_PERCENT + ".." + MAX_SHELL_OPACITY_PERCENT
					+ ", got " + shellOpacityPercent);
		}
	}

	/** The nearest permitted percentage to the given one. Used when repairing a read value. */
	public static int clampShellOpacityPercent(int percent) {
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
		return new SculkSightConfig(percent);
	}
}
