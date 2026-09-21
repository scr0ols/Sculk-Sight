package com.scr0ols.sculksight.solver;

/** Receives boundary faces as {@link BoundaryFaceExtractor} finds them. */
@FunctionalInterface
public interface BoundaryFaceSink {

	/** One face of one member of the set, with offsets relative to the sensor block. */
	void accept(int dx, int dy, int dz, Face face);
}
