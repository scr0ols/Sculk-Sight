package com.scr0ols.sculksight.solver;

/** A per-position predicate applied alongside the range test. */
@FunctionalInterface
public interface PositionFilter {

	/** Whether to keep the position at the given offset from the sensor block. */
	boolean keep(int dx, int dy, int dz);

	/** Keeps everything. */
	PositionFilter ACCEPT_ALL = (dx, dy, dz) -> true;
}
