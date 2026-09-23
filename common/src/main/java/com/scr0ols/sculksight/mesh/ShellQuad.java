package com.scr0ols.sculksight.mesh;

import com.scr0ols.sculksight.solver.Face;

/** The corner positions of one boundary face, in the sensor-relative frame. */
public final class ShellQuad {

	/** Four corners, three floats each. */
	public static final int FLOATS = 12;

	private ShellQuad() {
	}

	/** Writes the boundary face's four corners into {@code out}, counter-clockwise seen from outside. */
	public static void corners(int dx, int dy, int dz, Face face, float[] out) {
		if (out.length < FLOATS) {
			throw new IllegalArgumentException("out must hold at least " + FLOATS + " floats");
		}

		float x0 = dx;
		float y0 = dy;
		float z0 = dz;
		float x1 = dx + 1.0F;
		float y1 = dy + 1.0F;
		float z1 = dz + 1.0F;

		switch (face) {
			case DOWN -> write(out, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
			case UP -> write(out, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
			case NORTH -> write(out, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
			case SOUTH -> write(out, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
			case WEST -> write(out, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
			case EAST -> write(out, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
		}
	}

	private static void write(float[] out,
			float ax, float ay, float az,
			float bx, float by, float bz,
			float cx, float cy, float cz,
			float ex, float ey, float ez) {

		out[0] = ax;
		out[1] = ay;
		out[2] = az;
		out[3] = bx;
		out[4] = by;
		out[5] = bz;
		out[6] = cx;
		out[7] = cy;
		out[8] = cz;
		out[9] = ex;
		out[10] = ey;
		out[11] = ez;
	}
}
