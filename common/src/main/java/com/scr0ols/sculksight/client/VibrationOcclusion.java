package com.scr0ols.sculksight.client;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/** The mod's vibration-occlusion predicates, including wool carpets by product decision. */
final class VibrationOcclusion {

	private static final Set<Block> WOOL_CARPETS = Set.copyOf(Blocks.CARPET.asList());
	private static final Set<Block> WOOL_BLOCKS = Set.copyOf(Blocks.WOOL.asList());

	private VibrationOcclusion() {
	}

	static boolean isOccluder(BlockState state) {
		return state.is(BlockTags.OCCLUDES_VIBRATION_SIGNALS)
				|| WOOL_CARPETS.contains(state.getBlock());
	}

	/** Vanilla's separate event-source dampening predicate. Its tag includes wool carpets. */
	static boolean isDampener(BlockState state) {
		// The tag is the vanilla predicate. The explicit built-in sets keep the same default
		// behaviour in the plain registry bootstrap used by JVM tests, before datapack tags load.
		return state.is(BlockTags.DAMPENS_VIBRATIONS)
				|| WOOL_BLOCKS.contains(state.getBlock())
				|| WOOL_CARPETS.contains(state.getBlock());
	}
}
