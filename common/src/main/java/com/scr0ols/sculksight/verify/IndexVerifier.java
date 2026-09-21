package com.scr0ols.sculksight.verify;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Diffs an independent ground truth against the live sensor index. */
public final class IndexVerifier {

	private IndexVerifier() {
	}

	/** Classifies every entry on both sides and returns the discrepancies, sorted by position. */
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
