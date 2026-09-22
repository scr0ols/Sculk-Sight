package com.scr0ols.sculksight.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;

import org.jspecify.annotations.Nullable;

final class VolumeSnapshot implements BlockGetter {

	private final SectionGrid grid;

	private final SectionSnapshot[] sections;

	private final int minY;

	private final int height;

	private VolumeSnapshot(SectionGrid grid, SectionSnapshot[] sections, int minY, int height) {
		this.grid = grid;
		this.sections = sections;
		this.minY = minY;
		this.height = height;
	}

	static VolumeSnapshot of(Level level, int sensorX, int sensorY, int sensorZ, int radius) {
		SectionGrid grid = SectionGrid.over(
				SectionPos.blockToSectionCoord(sensorX - radius),
				SectionPos.blockToSectionCoord(sensorY - radius),
				SectionPos.blockToSectionCoord(sensorZ - radius),
				SectionPos.blockToSectionCoord(sensorX + radius),
				SectionPos.blockToSectionCoord(sensorY + radius),
				SectionPos.blockToSectionCoord(sensorZ + radius));

		SectionSnapshot[] sections = new SectionSnapshot[grid.size()];

		int maxSectionX = grid.minSectionX() + grid.spanX();
		int maxSectionY = grid.minSectionY() + grid.spanY();
		int maxSectionZ = grid.minSectionZ() + grid.spanZ();

		for (int sectionZ = grid.minSectionZ(); sectionZ < maxSectionZ; sectionZ++) {
			for (int sectionX = grid.minSectionX(); sectionX < maxSectionX; sectionX++) {
				LevelChunk chunk = level.getChunk(sectionX, sectionZ);

				for (int sectionY = grid.minSectionY(); sectionY < maxSectionY; sectionY++) {
					sections[grid.index(sectionX, sectionY, sectionZ)] =
							new SectionSnapshot(chunk, chunk.getSectionIndexFromSectionY(sectionY));
				}
			}
		}

		return new VolumeSnapshot(grid, sections, level.getMinY(), level.getHeight());
	}

	/** The copied state at one position, or air outside the copied grid. */
	@Override
	public BlockState getBlockState(BlockPos pos) {
		int index = grid.index(
				SectionPos.blockToSectionCoord(pos.getX()),
				SectionPos.blockToSectionCoord(pos.getY()),
				SectionPos.blockToSectionCoord(pos.getZ()));

		if (index < 0) {
			return Blocks.AIR.defaultBlockState();
		}

		return sections[index].getBlockState(pos);
	}

	/** The fluid state derived from the copied block state. */
	@Override
	public FluidState getFluidState(BlockPos pos) {
		return getBlockState(pos).getFluidState();
	}

	/** Always null: this snapshot deliberately copies no block entities. */
	@Override
	public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
		return null;
	}

	/** The level's own value, read once at snapshot time. */
	@Override
	public int getMinY() {
		return minY;
	}

	/** The level's own value, read once at snapshot time. */
	@Override
	public int getHeight() {
		return height;
	}
}
