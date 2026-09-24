package com.scr0ols.sculksight.mesh;

import com.scr0ols.sculksight.config.SculkSightConfig;
import com.scr0ols.sculksight.solver.Face;

/** How the shell looks: a {@code 0xRRGGBB} colour, two alphas in 0..1 and a per-{@link Face} colour multiplier. */
public record ShellStyle(int colour, float depthTestedAlpha, float seeThroughAlpha, float[] shadeByFace) {

	/** The authored style: amber {@code #FFA300}, alpha 0.25 depth-tested and 0.10 see-through. */
	public static ShellStyle v0() {
		float[] shade = new float[Face.values().length];

		shade[Face.UP.ordinal()] = 1.00F;
		shade[Face.NORTH.ordinal()] = 0.86F;
		shade[Face.SOUTH.ordinal()] = 0.86F;
		shade[Face.EAST.ordinal()] = 0.78F;
		shade[Face.WEST.ordinal()] = 0.78F;
		shade[Face.DOWN.ordinal()] = 0.66F;

		return new ShellStyle(0xFFA300, 0.25F, 0.10F, shade);
	}

	/** The authored style with both alphas taken from the player's opacity setting. */
	public static ShellStyle fromConfig(SculkSightConfig config) {
		ShellStyle authored = v0();

		return new ShellStyle(authored.colour(), config.depthTestedAlpha(), config.seeThroughAlpha(),
				authored.shadeByFace());
	}

	/** Applies a detector palette colour without changing alpha or face shading. */
	public ShellStyle withColour(int newColour) {
		return new ShellStyle(newColour, depthTestedAlpha, seeThroughAlpha, shadeByFace.clone());
	}

	public ShellStyle {
		if (shadeByFace.length != Face.values().length) {
			throw new IllegalArgumentException(
					"shadeByFace must have one entry per Face, got " + shadeByFace.length);
		}
	}

	/** The shaded red channel for the given face, 0..255. */
	public int red(Face face) {
		return shade(colour >> 16 & 0xFF, face);
	}

	/** The shaded green channel for the given face, 0..255. */
	public int green(Face face) {
		return shade(colour >> 8 & 0xFF, face);
	}

	/** The shaded blue channel for the given face, 0..255. */
	public int blue(Face face) {
		return shade(colour & 0xFF, face);
	}

	/** The depth-tested alpha the encoder writes into every face vertex, 0..255. */
	public int encodedAlpha() {
		return Alphas.toChannel(depthTestedAlpha);
	}

	/**
	 * The factor a face pass multiplies the encoded alpha by, which requires a positive encoded
	 * alpha.
	 *
	 * <p>A camera inside the shell sees a single face along any given ray, so that face is encoded
	 * at exactly the target alpha. A camera outside the shell sees two faces stacked along the ray
	 * (near and far), both blended with the standard "over" operator, so each face is encoded
	 * dimmer than the target - just enough that the pair composites back up to it, rather than past
	 * it. This keeps the perceived strength the same on both sides of the shell's boundary.
	 */
	public float faceModulation(boolean seeThrough, boolean cameraInside) {
		return modulation(seeThrough ? seeThroughAlpha : depthTestedAlpha, depthTestedAlpha, cameraInside);
	}

	private static float modulation(float targetAlpha, float encodedAlpha, boolean cameraInside) {
		if (encodedAlpha <= 0.0F) {
			throw new IllegalStateException("the encoded alpha must be positive to modulate from");
		}

		float wanted = cameraInside ? targetAlpha : Alphas.singleLayerAlphaFor(targetAlpha);
		return wanted / encodedAlpha;
	}

	private int shade(int channel, Face face) {
		return Alphas.clampChannel(Math.round(channel * shadeByFace[face.ordinal()]));
	}
}
