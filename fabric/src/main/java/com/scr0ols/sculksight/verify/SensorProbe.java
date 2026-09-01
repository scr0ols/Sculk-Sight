package com.scr0ols.sculksight.verify;

/**
 * Triggers a vibration at a chosen position and reports whether the sensor reacted.
 *
 * <p><b>This interface is a placeholder for an unanswered question, and that is its whole
 * purpose.</b> How a vibration is triggered at an arbitrary position, and how "did the sensor
 * react" is observed, are unspecified by the plan and deliberately not invented
 * (TESTING-STRATEGY.md section 3). They are now c-docs/research/R14.md. Answering R14 means
 * writing one implementation of this interface; nothing above it changes.
 *
 * <p>This is the same move ADR-015 made for {@link com.scr0ols.sculksight.solver.WorldView},
 * applied to a second unread thing, and for the same reason: it puts the code that can be
 * tested on one side of a line and the code that cannot on the other. Recorded as ADR-020.
 *
 * <p><b>No implementation can be client-side, and that is settled rather than merely likely.</b>
 * R8 point 2 records that vanilla's detection logic is typed to {@code ServerLevel} throughout,
 * and R2 point 3 records that a client chunk's listener registry is
 * {@code GameEventListenerRegistry.NOOP} - so even a client-side dispatch would be offered to a
 * registry with no listeners in it. Any implementation therefore reaches server-side state
 * under the development-environment-only concession of ADR-019.
 */
@FunctionalInterface
public interface SensorProbe {

	/**
	 * Triggers a vibration at the source position and reports what the sensor did.
	 *
	 * <p>Coordinates are absolute world block positions, not sensor-relative offsets. The
	 * solver works in offsets and the game works in world positions, and the translation
	 * happens in {@link DifferentialVerifier} rather than being pushed into every
	 * implementation of this interface.
	 *
	 * <p>Implementations must return {@link Reaction#INCONCLUSIVE} rather than guessing
	 * whenever the observation is unreliable. An implementation that reports a confident
	 * answer it does not have defeats the entire mechanism, which exists precisely because
	 * confident wrong answers are this project's central risk.
	 */
	Reaction test(int sensorX, int sensorY, int sensorZ, int sourceX, int sourceY, int sourceZ);
}
