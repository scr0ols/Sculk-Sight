package com.scr0ols.sculksight.client;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

final class VibrationOcclusion {

	private static final Set<Block> WOOL_CARPETS = Set.copyOf(Blocks.CARPET.asList());
	private static final Set<Block> WOOL_BLOCKS = Set.copyOf(Blocks.WOOL.asList());

	private VibrationOcclusion() {
	}

	static boolean isOccluder(BlockState state) {
		return state.is(BlockTags.OCCLUDES_VIBRATION_SIGNALS)
				|| WOOL_CARPETS.contains(state.getBlock());
	}

	static boolean isDampener(BlockState state) {
		return state.is(BlockTags.DAMPENS_VIBRATIONS)
				|| WOOL_BLOCKS.contains(state.getBlock())
				|| WOOL_CARPETS.contains(state.getBlock());
	}
}
