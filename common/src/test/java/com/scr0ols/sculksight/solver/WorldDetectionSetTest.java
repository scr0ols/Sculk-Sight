package com.scr0ols.sculksight.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

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
}
