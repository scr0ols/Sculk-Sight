package com.scr0ols.sculksight.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class RadiusAuditModeTest {

	@Test
	void bothModesParseFromTheWordAPlayerTypes() throws RadiusAuditArgumentException {
		assertEquals(RadiusAuditMode.STATIC, RadiusAuditMode.of("static"));
		assertEquals(RadiusAuditMode.LIVE, RadiusAuditMode.of("live"));
	}

	@Test
	void caseIsNotSignificant() throws RadiusAuditArgumentException {
		assertEquals(RadiusAuditMode.STATIC, RadiusAuditMode.of("STATIC"));
		assertEquals(RadiusAuditMode.LIVE, RadiusAuditMode.of("Live"));
	}

	@Test
	void everySuggestedNameParsesBackToAMode() {
		for (String name : RadiusAuditMode.NAMES) {
			assertEquals(name, assertDoesNotThrowOf(name).modeName(),
					"a name offered as a suggestion must be one the validator accepts");
		}
	}

	private static RadiusAuditMode assertDoesNotThrowOf(String name) {
		try {
			return RadiusAuditMode.of(name);
		} catch (RadiusAuditArgumentException problem) {
			throw new AssertionError("suggested name '" + name + "' was rejected", problem);
		}
	}

	@Test
	void everyModeAppearsInTheSuggestionList() {
		assertEquals(RadiusAuditMode.values().length, RadiusAuditMode.NAMES.size());
		for (RadiusAuditMode mode : RadiusAuditMode.values()) {
			assertTrue(RadiusAuditMode.NAMES.contains(mode.modeName()), mode.modeName());
		}
	}

	@Test
	void anUnknownWordIsRejectedWithAMessageNamingTheRealModes() {
		RadiusAuditArgumentException problem = assertThrows(RadiusAuditArgumentException.class,
				() -> RadiusAuditMode.of("statc"));

		assertTrue(problem.getMessage().contains("statc"), problem.getMessage());
		assertTrue(problem.getMessage().contains("static"), problem.getMessage());
		assertTrue(problem.getMessage().contains("live"), problem.getMessage());
	}

	@Test
	void anAbsentModeIsRejectedRatherThanDefaulted() {
		RadiusAuditArgumentException problem = assertThrows(RadiusAuditArgumentException.class,
				() -> RadiusAuditMode.of(null));

		assertTrue(problem.getMessage().contains("static"), problem.getMessage());
		assertTrue(problem.getMessage().contains("live"), problem.getMessage());
	}

	@Test
	void theSuggestionListIsImmutable() {
		List<String> names = RadiusAuditMode.NAMES;

		assertThrows(UnsupportedOperationException.class, names::clear);
	}
}
