package com.scr0ols.sculksight.solver;

/** The result of one solve, split into the two ways a candidate can fail to reach the detection set. */
public final class ShellSolution {

	private final DetectionSet accepted;
	private final DetectionSet occludedOut;

	public ShellSolution(DetectionSet accepted, DetectionSet occludedOut) {
		if (accepted.radius() != occludedOut.radius()) {
			throw new IllegalArgumentException(
					"accepted and occludedOut must share a radius: " + accepted.radius()
							+ " vs " + occludedOut.radius());
		}

		this.accepted = accepted;
		this.occludedOut = occludedOut;
	}

	/** Positions in the detection set: in range and not occluded. */
	public DetectionSet accepted() {
		return accepted;
	}

	/** Positions that passed the range test but were excluded by {@link OcclusionTest#isOccluded}. */
	public DetectionSet occludedOut() {
		return occludedOut;
	}

	public int radius() {
		return accepted.radius();
	}

	/** True for an offset that is neither accepted nor occluded-out. */
	public boolean isOutOfRange(int dx, int dy, int dz) {
		return !accepted.contains(dx, dy, dz) && !occludedOut.contains(dx, dy, dz);
	}
}
