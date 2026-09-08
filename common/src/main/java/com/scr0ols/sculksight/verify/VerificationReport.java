package com.scr0ols.sculksight.verify;

import java.util.List;

/**
 * The result of one differential verification run against one scene.
 *
 * <p>The three outcome counts are reported separately and never summed into a single pass/fail
 * number, which is the point rather than a formatting preference. A run that was mostly
 * inconclusive has not verified anything, and a report that showed only "0 disagreements" would
 * let such a run be mistaken for a clean one. See ADR-020.
 *
 * <p>The three sampled-per-class counts exist for a related but distinct reason: they are the
 * concrete answer to `OPEN-QUESTIONS.md` section 13's question, printed by the mechanism itself
 * rather than computed by hand afterwards. A report that read "200 sampled, 0 disagree" gave no
 * way to tell whether the occlusion seam was tested twice or two hundred times; this one says so
 * directly.
 */
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

	/**
	 * The compact constructor of a record runs before the fields are assigned and is the place
	 * to validate or normalise arguments. Here it defensively copies the list, so a caller
	 * cannot mutate a report after it has been produced.
	 */
	public VerificationReport {
		disagreementDetail = List.copyOf(disagreementDetail);
	}

	/** Samples that told us something: agreements plus disagreements, excluding inconclusive. */
	public int conclusive() {
		return agreements + disagreements;
	}

	/**
	 * Whether this run found no disagreement <em>and</em> actually observed something.
	 *
	 * <p>The second half is not defensive padding. A probe that returned
	 * {@link Reaction#INCONCLUSIVE} for every sample - because the sensor was permanently busy,
	 * or because the trigger silently failed - produces zero disagreements, and calling that
	 * clean would be exactly the convincing lie this project is built to avoid.
	 *
	 * <p>This is not the v0.0 exit criterion. That criterion is 200 conclusive samples across
	 * three distinct scenes with zero disagreements (PLAN.md section 5), and it spans several
	 * runs, so it is judged over a set of these reports rather than by any one of them.
	 */
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
