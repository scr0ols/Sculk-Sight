package com.scr0ols.sculksight.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.scr0ols.sculksight.solver.Face;

/**
 * Tests for the shell's quad geometry.
 *
 * <p>This is what {@code ShellMeshBuilder} could not be tested for directly: the builder names
 * {@code BufferBuilder} and {@code MeshData} and therefore lives in the client source set, out of
 * reach of a plain JVM test. Splitting the corner computation out into {@link ShellQuad} is what
 * puts the part that can put the shell in the wrong place under test.
 *
 * <p>Per TESTING-STRATEGY.md section 1, none of this validates the game - it validates that the
 * mesh is the geometric surface of the set the solver produced. The claim that the set matches
 * vanilla is differential verification's, not JUnit's.
 */
class ShellQuadTest {

	@ParameterizedTest
	@EnumSource(Face.class)
	void everyCornerLiesOnTheBlockCube(Face face) {
		float[] corners = new float[ShellQuad.FLOATS];
		ShellQuad.corners(3, -4, 5, face, corners);

		for (int corner = 0; corner < 4; corner++) {
			float x = corners[corner * 3];
			float y = corners[corner * 3 + 1];
			float z = corners[corner * 3 + 2];

			assertTrue(x == 3.0F || x == 4.0F, face + " corner " + corner + " x was " + x);
			assertTrue(y == -4.0F || y == -3.0F, face + " corner " + corner + " y was " + y);
			assertTrue(z == 5.0F || z == 6.0F, face + " corner " + corner + " z was " + z);
		}
	}

	/**
	 * The face lies on the side of the cube its {@link Face} points at, and is flat.
	 *
	 * <p>This is the assertion that catches the whole family of sign errors - a face drawn on the
	 * opposite side of its own block is one block out of place, which is exactly the kind of error
	 * that still produces a plausible-looking shell.
	 */
	@ParameterizedTest
	@EnumSource(Face.class)
	void theFaceIsFlatOnTheSideItPointsAt(Face face) {
		float[] corners = new float[ShellQuad.FLOATS];
		ShellQuad.corners(0, 0, 0, face, corners);

		int axis = face.stepX() != 0 ? 0 : face.stepY() != 0 ? 1 : 2;
		int step = face.stepX() + face.stepY() + face.stepZ();

		// step is -1 for the low side of the cube and +1 for the high side, so the constant
		// coordinate is 0 or 1 respectively - which for a block at the origin is the near or far
		// face on that axis.
		float expected = step > 0 ? 1.0F : 0.0F;

		for (int corner = 0; corner < 4; corner++) {
			assertEquals(expected, corners[corner * 3 + axis], 0.0F,
					face + " corner " + corner + " off the plane");
		}
	}

	/**
	 * Corners wind counter-clockwise as seen from outside, so the geometric normal agrees with the
	 * {@link Face}.
	 *
	 * <p>Nothing depends on this today - the chosen pipeline family sets {@code cull = false}
	 * (R15.4), so a reversed face is still drawn. It is asserted so that turning culling on later
	 * is a one-line change rather than an investigation into why half the shell vanished.
	 */
	@ParameterizedTest
	@EnumSource(Face.class)
	void theWindingAgreesWithTheFaceNormal(Face face) {
		float[] c = new float[ShellQuad.FLOATS];
		ShellQuad.corners(0, 0, 0, face, c);

		float ux = c[3] - c[0];
		float uy = c[4] - c[1];
		float uz = c[5] - c[2];
		float vx = c[6] - c[3];
		float vy = c[7] - c[4];
		float vz = c[8] - c[5];

		assertEquals(face.stepX(), uy * vz - uz * vy, 0.0F, face + " normal x");
		assertEquals(face.stepY(), uz * vx - ux * vz, 0.0F, face + " normal y");
		assertEquals(face.stepZ(), ux * vy - uy * vx, 0.0F, face + " normal z");
	}

	@Test
	void oppositeFacesOfNeighbouringBlocksCoincide() {
		float[] eastOfOrigin = new float[ShellQuad.FLOATS];
		float[] westOfNeighbour = new float[ShellQuad.FLOATS];

		ShellQuad.corners(0, 0, 0, Face.EAST, eastOfOrigin);
		ShellQuad.corners(1, 0, 0, Face.WEST, westOfNeighbour);

		// Both quads must sit on the plane x = 1: the shared boundary between the two blocks. A
		// frame that put a block's cube from d-0.5 to d+0.5, or from d-1 to d, would still pass
		// the flatness test above and fail here.
		for (int corner = 0; corner < 4; corner++) {
			assertEquals(1.0F, eastOfOrigin[corner * 3], 0.0F);
			assertEquals(1.0F, westOfNeighbour[corner * 3], 0.0F);
		}
	}
}
