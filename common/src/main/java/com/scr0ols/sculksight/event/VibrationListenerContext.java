package com.scr0ols.sculksight.event;

/** Listener-side facts kept separate from geometric occlusion and raycasting. */
public record VibrationListenerContext(int listenerRadius, boolean alreadyVibrating) {

	public VibrationListenerContext {
		if (listenerRadius < 0) {
			throw new IllegalArgumentException("listenerRadius must be non-negative");
		}
	}
}
