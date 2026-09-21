package com.scr0ols.sculksight.verify;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Computes what a sensor index should apply for one region, as ordinary maps. */
public final class IndexReconciliation {

	private IndexReconciliation() {
	}

	/** Merges a region's ground truth into the current entries, dropping entries the truth does not confirm. */
	public static Map<WorldPosition, Integer> apply(Map<WorldPosition, Integer> current,
			Map<WorldPosition, Integer> truth, Predicate<WorldPosition> inRegion) {

		Map<WorldPosition, Integer> result = new HashMap<>(current);
		result.keySet().removeIf(pos -> inRegion.test(pos) && !truth.containsKey(pos));
		result.putAll(truth);
		return result;
	}
}
