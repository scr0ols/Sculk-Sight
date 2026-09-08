package com.scr0ols.sculksight.client;

import com.scr0ols.sculksight.timing.DelayBand;

/** Independent shell colour modes. The normal mode delegates exactly to the detector palette. */
public enum ShellDisplayMode {
	TYPE,
	DELAY_HEATMAP;

	public int colour(DetectorType detector, DelayBand delayBand) {
		return this == DELAY_HEATMAP ? delayBand.colour() : detector.colour();
	}
}
