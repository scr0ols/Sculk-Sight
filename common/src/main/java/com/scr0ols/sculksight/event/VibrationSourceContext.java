package com.scr0ols.sculksight.event;

/** Immutable source-side facts needed before an event can reach a listener. */
public record VibrationSourceContext(
		boolean affectedStateDampens,
		VibrationSourceKind sourceKind,
		boolean sneaking) {

	public VibrationSourceContext {
		if (sourceKind == null) {
			throw new NullPointerException("sourceKind");
		}
	}
}
