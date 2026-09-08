package com.scr0ols.sculksight.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * A fake {@link WorldView} that records every ray it is asked about and answers by call index.
 *
 * <p><b>What this can and cannot establish.</b> This fake implements a traversal that is not
 * the game's traversal - it does not implement one at all, it answers from a script. Tests
 * built on it therefore validate the composition above {@link WorldView}: the nudge, the
 * centre snapping, the conjunction, the early return, and how the solver orders its calls.
 * They say nothing whatsoever about whether {@code isBlockInLine} agrees with any of it.
 * That is ARCHITECTURE.md section 2.2's seam and TESTING-STRATEGY.md section 1's circularity
 * problem, and only differential verification reaches it.
 *
 * <p>Answering by call index rather than by geometry is deliberate. It lets a test say
 * "block every ray except the fourth" without having to model space, which keeps the tests
 * about the rule rather than about the fake.
 */
final class RecordingWorld implements WorldView {

	/**
	 * One recorded query. A record is a class whose fields, constructor, accessors,
	 * {@code equals}, {@code hashCode} and {@code toString} are all generated from this one
	 * line; the fields are final and the type is immutable. Used here because a failed
	 * assertion prints the whole ray legibly for free.
	 */
	record Ray(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
	}

	private final List<Ray> rays = new ArrayList<>();
	private final IntPredicate blockedAtIndex;

	private RecordingWorld(IntPredicate blockedAtIndex) {
		this.blockedAtIndex = blockedAtIndex;
	}

	/** Every segment is clear, so no position is ever occluded. */
	static RecordingWorld allClear() {
		return new RecordingWorld(i -> false);
	}

	/** Every segment meets an occluder, so every position is occluded. */
	static RecordingWorld allBlocked() {
		return new RecordingWorld(i -> true);
	}

	/** Every segment is blocked except the one at the given zero-based call index. */
	static RecordingWorld allBlockedExceptCall(int clearIndex) {
		return new RecordingWorld(i -> i != clearIndex);
	}

	@Override
	public boolean occluderOnSegment(double fromX, double fromY, double fromZ,
			double toX, double toY, double toZ) {
		int index = rays.size();
		rays.add(new Ray(fromX, fromY, fromZ, toX, toY, toZ));
		return blockedAtIndex.test(index);
	}

	List<Ray> rays() {
		return List.copyOf(rays);
	}

	int rayCount() {
		return rays.size();
	}
}
