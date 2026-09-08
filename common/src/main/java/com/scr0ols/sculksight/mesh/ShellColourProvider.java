package com.scr0ols.sculksight.mesh;

import com.scr0ols.sculksight.solver.Face;

/** Supplies the unshaded RGB colour for a source cell and boundary face. */
@FunctionalInterface
public interface ShellColourProvider {
	int colour(int dx, int dy, int dz, Face face);
}
