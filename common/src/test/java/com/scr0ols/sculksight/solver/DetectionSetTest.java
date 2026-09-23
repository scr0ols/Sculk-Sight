package com.scr0ols.sculksight.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DetectionSetTest {

	@Test
	@DisplayName("every position in the cube maps to its own bit, with no collisions")
	void indexingIsInjectiveAcrossTheWholeCube() {
		int radius = 4;
		DetectionSet set = new DetectionSet(radius);
		int added = 0;

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					set.add(dx, dy, dz);
					added++;
					assertEquals(added, set.size(),
							"adding (" + dx + ", " + dy + ", " + dz + ") should add exactly one bit");
				}
			}
		}

		int side = 2 * radius + 1;
		assertEquals(side * side * side, set.size());
	}

	@Test
	@DisplayName("what was added is contained, and nothing else is")
	void containsMatchesWhatWasAdded() {
		int radius = 3;
		DetectionSet set = new DetectionSet(radius);

		set.add(0, 0, 0);
		set.add(-3, 2, 1);
		set.add(3, -3, 3);

		assertTrue(set.contains(0, 0, 0));
		assertTrue(set.contains(-3, 2, 1));
		assertTrue(set.contains(3, -3, 3));

		assertFalse(set.contains(1, 0, 0));
		assertFalse(set.contains(-3, 2, 2));
		assertFalse(set.contains(0, 0, 1));
		assertEquals(3, set.size());
	}

	@Test
	@DisplayName("adding the same position twice does not change the set")
	void addIsIdempotent() {
		DetectionSet set = new DetectionSet(2);

		set.add(1, 1, 1);
		set.add(1, 1, 1);

		assertEquals(1, set.size());
		assertTrue(set.contains(1, 1, 1));
	}

	@Test
	@DisplayName("contains returns false outside the cube on every axis and in both directions")
	void containsClampsOutsideTheCube() {
		int radius = 2;
		DetectionSet set = new DetectionSet(radius);

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					set.add(dx, dy, dz);
				}
			}
		}

		int out = radius + 1;

		assertFalse(set.contains(out, 0, 0));
		assertFalse(set.contains(-out, 0, 0));
		assertFalse(set.contains(0, out, 0));
		assertFalse(set.contains(0, -out, 0));
		assertFalse(set.contains(0, 0, out));
		assertFalse(set.contains(0, 0, -out));
	}

	@Test
	@DisplayName("adding outside the cube throws rather than being dropped silently")
	void addOutsideTheCubeThrows() {
		DetectionSet set = new DetectionSet(2);

		assertThrows(IndexOutOfBoundsException.class, () -> set.add(3, 0, 0));
		assertThrows(IndexOutOfBoundsException.class, () -> set.add(0, -3, 0));
		assertEquals(0, set.size());
	}

	@Test
	@DisplayName("a new set is empty and reports its radius")
	void newSetIsEmpty() {
		DetectionSet set = new DetectionSet(8);

		assertEquals(8, set.radius());
		assertEquals(0, set.size());
		assertFalse(set.contains(0, 0, 0));
	}

	@Test
	@DisplayName("radius zero is a single position, not an empty cube")
	void radiusZeroHoldsOnePosition() {
		DetectionSet set = new DetectionSet(0);

		set.add(0, 0, 0);

		assertEquals(1, set.size());
		assertTrue(set.contains(0, 0, 0));
		assertFalse(set.contains(1, 0, 0));
	}

	@Test
	@DisplayName("the word array is large enough for the real radii")
	void realRadiiAreAddressableAtTheirCorners() {
		for (int radius : new int[] { 8, 16 }) {
			DetectionSet set = new DetectionSet(radius);

			set.add(radius, radius, radius);
			set.add(-radius, -radius, -radius);

			assertTrue(set.contains(radius, radius, radius));
			assertTrue(set.contains(-radius, -radius, -radius));
			assertEquals(2, set.size());
		}
	}
}
