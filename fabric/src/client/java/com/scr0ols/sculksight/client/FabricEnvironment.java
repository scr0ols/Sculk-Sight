package com.scr0ols.sculksight.client;

import java.nio.file.Path;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric's {@link Environment}: every method is a direct pass-through to {@code FabricLoader},
 * exactly what {@link TimingGate} and {@link TimingLog} read before DECISIONS.md ADR-043's
 * follow-up split moved them to {@code common}. Installed once by {@code SculkSightClient}, as
 * the first thing it does.
 *
 * <p>{@link #configDir()} arrived with the v0.1 config screen (PLAN.md section 4) and is asked of
 * the loader rather than derived from {@link #gameDir()}, for the reason {@link Environment}'s own
 * javadoc gives. {@code FabricLoader.getConfigDir} was read off the real
 * {@code fabric-loader-0.19.3} artifact on 2026-09-06 rather than recalled, per CONVENTIONS.md
 * section 6.
 */
final class FabricEnvironment implements Environment {

	@Override
	public boolean isDevelopmentEnvironment() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}

	@Override
	public Path gameDir() {
		return FabricLoader.getInstance().getGameDir();
	}

	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}
}
