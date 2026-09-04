package com.scr0ols.sculksight.mesh;

/**
 * The two pieces of alpha arithmetic a style type needs.
 *
 * <p>Package-private and static-only. It exists so that the style types cannot drift apart on the
 * two things any of them has to get identically right: how an alpha becomes a vertex channel, and
 * how ADR-029's inside-the-shell factor is computed. It served the crease edges' own style as well
 * until ADR-030 removed that type, and {@link ShellStyle} is its only caller now.
 */
final class Alphas {

	private Alphas() {
	}

	/** Clamps an already-scaled channel value into 0..255. */
	static int clampChannel(int value) {
		return Math.max(0, Math.min(255, value));
	}

	/** An alpha in 0..1 as the 0..255 channel the vertex format stores. */
	static int toChannel(float alpha) {
		return clampChannel(Math.round(alpha * 255.0F));
	}

	/**
	 * ADR-029's factor: what to multiply an alpha by so that one crossed layer composites to what
	 * two crossed layers give at the same alpha.
	 *
	 * <p><b>Where the expression comes from.</b> ADR-022 chose the alphas against the fact that the
	 * chosen pipeline family does not cull back faces, so a ray through a closed shell crosses at
	 * least two translucent layers: at alpha {@code a} the composite is
	 * {@code 1 - (1 - a) * (1 - a)}. From inside the shell the near half is behind the camera and
	 * the ray crosses one layer, so the composite is only {@code a}. Dividing the two gives the
	 * factor that restores the outside appearance, 1.75 at 0.25 and 1.90 at 0.10.
	 *
	 * <p>Computed rather than tabulated so that it stays correct when the v0.1 opacity slider moves
	 * the alphas it is derived from.
	 *
	 * <p>Returns 1 for a non-positive alpha, where the ratio is undefined and there is nothing to
	 * correct anyway.
	 */
	static float insideFactor(float alpha) {
		if (alpha <= 0.0F) {
			return 1.0F;
		}

		float outside = 1.0F - (1.0F - alpha) * (1.0F - alpha);
		return outside / alpha;
	}
}
