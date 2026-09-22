package com.scr0ols.sculksight.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.scr0ols.sculksight.solver.Face;

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

	@ParameterizedTest
	@EnumSource(Face.class)
	void theFaceIsFlatOnTheSideItPointsAt(Face face) {
		float[] corners = new float[ShellQuad.FLOATS];
		ShellQuad.corners(0, 0, 0, face, corners);

		int axis = face.stepX() != 0 ? 0 : face.stepY() != 0 ? 1 : 2;
		int step = face.stepX() + face.stepY() + face.stepZ();

		float expected = step > 0 ? 1.0F : 0.0F;

		for (int corner = 0; corner < 4; corner++) {
			assertEquals(expected, corners[corner * 3 + axis], 0.0F,
					face + " corner " + corner + " off the plane");
		}
	}

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

		for (int corner = 0; corner < 4; corner++) {
			assertEquals(1.0F, eastOfOrigin[corner * 3], 0.0F);
			assertEquals(1.0F, westOfNeighbour[corner * 3], 0.0F);
		}
	}
}
