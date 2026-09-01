package com.scr0ols.sculksight.solver;

/**
 * Receives boundary faces as {@link BoundaryFaceExtractor} finds them.
 *
 * <p><b>A sink rather than a returned collection</b>, per ARCHITECTURE.md section 3.2. One
 * extraction pass then serves two consumers with no intermediate allocation: the mesh encoder
 * writes each face straight into a vertex buffer, and a test collects faces into a list to
 * compare against an oracle. Returning a list of face records would allocate one object per
 * face on the worker thread for the sole benefit of the test, inside the path PLAN.md
 * section 3.3 budgets at 2 ms.
 */
@FunctionalInterface
public interface BoundaryFaceSink {

	/**
	 * One face of one member of the set, on the given side, where the neighbour on that side
	 * is not a member. Offsets are relative to the sensor block.
	 */
	void accept(int dx, int dy, int dz, Face face);
}
