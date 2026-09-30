package com.scr0ols.sculksight.config;

/**
 * How strongly the shell's see-through pass draws while the camera is inside the shell.
 *
 * <p>The see-through pass has no depth test, so from inside a shell its far wall paints over
 * almost every block the player can see, washing out the room. This setting only changes that
 * one case: how the shell looks from outside, and the depth-tested pass either way, are
 * untouched.
 */
public enum SeeThroughInsideMode {

	/** The see-through pass keeps its normal strength, even from inside the shell. The default. */
	FULL(1.0F),

	/** The see-through pass is dimmed to a fraction of its normal strength while inside the shell. */
	WEAK(0.35F),

	/** The see-through pass is skipped entirely while inside the shell. */
	OFF(0.0F);

	private final float insideFactor;

	SeeThroughInsideMode(float insideFactor) {
		this.insideFactor = insideFactor;
	}

	/**
	 * The multiplier applied to the see-through pass's alpha while the camera is inside the
	 * shell; has no effect on the alpha used from outside.
	 */
	public float insideFactor() {
		return insideFactor;
	}
}
