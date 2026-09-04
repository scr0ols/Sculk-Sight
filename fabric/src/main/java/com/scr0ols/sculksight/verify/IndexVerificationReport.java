package com.scr0ols.sculksight.verify;

import java.util.List;

/**
 * The result of one index-verification run: how many sensors an independent sweep found, how
 * many of them the index agreed with, and every position where the two disagreed.
 * DECISIONS.md ADR-041.
 *
 * <p>Modelled on {@link VerificationReport}, which mode A and mode C's own differential
 * verification already returns, for the reason that report's own javadoc gives: a headline count
 * alone cannot be trusted, because a run that verified nothing can still print "0 disagreements".
 * The equivalent trap here is a sweep over an area with no sensors in it at all - {@link #clean()}
 * guards against it the same way {@link VerificationReport#clean()} does.
 */
public record IndexVerificationReport(int sweptSensors, int matched, List<IndexDiscrepancy> discrepancies) {

	/**
	 * The compact constructor of a record runs before the fields are assigned and is the place
	 * to validate or normalise arguments. Here it defensively copies the list, so a caller cannot
	 * mutate a report after it has been produced.
	 */
	public IndexVerificationReport {
		discrepancies = List.copyOf(discrepancies);
	}

	/**
	 * Whether the sweep found something and the index agreed with it everywhere.
	 *
	 * <p>The first half is not defensive padding. A sweep over an area the player happens to have
	 * no sensors near produces zero discrepancies by never finding anything to disagree about,
	 * and calling that clean would claim the index was checked when nothing was.
	 */
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
