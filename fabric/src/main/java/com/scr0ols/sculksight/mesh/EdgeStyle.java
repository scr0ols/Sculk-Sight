package com.scr0ols.sculksight.mesh;

/**
 * How the crease edges look. Fixed for v0.0 by c-docs/DECISIONS.md ADR-028.
 *
 * <p>Colour is {@code 0xRRGGBB}, alphas are 0..1, and {@code lineWidth} is in pixels, which is what
 * {@code core/rendertype_lines.vsh} treats it as: it offsets each vertex perpendicular to the line
 * by {@code lineWidth / ScreenSize} in screen space (RESEARCH-LOG.md R15.7). The width is therefore
 * constant on screen rather than in the world, which is the property ADR-028 chose a line pass for.
 *
 * <p><b>Why the edges carry their own alphas rather than the faces'.</b> A line two pixels wide at
 * the faces' 0.25 is not visible at all. The seam has to read as a line, so it sits near opaque on
 * the depth-tested pass; the see-through pass drops it far enough to stay behind the depth-tested
 * one without vanishing. ADR-028 has the argument.
 *
 * <p>There is no directional shading here and there is nothing to shade: an edge has an
 * orientation but no side, so there is no face whose direction could pick a multiplier.
 */
public record EdgeStyle(int colour, float depthTestedAlpha, float seeThroughAlpha, float lineWidth) {

	/** Black, alpha 0.85 depth-tested and 0.35 see-through, two pixels wide (ADR-028). */
	public static EdgeStyle v0() {
		return new EdgeStyle(0x000000, 0.85F, 0.35F, 2.0F);
	}

	/** The red channel of {@link #colour}, 0..255. */
	public int red() {
		return colour >> 16 & 0xFF;
	}

	/** The green channel of {@link #colour}, 0..255. */
	public int green() {
		return colour >> 8 & 0xFF;
	}

	/** The blue channel of {@link #colour}, 0..255. */
	public int blue() {
		return colour & 0xFF;
	}

	/**
	 * The alpha the encoder writes into every line vertex, 0..255.
	 *
	 * <p>The depth-tested value, exactly as {@code ShellStyle.encodedAlpha()} is for the faces: one
	 * mesh serves both passes and the see-through pass modulates down from here.
	 */
	public int encodedAlpha() {
		return Alphas.toChannel(depthTestedAlpha);
	}
}
