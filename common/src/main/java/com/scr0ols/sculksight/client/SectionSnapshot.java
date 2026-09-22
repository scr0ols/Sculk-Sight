package com.scr0ols.sculksight.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

import org.jspecify.annotations.Nullable;

final class SectionSnapshot {

	private final @Nullable PalettedContainer<BlockState> states;

	SectionSnapshot(LevelChunk chunk, int sectionIndex) {
		this.states = copyStates(chunk, sectionIndex);
	}

	private static @Nullable PalettedContainer<BlockState> copyStates(LevelChunk chunk, int sectionIndex) {
		if (chunk instanceof EmptyLevelChunk) {
			return null;
		}

		LevelChunkSection[] sections = chunk.getSections();

		if (sectionIndex < 0 || sectionIndex >= sections.length) {
			return null;
		}

		LevelChunkSection section = sections[sectionIndex];

		return section.hasOnlyAir() ? null : section.getStates().copy();
	}

	BlockState getBlockState(BlockPos pos) {
		if (states == null) {
			return Blocks.AIR.defaultBlockState();
		}

		return states.get(
				SectionPos.sectionRelative(pos.getX()),
				SectionPos.sectionRelative(pos.getY()),
				SectionPos.sectionRelative(pos.getZ()));
	}
}
