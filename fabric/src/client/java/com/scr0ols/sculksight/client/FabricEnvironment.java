package com.scr0ols.sculksight.client;

import java.nio.file.Path;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric's {@link Environment}: both methods are a direct pass-through to {@code FabricLoader},
 * exactly what {@link TimingGate} and {@link TimingLog} read before DECISIONS.md ADR-043's
 * follow-up split moved them to {@code common}. Installed once by {@code SculkSightClient}, as
 * the first thing it does.
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
}
