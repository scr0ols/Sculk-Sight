package com.scr0ols.sculksight.event;

/** Pure source-side validity rules mirrored from vanilla's vibration user. */
public final class VibrationSourceValidator {
	private VibrationSourceValidator() {
	}

	public static VibrationEvaluation evaluate(VibrationEventCandidate event, VibrationSourceContext source) {
		if (event == null || source == null) {
			return VibrationEvaluation.UNAVAILABLE;
		}
		if (source.affectedStateDampens()
				|| source.sourceKind() == VibrationSourceKind.SPECTATOR
				|| source.sourceKind() == VibrationSourceKind.DAMPENING_ENTITY
				|| (source.sneaking() && event.ignoredWhenSneaking())) {
			return VibrationEvaluation.INVALID_SOURCE;
		}
		return VibrationEvaluation.ACCEPTED;
	}
}
