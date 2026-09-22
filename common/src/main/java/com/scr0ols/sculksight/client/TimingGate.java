package com.scr0ols.sculksight.client;

final class TimingGate {

	static final String PROPERTY = "sculksight.timing";

	static final boolean ENABLED = ClientPlatform.get().isDevelopmentEnvironment()
			|| Boolean.getBoolean(PROPERTY);

	private TimingGate() {
	}
}
