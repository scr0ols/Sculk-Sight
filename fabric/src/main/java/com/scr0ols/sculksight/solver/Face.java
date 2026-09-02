package com.scr0ols.sculksight.solver;

/**
 * The six axis-aligned directions, as this project's own type.
 *
 * <p>Deliberately not {@code net.minecraft.core.Direction}: layer 1 may not name a Minecraft
 * class (ARCHITECTURE.md section 2.1, ADR-018). Any mapping to {@code Direction} belongs in
 * layer 2 or layer 4.
 *
 * <p>Used for two unrelated things that happen to need the same six vectors: the nudge
 * directions of the six-ray occlusion rule ({@link OcclusionTest}) and the neighbour
 * directions of boundary-face extraction ({@link BoundaryFaceExtractor}).
 *
 * <p><b>The declaration order below carries no meaning and nothing may depend on it.</b> The
 * occlusion rule is a conjunction over all six, so its result is order-independent; order
 * decides only which ray happens to short-circuit first, which is a cost, not an outcome.
 */
public enum Face {
	DOWN(0, -1, 0),
	UP(0, 1, 0),
	NORTH(0, 0, -1),
	SOUTH(0, 0, 1),
	WEST(-1, 0, 0),
	EAST(1, 0, 0);

	/**
	 * Cached because {@code values()} allocates a fresh array on every call - it has to, since
	 * arrays are mutable and the JDK cannot hand out a shared one. Iterating this constant in
	 * the solver's inner loop avoids an allocation per position. It is private and never
	 * escapes, so nothing can modify it.
	 */
	private static final Face[] VALUES = values();

	private final int stepX;
	private final int stepY;
	private final int stepZ;

	Face(int stepX, int stepY, int stepZ) {
		this.stepX = stepX;
		this.stepY = stepY;
		this.stepZ = stepZ;
	}

	/** The six faces, without allocating. Callers must not modify the returned array. */
	static Face[] allWithoutCopy() {
		return VALUES;
	}

	public int stepX() {
		return stepX;
	}

	public int stepY() {
		return stepY;
	}

	public int stepZ() {
		return stepZ;
	}
}
