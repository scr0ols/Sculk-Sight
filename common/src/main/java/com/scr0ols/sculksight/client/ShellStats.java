package com.scr0ols.sculksight.client;

/** What one solve produced, carried alongside the mesh. */
public record ShellStats(int radius, int predicted, int occludedOut, int boundaryFaces) {

	public String summary() {
		return "radius " + radius + ": " + predicted + " positions in range (" + occludedOut
				+ " occluded out), " + boundaryFaces + " boundary faces";
	}
}
