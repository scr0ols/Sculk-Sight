package com.scr0ols.sculksight.verify;

/** How one sample's prediction compared with what the game did. */
public enum Outcome {

	/** The solver and the game agree, in either direction. */
	AGREEMENT,

	/** The solver and the game disagree. */
	DISAGREEMENT,

	/** The probe could not tell. */
	INCONCLUSIVE
}
