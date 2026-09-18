package com.scr0ols.sculksight.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VibrationSourceValidatorTest {
	private static final VibrationEventCandidate IGNORES_SNEAKING = new VibrationEventCandidate(4, 16, true);
	private static final VibrationEventCandidate ACCEPTS_SNEAKING = new VibrationEventCandidate(4, 16, false);

	@Test
	void acceptsOrdinarySource() {
		assertEquals(VibrationEvaluation.ACCEPTED, VibrationSourceValidator.evaluate(
				IGNORES_SNEAKING, new VibrationSourceContext(false, VibrationSourceKind.NONE, false)));
	}

	@Test
	void rejectsDampeningAffectedState() {
		assertEquals(VibrationEvaluation.INVALID_SOURCE, VibrationSourceValidator.evaluate(
				IGNORES_SNEAKING, new VibrationSourceContext(true, VibrationSourceKind.NONE, false)));
	}

	@Test
	void rejectsSpectatorSource() {
		assertEquals(VibrationEvaluation.INVALID_SOURCE, VibrationSourceValidator.evaluate(
				IGNORES_SNEAKING, new VibrationSourceContext(false, VibrationSourceKind.SPECTATOR, false)));
	}

	@Test
	void rejectsEntityThatDampensVibrations() {
		assertEquals(VibrationEvaluation.INVALID_SOURCE, VibrationSourceValidator.evaluate(
				IGNORES_SNEAKING, new VibrationSourceContext(false, VibrationSourceKind.DAMPENING_ENTITY, false)));
	}

	@Test
	void rejectsSneakingWhenEventIsTaggedToIgnoreIt() {
		assertEquals(VibrationEvaluation.INVALID_SOURCE, VibrationSourceValidator.evaluate(
				IGNORES_SNEAKING, new VibrationSourceContext(false, VibrationSourceKind.NONE, true)));
	}

	@Test
	void acceptsSneakingWhenEventIsNotTaggedToIgnoreIt() {
		assertEquals(VibrationEvaluation.ACCEPTED, VibrationSourceValidator.evaluate(
				ACCEPTS_SNEAKING, new VibrationSourceContext(false, VibrationSourceKind.NONE, true)));
	}

	@Test
	void unavailableInputsAreNotTreatedAsAnInvalidSource() {
		assertEquals(VibrationEvaluation.UNAVAILABLE, VibrationSourceValidator.evaluate(null,
				new VibrationSourceContext(false, VibrationSourceKind.NONE, false)));
	}
}
