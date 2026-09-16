package com.scr0ols.sculksight.timing;

/** Converts a continuous centre-to-centre distance into the travel delay in ticks. */
public final class DelayQuantizer {

	private DelayQuantizer() {
	}

	/** Returns the floored distance, with vanilla's one-tick minimum. */
	public static int ticksForDistance(double distance) {
		return Math.max((int) Math.floor(distance), 1);
	}
}
