package com.scr0ols.sculksight.mesh;

import com.scr0ols.sculksight.solver.Axis;

/**
 * The endpoints of one crease edge, in the sensor-relative frame, plus the unit direction the
 * {@code LINES} vertex format wants alongside them.
 *
 * <p>Split out of the encoder for the same reason {@link ShellQuad} was: the geometry is the part
 * that can be wrong in a way that puts a line in the wrong place, and it is worth being reachable
 * from a plain JVM test. Nothing here names a Minecraft class.
 *
 * <p><b>The frame is {@link ShellQuad}'s.</b> Per ADR-014 the cached vertices are relative to the
 * sensor's block position, and the block at offset {@code (dx, dy, dz)} occupies the unit cube from
 * {@code (dx, dy, dz)} to {@code (dx+1, dy+1, dz+1)}. A crease edge is named by one corner of that
 * lattice and runs one unit along its axis, so its endpoints need no adjustment to sit exactly on
 * the boundary faces they separate.
 *
 * <p><b>Why the direction is a separate output.</b> The {@code LINES} pipeline family binds
 * {@code DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH}, and its {@code Normal} attribute is
 * <b>not</b> a surface normal: {@code core/rendertype_lines.vsh} reads it as the offset to the
 * line's other endpoint, computing {@code Position + Normal} to derive the screen-space direction
 * it expands the line across (RESEARCH-LOG.md R15.7). The attribute's format is
 * {@code RGBA8_SNORM}, so its components are clamped to the range -1 to 1 and the value written has
 * to be the unit direction rather than the true offset. For a unit-length crease edge the two
 * coincide, which is why this class can supply one array for both purposes, but the distinction is
 * recorded because it stops being harmless the moment an edge is longer than one block.
 */
public final class ShellEdge {

	/** Two endpoints, three floats each, in the order this class documents. */
	public static final int FLOATS = 6;

	private ShellEdge() {
	}

	/**
	 * Writes the two endpoints of the given crease edge into {@code out}, as
	 * {@code x0,y0,z0, x1,y1,z1}.
	 *
	 * @param out an array of at least {@link #FLOATS} elements, overwritten from index 0
	 */
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
