package com.scr0ols.sculksight.client;

/**
 * What one solve produced, carried alongside the meshes so the drawn shell can be checked against
 * the solver's output rather than merely looked at.
 *
 * <p>This is the v0.0 second exit criterion made checkable. PLAN.md section 5 states the criterion
 * as "the rendered shell matches the solver's output", and the way that fails silently is an
 * encoder that drops or duplicates primitives, which changes the picture subtly and changes the
 * vertex count exactly. {@code boundaryFaces} is counted by {@code BoundaryFaceExtractor} and
 * {@code creaseEdges} by {@code CreaseEdgeExtractor}; the uploaded buffers' vertex counts are
 * counted by {@code BufferBuilder}; four times each count must equal the matching vertex count, and
 * {@link ShellRenderer} refuses to draw a shell where either does not.
 *
 * <p><b>Four for both, and for two different reasons that happen to agree.</b> A boundary face is
 * one quad and a quad is four vertices. A crease edge is two authored vertices, which
 * {@code BufferBuilder} doubles because the lines topology needs both sides of the screen-space
 * expansion (R15.7). The arithmetic is the same; the mechanism is not, and the two are worth not
 * confusing.
 *
 * <p>{@code predicted} and {@code occludedOut} are the same two numbers {@code /sculksight-verify}
 * prints for the same sensor, so the two mechanisms can be compared directly on one scene.
 */
public record ShellStats(int radius, int predicted, int occludedOut, int boundaryFaces, int creaseEdges) {

	public String summary() {
		return "radius " + radius + ": " + predicted + " positions in range (" + occludedOut
				+ " occluded out), " + boundaryFaces + " boundary faces, " + creaseEdges + " crease edges";
	}
}
