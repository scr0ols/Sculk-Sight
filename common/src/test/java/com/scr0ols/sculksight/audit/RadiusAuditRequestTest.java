package com.scr0ols.sculksight.audit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.scr0ols.sculksight.client.DetectorType;

/**
 * Argument parsing and validation for {@code /sculksight radius <n> [type]}.
 *
 * <p>These are the tests ARCHITECTURE.md section 12.1 says the layer-1 half of mode B exists to
 * make possible: no {@code ClientLevel}, no dispatcher, no loader. What they do not cover is
 * whether the command is reachable in a running game, which is a live run and not a JUnit test.
 */
class RadiusAuditRequestTest {

	@Test
	void omittedTypeMeansEveryDetector() throws Exception {
		RadiusAuditRequest request = RadiusAuditRequest.of(32, null);

		assertEquals(32, request.radius());
		assertEquals(Optional.empty(), request.detector());
	}

	@ParameterizedTest
	@CsvSource({
		"sensor, NORMAL_SENSOR",
		"calibrated, CALIBRATED_SENSOR",
		"shrieker, SHRIEKER",
	})
	void eachDetectorNameParses(String name, DetectorType expected) throws Exception {
		assertEquals(Optional.of(expected), RadiusAuditRequest.of(16, name).detector());
	}

	@ParameterizedTest
	@ValueSource(strings = {"SENSOR", "Calibrated", "ShRiEkEr"})
	void detectorNamesAreCaseInsensitive(String name) throws Exception {
		assertTrue(RadiusAuditRequest.of(16, name).detector().isPresent());
	}

	@Test
	void everyOfferedCompletionIsAcceptedByTheParser() {
		for (String name : RadiusAuditRequest.DETECTOR_NAMES) {
			assertDoesNotThrow(
					() -> RadiusAuditRequest.of(16, name),
					"suggestion '" + name + "' is offered but rejected");
		}
	}

	@Test
	void unknownDetectorNameIsRejectedAndTheMessageNamesTheAlternatives() {
		RadiusAuditArgumentException problem = assertThrows(RadiusAuditArgumentException.class,
				() -> RadiusAuditRequest.of(16, "warden"));

		assertTrue(problem.getMessage().contains("warden"), problem.getMessage());
		for (String name : RadiusAuditRequest.DETECTOR_NAMES) {
			assertTrue(problem.getMessage().contains(name), problem.getMessage());
		}
	}

	@Test
	void theEmptyStringIsNotAnOmittedArgument() {
		assertThrows(RadiusAuditArgumentException.class, () -> RadiusAuditRequest.of(16, ""));
	}

	@ParameterizedTest
	@ValueSource(ints = {RadiusAuditRequest.MIN_RADIUS, 64, RadiusAuditRequest.MAX_RADIUS})
	void radiiInsideTheRangeAreAccepted(int radius) throws Exception {
		assertEquals(radius, RadiusAuditRequest.of(radius, null).radius());
	}

	@ParameterizedTest
	@ValueSource(ints = {Integer.MIN_VALUE, -1, 0, RadiusAuditRequest.MAX_RADIUS + 1, Integer.MAX_VALUE})
	void radiiOutsideTheRangeAreRejected(int radius) {
		RadiusAuditArgumentException problem = assertThrows(RadiusAuditArgumentException.class,
				() -> RadiusAuditRequest.of(radius, null));

		assertTrue(problem.getMessage().contains(String.valueOf(radius)), problem.getMessage());
	}

	/**
	 * The bounds are handed to Brigadier's own {@code IntegerArgumentType} by both loader classes,
	 * so an out-of-range radius is normally rejected before {@link RadiusAuditRequest#of} sees it.
	 * The record checks anyway: a constructor that trusts its caller is one refactor away from
	 * being wrong, and this is the only place the range is defined.
	 */
	@Test
	void theCanonicalConstructorValidatesToo() {
		assertThrows(IllegalArgumentException.class,
				() -> new RadiusAuditRequest(0, Optional.empty()));
		assertThrows(IllegalArgumentException.class,
				() -> new RadiusAuditRequest(16, null));
	}

	@Test
	void descriptionDistinguishesOneDetectorFromAll() throws Exception {
		assertEquals("radius 64, all detectors", RadiusAuditRequest.of(64, null).describe());
		assertEquals("radius 64, detector calibrated",
				RadiusAuditRequest.of(64, "calibrated").describe());
	}

	/**
	 * Guards the pair of switches in the record against a fourth detector being added to
	 * {@link DetectorType} and reaching only one of them. Both are exhaustive switches over the
	 * enum, so the compiler catches the parse side; nothing but this catches a name that parses
	 * and then describes itself as something the parser would not accept.
	 */
	@Test
	void everyDetectorTypeRoundTripsThroughItsName() throws Exception {
		for (DetectorType type : DetectorType.values()) {
			String name = describedNameOf(type);

			assertEquals(Optional.of(type), RadiusAuditRequest.of(16, name).detector(),
					"detector " + type + " does not round trip through its own name");
			assertTrue(RadiusAuditRequest.DETECTOR_NAMES.contains(name),
					"detector " + type + " describes itself as '" + name
							+ "', which is not offered as a completion");
		}
	}

	private static String describedNameOf(DetectorType type) {
		String description = new RadiusAuditRequest(16, Optional.of(type)).describe();
		return description.substring(description.indexOf("detector ") + "detector ".length());
	}
}
