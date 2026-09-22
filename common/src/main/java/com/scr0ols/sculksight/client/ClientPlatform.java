package com.scr0ols.sculksight.client;

import org.jspecify.annotations.Nullable;

/** Holds the one {@link Environment} implementation for the running loader. */
public final class ClientPlatform {

	private static @Nullable Environment environment;

	private ClientPlatform() {
	}

	/** Called once, by each loader's own entrypoint, before any registration. */
	public static void set(Environment platform) {
		environment = platform;
	}

	/** Returns the loader's environment, failing if none was set. */
	public static Environment get() {
		Environment current = environment;

		if (current == null) {
			throw new NullStateException(
					"ClientPlatform.set was never called - TimingGate, TimingLog and ClientConfig "
							+ "need a running, initialised game.");
		}

		return current;
	}

	static final class NullStateException extends IllegalStateException {

		NullStateException(String message) {
			super(message);
		}
	}
}
