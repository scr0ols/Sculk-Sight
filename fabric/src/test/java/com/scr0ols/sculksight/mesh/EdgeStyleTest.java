package com.scr0ols.sculksight.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The crease-edge distance fade of c-docs/DECISIONS.md ADR-028's 2026-09-02 addendum.
 *
 * <p>This exercises the curve itself rather than what it looks like. What it cannot prove is the
 * thing the addendum was taken for: whether the shell still reads at a distance once the seam has
 * faded, and whether the seam is still there when the player wants it. That is a live check, and
 * TESTING-STRATEGY.md section 5 owns it.
 */
class EdgeStyleTest {

	@Test
	void theV0FadeIsTheOneTheAddendumChose() {
		EdgeStyle edges = EdgeStyle.v0();

		assertEquals(8.0F, edges.fadeStartBlocks());
		assertEquals(24.0F, edges.fadeEndBlocks());
		assertEquals(0.12F, edges.fadeFloor());
	}

	/** Full strength anywhere inside the start distance, including at the sensor itself. */
	@ParameterizedTest
	@ValueSource(doubles = {0.0, 1.0, 7.9, 8.0})
	void theFadeIsOneUpToTheStartDistance(double distance) {
		assertEquals(1.0F, EdgeStyle.v0().distanceFade(distance), 1.0E-6F);
	}

	/** The floor, and never below it, however far away the camera goes. */
	@ParameterizedTest
	@ValueSource(doubles = {24.0, 40.0, 200.0, 5000.0})
	void theFadeHoldsAtTheFloorBeyondTheEndDistance(double distance) {
		assertEquals(0.12F, EdgeStyle.v0().distanceFade(distance), 1.0E-6F);
	}

	/**
	 * Linear in between, checked at the midpoint where the arithmetic is unambiguous.
	 *
	 * <p>The fade runs from 8 blocks to 24, so 16 is halfway and the value is halfway from 1 to the
	 * floor, which is 0.56.
	 */
	@Test
	void theFadeIsLinearBetweenTheTwoDistances() {
		assertEquals(0.56F, EdgeStyle.v0().distanceFade(16.0), 1.0E-6F);
	}

	/**
	 * The fade is a function of distance alone, which is the addendum's second revision.
	 *
	 * <p>Stated as a test because the first revision scaled the two distances by the shell's radius
	 * and the correction is easy to undo by accident. The signature carries no radius, so what this
	 * actually guards is the constants: a radius-8 shell and a radius-16 shell fade identically
	 * because there is only one curve, and the numbers below are the ones a normal sensor and a
	 * calibrated one would both see.
	 */
	@Test
	void theSameDistanceFadesTheSameWhateverTheShell() {
		EdgeStyle edges = EdgeStyle.v0();

		assertEquals(0.34F, edges.distanceFade(20.0), 1.0E-2F);
		assertEquals(edges.fadeFloor(), edges.distanceFade(30.0), 1.0E-6F);
		assertEquals(edges.fadeFloor(), edges.distanceFade(40.0), 1.0E-6F);
	}

	/** Never rises with distance, which is the whole shape of the curve in one assertion. */
	@Test
	void theFadeNeverRisesWithDistance() {
		EdgeStyle edges = EdgeStyle.v0();
		float previous = 1.0F;

		for (double distance = 0.0; distance <= 120.0; distance += 0.5) {
			float fade = edges.distanceFade(distance);

			assertTrue(fade <= previous, "fade rose at " + distance);
			assertTrue(fade >= edges.fadeFloor(), "fade fell below the floor at " + distance);

			previous = fade;
		}
	}

	/** The floor is not zero, so there is always a line left to delimit the shell. */
	@Test
	void theFadeNeverReachesZero() {
		EdgeStyle edges = EdgeStyle.v0();

		assertTrue(edges.fadeFloor() > 0.0F);
		assertTrue(edges.distanceFade(Double.MAX_VALUE) > 0.0F);
	}

	@Test
	void anEndDistanceThatDoesNotFollowTheStartIsRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> new EdgeStyle(0x000000, 0.85F, 0.35F, 2.0F, 24.0F, 24.0F, 0.12F));
		assertThrows(IllegalArgumentException.class,
				() -> new EdgeStyle(0x000000, 0.85F, 0.35F, 2.0F, 24.0F, 8.0F, 0.12F));
	}

	@Test
	void aNegativeStartDistanceIsRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> new EdgeStyle(0x000000, 0.85F, 0.35F, 2.0F, -1.0F, 24.0F, 0.12F));
	}

	@ParameterizedTest
	@ValueSource(floats = {-0.01F, 1.01F})
	void aFloorOutsideZeroToOneIsRejected(float floor) {
		assertThrows(IllegalArgumentException.class,
				() -> new EdgeStyle(0x000000, 0.85F, 0.35F, 2.0F, 8.0F, 24.0F, floor));
	}
}
