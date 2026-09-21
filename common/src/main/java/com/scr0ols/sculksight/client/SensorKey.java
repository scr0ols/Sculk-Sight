package com.scr0ols.sculksight.client;

import net.minecraft.core.BlockPos;

/** The cache key: a sensor's block position. */
public record SensorKey(int x, int y, int z) {

	public static SensorKey of(BlockPos pos) {
		return new SensorKey(pos.getX(), pos.getY(), pos.getZ());
	}
}
