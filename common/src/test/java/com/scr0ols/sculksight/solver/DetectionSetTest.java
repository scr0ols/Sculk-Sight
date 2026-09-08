package com.scr0ols.sculksight.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DetectionSet}.
 *
 * <p>These are not subject to TESTING-STRATEGY.md section 1's circularity problem, and it is
 * worth being clear about why: this class is a bitset with no model of Minecraft in it. Its
 * oracle is arithmetic, not the author's understanding of the game, so a green run here means
 * what it appears to mean.
 */
class DetectionSetTest {

	@Test
	@DisplayName("every position in the cube maps to its own bit, with no collisions")
	void indexingIsInjectiveAcrossTheWholeCube() {
		// The failure this is written against is an index formula that aliases two offsets onto
		// one bit - which would show up as a shell with stray members far from anything, and
		// would be very hard to diagnose from the rendered image. Adding each position one at a
		// time and checking the count is exactly the check that catches it.
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

		// Filling the cube means a false answer below can only come from the clamp, not from
		// the position merely not having been added.
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
		// A degenerate case worth pinning: side is 2*0+1 = 1, so the cube is one block. Nothing
		// in the mod solves at radius 0, but an off-by-one in the side computation would show
		// up here first and most cheaply.
		DetectionSet set = new DetectionSet(0);

		set.add(0, 0, 0);

		assertEquals(1, set.size());
		assertTrue(set.contains(0, 0, 0));
		assertFalse(set.contains(1, 0, 0));
	}

	@Test
	@DisplayName("the word array is large enough for the real radii")
	void realRadiiAreAddressableAtTheirCorners() {
		// Radius 8 and 16 are the only two the mod needs (R1). The corner is the highest bit
		// index, so if the array were undersized by even one word this is where it would throw.
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
