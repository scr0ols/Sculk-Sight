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
 *
 * <p><b>The three fade components, and why they exist at all.</b> The constant screen-space width
 * that makes a seam legible up close is the same property that makes the shell go black at a
 * distance: the shell shrinks on screen, the lines do not, so the fraction of the shell covered by
 * line grows with distance until it saturates. {@link #distanceFade} is the correction, and
 * ADR-028's 2026-09-02 addendum is the argument for it. The two distances are <b>in blocks</b>,
 * not in multiples of the shell's radius: what fills the screen with line is the ratio between the
 * line's width in pixels and the spacing between lines in pixels, and adjacent creases are one
 * block apart whatever the shell's size, so the radius does not enter. {@code fadeFloor} is what
 * the fade never drops below, and it is not zero because a shell with no seam at all loses its
 * silhouette against bright terrain.
 */
public record EdgeStyle(int colour, float depthTestedAlpha, float seeThroughAlpha, float lineWidth,
		float fadeStartBlocks, float fadeEndBlocks, float fadeFloor) {

	/**
	 * Black, alpha 0.85 depth-tested and 0.35 see-through, two pixels wide (ADR-028), fading from
	 * full strength at eight blocks to a floor of 0.12 at twenty-four (ADR-028's 2026-09-02
	 * addendum, second revision).
	 */
	public static EdgeStyle v0() {
		return new EdgeStyle(0x000000, 0.85F, 0.35F, 2.0F, 8.0F, 24.0F, 0.12F);
	}

	public EdgeStyle {
		if (!(fadeEndBlocks > fadeStartBlocks)) {
			throw new IllegalArgumentException("fadeEndBlocks must be greater than fadeStartBlocks, got "
					+ fadeStartBlocks + " and " + fadeEndBlocks);
		}

		if (fadeStartBlocks < 0.0F) {
			throw new IllegalArgumentException(
					"fadeStartBlocks must not be negative, got " + fadeStartBlocks);
		}

		if (fadeFloor < 0.0F || fadeFloor > 1.0F) {
			throw new IllegalArgumentException("fadeFloor must be within 0..1, got " + fadeFloor);
		}
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

	/**
	 * What both edge passes multiply their alpha by at this viewing distance, 0..1.
	 *
	 * <p>One at {@link #fadeStartBlocks} and closer, {@link #fadeFloor} at {@link #fadeEndBlocks}
	 * and beyond, linear in between. Applied on top of ADR-029's inside-the-shell correction rather
	 * than folded into it: the correction restores what the authored alpha was chosen to look like,
	 * and this attenuates the result, so they compose in that order and not the other.
	 *
	 * <p>The fade is per shell and not per line. The whole shell is one object at one distance for
	 * this purpose, which is what makes it a single uniform value per pass and therefore free, and
	 * at the distances where the fade does anything the near and far sides of even a radius-16
	 * shell differ by too little to see.
	 *
	 * @param cameraDistance blocks from the camera to the sensor
	 */
	public float distanceFade(double cameraDistance) {
		if (cameraDistance <= fadeStartBlocks) {
			return 1.0F;
		}

		if (cameraDistance >= fadeEndBlocks) {
			return fadeFloor;
		}

		double travelled = (cameraDistance - fadeStartBlocks) / (fadeEndBlocks - fadeStartBlocks);
		return (float) (1.0 - travelled * (1.0 - fadeFloor));
	}
}
