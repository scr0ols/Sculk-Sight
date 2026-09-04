package com.scr0ols.sculksight.verify;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Diffs an independent ground truth against {@code SensorIndex}'s live contents. DECISIONS.md
 * ADR-041.
 *
 * <p>Ordinary logic over two maps, with no model of Minecraft in it and no live level anywhere
 * near it - the same shape {@link DifferentialVerifier} already has, and for the same reason: it
 * is what makes this class unit-testable without a running game, while proving nothing at all
 * about the game itself. What {@code IndexVerifierTest} establishes is that the diff is correct
 * given two maps; only {@code /sculksight-verify-index} against a running client establishes that
 * either map was built correctly, and that command's own javadoc says which of the two is the
 * one under test.
 *
 * <p>Both maps are expected to already be scoped to the same region - the caller's job, since only
 * the caller knows what region a particular sweep covered ({@code IndexSweep#withinSweep}). A
 * position present in one map and absent from the other is read here as a real discrepancy, not
 * as a boundary artifact, so a caller that diffs mismatched regions will get a report full of
 * false positives rather than a warning.
 */
public final class IndexVerifier {

	private IndexVerifier() {
	}

	/**
	 * Every ground-truth entry is classified against the index: present with the same radius
	 * ({@link IndexVerificationReport#matched}), present with a different radius
	 * ({@link IndexDiscrepancy.Kind#RADIUS_MISMATCH}), or absent
	 * ({@link IndexDiscrepancy.Kind#MISSING_FROM_INDEX}). A second pass then finds every index
	 * entry the first pass never visited, which is exactly the entries with no ground-truth
	 * counterpart ({@link IndexDiscrepancy.Kind#STALE_IN_INDEX}).
	 *
	 * <p>Discrepancies are sorted by position before being returned, so a report is reproducible
	 * for a given pair of maps regardless of the hash-map iteration order either arrived in.
	 */
	public static IndexVerificationReport diff(Map<WorldPosition, Integer> groundTruth,
			Map<WorldPosition, Integer> index) {

		int matched = 0;
		List<IndexDiscrepancy> discrepancies = new ArrayList<>();

		for (Map.Entry<WorldPosition, Integer> entry : groundTruth.entrySet()) {
			WorldPosition pos = entry.getKey();
			int sweptRadius = entry.getValue();
			Integer indexedRadius = index.get(pos);

			if (indexedRadius == null) {
				discrepancies.add(new IndexDiscrepancy(pos.x(), pos.y(), pos.z(),
						IndexDiscrepancy.Kind.MISSING_FROM_INDEX, sweptRadius, IndexDiscrepancy.NO_ENTRY));
			} else if (indexedRadius != sweptRadius) {
				discrepancies.add(new IndexDiscrepancy(pos.x(), pos.y(), pos.z(),
						IndexDiscrepancy.Kind.RADIUS_MISMATCH, sweptRadius, indexedRadius));
			} else {
				matched++;
			}
		}

		for (Map.Entry<WorldPosition, Integer> entry : index.entrySet()) {
			WorldPosition pos = entry.getKey();

			if (!groundTruth.containsKey(pos)) {
				discrepancies.add(new IndexDiscrepancy(pos.x(), pos.y(), pos.z(),
						IndexDiscrepancy.Kind.STALE_IN_INDEX, IndexDiscrepancy.NO_ENTRY, entry.getValue()));
			}
		}

		discrepancies.sort(Comparator.<IndexDiscrepancy>comparingInt(IndexDiscrepancy::x)
				.thenComparingInt(IndexDiscrepancy::y)
				.thenComparingInt(IndexDiscrepancy::z));

		return new IndexVerificationReport(groundTruth.size(), matched, discrepancies);
	}
}
