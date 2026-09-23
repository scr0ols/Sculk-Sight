package com.scr0ols.sculksight.client;

final class TimingGate {

	static final String PROPERTY = "sculksight.timing";

	static final boolean ENABLED = ClientPlatform.get().isDevelopmentEnvironment()
			|| Boolean.getBoolean(PROPERTY);

	// The property alone, never the development environment: a dev client is played in as well as
	// profiled, and the aggregates flush every ten seconds for as long as a shell is up. The
	// instrument stays fully on either way - only its chat mirror is narrowed.
	static final boolean CHAT = Boolean.getBoolean(PROPERTY);

	private TimingGate() {
	}
}
