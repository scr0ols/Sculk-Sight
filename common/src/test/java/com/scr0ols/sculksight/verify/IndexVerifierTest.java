package com.scr0ols.sculksight.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link IndexVerifier}.
 *
 * <p><b>What a green run here means, the same caveat every verification-mechanism test in this
 * project carries.</b> Both maps below are ordinary Java {@code Map}s, not a live {@code SensorIndex}
 * and not a real chunk sweep, so these tests establish that the diff is correct given two maps.
 * They establish nothing about whether {@code SensorIndex} or {@code IndexSweep} are themselves
 * built correctly against the game - only {@code /sculksight-verify-index} against a running
 * client does that (`DECISIONS.md` ADR-041).
 */
class IndexVerifierTest {

	private static final WorldPosition A = new WorldPosition(10, 64, -20);
	private static final WorldPosition B = new WorldPosition(11, 65, -20);
	private static final WorldPosition C = new WorldPosition(9, 64, -19);

	@Test
	@DisplayName("two empty maps sweep nothing, and an empty run is not reported as clean")
	void emptyMapsProveNothing() {
		IndexVerificationReport report = IndexVerifier.diff(Map.of(), Map.of());

		assertEquals(0, report.sweptSensors());
		assertEquals(0, report.matched());
		assertTrue(report.discrepancies().isEmpty());
		assertFalse(report.clean(), "a sweep that found nothing has verified nothing");
	}

	@Test
	@DisplayName("identical maps match on every entry and the run is clean")
	void identicalMapsAreClean() {
		Map<WorldPosition, Integer> groundTruth = Map.of(A, 8, B, 16);
		Map<WorldPosition, Integer> index = Map.of(A, 8, B, 16);

		IndexVerificationReport report = IndexVerifier.diff(groundTruth, index);

		assertEquals(2, report.sweptSensors());
		assertEquals(2, report.matched());
		assertTrue(report.discrepancies().isEmpty());
		assertTrue(report.clean());
	}

	@Test
	@DisplayName("a sensor the sweep found but the index has no entry for is MISSING_FROM_INDEX")
	void aSweptPositionAbsentFromTheIndexIsMissing() {
		Map<WorldPosition, Integer> groundTruth = Map.of(A, 8);
		Map<WorldPosition, Integer> index = Map.of();

		IndexVerificationReport report = IndexVerifier.diff(groundTruth, index);

		assertEquals(1, report.sweptSensors());
		assertEquals(0, report.matched());
		assertFalse(report.clean());

		IndexDiscrepancy only = report.discrepancies().getFirst();
		assertEquals(IndexDiscrepancy.Kind.MISSING_FROM_INDEX, only.kind());
		assertEquals(8, only.sweptRadius());
		assertEquals(IndexDiscrepancy.NO_ENTRY, only.indexedRadius());
	}

	@Test
	@DisplayName("a sensor the index has but the sweep did not find is STALE_IN_INDEX")
	void anIndexedPositionAbsentFromTheSweepIsStale() {
		Map<WorldPosition, Integer> groundTruth = Map.of();
		Map<WorldPosition, Integer> index = Map.of(A, 8);

		IndexVerificationReport report = IndexVerifier.diff(groundTruth, index);

		// The sweep found nothing, so this run proves nothing per clean()'s own rule - but the
		// stale entry is still a real discrepancy and must still be reported, exactly like a
		// disagreement is reported even from an otherwise inconclusive VerificationReport.
		assertEquals(0, report.sweptSensors());
		assertFalse(report.clean());

		IndexDiscrepancy only = report.discrepancies().getFirst();
		assertEquals(IndexDiscrepancy.Kind.STALE_IN_INDEX, only.kind());
		assertEquals(IndexDiscrepancy.NO_ENTRY, only.sweptRadius());
		assertEquals(8, only.indexedRadius());
	}

	@Test
	@DisplayName("the same position at two different radii is RADIUS_MISMATCH, not missing or stale")
	void aRadiusDisagreementIsItsOwnKind() {
		Map<WorldPosition, Integer> groundTruth = Map.of(A, 8);
		Map<WorldPosition, Integer> index = Map.of(A, 16);

		IndexVerificationReport report = IndexVerifier.diff(groundTruth, index);

		assertEquals(0, report.matched());
		assertFalse(report.clean());

		IndexDiscrepancy only = report.discrepancies().getFirst();
		assertEquals(IndexDiscrepancy.Kind.RADIUS_MISMATCH, only.kind());
		assertEquals(8, only.sweptRadius());
		assertEquals(16, only.indexedRadius());
	}

	@Test
	@DisplayName("a mix of agreement, a miss and a stale entry all show up, matched excludes both")
	void aMixOfOutcomesIsReportedTogether() {
		Map<WorldPosition, Integer> groundTruth = Map.of(A, 8, B, 16);
		Map<WorldPosition, Integer> index = Map.of(A, 8, C, 16);

		IndexVerificationReport report = IndexVerifier.diff(groundTruth, index);

		assertEquals(2, report.sweptSensors());
		assertEquals(1, report.matched());
		assertEquals(2, report.discrepancies().size());
		assertFalse(report.clean());

		List<IndexDiscrepancy.Kind> kinds = report.discrepancies().stream()
				.map(IndexDiscrepancy::kind)
				.sorted()
				.toList();
		assertEquals(List.of(IndexDiscrepancy.Kind.MISSING_FROM_INDEX, IndexDiscrepancy.Kind.STALE_IN_INDEX), kinds);
	}

	@Test
	@DisplayName("discrepancies are sorted by position regardless of map iteration order")
	void discrepanciesAreSortedByPosition() {
		Map<WorldPosition, Integer> groundTruth = Map.of(A, 8, B, 8, C, 8);
		Map<WorldPosition, Integer> index = Map.of();

		IndexVerificationReport report = IndexVerifier.diff(groundTruth, index);

		List<WorldPosition> ordered = report.discrepancies().stream()
				.map(d -> new WorldPosition(d.x(), d.y(), d.z()))
				.toList();

		assertEquals(List.of(C, A, B), ordered, "sorted by x, then y, then z: C(9,..) < A(10,..) < B(11,..)");
	}

	@Test
	@DisplayName("a report's list cannot be mutated by its caller after it was produced")
	void theDiscrepancyListIsDefensivelyCopied() {
		IndexVerificationReport report = IndexVerifier.diff(Map.of(A, 8), Map.of());

		assertTrue(report.discrepancies() instanceof List<IndexDiscrepancy>);
		org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
				() -> report.discrepancies().add(null));
	}
}
