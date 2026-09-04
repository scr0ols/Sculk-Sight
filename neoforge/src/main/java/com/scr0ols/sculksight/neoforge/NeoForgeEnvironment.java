package com.scr0ols.sculksight.neoforge;

import java.nio.file.Path;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

import com.scr0ols.sculksight.client.Environment;

/**
 * NeoForge's {@link Environment}: {@link FMLEnvironment#isProduction()} and
 * {@link FMLPaths#GAMEDIR}, read from FancyModLoader's own source (`neoforged/FancyModLoader`,
 * `main`, read 2026-09-04, per CONVENTIONS.md §6) rather than assumed from Fabric's shape.
 * {@code isProduction()} is the negation of Fabric's own gate - development is the state where
 * it is false - and {@code FMLPaths.GAMEDIR.get()} is the same "instance folder for an install,
 * {@code run/} in dev" directory {@code FabricLoader#getGameDir} resolves.
 *
 * <p>Installed once by {@code SculkSightNeoForge}, in its {@code @Mod} constructor - the
 * earliest point in NeoForge's own mod lifecycle, ahead of every event this mod subscribes to,
 * the same ordering requirement {@code ClientPlatform}'s own javadoc describes for Fabric.
 */
final class NeoForgeEnvironment implements Environment {

	@Override
	public boolean isDevelopmentEnvironment() {
		return !FMLEnvironment.isProduction();
	}

	@Override
	public Path gameDir() {
		return FMLPaths.GAMEDIR.get();
	}
}
