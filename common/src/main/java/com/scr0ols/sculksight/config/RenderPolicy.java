package com.scr0ols.sculksight.config;

/** How the mod draws the selected sensors' detection shells. */
public enum RenderPolicy {

	/** One merged shape across every selected sensor's effective range. The default. */
	UNION,

	/** Each selected sensor drawn separately, coloured by its own detector type. */
	PER_SENSOR
}
