package com.scr0ols.sculksight.verify;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEventListener;

import com.scr0ols.sculksight.client.DetectorType;

/** An independent ground truth of detectors, built by sweeping loaded chunks directly. */
public final class IndexSweep {

	private IndexSweep() {
	}

	/** Sweeps every loaded chunk within {@code chunkRadius} of {@code center} and returns each detector keyed by world position. */
	public static Map<WorldPosition, Integer> sweep(ClientLevel level, BlockPos center, int chunkRadius) {
		Map<WorldPosition, Integer> found = new HashMap<>();
		ChunkPos centerChunk = ChunkPos.containing(center);

		for (ChunkPos chunkPos : ChunkPos.rangeClosed(centerChunk, chunkRadius).toList()) {
			LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x(), chunkPos.z());

			if (chunk == null) {
				continue;
			}

			for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
				if (blockEntity instanceof GameEventListener.Provider<?> provider
						&& DetectorType.of(blockEntity.getBlockState().getBlock()).isPresent()) {
					BlockPos pos = blockEntity.getBlockPos();

					found.put(new WorldPosition(pos.getX(), pos.getY(), pos.getZ()),
							provider.getListener().getListenerRadius());
				}
			}
		}

		return found;
	}

	/** Whether {@code pos} lies within the same bound {@link #sweep} covers for {@code center} and {@code chunkRadius}. */
	public static boolean withinSweep(BlockPos pos, BlockPos center, int chunkRadius) {
		ChunkPos posChunk = ChunkPos.containing(pos);
		ChunkPos centerChunk = ChunkPos.containing(center);

		return Math.abs(posChunk.x() - centerChunk.x()) <= chunkRadius
				&& Math.abs(posChunk.z() - centerChunk.z()) <= chunkRadius;
	}
}
