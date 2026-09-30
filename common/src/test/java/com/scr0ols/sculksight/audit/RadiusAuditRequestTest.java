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

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

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

		TranslatableContents contents = (TranslatableContents) problem.component().getContents();
		assertEquals("sculksight.command.find.detector.unknown", contents.getKey());
		assertEquals("warden", contents.getArgs()[0]);
		for (String name : RadiusAuditRequest.TYPE_NAMES) {
			assertTrue(String.valueOf(contents.getArgs()[1]).contains(name),
					String.valueOf(contents.getArgs()[1]));
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

		TranslatableContents contents = (TranslatableContents) problem.component().getContents();
		assertEquals("sculksight.command.find.radius.out_of_range", contents.getKey());
		assertEquals(radius, contents.getArgs()[0]);
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
		TranslatableContents all =
				(TranslatableContents) RadiusAuditRequest.of(64, "all").describe().getContents();
		assertEquals("sculksight.command.find.description", all.getKey());
		assertEquals(64, all.getArgs()[0]);
		TranslatableContents allDetector = detectorClauseOf(all);
		assertEquals("sculksight.command.find.description.all", allDetector.getKey());

		TranslatableContents calibrated = (TranslatableContents)
				RadiusAuditRequest.of(64, "calibrated").describe().getContents();
		assertEquals(64, calibrated.getArgs()[0]);
		TranslatableContents calibratedDetector = detectorClauseOf(calibrated);
		assertEquals("sculksight.command.find.description.detector", calibratedDetector.getKey());
		assertEquals("calibrated", calibratedDetector.getArgs()[0]);
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

	private static TranslatableContents detectorClauseOf(TranslatableContents description) {
		return (TranslatableContents) ((Component) description.getArgs()[1]).getContents();
	}

	private static String describedNameOf(DetectorType type) {
		TranslatableContents outer = (TranslatableContents)
				new RadiusAuditRequest(16, Optional.of(type)).describe().getContents();
		TranslatableContents detector = detectorClauseOf(outer);
		return String.valueOf(detector.getArgs()[0]);
	}
}
