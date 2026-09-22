package com.scr0ols.sculksight.client;

import java.nio.file.Path;

import net.fabricmc.loader.api.FabricLoader;

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
