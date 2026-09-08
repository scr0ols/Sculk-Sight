package com.scr0ols.sculksight.verify;

/**
 * What the game did when a vibration was triggered at a sampled position.
 *
 * <p>Three values, not two. The third is load-bearing: R2 point 4 records that the first thing
 * {@code VibrationSystem.Listener#handleGameEvent} tests is whether a vibration is already in
 * flight, so a sample taken while the sensor is busy is rejected for a reason that has nothing
 * to do with geometry. Folding that into {@link #DID_NOT_REACT} would manufacture disagreements
 * that look like solver bugs; folding it into {@link #REACTED} would hide real ones.
 *
 * <p>Recorded as c-docs/DECISIONS.md ADR-020.
 */
public enum Reaction {

	/** The sensor accepted the vibration. */
	REACTED,

	/** The sensor did not accept the vibration, and the probe is confident that is a real no. */
	DID_NOT_REACT,

	/**
	 * The observation carries no information - the sensor was busy, the trigger did not land,
	 * or the probe could not tell. Does not count toward the exit criteria's sample count.
	 */
	INCONCLUSIVE
}
