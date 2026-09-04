package com.scr0ols.sculksight.solver;

/**
 * Receives crease edges as {@link CreaseEdgeExtractor} finds them.
 *
 * <p>The same shape and the same reason as {@link BoundaryFaceSink}: a callback rather than a
 * returned collection, so that a shell with thousands of edges costs no per-edge allocation on the
 * thread that solves it.
 *
 * <p>The three coordinates name a <b>lattice point</b>, not a block. The edge runs from that point
 * one unit along {@code axis}. In the sensor-relative frame of ADR-014, where the block at offset
 * {@code (dx, dy, dz)} occupies the unit cube from {@code (dx, dy, dz)} to
 * {@code (dx+1, dy+1, dz+1)}, a lattice point is a corner of that cube.
 */
@FunctionalInterface
public interface CreaseEdgeSink {

	void accept(int x, int y, int z, Axis axis);
}
