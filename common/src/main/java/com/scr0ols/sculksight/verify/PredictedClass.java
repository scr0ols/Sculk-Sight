package com.scr0ols.sculksight.verify;

/** How a sampled offset relates to a solver's detection set. */
public enum PredictedClass {

	/** Accepted into the detection set: in range and not occluded. */
	IN_SET,

	/** Passed the range test but was excluded by {@code OcclusionTest#isOccluded}. */
	OCCLUDED_OUT,

	/** Failed the range test outright. */
	OUT_OF_RANGE
}
