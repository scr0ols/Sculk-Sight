package com.scr0ols.sculksight.neoforge;

import java.nio.file.Path;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

import com.scr0ols.sculksight.client.Environment;

final class NeoForgeEnvironment implements Environment {

	@Override
	public boolean isDevelopmentEnvironment() {
		return !FMLEnvironment.isProduction();
	}

	@Override
	public Path gameDir() {
		return FMLPaths.GAMEDIR.get();
	}

	@Override
	public Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}
}
