package com.scr0ols.sculksight.solver;

/** The solver's entire contact with the world. */
public interface WorldView {

	/** Whether the block directly below an event source dampens its vibration. */
	default boolean dampensVibrationsBelow(int sourceX, int sourceY, int sourceZ) {
		return false;
	}

	/** True if a block matching the vibration-occlusion predicate lies on the segment from-to. */
	boolean occluderOnSegment(double fromX, double fromY, double fromZ,
			double toX, double toY, double toZ);
}
