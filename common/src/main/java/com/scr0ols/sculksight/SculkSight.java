package com.scr0ols.sculksight;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared constants for the mod. */
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
