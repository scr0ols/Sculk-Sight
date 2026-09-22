package com.scr0ols.sculksight.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PaletteColourVisionTest {

	private static final int AMBER = DetectorType.NORMAL_SENSOR.colour();

	private static final int SHRIEKER_RED = DetectorType.SHRIEKER.colour();

	private static final float COMPOSITED_SHELL_OPACITY = 0.56F;

	private static final float[] BLACK_BACKDROP = {0.0F, 0.0F, 0.0F};

	private static final float[] WHITE_BACKDROP = {1.0F, 1.0F, 1.0F};

	private static final double MIN_DISTINCT_DELTA_E = 10.0;

	private enum Deficiency {

		PROTANOPIA(new double[][] {
				{0.152286, 1.052583, -0.204868},
				{0.114503, 0.786281, 0.099216},
				{-0.003882, -0.048116, 1.051998}}),

		DEUTERANOPIA(new double[][] {
				{0.367322, 0.860646, -0.227968},
				{0.280085, 0.672501, 0.047413},
				{-0.011820, 0.042940, 0.968881}}),

		TRITANOPIA(new double[][] {
				{1.255528, -0.076749, -0.178779},
				{-0.078411, 0.930809, 0.147602},
				{0.004733, 0.691367, 0.303900}});

		private final double[][] matrix;

		Deficiency(double[][] matrix) {
			this.matrix = matrix;
		}
	}

	@ParameterizedTest
	@EnumSource(Deficiency.class)
	void theRawPaletteSwatchesRemainDistinctUnderSimulation(Deficiency deficiency) {
		assertDistinctUnderSimulation(deficiency, "raw swatches",
				hexToRgb(AMBER), hexToRgb(SHRIEKER_RED));
	}

	@ParameterizedTest
	@EnumSource(Deficiency.class)
	void eachShellsOwnRenderedAppearanceRemainsDistinctOverBothBackdrops(Deficiency deficiency) {
		assertDistinctUnderSimulation(deficiency, "composited over black",
				over(hexToRgb(AMBER), COMPOSITED_SHELL_OPACITY, BLACK_BACKDROP),
				over(hexToRgb(SHRIEKER_RED), COMPOSITED_SHELL_OPACITY, BLACK_BACKDROP));

		assertDistinctUnderSimulation(deficiency, "composited over white",
				over(hexToRgb(AMBER), COMPOSITED_SHELL_OPACITY, WHITE_BACKDROP),
				over(hexToRgb(SHRIEKER_RED), COMPOSITED_SHELL_OPACITY, WHITE_BACKDROP));
	}

	@ParameterizedTest
	@EnumSource(Deficiency.class)
	void theOverlapRegionRemainsDistinctFromEitherShellAloneOverBothBackdrops(Deficiency deficiency) {
		for (float[] backdrop : new float[][] {BLACK_BACKDROP, WHITE_BACKDROP}) {
			String backdropLabel = backdrop == BLACK_BACKDROP ? "black" : "white";

			float[] amberAlone = over(hexToRgb(AMBER), COMPOSITED_SHELL_OPACITY, backdrop);
			float[] shriekerAlone = over(hexToRgb(SHRIEKER_RED), COMPOSITED_SHELL_OPACITY, backdrop);

			float[] shriekerOnAmber = over(hexToRgb(SHRIEKER_RED), COMPOSITED_SHELL_OPACITY, amberAlone);
			float[] amberOnShrieker = over(hexToRgb(AMBER), COMPOSITED_SHELL_OPACITY, shriekerAlone);

			assertDistinctUnderSimulation(deficiency,
					"shrieker-on-amber vs. amber alone, over " + backdropLabel, shriekerOnAmber, amberAlone);
			assertDistinctUnderSimulation(deficiency,
					"amber-on-shrieker vs. shrieker alone, over " + backdropLabel, amberOnShrieker, shriekerAlone);

			assertDistinctUnderSimulation(deficiency,
					"shrieker-on-amber vs. amber-on-shrieker, over " + backdropLabel,
					shriekerOnAmber, amberOnShrieker);
		}
	}

	private static void assertDistinctUnderSimulation(Deficiency deficiency, String scenario,
			float[] first, float[] second) {
		float[] simFirst = simulate(deficiency, first);
		float[] simSecond = simulate(deficiency, second);
		double deltaE = deltaE76(toLab(simFirst), toLab(simSecond));

		assertTrue(deltaE >= MIN_DISTINCT_DELTA_E,
				"amber and shrieker must remain distinguishable under " + deficiency + " simulation ("
						+ scenario + "): deltaE76 was " + deltaE + ", wanted at least "
						+ MIN_DISTINCT_DELTA_E);
	}

	private static float[] hexToRgb(int hex) {
		return new float[] {((hex >> 16) & 0xFF) / 255.0F, ((hex >> 8) & 0xFF) / 255.0F,
				(hex & 0xFF) / 255.0F};
	}

	private static float[] over(float[] top, float alpha, float[] bottom) {
		return new float[] {
				top[0] * alpha + bottom[0] * (1 - alpha),
				top[1] * alpha + bottom[1] * (1 - alpha),
				top[2] * alpha + bottom[2] * (1 - alpha)};
	}

	private static float[] simulate(Deficiency deficiency, float[] srgb) {
		double[] linear = {srgbToLinear(srgb[0]), srgbToLinear(srgb[1]), srgbToLinear(srgb[2])};
		double[][] matrix = deficiency.matrix;

		double[] simulatedLinear = {
				matrix[0][0] * linear[0] + matrix[0][1] * linear[1] + matrix[0][2] * linear[2],
				matrix[1][0] * linear[0] + matrix[1][1] * linear[1] + matrix[1][2] * linear[2],
				matrix[2][0] * linear[0] + matrix[2][1] * linear[1] + matrix[2][2] * linear[2]};

		return new float[] {
				linearToSrgb(simulatedLinear[0]), linearToSrgb(simulatedLinear[1]),
				linearToSrgb(simulatedLinear[2])};
	}

	private static double srgbToLinear(float c) {
		return c <= 0.04045F ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
	}

	private static float linearToSrgb(double c) {
		double clamped = Math.max(0.0, Math.min(1.0, c));
		return (float) (clamped <= 0.0031308 ? clamped * 12.92 : 1.055 * Math.pow(clamped, 1 / 2.4) - 0.055);
	}

	private static double[] toLab(float[] srgb) {
		double r = srgbToLinear(srgb[0]);
		double g = srgbToLinear(srgb[1]);
		double b = srgbToLinear(srgb[2]);

		double x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b;
		double y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b;
		double z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b;

		double fx = labF(x / 0.95047);
		double fy = labF(y / 1.00000);
		double fz = labF(z / 1.08883);

		return new double[] {116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
	}

	private static double labF(double t) {
		return t > 0.008856 ? Math.cbrt(t) : (7.787 * t) + 16.0 / 116.0;
	}

	private static double deltaE76(double[] lab1, double[] lab2) {
		double dl = lab1[0] - lab2[0];
		double da = lab1[1] - lab2[1];
		double db = lab1[2] - lab2[2];

		return Math.sqrt(dl * dl + da * da + db * db);
	}
}
