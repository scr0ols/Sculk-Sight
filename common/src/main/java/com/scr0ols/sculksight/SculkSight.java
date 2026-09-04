package com.scr0ols.sculksight;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants for the mod.
 *
 * <p>This class lives in the "main" source set, which is loaded on both physical sides.
 * Sculk Sight is client-side only (see c-docs/DECISIONS.md ADR-006), so nothing here may
 * touch client-only types; anything that does belongs in the "client" source set instead.
 * The eventual solver is environment-agnostic and will live on this side of the split.
 *
 * <p>This class deliberately does not implement ModInitializer. A client-only mod needs no
 * common entrypoint, and declaring one that does nothing would misrepresent the mod's shape.
 */
public final class SculkSight {
	public static final String MOD_ID = "sculksight";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private SculkSight() {
	}

	/** Builds an Identifier in this mod's namespace, for textures, key bindings and the like. */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
