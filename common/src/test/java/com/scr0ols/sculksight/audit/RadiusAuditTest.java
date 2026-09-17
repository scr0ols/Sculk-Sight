package com.scr0ols.sculksight.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.scr0ols.sculksight.audit.RadiusAudit.AuditedSensor;
import com.scr0ols.sculksight.client.DetectorType;
import com.scr0ols.sculksight.client.SensorKey;

/**
 * Section 12.1's selection stage: proximity to a centre, an optional type filter, a defined
 * order. No cap here - that is section 12.4's own task, not this one.
 */
class RadiusAuditTest {

	private static AuditedSensor sensorAt(int x, int y, int z, DetectorType type) {
		return new AuditedSensor(new SensorKey(x, y, z), 8, type);
	}

	@Test
	void aSensorExactlyAtTheRadiusQualifies() {
		AuditedSensor sensor = sensorAt(10, 0, 0, DetectorType.NORMAL_SENSOR);
		RadiusAuditRequest request = new RadiusAuditRequest(10, Optional.empty());

		List<AuditedSensor> selected = RadiusAudit.select(0, 0, 0, request, List.of(sensor));

		assertEquals(List.of(sensor), selected);
	}

	@Test
	void aSensorOneBlockBeyondTheRadiusIsExcluded() {
		AuditedSensor sensor = sensorAt(11, 0, 0, DetectorType.NORMAL_SENSOR);
		RadiusAuditRequest request = new RadiusAuditRequest(10, Optional.empty());

		List<AuditedSensor> selected = RadiusAudit.select(0, 0, 0, request, List.of(sensor));

		assertTrue(selected.isEmpty(), selected.toString());
	}

	@Test
	void anAbsentDetectorFilterKeepsEveryType() {
		AuditedSensor normal = sensorAt(1, 0, 0, DetectorType.NORMAL_SENSOR);
		AuditedSensor calibrated = sensorAt(2, 0, 0, DetectorType.CALIBRATED_SENSOR);
		AuditedSensor shrieker = sensorAt(3, 0, 0, DetectorType.SHRIEKER);
		RadiusAuditRequest request = new RadiusAuditRequest(10, Optional.empty());

		List<AuditedSensor> selected = RadiusAudit.select(0, 0, 0, request,
				List.of(shrieker, calibrated, normal));

		assertEquals(3, selected.size(), selected.toString());
	}

	@Test
	void aPresentDetectorFilterKeepsOnlyThatType() {
		AuditedSensor normal = sensorAt(1, 0, 0, DetectorType.NORMAL_SENSOR);
		AuditedSensor calibrated = sensorAt(2, 0, 0, DetectorType.CALIBRATED_SENSOR);
		RadiusAuditRequest request = new RadiusAuditRequest(10, Optional.of(DetectorType.CALIBRATED_SENSOR));

		List<AuditedSensor> selected = RadiusAudit.select(0, 0, 0, request, List.of(normal, calibrated));

		assertEquals(List.of(calibrated), selected);
	}

	@Test
	void selectionIsOrderedNearestFirstRegardlessOfCandidateOrder() {
		AuditedSensor near = sensorAt(1, 0, 0, DetectorType.NORMAL_SENSOR);
		AuditedSensor middle = sensorAt(5, 0, 0, DetectorType.NORMAL_SENSOR);
		AuditedSensor far = sensorAt(9, 0, 0, DetectorType.NORMAL_SENSOR);
		RadiusAuditRequest request = new RadiusAuditRequest(10, Optional.empty());

		List<AuditedSensor> selected = RadiusAudit.select(0, 0, 0, request, List.of(far, near, middle));

		assertEquals(List.of(near, middle, far), selected);
	}

	@Test
	void equidistantSensorsTieBreakOnPositionSoOrderIsDeterministic() {
		AuditedSensor a = sensorAt(3, 0, 0, DetectorType.NORMAL_SENSOR);
		AuditedSensor b = sensorAt(0, 3, 0, DetectorType.NORMAL_SENSOR);
		RadiusAuditRequest request = new RadiusAuditRequest(10, Optional.empty());

		List<AuditedSensor> selectedOneOrder = RadiusAudit.select(0, 0, 0, request, List.of(a, b));
		List<AuditedSensor> selectedOtherOrder = RadiusAudit.select(0, 0, 0, request, List.of(b, a));

		assertEquals(selectedOneOrder, selectedOtherOrder);
		assertEquals(List.of(b, a), selectedOneOrder);
	}

	@Test
	void noCandidatesSelectsNothing() {
		RadiusAuditRequest request = new RadiusAuditRequest(64, Optional.empty());

		List<AuditedSensor> selected = RadiusAudit.select(0, 0, 0, request, List.of());

		assertTrue(selected.isEmpty(), selected.toString());
	}

	@Test
	void aSelectionAtOrUnderTheCapIsNotMarkedCapped() {
		AuditedSensor sensor = sensorAt(1, 0, 0, DetectorType.NORMAL_SENSOR);
		RadiusAuditRequest request = new RadiusAuditRequest(10, Optional.empty());

		RadiusAudit.CappedSelection selection =
				RadiusAudit.selectWithCap(0, 0, 0, request, List.of(sensor), 1);

		assertEquals(List.of(sensor), selection.selected());
		assertTrue(!selection.capped());
		assertEquals(1, selection.matchedCount());
	}

	/**
	 * Section 12.4: the cap keeps the nearest entries, which is what makes {@link #select}'s
	 * nearest-first order a meaningful truncation rather than an arbitrary one.
	 */
	@Test
	void aSelectionOverTheCapKeepsOnlyTheNearestEntries() {
		AuditedSensor near = sensorAt(1, 0, 0, DetectorType.NORMAL_SENSOR);
		AuditedSensor middle = sensorAt(5, 0, 0, DetectorType.NORMAL_SENSOR);
		AuditedSensor far = sensorAt(9, 0, 0, DetectorType.NORMAL_SENSOR);
		RadiusAuditRequest request = new RadiusAuditRequest(10, Optional.empty());

		RadiusAudit.CappedSelection selection =
				RadiusAudit.selectWithCap(0, 0, 0, request, List.of(far, near, middle), 2);

		assertEquals(List.of(near, middle), selection.selected());
		assertTrue(selection.capped());
		assertEquals(3, selection.matchedCount());
	}
}
