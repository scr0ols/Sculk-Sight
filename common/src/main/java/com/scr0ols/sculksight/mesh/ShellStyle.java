package com.scr0ols.sculksight.mesh;

import com.scr0ols.sculksight.config.SculkSightConfig;
import com.scr0ols.sculksight.solver.Face;

/**
 * How the shell looks: a {@code 0xRRGGBB} colour, two alphas in 0..1, a per-{@link Face} colour
 * multiplier, and the factor the see-through pass is scaled by while the camera is inside it.
 *
 * <p>The see-through pass itself is permanently disabled - see {@link #skipsSeeThroughPass()} -
 * because the shell must never render anything through geometry that blocks direct line of
 * sight. {@code seeThroughAlpha}, {@code insideSeeThroughFactor} and {@link #faceModulation} are
 * kept so the alpha arithmetic they support stays intact and tested, but the renderer never
 * issues a draw call that uses the value they compute for the see-through case.
 */
public record ShellStyle(int colour, float depthTestedAlpha, float seeThroughAlpha, float[] shadeByFace,
		float insideSeeThroughFactor) {

	/** The inside factor an authored or default-config style applies to the see-through pass: none. */
	private static final float DEFAULT_INSIDE_SEE_THROUGH_FACTOR = 1.0F;

	/** The authored style: amber {@code #FFA300}, alpha 0.25 depth-tested and 0.10 see-through. */
	public static ShellStyle v0() {
		float[] shade = new float[Face.values().length];

		shade[Face.UP.ordinal()] = 1.00F;
		shade[Face.NORTH.ordinal()] = 0.86F;
		shade[Face.SOUTH.ordinal()] = 0.86F;
		shade[Face.EAST.ordinal()] = 0.78F;
		shade[Face.WEST.ordinal()] = 0.78F;
		shade[Face.DOWN.ordinal()] = 0.66F;

		return new ShellStyle(0xFFA300, 0.25F, 0.10F, shade, DEFAULT_INSIDE_SEE_THROUGH_FACTOR);
	}

	/**
	 * The authored style with both alphas taken from the player's opacity setting, and the
	 * see-through-while-inside factor taken from {@link SculkSightConfig#seeThroughInsideMode()}.
	 */
	public static ShellStyle fromConfig(SculkSightConfig config) {
		ShellStyle authored = v0();

		return new ShellStyle(authored.colour(), config.depthTestedAlpha(), config.seeThroughAlpha(),
				authored.shadeByFace(), config.seeThroughInsideMode().insideFactor());
	}

	/** Applies a detector palette colour without changing alpha, face shading or the inside factor. */
	public ShellStyle withColour(int newColour) {
		return new ShellStyle(newColour, depthTestedAlpha, seeThroughAlpha, shadeByFace.clone(),
				insideSeeThroughFactor);
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
	 *
	 * <p>The see-through pass is further scaled by {@link #insideSeeThroughFactor} while the camera
	 * is inside the shell. That pass draws only the half of the shell that is occluded by the
	 * world (the complement of the depth-tested pass's test), but from inside the shell that
	 * occluded half is most of what is on screen - nearly every block between the camera and the
	 * shell's far boundary hides it. The factor never touches the outside case or the
	 * depth-tested pass.
	 */
	public float faceModulation(boolean seeThrough, boolean cameraInside) {
		float targetAlpha = seeThrough ? seeThroughAlpha : depthTestedAlpha;
		if (seeThrough && cameraInside) {
			targetAlpha *= insideSeeThroughFactor;
		}
		return modulation(targetAlpha, depthTestedAlpha, cameraInside);
	}

	/**
	 * Whether the see-through pass can be skipped outright.
	 *
	 * <p>Always {@code true}: the shell must never render anything through geometry that blocks
	 * direct line of sight, so the see-through pass is permanently disabled and only the
	 * depth-tested pass ever draws. This is a deliberate design decision, not a mode-dependent
	 * optimisation - unlike {@link #faceModulation}, which still computes a see-through alpha that
	 * the renderer never uses, this method has no remaining dependency on camera position or
	 * {@link #insideSeeThroughFactor}. Letting the caller skip the draw call entirely, rather than
	 * issuing it with a modulation of zero, avoids paying its GPU cost for a pass that never
	 * contributes anything to the frame.
	 */
	public boolean skipsSeeThroughPass() {
		return true;
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
