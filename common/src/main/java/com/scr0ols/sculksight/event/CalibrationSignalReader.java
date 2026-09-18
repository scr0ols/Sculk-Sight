package com.scr0ols.sculksight.event;

/** Reads the already-selected client block state without exposing a Minecraft world to the core. */
@FunctionalInterface
public interface CalibrationSignalReader {
	int readSignal();
}
