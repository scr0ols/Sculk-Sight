package com.scr0ols.sculksight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The section index arithmetic behind ARCHITECTURE.md section 6.2's snapshot phase.
 *
 * <p><b>What this proves and what it does not.</b> It proves the grid: that the span derived from a
 * bounding cube is the span that cube actually touches, that every slot is reachable exactly once,
 * and that a section outside the grid is reported rather than read out of bounds. It proves nothing
 * about the copy itself, which needs a live {@code LevelChunk} and therefore belongs to
 * differential verification rather than to JUnit, the same division TESTING-STRATEGY.md draws for
 * the solver: JUnit validates the model, the game validates the reading of the game.
 *
 * <p>Section coordinates are used directly throughout, never block coordinates, because that is the
 * only vocabulary {@link SectionGrid} has: the block-to-section reduction is vanilla's own and
 * happens in {@code VolumeSnapshot}, which is why none of this needs Minecraft on the classpath.
 */
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
		// The radius 16 case: a 33-block cube spans exactly three sections per axis at every
		// alignment, so this mod copies the same 27 sections vanilla's RenderRegionCache does
		// (RESEARCH-LOG.md R16, implications).
		SectionGrid grid = SectionGrid.over(-1, -1, -1, 1, 1, 1);

		assertEquals(27, grid.size());
	}

	@Test
	void everySlotIsReachableExactlyOnce() {
		// The property the copy loop depends on: if two sections shared a slot, one section's copy
		// would silently overwrite another's and the solve would read the wrong blocks for a whole
		// section, which is drawing the wrong shape (PLAN.md section 1) rather than failing.
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
		// The inverse of the test above, and what the copy loop would need if it ever iterated by
		// slot rather than by coordinate. Round-tripping both ways is what makes the stride order
		// checkable rather than merely self-consistent.
		SectionGrid grid = SectionGrid.over(5, -4, 11, 7, -2, 12);

		for (int index = 0; index < grid.size(); index++) {
			assertEquals(index,
					grid.index(grid.sectionXOf(index), grid.sectionYOf(index), grid.sectionZOf(index)),
					"slot " + index + " did not round-trip");
		}
	}

	@Test
	void aSectionOutsideTheGridIsReportedRatherThanReadOutOfBounds() {
		// ARCHITECTURE.md section 5 derives that no block outside the bounding cube can occlude any
		// ray inside it, so this is not expected to happen in a solve. It is handled anyway because
		// the traversal underneath isBlockInLine is deliberately unread (ARCHITECTURE.md section 8),
		// and an unread traversal is not something to hand an unchecked array index to.
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
		// A grid with a different span per axis catches the classic stride mistake, which a cubic
		// grid cannot: with spanX and spanY swapped, (1,0,0) and (0,1,0) collide.
		SectionGrid grid = SectionGrid.over(0, 0, 0, 3, 1, 2);

		assertNotEquals(grid.index(1, 0, 0), grid.index(0, 1, 0));
		assertNotEquals(grid.index(1, 0, 0), grid.index(0, 0, 1));
		assertNotEquals(grid.index(0, 1, 0), grid.index(0, 0, 1));

		// X fastest, then Y, then Z: the same stride order as vanilla's RenderSectionRegion.index,
		// with its hardcoded 3 replaced by this grid's own spans.
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
