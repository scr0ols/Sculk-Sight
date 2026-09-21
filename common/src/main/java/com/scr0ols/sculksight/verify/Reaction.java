package com.scr0ols.sculksight.verify;

/** What the game did when a vibration was triggered at a sampled position. */
public enum Reaction {

	/** The sensor accepted the vibration. */
	REACTED,

	/** The sensor did not accept the vibration, and the probe is confident that is a real no. */
	DID_NOT_REACT,

	/** The observation carries no information. */
	INCONCLUSIVE
}
