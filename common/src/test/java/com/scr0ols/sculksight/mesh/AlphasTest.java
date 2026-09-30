package com.scr0ols.sculksight.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AlphasTest {

	@Test
	void aTargetOfZeroNeedsNoSingleLayerAlpha() {
		assertEquals(0.0F, Alphas.singleLayerAlphaFor(0.0F), 1.0E-6F);
	}

	@Test
	void aFullyOpaqueTargetNeedsAFullyOpaqueSingleLayer() {
		assertEquals(1.0F, Alphas.singleLayerAlphaFor(1.0F), 1.0E-6F);
	}

	@Test
	void theKnownDefaultTargetProducesTheExpectedSingleLayerAlpha() {
		// 1 - sqrt(1 - 0.25) = 1 - sqrt(0.75) ~= 0.133975
		assertEquals(0.133975F, Alphas.singleLayerAlphaFor(0.25F), 1.0E-6F);
	}

	@ParameterizedTest
	@ValueSource(floats = {0.001F, 0.01F, 0.05F, 0.1F, 0.25F, 0.5F, 0.75F, 0.9F, 0.99F, 1.0F})
	void theSingleLayerAlphaNeverExceedsTheTargetItComposesTo(float target) {
		// A single layer must never need to be brighter than the two-layer composite it is meant to
		// reach - that would mean the correction darkens rather than dims, which is not the point.
		assertTrue(Alphas.singleLayerAlphaFor(target) <= target + 1.0E-6F);
	}

	@ParameterizedTest
	@ValueSource(floats = {0.0F, 0.001F, 0.01F, 0.05F, 0.1F, 0.25F, 0.5F, 0.75F, 0.9F, 0.99F, 1.0F})
	void theSingleLayerAlphaStaysBoundedAndNeverBlowsUpForSmallTargets(float target) {
		// The formula divides by nothing (it is a closed-form 1 - sqrt(1 - a)), so even the smallest
		// positive target must stay a small, finite, non-negative value - never spike toward
		// infinity the way a naive alpha/alpha division might suggest.
		float singleLayer = Alphas.singleLayerAlphaFor(target);

		assertTrue(singleLayer >= 0.0F);
		assertTrue(singleLayer <= 1.0F);
	}

	@ParameterizedTest
	@ValueSource(floats = {0.001F, 0.01F, 0.05F, 0.1F, 0.25F, 0.5F, 0.75F, 0.9F, 0.99F, 1.0F})
	void twoLayersOfTheSingleLayerAlphaCompositeBackToTheTarget(float target) {
		float singleLayer = Alphas.singleLayerAlphaFor(target);
		float composite = 1.0F - (1.0F - singleLayer) * (1.0F - singleLayer);

		assertEquals(target, composite, 1.0E-5F);
	}
}
