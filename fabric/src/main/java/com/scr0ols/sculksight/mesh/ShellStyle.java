package com.scr0ols.sculksight.mesh;

import com.scr0ols.sculksight.solver.Face;

/**
 * How the shell looks. Fixed for v0.0 by c-docs/DECISIONS.md ADR-022 and ADR-023, and specified
 * in ARCHITECTURE.md section 4.3.
 *
 * <p>Colour is {@code 0xRRGGBB}; alphas are 0..1; {@code shadeByFace} is a per-{@link Face}
 * colour multiplier indexed by {@link Face#ordinal()}, <b>never applied to alpha</b>.
 *
 * <p><b>Why alpha is uniform across faces, stated here because it is load-bearing rather than
 * incidental.</b> The chosen pipeline family does not cull back faces (R15.4), so a view ray
 * through a closed shell crosses at least two translucent layers and the composited opacity is
 * roughly double the nominal figure. There is also no depth write on this family, so translucent
 * faces are not sorted and blend order is arbitrary. Keeping alpha constant means total coverage
 * depends only on how many faces a ray crossed and not on the order it crossed them in; letting
 * the directional shading touch alpha would make coverage order-dependent as well as brightness.
 * ADR-022 has the full argument.
 *
 * <p>This type names no Minecraft class, which is why it lives in the main source set and can be
 * exercised by JUnit alongside {@link ShellQuad}. {@code ShellMeshBuilder}, which does name
 * Minecraft classes, is in the client source set.
 */
public record ShellStyle(int colour, float depthTestedAlpha, float seeThroughAlpha, float[] shadeByFace) {

	/**
	 * The v0.0 style: amber {@code #FFA33C} (ADR-023), alpha 0.25 depth-tested and 0.10
	 * see-through (ADR-022), with the directional multipliers ARCHITECTURE.md section 4.3 lists -
	 * UP 1.00, NORTH/SOUTH 0.92, EAST/WEST 0.86, DOWN 0.80.
	 *
	 * <p>Hardcoded, per PLAN.md section 4: v0.0 has no config. These become sliders in v0.1 when
	 * Cloth Config arrives, which is why they are gathered in one record rather than spread
	 * through the encoder.
	 *
	 * <p>The array is built fresh on each call rather than shared as a constant, because a
	 * {@code float[]} in a record component is mutable and a shared one would be a mutable static.
	 */
	public static ShellStyle v0() {
		float[] shade = new float[Face.values().length];

		shade[Face.UP.ordinal()] = 1.00F;
		shade[Face.NORTH.ordinal()] = 0.92F;
		shade[Face.SOUTH.ordinal()] = 0.92F;
		shade[Face.EAST.ordinal()] = 0.86F;
		shade[Face.WEST.ordinal()] = 0.86F;
		shade[Face.DOWN.ordinal()] = 0.80F;

		return new ShellStyle(0xFFA33C, 0.25F, 0.10F, shade);
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

	/**
	 * The alpha the encoder writes into every vertex, 0..255.
	 *
	 * <p>This is the <b>depth-tested</b> alpha, and the see-through pass reaches its lower value
	 * by modulating rather than by a second mesh - see {@link #seeThroughModulation()}.
	 * ARCHITECTURE.md section 4.3 leaves that as an implementation choice and notes that
	 * modulating is the cheaper of the two; this is that choice taken.
	 */
	public int encodedAlpha() {
		return clampChannel(Math.round(depthTestedAlpha * 255.0F));
	}

	/**
	 * The factor the see-through pass multiplies the encoded alpha by, so that one mesh carrying
	 * {@link #depthTestedAlpha()} renders at {@link #seeThroughAlpha()} on that pass.
	 *
	 * <p>The vanilla fragment shader {@code core/position_color} multiplies the vertex colour by
	 * {@code ColorModulator}, which is a member of the same {@code DynamicTransforms} block the
	 * two passes already bind (R15.4), so this costs nothing beyond a different uniform value per
	 * draw.
	 */
	public float seeThroughModulation() {
		if (depthTestedAlpha <= 0.0F) {
			throw new IllegalStateException("depthTestedAlpha must be positive to modulate from");
		}

		return seeThroughAlpha / depthTestedAlpha;
	}

	private int shade(int channel, Face face) {
		return clampChannel(Math.round(channel * shadeByFace[face.ordinal()]));
	}

	private static int clampChannel(int value) {
		return Math.max(0, Math.min(255, value));
	}
}
