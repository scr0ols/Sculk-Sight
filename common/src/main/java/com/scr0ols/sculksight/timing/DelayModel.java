package com.scr0ols.sculksight.timing;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Computes the vanilla-style travel delay between a source block and a sensor block. */
public final class DelayModel {

	private DelayModel() {
	}

	/**
	 * Returns the delay in ticks for a vibration travelling between block centres.
	 *
	 * <p>The minimum of one tick is intentional: a zero-distance vibration is still selected and
	 * delivered on the next server tick. This model only describes travel delay; it does not include
	 * redstone signal strength or attenuation.
	 */
	public static int ticksBetween(BlockPos source, BlockPos sensor) {
		Vec3 sourceCenter = Vec3.atCenterOf(source);
		Vec3 sensorCenter = Vec3.atCenterOf(sensor);
		return DelayQuantizer.ticksForDistance(sourceCenter.distanceTo(sensorCenter));
	}
}
