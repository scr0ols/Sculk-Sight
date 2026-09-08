package com.scr0ols.sculksight.verify;

/**
 * One sampled position: what the solver predicted, and what the game did.
 *
 * <p>Offsets are sensor-relative, matching the solver's frame, because that is the frame a
 * disagreement has to be read in when someone goes to look at it in game.
 */
public record VerificationSample(int dx, int dy, int dz, PredictedClass predictedClass, Reaction observed) {

	/**
	 * Compares the prediction with the observation.
	 *
	 * <p>The comparison is symmetric on purpose, and symmetric across both ways of being
	 * predicted absent. A position the solver excluded - for either reason - and the game
	 * accepted is exactly as much of a failure as the reverse: it is a hole in the drawn shell
	 * rather than a bulge, and PLAN.md section 1 ranks a wrong shape as wrong in either
	 * direction. {@link PredictedClass#OCCLUDED_OUT} and {@link PredictedClass#OUT_OF_RANGE}
	 * are therefore treated identically here - the distinction exists for sampling and
	 * reporting (see {@link DifferentialVerifier}), not for judging agreement.
	 */
	public Outcome outcome() {
		boolean predictedInSet = predictedClass == PredictedClass.IN_SET;

		return switch (observed) {
			case INCONCLUSIVE -> Outcome.INCONCLUSIVE;
			case REACTED -> predictedInSet ? Outcome.AGREEMENT : Outcome.DISAGREEMENT;
			case DID_NOT_REACT -> predictedInSet ? Outcome.DISAGREEMENT : Outcome.AGREEMENT;
		};
	}

	/** A one-line description for a report, readable without the surrounding context. */
	public String describe() {
		String predictionText = switch (predictedClass) {
			case IN_SET -> "predicted IN set";
			case OCCLUDED_OUT -> "predicted OUT of set (occluded)";
			case OUT_OF_RANGE -> "predicted OUT of set (out of range)";
		};

		return "offset (" + dx + ", " + dy + ", " + dz + "): " + predictionText + ", game " + observed;
	}
}
