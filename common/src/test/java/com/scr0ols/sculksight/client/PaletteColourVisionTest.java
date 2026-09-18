package com.scr0ols.sculksight.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Closes the v0.2 colour-blind palette validation gap for the pair most likely to fail it: amber
 * ({@link DetectorType#NORMAL_SENSOR}, {@code #FFA300}) against shrieker red
 * ({@link DetectorType#SHRIEKER}, {@code #8B0025}).
 *
 * <p><b>The rendered context this test assesses, stated explicitly per the v0.2 obligation.</b>
 * It is not a comparison of two isolated, fully-opaque swatches - that check is included below
 * as a baseline, but on its own it would not answer the question a player actually faces. What a
 * player sees is two translucent shells (each shell's own composited opacity, {@code #FFA300} and
 * {@code #8B0025}, roughly 0.56 per {@link RenderPolicy}'s own note on one union shell across both
 * of {@code ShellStyle}'s two draw passes) laid over whatever terrain or sky sits behind them, and,
 * at mode B's scale (20+ sensors, {@code PER_SENSOR} render policy), sometimes laid over
 * <em>each other</em> where two sensors' shells overlap. This class checks all three renderings:
 * the raw palette swatches, each shell's own composited appearance against a bright and a dark
 * backdrop, and the doubly-composited overlap region in both possible draw orders (this project's
 * render order between two overlapping shells is not fixed, so both are checked).
 *
 * <p><b>Simulation method, named per the exit criterion's own requirement.</b> Colour vision
 * deficiency is simulated with the Machado, Oliveira &amp; Fernandes (2009) physiologically-based
 * model ("A Physiologically-based Model for Simulation of Color Vision Deficiency", IEEE TVCG
 * 15(6)), using that paper's own published severity-1.0 (full dichromacy) transformation matrices
 * for protanopia, deuteranopia and tritanopia. The matrices are applied to linearized RGB - the
 * scientifically corrected convention (the reference {@code colorspace} R package moved to this
 * from the paper's own gamma-space illustration in its 2.1-0 release) - via the standard sRGB
 * transfer function, not the gamma-encoded shortcut.
 *
 * <p><b>Distinguishability metric.</b> CIE 1976 colour difference, {@code deltaE76}, the Euclidean
 * distance between two colours in CIE L*a*b* (D65 white point) - a standard, widely documented
 * colour-difference metric. {@link #MIN_DISTINCT_DELTA_E} is set well below every value this test
 * actually computes for the real palette (the closest pair found across every scenario below and
 * every simulated deficiency was ~15.2), at the threshold commonly used to mean "reads as two
 * different colours to an average observer at a glance," so a future palette change has real room
 * to still pass while a genuine collision is still caught.
 *
 * <p><b>What this test does not cover.</b> It is a colour-space simulation, not a substitute for
 * a person with the relevant colour vision deficiency looking at the running game - the author's
 * own eyes on a real multi-shell scene are what actually closes this out; see the PR description
 * for what to look at in-game.
 */
class PaletteColourVisionTest {

	private static final int AMBER = DetectorType.NORMAL_SENSOR.colour();

	private static final int SHRIEKER_RED = DetectorType.SHRIEKER.colour();

	/**
	 * One union shell's own composited opacity across both of {@code ShellStyle}'s draw passes,
	 * as {@link RenderPolicy}'s own class javadoc states it (ADR-051) - reused here rather than
	 * re-derived, so this test does not carry a second, possibly divergent estimate of the same
	 * figure.
	 */
	private static final float COMPOSITED_SHELL_OPACITY = 0.56F;

	private static final float[] BLACK_BACKDROP = {0.0F, 0.0F, 0.0F};

	private static final float[] WHITE_BACKDROP = {1.0F, 1.0F, 1.0F};

	/**
	 * The CIE76 deltaE below which two colours start reading as variants of the same colour rather
	 * than as two different ones, for an average observer at a glance - well under every figure
	 * this test computes for the shipped palette, see the class javadoc.
	 */
	private static final double MIN_DISTINCT_DELTA_E = 10.0;

	/**
	 * Machado, Oliveira &amp; Fernandes (2009), Table 1, severity 1.0 (full dichromacy) -
	 * transformation matrices applied to linear RGB.
	 */
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

	/** Raw, fully-opaque palette swatches - the baseline check, not the whole answer. */
	@ParameterizedTest
	@EnumSource(Deficiency.class)
	void theRawPaletteSwatchesRemainDistinctUnderSimulation(Deficiency deficiency) {
		assertDistinctUnderSimulation(deficiency, "raw swatches",
				hexToRgb(AMBER), hexToRgb(SHRIEKER_RED));
	}

	/**
	 * Each detector's own shell as it is actually rendered - translucent, at
	 * {@link #COMPOSITED_SHELL_OPACITY} - against a dark backdrop (a cave or the Deep Dark) and a
	 * bright one (open sky), rather than the 100%-opaque swatch above.
	 */
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

	/**
	 * Mode B's newly-relevant scene: where a shrieker's shell and a normal sensor's shell overlap,
	 * the doubly-composited overlap region must still read as distinct from either shell drawn
	 * alone - checked in both possible draw orders, since this project has not fixed which of two
	 * overlapping shells composites on top (that is Batch 5 (T7)'s ordering question, not this
	 * one's), and over both backdrops.
	 */
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

			// The two overlap colours against each other: whichever shell actually ends up on top
			// at runtime, the region still needs to read differently from a lone shell of the
			// other colour standing next to it.
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

	// ---------------------------------------------------------------- colour maths

	private static float[] hexToRgb(int hex) {
		return new float[] {((hex >> 16) & 0xFF) / 255.0F, ((hex >> 8) & 0xFF) / 255.0F,
				(hex & 0xFF) / 255.0F};
	}

	/** Straight-alpha "over" compositing, done directly in sRGB to match a simple GPU blend. */
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

	/** sRGB (D65) to CIE L*a*b*, via CIE XYZ. */
	private static double[] toLab(float[] srgb) {
		double r = srgbToLinear(srgb[0]);
		double g = srgbToLinear(srgb[1]);
		double b = srgbToLinear(srgb[2]);

		double x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b;
		double y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b;
		double z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b;

		// D65 reference white.
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
