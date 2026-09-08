package com.scr0ols.sculksight.client;

/**
 * The index arithmetic over the chunk sections a {@link VolumeSnapshot} copies: which sections the
 * bounding cube spans, and where each one sits in a flat array.
 *
 * <p><b>Why this is a separate type with no Minecraft on it.</b> Vanilla keeps the same arithmetic
 * as a {@code public static int index(...)} on {@code RenderSectionRegion} itself, which is fine
 * there because vanilla's span is the constant 3 on every axis and there is nothing to get wrong.
 * This mod's span is derived from the sensor's own radius (see {@link #over}), so the arithmetic
 * has real cases in it, and every one of them is integer work over section coordinates the caller
 * has already converted. Keeping it here means JUnit can reach all of it without a running game,
 * which is the same reason the solver depends on {@code WorldView} rather than on Minecraft
 * (ARCHITECTURE.md section 2.2, ADR-015).
 *
 * <p><b>Section coordinates in, never block coordinates.</b> Converting a block coordinate to a
 * section coordinate is a game fact, not arithmetic this project is entitled to write down:
 * {@link VolumeSnapshot} does it through vanilla's own {@code SectionPos.blockToSectionCoord},
 * which is PLAN.md section 3.2's derive-constants-from-the-game rule applied to the section size.
 * This class never learns how many blocks a section is.
 *
 * @param minSectionX the lowest section X the cube touches
 * @param minSectionY the lowest section Y the cube touches
 * @param minSectionZ the lowest section Z the cube touches
 * @param spanX how many sections the cube touches along X, at least 1
 * @param spanY how many sections the cube touches along Y, at least 1
 * @param spanZ how many sections the cube touches along Z, at least 1
 */
record SectionGrid(int minSectionX, int minSectionY, int minSectionZ,
		int spanX, int spanY, int spanZ) {

	SectionGrid {
		if (spanX < 1 || spanY < 1 || spanZ < 1) {
			throw new IllegalArgumentException(
					"span must be at least 1 on every axis, was " + spanX + "x" + spanY + "x" + spanZ);
		}
	}

	/**
	 * The grid covering everything between two section coordinates inclusive.
	 *
	 * <p>Inclusive on both ends because both are sections the cube actually touches: a cube lying
	 * entirely inside one section gives {@code min == max} and a span of 1, which is a real case
	 * for a small radius and not a degenerate one.
	 */
	static SectionGrid over(int minSectionX, int minSectionY, int minSectionZ,
			int maxSectionX, int maxSectionY, int maxSectionZ) {

		return new SectionGrid(minSectionX, minSectionY, minSectionZ,
				maxSectionX - minSectionX + 1,
				maxSectionY - minSectionY + 1,
				maxSectionZ - minSectionZ + 1);
	}

	/** How many sections this grid covers, and so how long the snapshot's own array is. */
	int size() {
		return spanX * spanY * spanZ;
	}

	/**
	 * The array slot for one section, or {@code -1} when the section lies outside this grid.
	 *
	 * <p>The stride order matches vanilla's own ({@code X} fastest, then {@code Y}, then {@code Z},
	 * {@code RenderSectionRegion.index}) with the constant 3 replaced by this grid's own spans.
	 *
	 * <p><b>Out of range returns {@code -1} rather than throwing</b>, and the caller substitutes air
	 * for it. ARCHITECTURE.md section 5 derives that no block outside the bounding cube can occlude
	 * any ray inside it, so a query outside this grid is not expected; but the traversal underneath
	 * {@code isBlockInLine} is deliberately unread by this project (ARCHITECTURE.md section 8), and
	 * an unread traversal is not something to hand an unchecked array index to on the strength of a
	 * derivation. Vanilla's own {@code SectionCopy} makes the same substitution for an out-of-range
	 * section index (R16 point 1).
	 */
	int index(int sectionX, int sectionY, int sectionZ) {
		int x = sectionX - minSectionX;
		int y = sectionY - minSectionY;
		int z = sectionZ - minSectionZ;

		if (x < 0 || x >= spanX || y < 0 || y >= spanY || z < 0 || z >= spanZ) {
			return -1;
		}

		return x + y * spanX + z * spanX * spanY;
	}

	/** The section coordinate of slot {@code index} along X, for the copy loop. */
	int sectionXOf(int index) {
		return minSectionX + index % spanX;
	}

	/** The section coordinate of slot {@code index} along Y, for the copy loop. */
	int sectionYOf(int index) {
		return minSectionY + index / spanX % spanY;
	}

	/** The section coordinate of slot {@code index} along Z, for the copy loop. */
	int sectionZOf(int index) {
		return minSectionZ + index / (spanX * spanY);
	}
}
