package com.scr0ols.sculksight.verify;

/** How one sample's prediction compared with what the game did. */
public enum Outcome {

	/** The solver and the game agree, in either direction. */
	AGREEMENT,

	/**
	 * The solver and the game disagree. This is the finding the whole mechanism exists to
	 * produce, and a single one of these fails the v0.0 exit criteria (PLAN.md section 5).
	 */
	DISAGREEMENT,

	/** The probe could not tell. Counts toward neither side, and toward no exit criterion. */
	INCONCLUSIVE
}
