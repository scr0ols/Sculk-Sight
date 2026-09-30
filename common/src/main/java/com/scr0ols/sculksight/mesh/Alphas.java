package com.scr0ols.sculksight.mesh;

final class Alphas {

	private Alphas() {
	}

	static int clampChannel(int value) {
		return Math.max(0, Math.min(255, value));
	}

	static int toChannel(float alpha) {
		return clampChannel(Math.round(alpha * 255.0F));
	}

	/**
	 * The single-layer alpha whose two-layer "over" composite reaches {@code targetComposite}.
	 *
	 * <p>Used for the outside-the-shell case, where a camera looking through the mesh sees two
	 * stacked faces (the near one and the far one) blended with the standard "over" operator. Each
	 * face must be encoded dimmer than the wanted final result so that, once both are composited,
	 * the perceived alpha lands on {@code targetComposite} rather than past it.
	 */
	static float singleLayerAlphaFor(float targetComposite) {
		if (targetComposite <= 0.0F) {
			return 0.0F;
		}
		if (targetComposite >= 1.0F) {
			return 1.0F;
		}

		return 1.0F - (float) Math.sqrt(1.0 - targetComposite);
	}
}
