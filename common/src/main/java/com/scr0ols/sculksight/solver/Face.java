package com.scr0ols.sculksight.solver;

/** The six axis-aligned directions, as this project's own type. */
public enum Face {
	DOWN(0, -1, 0),
	UP(0, 1, 0),
	NORTH(0, 0, -1),
	SOUTH(0, 0, 1),
	WEST(-1, 0, 0),
	EAST(1, 0, 0);

	private static final Face[] VALUES = values();

	private final int stepX;
	private final int stepY;
	private final int stepZ;

	Face(int stepX, int stepY, int stepZ) {
		this.stepX = stepX;
		this.stepY = stepY;
		this.stepZ = stepZ;
	}

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
