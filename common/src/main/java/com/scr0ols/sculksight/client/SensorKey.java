package com.scr0ols.sculksight.client;

import net.minecraft.core.BlockPos;

/**
 * The cache key: a sensor's block position. ARCHITECTURE.md section 3.3, ADR-016.
 *
 * <p><b>Dimension is deliberately not part of the key.</b> A dimension change clears the whole
 * cache (ARCHITECTURE.md section 5), which is simpler and avoids naming a dimension identifier
 * type in the contract.
 *
 * <p>Primitive components rather than a {@code BlockPos} field, so the key has value equality
 * without depending on {@code BlockPos}'s own; the conversion is here and nowhere else.
 */
public record SensorKey(int x, int y, int z) {

	public static SensorKey of(BlockPos pos) {
		return new SensorKey(pos.getX(), pos.getY(), pos.getZ());
	}
}
