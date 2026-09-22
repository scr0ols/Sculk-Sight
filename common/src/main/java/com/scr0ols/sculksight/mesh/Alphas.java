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

	static float insideFactor(float alpha) {
		if (alpha <= 0.0F) {
			return 1.0F;
		}

		float outside = 1.0F - (1.0F - alpha) * (1.0F - alpha);
		return outside / alpha;
	}
}
