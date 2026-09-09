package com.scr0ols.sculksight.client;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** The mod's vibration-occlusion predicate, including wool carpets by product decision. */
final class VibrationOcclusion {

	private VibrationOcclusion() {
	}

	static boolean isOccluder(BlockState state) {
		return state.is(BlockTags.OCCLUDES_VIBRATION_SIGNALS)
				|| Blocks.CARPET.asList().contains(state.getBlock());
	}
}
