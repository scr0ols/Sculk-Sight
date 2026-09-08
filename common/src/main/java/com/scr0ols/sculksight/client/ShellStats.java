package com.scr0ols.sculksight.client;

/**
 * What one solve produced, carried alongside the mesh so the drawn shell can be checked against
 * the solver's output rather than merely looked at.
 *
 * <p>This is the v0.0 second exit criterion made checkable. PLAN.md section 5 states the criterion
 * as "the rendered shell matches the solver's output", and the way that fails silently is an
 * encoder that drops or duplicates boundary faces - which changes the picture subtly and changes
 * the vertex count exactly. {@code boundaryFaces} is counted by
 * {@code BoundaryFaceExtractor} and the uploaded buffer's vertex count is counted by
 * {@code BufferBuilder}; four times the first must equal the second, and
 * {@link ShellRenderer} refuses to draw a shell where it does not.
 *
 * <p>{@code predicted} and {@code occludedOut} are the same two numbers {@code /sculksight-verify}
 * prints for the same sensor, so the two mechanisms can be compared directly on one scene.
 */
public record ShellStats(int radius, int predicted, int occludedOut, int boundaryFaces) {

	public String summary() {
		return "radius " + radius + ": " + predicted + " positions in range (" + occludedOut
				+ " occluded out), " + boundaryFaces + " boundary faces";
	}
}
