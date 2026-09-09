package com.scr0ols.sculksight.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.scr0ols.sculksight.client.DetectorType;

class WorldDetectionSetTest {

	@Test
	void unionDeduplicatesOverlappingSensorPositionsAndReportsBounds() {
		DetectionSet first = new DetectionSet(1);
		first.add(0, 0, 0);
		DetectionSet second = new DetectionSet(1);
		second.add(0, 0, 0);

		WorldDetectionSet union = new WorldDetectionSet();
		union.add(first, 10, 20, 30);
		union.add(second, 11, 20, 30);

		assertEquals(2, union.size());
		assertTrue(union.contains(10, 20, 30));
		assertTrue(union.contains(11, 20, 30));
		assertEquals(new WorldDetectionSet.Bounds(10, 20, 30, 11, 20, 30, false), union.bounds());
	}

	@Test
	void unionBoundaryOmitsTheInternalFace() {
		WorldDetectionSet union = new WorldDetectionSet();
		union.add(0, 0, 0);
		union.add(1, 0, 0);
		Set<String> faces = new HashSet<>();
		union.extractBoundaryFaces((x, y, z, face) -> faces.add(x + ":" + y + ":" + z + ":" + face));

		assertEquals(10, faces.size());
		assertFalse(faces.contains("0:0:0:EAST"));
		assertFalse(faces.contains("1:0:0:WEST"));
	}

	@Test
	void mixedDetectorUnionRetainsTypeOnPositionsAndBoundaryFaces() {
		WorldDetectionSet union = new WorldDetectionSet();
		DetectionSet normal = new DetectionSet(0);
		normal.add(0, 0, 0);
		DetectionSet calibrated = new DetectionSet(0);
		calibrated.add(0, 0, 0);
		DetectionSet shrieker = new DetectionSet(0);
		shrieker.add(0, 0, 0);
		union.add(normal, 0, 0, 0, DetectorType.NORMAL_SENSOR);
		union.add(calibrated, 2, 0, 0, DetectorType.CALIBRATED_SENSOR);
		union.add(shrieker, 4, 0, 0, DetectorType.SHRIEKER);

		assertEquals(Optional.of(DetectorType.NORMAL_SENSOR), union.detectorAt(0, 0, 0));
		assertEquals(Optional.of(DetectorType.CALIBRATED_SENSOR), union.detectorAt(2, 0, 0));
		assertEquals(Optional.of(DetectorType.SHRIEKER), union.detectorAt(4, 0, 0));

		Map<DetectorType, Integer> faceCounts = new EnumMap<>(DetectorType.class);
		union.extractBoundaryFaces((x, y, z, face, detector) ->
				faceCounts.merge(detector, 1, Integer::sum));

		assertEquals(Map.of(
				DetectorType.NORMAL_SENSOR, 6,
				DetectorType.CALIBRATED_SENSOR, 6,
				DetectorType.SHRIEKER, 6), faceCounts);
	}

	@Test
	void overlappingUnionPositionKeepsTheFirstDetectorType() {
		WorldDetectionSet union = new WorldDetectionSet();
		union.add(1, 2, 3, DetectorType.CALIBRATED_SENSOR);
		union.add(1, 2, 3, DetectorType.SHRIEKER);

		assertEquals(Optional.of(DetectorType.CALIBRATED_SENSOR), union.detectorAt(1, 2, 3));
	}
}
