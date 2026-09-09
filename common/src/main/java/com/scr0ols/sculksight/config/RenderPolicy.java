package com.scr0ols.sculksight.config;

/**
 * How the mod draws a bounded, capped set of selected sensors' detection shells at once.
 *
 * <p>The renderer reads this policy after solving each enabled tracked sensor. Union translates
 * each accepted set into world coordinates and removes overlapping members and internal faces;
 * per-sensor retains one detector-coloured shell for each selected sensor.
 *
 * <p><b>Union is the default, and stays the default.</b> `DECISIONS.md` ADR-051 fixed this from
 * the pipeline's own crossed-layer arithmetic: one union shell across both of ADR-021's passes
 * composites to roughly 0.56 opacity, while five per-sensor shells compound to roughly 94% -
 * fog rather than a visualisation. Per-sensor colouring is kept available as a deliberate,
 * non-default choice for a player who wants to see which specific sensor covers a given
 * position, once the type palette (`VISUAL-SPEC.md` Q1) exists to colour it by.
 */
public enum RenderPolicy {

	/** One merged shape across every selected sensor's effective range. The default. */
	UNION,

	/** Each selected sensor drawn separately, coloured by its own detector type. */
	PER_SENSOR
}
