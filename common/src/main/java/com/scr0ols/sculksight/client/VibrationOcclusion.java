package com.scr0ols.sculksight.client;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/** The mod's vibration-occlusion predicate, including wool carpets by product decision. */
final class VibrationOcclusion {

	private static final Set<Block> WOOL_CARPETS = Set.copyOf(Blocks.CARPET.asList());

	private VibrationOcclusion() {
	}

	static boolean isOccluder(BlockState state) {
		return state.is(BlockTags.OCCLUDES_VIBRATION_SIGNALS)
				|| WOOL_CARPETS.contains(state.getBlock());
	}
}
