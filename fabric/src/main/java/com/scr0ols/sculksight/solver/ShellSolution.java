package com.scr0ols.sculksight.solver;

/**
 * The result of one solve, split into the two ways a candidate can fail to end up in the
 * detection set. Specified in ARCHITECTURE.md section 3.1, produced by
 * {@link ShellSolver#solveDetailed}.
 *
 * <p><b>Why this exists.</b> {@link DetectionSet} answers "is this position detected" and that
 * is all it has ever needed to answer for the solver's own purpose. Differential verification
 * needs a second question the solver already knows the answer to at zero extra cost: of the
 * positions that did not make the cut, which ones failed the range test outright, and which
 * ones passed it and were removed by the six-ray occlusion rule instead? The second class is
 * the only one that exercises {@code traverseBlocks} (ARCHITECTURE.md section 2.2) and is the
 * whole reason `OPEN-QUESTIONS.md` section 13 exists: sampling the undifferentiated leftover
 * class tests the range check far more than it tests occlusion, in proportion to how rare
 * occlusion is in a given scene rather than to how much it matters.
 *
 * <p><b>The third class needs no storage of its own.</b> A candidate offset inside the sensor's
 * bounding cube is in exactly one of three states after a solve: accepted, occluded-out, or
 * out of range. The first two are the two sets this class holds. The third is therefore never
 * a fact this project has to compute a second time with a second copy of the range formula -
 * it is simply "neither", which {@link #isOutOfRange} makes explicit. An offset outside the
 * cube also answers "neither" here, which is correct rather than a boundary bug: it is out of
 * range for the same reason {@link DetectionSet#contains} clamps to false there (R2).
 *
 * <p>Both sets share one radius, enforced by the constructor rather than assumed, because they
 * are always produced together by the same solve and nothing about this type is useful if they
 * disagree on the cube they describe.
 */
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

	/** Positions in the detection set: in range and not occluded. This is the shell the renderer draws. */
	public DetectionSet accepted() {
		return accepted;
	}

	/** Positions that passed the range test but were excluded by the six-ray occlusion rule (R4). */
	public DetectionSet occludedOut() {
		return occludedOut;
	}

	public int radius() {
		return accepted.radius();
	}

	/**
	 * True for an offset that is neither accepted nor occluded-out - i.e. failed the range test,
	 * or lies outside the bounding cube entirely.
	 *
	 * <p>Exact by construction, not by a second arithmetic test: {@link ShellSolver#solveDetailed}
	 * partitions every candidate in the cube into exactly one of {@link #accepted} or
	 * {@link #occludedOut}, so an offset in neither has nowhere else to have gone. This is what
	 * lets this method exist without this class, or its caller, ever writing the range formula
	 * a second time.
	 *
	 * <p>⚠ If {@link PositionFilter} ever becomes something other than {@code ACCEPT_ALL}
	 * (`OPEN-QUESTIONS.md` section 1, not yet decided), a position the filter rejects will also
	 * read as "out of range" here, which would not be quite true - it would have been excluded
	 * for usefulness, not for geometry. v0.0 always solves with {@code ACCEPT_ALL}, so no such
	 * position currently exists to be misclassified. Noted so it is not forgotten the day the
	 * filter stops being a no-op.
	 */
	public boolean isOutOfRange(int dx, int dy, int dz) {
		return !accepted.contains(dx, dy, dz) && !occludedOut.contains(dx, dy, dz);
	}
}
