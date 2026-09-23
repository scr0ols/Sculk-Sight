package com.scr0ols.sculksight.verify;

import java.util.List;

/** The result of one differential verification run against one scene. */
public record VerificationReport(
		String scene,
		int requested,
		int agreements,
		int disagreements,
		int inconclusive,
		int inSetSampled,
		int occludedOutSampled,
		int outOfRangeSampled,
		List<VerificationSample> disagreementDetail) {

	/** Defensively copies the disagreement list. */
	public VerificationReport {
		disagreementDetail = List.copyOf(disagreementDetail);
	}

	/** Samples that told us something: agreements plus disagreements, excluding inconclusive. */
	public int conclusive() {
		return agreements + disagreements;
	}

	/** Whether this run found no disagreement and actually observed something. */
	public boolean clean() {
		return disagreements == 0 && conclusive() > 0;
	}

	/** A human-readable summary, in the form a dev command would print into chat. */
	public String summary() {
		StringBuilder text = new StringBuilder();

		text.append("[").append(scene).append("] ")
				.append(requested).append(" sampled (")
				.append(inSetSampled).append(" in-set / ")
				.append(occludedOutSampled).append(" occluded-out / ")
				.append(outOfRangeSampled).append(" out-of-range), ")
				.append(conclusive()).append(" conclusive: ")
				.append(agreements).append(" agree, ")
				.append(disagreements).append(" DISAGREE, ")
				.append(inconclusive).append(" inconclusive.");

		if (disagreements > 0) {
			text.append(" First disagreements:");

			for (VerificationSample sample : disagreementDetail.subList(0, Math.min(5, disagreementDetail.size()))) {
				text.append("\n  ").append(sample.describe());
			}
		}

		return text.toString();
	}
}
