package com.scr0ols.sculksight.mesh;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.scr0ols.sculksight.solver.Axis;

/** Tests for the crease-edge geometry of ADR-028. */
class ShellEdgeTest {

	@Test
	void anEdgeRunsOneUnitAlongItsAxisFromTheGivenLatticePoint() {
		float[] out = new float[ShellEdge.FLOATS];

		ShellEdge.endpoints(3, -4, 5, Axis.X, out);
		assertArrayEquals(new float[] {3, -4, 5, 4, -4, 5}, out);

		ShellEdge.endpoints(3, -4, 5, Axis.Y, out);
		assertArrayEquals(new float[] {3, -4, 5, 3, -3, 5}, out);

		ShellEdge.endpoints(3, -4, 5, Axis.Z, out);
		assertArrayEquals(new float[] {3, -4, 5, 3, -4, 6}, out);
	}

	/**
	 * The direction the lines format wants is the unit axis vector, and it has to be unit length
	 * because the {@code Normal} attribute is {@code RGBA8_SNORM} and clamps outside -1 to 1
	 * (R15.7). For a one-unit edge the offset between the endpoints is that vector, which is what
	 * lets the encoder pass the axis steps straight through.
	 */
	@ParameterizedTest
	@EnumSource(Axis.class)
	void theOffsetBetweenTheEndpointsIsTheUnitAxisVector(Axis axis) {
		float[] out = new float[ShellEdge.FLOATS];
		ShellEdge.endpoints(0, 0, 0, axis, out);

		assertEquals(axis.stepX(), out[3] - out[0]);
		assertEquals(axis.stepY(), out[4] - out[1]);
		assertEquals(axis.stepZ(), out[5] - out[2]);

		float lengthSquared = (out[3] - out[0]) * (out[3] - out[0])
				+ (out[4] - out[1]) * (out[4] - out[1])
				+ (out[5] - out[2]) * (out[5] - out[2]);

		assertEquals(1.0F, lengthSquared);
	}

	@Test
	void anUndersizedOutputArrayIsRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> ShellEdge.endpoints(0, 0, 0, Axis.X, new float[ShellEdge.FLOATS - 1]));
	}
}
