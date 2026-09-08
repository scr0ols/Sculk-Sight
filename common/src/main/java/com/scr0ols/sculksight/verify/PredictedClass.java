package com.scr0ols.sculksight.verify;

/**
 * The three ways a sampled offset can relate to a {@link com.scr0ols.sculksight.solver.ShellSolution}.
 *
 * <p>Replaces a plain {@code predictedInSet} boolean so that the two ways a position can be
 * predicted absent are distinguishable. See `OPEN-QUESTIONS.md` section 13: a solver's
 * out-of-set class is overwhelmingly the positions that failed the cheap range test, so
 * sampling it without this distinction tests the range check far more than it tests the six-ray
 * occlusion rule - the one seam (`traverseBlocks`, ARCHITECTURE.md section 2.2) no unit test can
 * reach.
 */
public enum PredictedClass {

	/** Accepted into the detection set: in range and not occluded. */
	IN_SET,

	/** Passed the range test but was excluded by the six-ray occlusion rule. */
	OCCLUDED_OUT,

	/** Failed the range test outright. */
	OUT_OF_RANGE
}
