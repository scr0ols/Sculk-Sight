package com.scr0ols.sculksight.client;

import org.jspecify.annotations.Nullable;

/**
 * Holds the one {@link Environment} implementation for the running loader.
 *
 * <p><b>Set once, from each loader's entrypoint, before anything else in this package runs.</b>
 * Fabric's {@code SculkSightClient.onInitializeClient} calls {@link #set} as its very first
 * statement, ahead of every registration call - the same ordering requirement `SensorIndex`'s own
 * class comment already relies on for {@code ClientLevel} not existing yet. Nothing in this
 * package reads {@link #get} except lazily, on first use well after mod initialisation
 * ({@link TimingGate}'s static field, read the first time a caller checks whether the instrument
 * is on), so this ordering is sufficient without needing {@code set} to run before class loading
 * itself.
 *
 * <p><b>A holder class rather than a static member of {@link Environment} itself</b>, because an
 * interface's fields are implicitly {@code public static final} and cannot hold the mutable
 * reference this needs. Two small types cost less to read than one type doing two jobs.
 *
 * <p><b>{@link #get} stays package-private, but the class itself is public.</b> Only
 * {@link TimingGate} and {@link TimingLog} - this package's own reason this class exists - read
 * {@link #get}. {@link #set}, though, has to be reachable from each loader's own entrypoint
 * package - {@code com.scr0ols.sculksight.neoforge} is a genuinely different package from this
 * one, unlike Fabric's own {@code FabricEnvironment}, which sits inside this package physically
 * (fabric's client source set) even though it is a different module.
 */
public final class ClientPlatform {

	private static @Nullable Environment environment;

	private ClientPlatform() {
	}

	/** Called once, by each loader's own entrypoint, before any registration. */
	public static void set(Environment platform) {
		environment = platform;
	}

	/**
	 * @throws NullStateException if no loader has called {@link #set} yet - which is the correct
	 *         failure for an ordinary JUnit test touching {@link TimingGate} or {@link TimingLog}
	 *         to hit, mirroring the {@code ExceptionInInitializerError} touching {@code TimingGate}
	 *         produced before this seam existed (see that class's own javadoc). Both classes need a
	 *         running, initialised game; neither is meant to be testable in a plain JVM.
	 */
	static Environment get() {
		Environment current = environment;

		if (current == null) {
			throw new NullStateException(
					"ClientPlatform.set was never called - TimingGate and TimingLog need a running, "
							+ "initialised game.");
		}

		return current;
	}

	/** A small named exception rather than a bare {@code NullPointerException} at the call site. */
	static final class NullStateException extends IllegalStateException {

		NullStateException(String message) {
			super(message);
		}
	}
}
