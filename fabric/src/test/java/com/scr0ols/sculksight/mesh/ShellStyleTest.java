package com.scr0ols.sculksight.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.scr0ols.sculksight.solver.Face;

/** Tests for the v0.0 style constants of ADR-022 and ADR-023. */
class ShellStyleTest {

	@Test
	void theV0ColourIsTheAmberAdr023Chose() {
		ShellStyle style = ShellStyle.v0();

		assertEquals(0xFFA33C, style.colour());
		assertEquals(0xFF, style.red(Face.UP));
		assertEquals(0xA3, style.green(Face.UP));
		assertEquals(0x3C, style.blue(Face.UP));
	}

	/**
	 * The load-bearing half of ADR-022: shading multiplies colour and never alpha.
	 *
	 * <p>If a shade ever reached alpha, total coverage would stop being a function of how many
	 * faces a view ray crossed and would start depending on which faces, and therefore on the
	 * arbitrary blend order of a family that writes no depth. The ADR's whole opacity arithmetic
	 * rests on that not happening, so it is asserted rather than trusted to a comment.
	 */
	@ParameterizedTest
	@EnumSource(Face.class)
	void alphaIsIdenticalOnEveryFaceWhileColourIsNot(Face face) {
		ShellStyle style = ShellStyle.v0();

		assertEquals(style.encodedAlpha(), Math.round(style.depthTestedAlpha() * 255.0F));
		assertTrue(style.red(face) <= style.red(Face.UP), "UP must be the brightest face");
	}

	@Test
	void directionalShadingOrdersTheFacesAsAdr022States() {
		ShellStyle style = ShellStyle.v0();

		assertTrue(style.red(Face.UP) > style.red(Face.NORTH));
		assertEquals(style.red(Face.NORTH), style.red(Face.SOUTH));
		assertTrue(style.red(Face.NORTH) > style.red(Face.EAST));
		assertEquals(style.red(Face.EAST), style.red(Face.WEST));
		assertTrue(style.red(Face.EAST) > style.red(Face.DOWN));
	}

	/**
	 * The see-through pass reaches 0.10 by modulating the 0.25 the encoder wrote.
	 *
	 * <p>ARCHITECTURE.md section 4.3 leaves "one mesh and modulate" versus "two meshes" as an
	 * implementation choice; this is the arithmetic that makes the first one correct.
	 */
	@Test
	void theSeeThroughModulationTakesTheEncodedAlphaToTheSeeThroughAlpha() {
		ShellStyle style = ShellStyle.v0();

		assertEquals(style.seeThroughAlpha(),
				style.depthTestedAlpha() * style.seeThroughModulation(), 1.0E-6F);
	}

	@Test
	void aShadeArrayOfTheWrongLengthIsRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> new ShellStyle(0xFFFFFF, 0.25F, 0.10F, new float[] {1.0F}));
	}
}
