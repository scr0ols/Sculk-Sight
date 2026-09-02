package com.scr0ols.sculksight.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.scr0ols.sculksight.solver.Face;

/** Tests for the v0.0 style constants of ADR-022, ADR-023 and ADR-029. */
class ShellStyleTest {

	@Test
	void theV0ColourIsTheAmberAdr023Chose() {
		ShellStyle style = ShellStyle.v0();

		assertEquals(0xFFA300, style.colour());
		assertEquals(0xFF, style.red(Face.UP));
		assertEquals(0xA3, style.green(Face.UP));
		assertEquals(0x00, style.blue(Face.UP));
	}

	/**
	 * ADR-023's 2026-09-02 amendment: the shell colour has no blue component on any face.
	 *
	 * <p>This is the whole of that amendment and it is asserted rather than left to the constant,
	 * because the reason it is right is not visible at the constant. Measured against a third-party
	 * sphere overlay in the same scene, the two shells composited to the same opacity; what made
	 * ours read as pale tan and theirs as gold was that ours carried blue into a blue-green
	 * background. A future palette entry that reintroduces blue here would undo that silently.
	 *
	 * <p>Every face is checked rather than only UP, since the shading multiplies each channel by a
	 * different factor and zero is the one value that survives all of them.
	 */
	@ParameterizedTest
	@EnumSource(Face.class)
	void theV0ColourCarriesNoBlueOnAnyFace(Face face) {
		assertEquals(0, ShellStyle.v0().blue(face));
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
				style.depthTestedAlpha() * style.faceModulation(true, false), 1.0E-6F);
		assertEquals(style.depthTestedAlpha(),
				style.depthTestedAlpha() * style.faceModulation(false, false), 1.0E-6F);
	}

	/**
	 * ADR-029: inside the shell a ray crosses one translucent layer rather than two, so each pass
	 * is corrected to the composite two layers would have produced.
	 *
	 * <p>Asserted as the composite rather than as the factor, because the composite is what the
	 * decision is about and the factor is only how it is reached. At 0.25 the outside composite is
	 * 1 - 0.75 * 0.75, which is 0.4375.
	 */
	@Test
	void insideTheShellEachPassCompositesToWhatTwoLayersGaveOutside() {
		ShellStyle style = ShellStyle.v0();

		assertEquals(0.4375F, style.depthTestedAlpha() * style.faceModulation(false, true), 1.0E-6F);
		assertEquals(outsideComposite(style.seeThroughAlpha()),
				style.depthTestedAlpha() * style.faceModulation(true, true), 1.0E-6F);
	}

	/** The correction only ever raises opacity, never lowers it. */
	@ParameterizedTest
	@EnumSource(Face.class)
	void theInsideCorrectionNeverDarkensAPass(Face face) {
		ShellStyle style = ShellStyle.v0();

		assertTrue(style.faceModulation(false, true) >= style.faceModulation(false, false));
		assertTrue(style.faceModulation(true, true) >= style.faceModulation(true, false));
		assertTrue(style.red(face) >= 0);
	}

	@Test
	void aShadeArrayOfTheWrongLengthIsRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> new ShellStyle(0xFFFFFF, 0.25F, 0.10F, new float[] {1.0F}));
	}

	private static float outsideComposite(float alpha) {
		return 1.0F - (1.0F - alpha) * (1.0F - alpha);
	}
}
