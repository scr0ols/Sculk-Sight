package com.scr0ols.sculksight.mesh;

import com.scr0ols.sculksight.solver.Axis;

/** The endpoints of one crease edge in the sensor-relative frame, plus the unit direction the {@code LINES} vertex format requires. */
public final class ShellEdge {

	/** Two endpoints, three floats each, in the order this class documents. */
	public static final int FLOATS = 6;

	private ShellEdge() {
	}

	/** Writes the crease edge's two endpoints into {@code out} as {@code x0,y0,z0, x1,y1,z1}. */
	public static void endpoints(int x, int y, int z, Axis axis, float[] out) {
		if (out.length < FLOATS) {
			throw new IllegalArgumentException("out must hold at least " + FLOATS + " floats");
		}

		out[0] = x;
		out[1] = y;
		out[2] = z;
		out[3] = x + axis.stepX();
		out[4] = y + axis.stepY();
		out[5] = z + axis.stepZ();
	}
}
