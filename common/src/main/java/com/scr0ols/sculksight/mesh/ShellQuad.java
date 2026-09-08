package com.scr0ols.sculksight.mesh;

import com.scr0ols.sculksight.solver.Face;

/**
 * The corner positions of one boundary face, in the sensor-relative frame.
 *
 * <p>Split out of {@code ShellMeshBuilder} so that the geometry - the part that can be wrong in a
 * way that puts the shell in the wrong place - is reachable from JUnit. The builder itself names
 * {@code BufferBuilder} and {@code MeshData} and therefore has to live in the client source set,
 * where a plain JVM test cannot follow it. Nothing here names a Minecraft class.
 *
 * <p><b>The frame.</b> Per ADR-014 the cached vertices are relative to the sensor's block
 * position, and a {@code BlockPos} names a block's minimum corner, so the block at offset
 * {@code (dx, dy, dz)} occupies the unit cube from {@code (dx, dy, dz)} to
 * {@code (dx+1, dy+1, dz+1)} in this frame. At radius 16 that puts every coordinate in
 * {@code [-16, +17]}, which is ARCHITECTURE.md section 3.4's "roughly -16 to +16".
 *
 * <p><b>The winding.</b> Corners are emitted counter-clockwise as seen from outside the set - the
 * side the face's {@link Face} points toward. The chosen pipeline family sets {@code cull = false}
 * (R15.4), so nothing currently depends on this and a reversed face would still be drawn; it is
 * done correctly anyway so that enabling culling later is a one-line change rather than an
 * investigation.
 */
public final class ShellQuad {

	/** Four corners, three floats each, in the order this class documents. */
	public static final int FLOATS = 12;

	private ShellQuad() {
	}

	/**
	 * Writes the four corners of the given boundary face into {@code out}, as
	 * {@code x0,y0,z0, x1,y1,z1, x2,y2,z2, x3,y3,z3}.
	 *
	 * @param out an array of at least {@link #FLOATS} elements, overwritten from index 0
	 */
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
