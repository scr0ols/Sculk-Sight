package com.scr0ols.sculksight.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

final class RecordingWorld implements WorldView {

	record Ray(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
	}

	private final List<Ray> rays = new ArrayList<>();
	private final IntPredicate blockedAtIndex;

	private RecordingWorld(IntPredicate blockedAtIndex) {
		this.blockedAtIndex = blockedAtIndex;
	}

	static RecordingWorld allClear() {
		return new RecordingWorld(i -> false);
	}

	static RecordingWorld allBlocked() {
		return new RecordingWorld(i -> true);
	}

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
