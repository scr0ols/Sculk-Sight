package com.scr0ols.sculksight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SectionGridTest {

	@Test
	void aCubeInsideOneSectionSpansOneSection() {
		SectionGrid grid = SectionGrid.over(4, 0, -3, 4, 0, -3);

		assertEquals(1, grid.spanX());
		assertEquals(1, grid.spanY());
		assertEquals(1, grid.spanZ());
		assertEquals(1, grid.size());
		assertEquals(0, grid.index(4, 0, -3));
	}

	@Test
	void aThreeByThreeByThreeGridHasVanillasOwnTwentySevenSlots() {
		SectionGrid grid = SectionGrid.over(-1, -1, -1, 1, 1, 1);

		assertEquals(27, grid.size());
	}

	@Test
	void everySlotIsReachableExactlyOnce() {
		SectionGrid grid = SectionGrid.over(-2, 3, 7, 0, 5, 8);

		assertEquals(3 * 3 * 2, grid.size());

		Set<Integer> seen = new HashSet<>();

		for (int z = 7; z <= 8; z++) {
			for (int y = 3; y <= 5; y++) {
				for (int x = -2; x <= 0; x++) {
					int index = grid.index(x, y, z);

					assertTrue(index >= 0 && index < grid.size(),
							"index " + index + " out of range for section " + x + "," + y + "," + z);
					assertTrue(seen.add(index),
							"slot " + index + " claimed twice, second by section " + x + "," + y + "," + z);
				}
			}
		}

		assertEquals(grid.size(), seen.size());
	}

	@Test
	void everySlotReportsTheSectionItHolds() {
		SectionGrid grid = SectionGrid.over(5, -4, 11, 7, -2, 12);

		for (int index = 0; index < grid.size(); index++) {
			assertEquals(index,
					grid.index(grid.sectionXOf(index), grid.sectionYOf(index), grid.sectionZOf(index)),
					"slot " + index + " did not round-trip");
		}
	}

	@Test
	void aSectionOutsideTheGridIsReportedRatherThanReadOutOfBounds() {
		SectionGrid grid = SectionGrid.over(0, 0, 0, 1, 1, 1);

		assertEquals(-1, grid.index(-1, 0, 0));
		assertEquals(-1, grid.index(2, 0, 0));
		assertEquals(-1, grid.index(0, -1, 0));
		assertEquals(-1, grid.index(0, 2, 0));
		assertEquals(-1, grid.index(0, 0, -1));
		assertEquals(-1, grid.index(0, 0, 2));
	}

	@Test
	void axesAreNotInterchangeable() {
		SectionGrid grid = SectionGrid.over(0, 0, 0, 3, 1, 2);

		assertNotEquals(grid.index(1, 0, 0), grid.index(0, 1, 0));
		assertNotEquals(grid.index(1, 0, 0), grid.index(0, 0, 1));
		assertNotEquals(grid.index(0, 1, 0), grid.index(0, 0, 1));

		assertEquals(1, grid.index(1, 0, 0));
		assertEquals(grid.spanX(), grid.index(0, 1, 0));
		assertEquals(grid.spanX() * grid.spanY(), grid.index(0, 0, 1));
	}

	@Test
	void anEmptySpanIsRejectedRatherThanSilentlyProducingAnEmptyGrid() {
		assertThrows(IllegalArgumentException.class, () -> new SectionGrid(0, 0, 0, 0, 1, 1));
		assertThrows(IllegalArgumentException.class, () -> new SectionGrid(0, 0, 0, 1, 0, 1));
		assertThrows(IllegalArgumentException.class, () -> new SectionGrid(0, 0, 0, 1, 1, 0));
	}
}
