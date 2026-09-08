package com.scr0ols.sculksight.verify;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Computes what {@code SensorIndex#reconcile} should apply for one region, as ordinary maps - the
 * same "no Minecraft type, no live level" shape {@link IndexVerifier} already has and for the same
 * reason: it is what makes this unit-testable without a running game, while proving nothing at all
 * about whether {@code SensorIndex} or {@code IndexSweep} are themselves built correctly against
 * it. Only {@code /sculksight-verify-index} against a running client establishes that.
 *
 * <p><b>The report this closes.</b> NeoForge has no live block-entity add/remove event
 * ({@code SculkSightNeoForge}'s own javadoc), so a sensor placed or broken while its containing
 * chunk stays loaded never reaches {@code SensorIndex} there at all - confirmed as the cause of the
 * 2026-09-08 captain report: a detection box built and tested in one continuous session read "not
 * detected" no matter where the player stood, because the sensor was never indexed in the first
 * place. {@code SensorIndex#reconcile} is the fix, called periodically for a small region around
 * the player; this class is the diff-and-merge arithmetic underneath it, split out so that
 * arithmetic can be tested on its own.
 */
public final class IndexReconciliation {

	private IndexReconciliation() {
	}

	/**
	 * Starting from {@code current}, keeps every entry {@code inRegion} does not accept
	 * unconditionally (this method has no opinion on positions outside the region {@code truth}
	 * describes), drops every entry {@code inRegion} does accept but {@code truth} does not
	 * confirm, and then writes every entry {@code truth} has - added, or overwriting whatever
	 * radius {@code current} had for that position.
	 *
	 * <p>{@code inRegion} must describe exactly the positions {@code truth} could have reported
	 * on - neither smaller, which would leave a position {@code truth} did not cover mistaken for
	 * stale and dropped, nor larger, which would drop a real entry outside where {@code truth}
	 * looked for having no ground-truth confirmation when the truth is that nobody checked there.
	 */
	public static Map<WorldPosition, Integer> apply(Map<WorldPosition, Integer> current,
			Map<WorldPosition, Integer> truth, Predicate<WorldPosition> inRegion) {

		Map<WorldPosition, Integer> result = new HashMap<>(current);
		result.keySet().removeIf(pos -> inRegion.test(pos) && !truth.containsKey(pos));
		result.putAll(truth);
		return result;
	}
}
