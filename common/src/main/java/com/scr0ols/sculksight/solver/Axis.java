package com.scr0ols.sculksight.solver;

/** The three coordinate axes, as this project's own type. */
public enum Axis {
	X(1, 0, 0),
	Y(0, 1, 0),
	Z(0, 0, 1);

	private static final Axis[] VALUES = values();

	private final int stepX;
	private final int stepY;
	private final int stepZ;

	Axis(int stepX, int stepY, int stepZ) {
		this.stepX = stepX;
		this.stepY = stepY;
		this.stepZ = stepZ;
	}

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
