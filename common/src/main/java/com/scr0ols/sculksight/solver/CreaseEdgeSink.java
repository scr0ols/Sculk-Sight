package com.scr0ols.sculksight.solver;

/** Receives crease edges, each a lattice point and an axis, from {@link CreaseEdgeExtractor}. */
@FunctionalInterface
public interface CreaseEdgeSink {

	void accept(int x, int y, int z, Axis axis);
}
