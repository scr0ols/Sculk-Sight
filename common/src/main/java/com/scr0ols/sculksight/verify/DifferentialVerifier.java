package com.scr0ols.sculksight.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.scr0ols.sculksight.solver.ShellSolution;

/** Compares the solver's prediction against what the running game actually does. */
public final class DifferentialVerifier {

	private DifferentialVerifier() {
	}

	/** Samples the cube, probes the game at each sampled position, and reports disagreements. */
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
