package com.scr0ols.sculksight.client;

/** Pure visibility rule shared by the global switch and each persisted sensor toggle. */
public final class SensorRenderState {

	private SensorRenderState() {
	}

	public static boolean shouldRender(boolean globalEnabled, boolean sensorEnabled) {
		return globalEnabled && sensorEnabled;
	}
}
