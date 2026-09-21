package com.scr0ols.sculksight.verify;

/** One sampled position: what the solver predicted, and what the game did. */
public record VerificationSample(int dx, int dy, int dz, PredictedClass predictedClass, Reaction observed) {

	/** Compares the prediction with the observation. */
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
