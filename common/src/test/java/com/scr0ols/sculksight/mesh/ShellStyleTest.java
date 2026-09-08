package com.scr0ols.sculksight.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.scr0ols.sculksight.config.SculkSightConfig;
import com.scr0ols.sculksight.client.DetectorType;
import com.scr0ols.sculksight.solver.Face;

/** Tests for the v0.0 style constants of ADR-022, ADR-023 and ADR-029. */
class ShellStyleTest {

	/**
	 * The v0.1 config screen must not move the shell by existing: at the default slider position
	 * the configured style is the authored one, component for component.
	 */
	@Test
	void atTheDefaultSettingTheConfiguredStyleIsTheAuthoredOne() {
		ShellStyle authored = ShellStyle.v0();
		ShellStyle configured = ShellStyle.fromConfig(SculkSightConfig.defaults());

		assertEquals(authored.colour(), configured.colour());
		assertEquals(authored.depthTestedAlpha(), configured.depthTestedAlpha(), 1.0E-6F);
		assertEquals(authored.seeThroughAlpha(), configured.seeThroughAlpha(), 1.0E-6F);

		for (Face face : Face.values()) {
			assertEquals(authored.red(face), configured.red(face));
			assertEquals(authored.green(face), configured.green(face));
			assertEquals(authored.blue(face), configured.blue(face));
		}
	}

	/** The slider moves the alphas and nothing else - ADR-023's colour is not a v0.1 setting. */
	@Test
	void theSliderMovesBothAlphasAndLeavesTheColourAlone() {
		ShellStyle configured = ShellStyle.fromConfig(new SculkSightConfig(60));

		assertEquals(0xFFA300, configured.colour());
		assertEquals(0.60F, configured.depthTestedAlpha(), 1.0E-6F);
		assertEquals(0.24F, configured.seeThroughAlpha(), 1.0E-6F);
		assertEquals(153, configured.encodedAlpha());
	}

	/** A configured style is still a fresh array, not a view onto the authored one. */
	@Test
	void aConfiguredStyleDoesNotShareItsShadingArrayWithTheAuthoredOne() {
		ShellStyle configured = ShellStyle.fromConfig(SculkSightConfig.defaults());

		configured.shadeByFace()[Face.UP.ordinal()] = 0.0F;

		assertEquals(1.00F, ShellStyle.v0().shadeByFace()[Face.UP.ordinal()], 1.0E-6F);
	}

	@Test
	void theV0ColourIsTheAmberAdr023Chose() {
		ShellStyle style = ShellStyle.v0();

		assertEquals(0xFFA300, style.colour());
		assertEquals(0xFF, style.red(Face.UP));
		assertEquals(0xA3, style.green(Face.UP));
		assertEquals(0x00, style.blue(Face.UP));
	}

	@ParameterizedTest
	@EnumSource(DetectorType.class)
	void detectorPaletteUsesTheApprovedColourAndConfiguredAlpha(DetectorType detector) {
		ShellStyle style = ShellStyle.fromConfig(new SculkSightConfig(60)).withColour(detector.colour());

		assertEquals(detector.colour(), style.colour());
		assertEquals(0.60F, style.depthTestedAlpha(), 1.0E-6F);
		assertEquals(0.24F, style.seeThroughAlpha(), 1.0E-6F);
	}

	@Test
	void detectorPalettePreservesFaceShading() {
		ShellStyle amber = ShellStyle.v0();
		ShellStyle calibrated = ShellStyle.fromConfig(SculkSightConfig.defaults())
				.withColour(DetectorType.CALIBRATED_SENSOR.colour());

		assertEquals(0x99CCFF, calibrated.colour());
		for (Face face : Face.values()) {
			assertEquals(amber.shadeByFace()[face.ordinal()], calibrated.shadeByFace()[face.ordinal()],
					1.0E-6F);
		}
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

	/**
	 * OPEN-QUESTIONS.md section 23's finding, pinned as a property rather than left as a sentence:
	 * the lowest setting the config layer permits produces a style this type cannot modulate.
	 *
	 * <p>This is not a defect being tolerated. ADR-022's scheme reaches every alpha but the encoded
	 * one by dividing by it, so at an encoded zero there is nothing to divide and no factor that
	 * would help; the decision (ADR-022's 2026-09-07 addendum) is that the renderer does not reach
	 * here at all when the fill is off. What this test holds is the half of that decision that
	 * lives in this module: that zero really is the throwing case, so that a future change which
	 * quietly made {@code modulation} return something for it - and therefore made the renderer's
	 * guard look redundant - fails the build instead.
	 *
	 * <p><b>What it does not cover, stated plainly.</b> It does not show that
	 * {@code ShellRenderer.onRender} actually returns before {@code draw} at this setting, because
	 * no test in this module can: {@code ShellRenderer} lives in {@code common/src/client/java},
	 * which this module's build does not compile (ADR-044, the same wall
	 * {@code ShellRendererStyleCaptureTest} documents), and neither loader module has a test source
	 * set. That half is held by the three-module compile and, ultimately, by a live client, where
	 * nothing here has yet been seen at any opacity.
	 *
	 * <p><b>And no source-text guard was added for it</b>, deliberately, which is the one place
	 * this diverges from section 22.1's precedent. That guard exists because a data race is
	 * invisible by nature - a passing suite would never have caught it and a reader checking by eye
	 * is the only other instrument. This defect is the opposite: it throws on every frame the
	 * shell is up, so removing the renderer's guard is caught by the first person who runs the
	 * client at zero. A second text-matching test would add a maintenance burden against a
	 * regression that cannot hide.
	 */
	@Test
	void theLowestPermittedOpacityIsExactlyTheOneThisTypeCannotModulate() {
		ShellStyle off = ShellStyle.fromConfig(
				new SculkSightConfig(SculkSightConfig.MIN_SHELL_OPACITY_PERCENT));

		assertEquals(0, off.encodedAlpha());
		assertThrows(IllegalStateException.class, () -> off.faceModulation(false, false));
		assertThrows(IllegalStateException.class, () -> off.faceModulation(true, false));
		assertThrows(IllegalStateException.class, () -> off.faceModulation(false, true));
		assertThrows(IllegalStateException.class, () -> off.faceModulation(true, true));
	}

	/**
	 * The renderer's guard tests the encoded alpha; the throwing guard tests the float it was
	 * rounded from. This pins that the two agree over every permitted setting, so that "the fill is
	 * off" and "the modulation would throw" cannot come apart on some percentage in the middle.
	 *
	 * <p>The interesting end is 1, where the alpha is 0.01 and the encoded channel is
	 * {@code Math.round(0.01 * 255)}, which is 3 rather than 0 - the rounding does not swallow the
	 * lowest visible setting.
	 */
	@Test
	void everyPermittedSettingAboveZeroModulatesWithoutThrowing() {
		for (int percent = SculkSightConfig.MIN_SHELL_OPACITY_PERCENT + 1;
				percent <= SculkSightConfig.MAX_SHELL_OPACITY_PERCENT; percent++) {

			ShellStyle style = ShellStyle.fromConfig(new SculkSightConfig(percent));

			assertTrue(style.encodedAlpha() > 0, "encoded alpha was zero at " + percent + "%");
			assertTrue(style.faceModulation(true, true) > 0.0F, "no modulation at " + percent + "%");
		}

		assertEquals(3, ShellStyle.fromConfig(new SculkSightConfig(1)).encodedAlpha());
	}

	private static float outsideComposite(float alpha) {
		return 1.0F - (1.0F - alpha) * (1.0F - alpha);
	}
}
