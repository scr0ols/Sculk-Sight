package com.scr0ols.sculksight.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.scr0ols.sculksight.solver.ShellSolution;

/**
 * Compares the solver's prediction against what the running game actually does.
 *
 * <p>This is the mechanism PLAN.md section 5.1 calls "the single thing standing between this
 * mod and one that lies convincingly", and TESTING-STRATEGY.md section 1 explains why nothing
 * cheaper will do: JUnit tests validate the solver against this project's model of the game,
 * and the oracle they compare against comes from the same model as the code under test. Green
 * unit tests prove internal consistency. Only this proves agreement with the game.
 *
 * <p>In particular, this is the only thing that checks the seam at
 * {@link com.scr0ols.sculksight.solver.WorldView}: the traversal beneath
 * {@code BlockGetter#isBlockInLine} has not been read, and a JUnit fake world implements a
 * different traversal, so no unit test reaches it (ARCHITECTURE.md section 2.2).
 *
 * <p>Everything in this class is ordinary logic over its own arguments, with no model of
 * Minecraft in it. That is what makes it unit-testable against a fake {@link SensorProbe}
 * without reintroducing the circularity - such a test proves the harness works and proves
 * nothing whatever about the solver.
 */
public final class DifferentialVerifier {

	private DifferentialVerifier() {
	}

	/**
	 * Samples the cube, probes the game at each sampled position, and reports disagreements.
	 *
	 * <p><b>Sampling is stratified across three classes, not two, and this is the second time
	 * this method has widened past the letter of TESTING-STRATEGY.md section 3.</b> That
	 * section asks only for members. The first widening split the out-of-set class from members
	 * so that a solver claiming too little could not pass unasked (a hole in the shell is as
	 * wrong as a bulge). That out-of-set class is not one thing, though: `OPEN-QUESTIONS.md`
	 * section 13 records that in a live wool scene it held 2 958 positions of which only 154
	 * were the occluded ones the six-ray rule actually carved - so a flat split tested the range
	 * check roughly two hundred times and the occlusion seam roughly five. {@link ShellSolution}
	 * already knows which out-of-set positions are which, at no extra cost (see its own
	 * javadoc), so this method now gives {@link PredictedClass#OCCLUDED_OUT} its own dedicated
	 * share of the sample instead of letting it be diluted by however common
	 * {@link PredictedClass#OUT_OF_RANGE} happens to be in a given scene.
	 *
	 * <p>The sample is split into thirds, one per {@link PredictedClass}, with any remainder
	 * from integer division given to {@code IN_SET} first and {@code OCCLUDED_OUT} second - so
	 * that neither of the two classes a solver bug could hide in is the one shortchanged by
	 * rounding.
	 *
	 * <p><b>A class smaller than its share is topped up from the other two, unlike the original
	 * two-way split.</b> That split refused to backfill a short class from the other, and said
	 * why: "silently rebalancing would let a run that examined almost no out-of-set positions be
	 * reported as a full sample." The reasoning still holds, but the fix it argues for is
	 * already in place for a different reason - {@link VerificationReport} now carries a
	 * sampled count per class, so a run that tested {@code OCCLUDED_OUT} zero times says so in
	 * the report rather than hiding behind a headline total. Once that is true, refusing to
	 * backfill has no benefit left and a real cost: {@code OCCLUDED_OUT} is empty in most
	 * scenes, since occlusion is the exception rather than the rule (an open-air or
	 * sphere-corner scene has none at all), and every one of those runs would silently lose a
	 * third of its requested sample for a class that was never going to contribute anything.
	 * Backfilling keeps the total sample size close to what was asked for whenever the other two
	 * classes have room, while the honest per-class counts mean nobody can mistake the result
	 * for having tested occlusion just because the headline total looks full.
	 *
	 * <p>Sampling is deterministic given the seed, so a disagreement can be reproduced by
	 * rerunning with the same seed rather than by hoping it recurs.
	 *
	 * @param sampleSize total positions to probe, split roughly evenly between the three classes
	 * @param seed       makes the run reproducible; see {@link com.scr0ols.sculksight.verify.VerificationCommand}
	 *                   for why this is a caller-supplied value rather than always derived from the sensor
	 */
	public static VerificationReport verify(String scene, ShellSolution solution,
			int sensorX, int sensorY, int sensorZ,
			SensorProbe probe, int sampleSize, long seed) {

		if (sampleSize < 0) {
			throw new IllegalArgumentException("sampleSize must not be negative: " + sampleSize);
		}

		final int radius = solution.radius();

		List<int[]> predictedIn = new ArrayList<>();
		List<int[]> occludedOut = new ArrayList<>();
		List<int[]> outOfRange = new ArrayList<>();

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					int[] offset = { dx, dy, dz };

					if (solution.accepted().contains(dx, dy, dz)) {
						predictedIn.add(offset);
					} else if (solution.occludedOut().contains(dx, dy, dz)) {
						occludedOut.add(offset);
					} else {
						outOfRange.add(offset);
					}
				}
			}
		}

		int share = sampleSize / 3;
		int remainder = sampleSize - 3 * share;

		// Remainder (0, 1 or 2 positions) goes to IN_SET first and OCCLUDED_OUT second, leaving
		// OUT_OF_RANGE - the class least likely to hide a bug worth this project's attention -
		// the one that can lose a slot to rounding.
		int[] shares = {
				share + (remainder > 0 ? 1 : 0),
				share + (remainder > 1 ? 1 : 0),
				share,
		};
		int[] capacities = { predictedIn.size(), occludedOut.size(), outOfRange.size() };
		int[] taken = allocateWithBackfill(shares, capacities);

		Random random = new Random(seed);
		List<int[]> chosen = new ArrayList<>();
		chosen.addAll(take(predictedIn, taken[0], random));
		chosen.addAll(take(occludedOut, taken[1], random));
		chosen.addAll(take(outOfRange, taken[2], random));

		int agreements = 0;
		int disagreements = 0;
		int inconclusive = 0;
		int inSetSampled = 0;
		int occludedOutSampled = 0;
		int outOfRangeSampled = 0;
		List<VerificationSample> disagreementDetail = new ArrayList<>();

		for (int[] offset : chosen) {
			int dx = offset[0];
			int dy = offset[1];
			int dz = offset[2];

			PredictedClass predictedClass;

			if (solution.accepted().contains(dx, dy, dz)) {
				predictedClass = PredictedClass.IN_SET;
				inSetSampled++;
			} else if (solution.occludedOut().contains(dx, dy, dz)) {
				predictedClass = PredictedClass.OCCLUDED_OUT;
				occludedOutSampled++;
			} else {
				predictedClass = PredictedClass.OUT_OF_RANGE;
				outOfRangeSampled++;
			}

			// The probe speaks in world positions; the solver speaks in offsets from the
			// sensor. The translation lives here so that every implementation of SensorProbe
			// does not have to repeat it, and cannot get it wrong differently.
			Reaction observed = probe.test(sensorX, sensorY, sensorZ,
					sensorX + dx, sensorY + dy, sensorZ + dz);

			VerificationSample sample = new VerificationSample(dx, dy, dz, predictedClass, observed);

			switch (sample.outcome()) {
				case AGREEMENT -> agreements++;
				case DISAGREEMENT -> {
					disagreements++;
					disagreementDetail.add(sample);
				}
				case INCONCLUSIVE -> inconclusive++;
			}
		}

		return new VerificationReport(scene, chosen.size(), agreements, disagreements, inconclusive,
				inSetSampled, occludedOutSampled, outOfRangeSampled, disagreementDetail);
	}

	/**
	 * Turns three desired shares into three actual take-counts, giving a class more than its
	 * share when another class cannot use all of its own.
	 *
	 * <p>Each class first gets {@code min(share, capacity)}. Whatever is left over - the sum of
	 * every class's unmet share - is then handed out one slot at a time, round-robin, to
	 * whichever classes still have spare capacity, until either the leftover is gone or every
	 * class is exhausted. A class already at its capacity never receives a slot, so this can
	 * never manufacture a position that does not exist: the sum of the result is capped by the
	 * sum of the capacities, exactly as {@link #verify} was already capped before this method
	 * existed.
	 *
	 * <p>The round-robin order is fixed ({@code IN_SET}, {@code OCCLUDED_OUT}, {@code
	 * OUT_OF_RANGE}), which is what keeps a run reproducible for a given seed: this method
	 * decides only how many positions each class contributes, and {@link #take} is still called
	 * in the same fixed order against the same {@link Random}, so the same seed always draws
	 * the same sequence of positions.
	 */
	private static int[] allocateWithBackfill(int[] shares, int[] capacities) {
		int n = shares.length;
		int[] taken = new int[n];
		int deficit = 0;

		for (int i = 0; i < n; i++) {
			taken[i] = Math.min(shares[i], capacities[i]);
			deficit += shares[i] - taken[i];
		}

		while (deficit > 0) {
			boolean progressed = false;

			for (int i = 0; i < n && deficit > 0; i++) {
				if (taken[i] < capacities[i]) {
					taken[i]++;
					deficit--;
					progressed = true;
				}
			}

			if (!progressed) {
				break;
			}
		}

		return taken;
	}

	/**
	 * Takes up to {@code count} distinct elements at random.
	 *
	 * <p>A partial Fisher-Yates shuffle: swap a random unseen element into each position in
	 * turn, and stop after {@code count} of them. This samples without replacement in one pass
	 * and without the retry loop that "pick a random index, skip it if already picked" would
	 * need - which degrades badly exactly when the sample is a large fraction of the
	 * population, as it is here for the in-set class at small radii, and now also for the
	 * occluded-out class whenever a scene carves only a handful of positions.
	 *
	 * <p>The source list is copied first so that the caller's list is not reordered.
	 */
	private static List<int[]> take(List<int[]> source, int count, Random random) {
		List<int[]> pool = new ArrayList<>(source);
		int wanted = Math.min(count, pool.size());

		for (int i = 0; i < wanted; i++) {
			int j = i + random.nextInt(pool.size() - i);
			int[] swap = pool.get(i);
			pool.set(i, pool.get(j));
			pool.set(j, swap);
		}

		return new ArrayList<>(pool.subList(0, wanted));
	}
}
