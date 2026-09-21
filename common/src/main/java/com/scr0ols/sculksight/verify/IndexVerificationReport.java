package com.scr0ols.sculksight.verify;

import java.util.List;

/** The result of one index-verification run. */
public record IndexVerificationReport(int sweptSensors, int matched, List<IndexDiscrepancy> discrepancies) {

	/** Defensively copies the discrepancy list. */
	public IndexVerificationReport {
		discrepancies = List.copyOf(discrepancies);
	}

	/** Whether the sweep found something and the index agreed with it everywhere. */
	public boolean clean() {
		return discrepancies.isEmpty() && sweptSensors > 0;
	}

	/** A human-readable summary, in the form a dev command would print into chat. */
	public String summary() {
		StringBuilder text = new StringBuilder();

		text.append(sweptSensors).append(" swept, ")
				.append(matched).append(" matched, ")
				.append(discrepancies.size()).append(" discrepancies.");

		if (!discrepancies.isEmpty()) {
			text.append(" First discrepancies:");

			for (IndexDiscrepancy discrepancy : discrepancies.subList(0, Math.min(5, discrepancies.size()))) {
				text.append("\n  ").append(discrepancy.describe());
			}
		}

		return text.toString();
	}
}
