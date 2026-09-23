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

class ShellStyleTest {

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

	@Test
	void theSliderMovesBothAlphasAndLeavesTheColourAlone() {
		ShellStyle configured =
				ShellStyle.fromConfig(new SculkSightConfig(60, SculkSightConfig.DEFAULT_RENDER_POLICY));

		assertEquals(0xFFA300, configured.colour());
		assertEquals(0.60F, configured.depthTestedAlpha(), 1.0E-6F);
		assertEquals(0.24F, configured.seeThroughAlpha(), 1.0E-6F);
		assertEquals(153, configured.encodedAlpha());
	}

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
		ShellStyle style = ShellStyle.fromConfig(
				new SculkSightConfig(60, SculkSightConfig.DEFAULT_RENDER_POLICY))
				.withColour(detector.colour());

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

	@ParameterizedTest
	@EnumSource(Face.class)
	void theV0ColourCarriesNoBlueOnAnyFace(Face face) {
		assertEquals(0, ShellStyle.v0().blue(face));
	}

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

	@Test
	void theSeeThroughModulationTakesTheEncodedAlphaToTheSeeThroughAlpha() {
		ShellStyle style = ShellStyle.v0();

		assertEquals(style.seeThroughAlpha(),
				style.depthTestedAlpha() * style.faceModulation(true, false), 1.0E-6F);
		assertEquals(style.depthTestedAlpha(),
				style.depthTestedAlpha() * style.faceModulation(false, false), 1.0E-6F);
	}

	@Test
	void insideTheShellEachPassCompositesToWhatTwoLayersGaveOutside() {
		ShellStyle style = ShellStyle.v0();

		assertEquals(0.4375F, style.depthTestedAlpha() * style.faceModulation(false, true), 1.0E-6F);
		assertEquals(outsideComposite(style.seeThroughAlpha()),
				style.depthTestedAlpha() * style.faceModulation(true, true), 1.0E-6F);
	}

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

	@Test
	void theLowestPermittedOpacityIsExactlyTheOneThisTypeCannotModulate() {
		ShellStyle off = ShellStyle.fromConfig(new SculkSightConfig(
				SculkSightConfig.MIN_SHELL_OPACITY_PERCENT, SculkSightConfig.DEFAULT_RENDER_POLICY));

		assertEquals(0, off.encodedAlpha());
		assertThrows(IllegalStateException.class, () -> off.faceModulation(false, false));
		assertThrows(IllegalStateException.class, () -> off.faceModulation(true, false));
		assertThrows(IllegalStateException.class, () -> off.faceModulation(false, true));
		assertThrows(IllegalStateException.class, () -> off.faceModulation(true, true));
	}

	@Test
	void everyPermittedSettingAboveZeroModulatesWithoutThrowing() {
		for (int percent = SculkSightConfig.MIN_SHELL_OPACITY_PERCENT + 1;
				percent <= SculkSightConfig.MAX_SHELL_OPACITY_PERCENT; percent++) {

			ShellStyle style = ShellStyle.fromConfig(
					new SculkSightConfig(percent, SculkSightConfig.DEFAULT_RENDER_POLICY));

			assertTrue(style.encodedAlpha() > 0, "encoded alpha was zero at " + percent + "%");
			assertTrue(style.faceModulation(true, true) > 0.0F, "no modulation at " + percent + "%");
		}

		assertEquals(3, ShellStyle.fromConfig(
				new SculkSightConfig(1, SculkSightConfig.DEFAULT_RENDER_POLICY)).encodedAlpha());
	}

	private static float outsideComposite(float alpha) {
		return 1.0F - (1.0F - alpha) * (1.0F - alpha);
	}
}
