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

class RadiusAuditRequestTest {

	@Test
	void theAllNameMeansEveryDetector() throws Exception {
		RadiusAuditRequest request = RadiusAuditRequest.of(32, "all");

		assertEquals(32, request.radius());
		assertEquals(Optional.empty(), request.detector());
	}

	@ParameterizedTest
	@ValueSource(strings = {"ALL", "All"})
	void theAllNameIsCaseInsensitiveToo(String name) throws Exception {
		assertEquals(Optional.empty(), RadiusAuditRequest.of(16, name).detector());
	}

	@Test
	void theAllNameIsBothOfferedAndAccepted() throws Exception {
		assertTrue(RadiusAuditRequest.TYPE_NAMES.contains("all"),
				"'all' is accepted by the parser but not offered as a completion");
		assertEquals(Optional.empty(), RadiusAuditRequest.of(16, "all").detector());
	}

	@Test
	void aMissingTypeIsRejectedRatherThanReadAsAll() {
		assertThrows(RadiusAuditArgumentException.class, () -> RadiusAuditRequest.of(16, null));
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
		for (String name : RadiusAuditRequest.TYPE_NAMES) {
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
		for (String name : RadiusAuditRequest.TYPE_NAMES) {
			assertTrue(problem.getMessage().contains(name), problem.getMessage());
		}
	}

	@Test
	void theEmptyStringIsRejected() {
		assertThrows(RadiusAuditArgumentException.class, () -> RadiusAuditRequest.of(16, ""));
	}

	@ParameterizedTest
	@ValueSource(ints = {RadiusAuditRequest.MIN_RADIUS, 64, RadiusAuditRequest.MAX_RADIUS})
	void radiiInsideTheRangeAreAccepted(int radius) throws Exception {
		assertEquals(radius, RadiusAuditRequest.of(radius, "all").radius());
	}

	@ParameterizedTest
	@ValueSource(ints = {Integer.MIN_VALUE, -1, 0, RadiusAuditRequest.MAX_RADIUS + 1, Integer.MAX_VALUE})
	void radiiOutsideTheRangeAreRejected(int radius) {
		RadiusAuditArgumentException problem = assertThrows(RadiusAuditArgumentException.class,
				() -> RadiusAuditRequest.of(radius, "all"));

		assertTrue(problem.getMessage().contains(String.valueOf(radius)), problem.getMessage());
	}

	@Test
	void theCanonicalConstructorValidatesToo() {
		assertThrows(IllegalArgumentException.class,
				() -> new RadiusAuditRequest(0, Optional.empty()));
		assertThrows(IllegalArgumentException.class,
				() -> new RadiusAuditRequest(16, null));
	}

	@Test
	void descriptionDistinguishesOneDetectorFromAll() throws Exception {
		assertEquals("radius 64, all detectors", RadiusAuditRequest.of(64, "all").describe());
		assertEquals("radius 64, detector calibrated",
				RadiusAuditRequest.of(64, "calibrated").describe());
	}

	@Test
	void everyDetectorTypeRoundTripsThroughItsName() throws Exception {
		for (DetectorType type : DetectorType.values()) {
			String name = describedNameOf(type);

			assertEquals(Optional.of(type), RadiusAuditRequest.of(16, name).detector(),
					"detector " + type + " does not round trip through its own name");
			assertTrue(RadiusAuditRequest.TYPE_NAMES.contains(name),
					"detector " + type + " describes itself as '" + name
							+ "', which is not offered as a completion");
		}
	}

	private static String describedNameOf(DetectorType type) {
		String description = new RadiusAuditRequest(16, Optional.of(type)).describe();
		return description.substring(description.indexOf("detector ") + "detector ".length());
	}
}
