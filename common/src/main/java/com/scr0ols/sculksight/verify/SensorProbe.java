package com.scr0ols.sculksight.verify;

/** Triggers a vibration at a chosen position and reports whether the sensor reacted. */
@FunctionalInterface
public interface SensorProbe {

	/** Triggers a vibration at the source position and reports what the sensor did. */
	Reaction test(int sensorX, int sensorY, int sensorZ, int sourceX, int sourceY, int sourceZ);
}
