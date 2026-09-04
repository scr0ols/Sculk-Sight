package com.scr0ols.sculksight.solver;

/**
 * The three coordinate axes, as this project's own type.
 *
 * <p>Deliberately not {@code net.minecraft.core.Direction.Axis}: layer 1 may not name a Minecraft
 * class (ARCHITECTURE.md section 2.1, ADR-018). Any mapping to a Minecraft type belongs in layer 2
 * or layer 4.
 *
 * <p>{@link Face} could not serve here and the difference is worth stating. A face is signed: it
 * names one of the six directions a boundary quad can point. A crease edge has no side, only an
 * orientation, so the six faces would name each edge twice. {@link CreaseEdgeExtractor} emits one
 * edge per lattice segment and needs a type with three members, not six.
 */
public enum Axis {
	X(1, 0, 0),
	Y(0, 1, 0),
	Z(0, 0, 1);

	/**
	 * Cached because {@code values()} allocates a fresh array on every call. The extractor iterates
	 * this once per axis rather than per position, so the saving is small, but the reason to keep a
	 * private copy is the same one {@link Face} gives: arrays are mutable and a shared public one
	 * would be a mutable static.
	 */
	private static final Axis[] VALUES = values();

	private final int stepX;
	private final int stepY;
	private final int stepZ;

	Axis(int stepX, int stepY, int stepZ) {
		this.stepX = stepX;
		this.stepY = stepY;
		this.stepZ = stepZ;
	}

	/** The three axes, without allocating. Callers must not modify the returned array. */
	static Axis[] allWithoutCopy() {
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
