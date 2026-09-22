package com.scr0ols.sculksight.client;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.gameevent.GameEventListener;

import com.scr0ols.sculksight.verify.IndexReconciliation;
import com.scr0ols.sculksight.verify.WorldPosition;

/** The client-side index of known detectors and their listener radii. */
public final class SensorIndex {

	private static final Map<BlockPos, Integer> SENSORS = new HashMap<>();

	private SensorIndex() {
	}

	/** Returns a defensive copy of the indexed sensors and their radii. */
	public static Map<BlockPos, Integer> snapshot() {
		return Map.copyOf(SENSORS);
	}

	/** Called from a loader's own block-entity-load event, forwarded with the loaded entity. */
	public static void onBlockEntityLoad(BlockEntity blockEntity) {
		tryAdd(blockEntity);
	}

	/** Called from a loader's own block-entity-unload event, forwarded with the unloaded entity. */
	public static void onBlockEntityUnload(BlockEntity blockEntity) {
		SENSORS.remove(blockEntity.getBlockPos());
	}

	/** Called from a loader's own chunk-load event, forwarded with the loaded chunk. */
	public static void onChunkLoad(LevelChunk chunk) {
		for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
			tryAdd(blockEntity);
		}
	}

	/** Removes every indexed sensor lying in the unloaded column. */
	public static void onChunkUnload(LevelChunk chunk) {
		int chunkX = chunk.getPos().x();
		int chunkZ = chunk.getPos().z();

		SENSORS.keySet().removeIf(pos ->
				SectionPos.blockToSectionCoord(pos.getX()) == chunkX
						&& SectionPos.blockToSectionCoord(pos.getZ()) == chunkZ);
	}

	/** Clears the whole index on a dimension change. */
	public static void onLevelChanged() {
		SENSORS.clear();
	}

	/** Reconciles this index against an observed ground truth for one bounded region. */
	public static void reconcile(Map<BlockPos, Integer> truth, Predicate<BlockPos> inRegion) {
		Map<WorldPosition, Integer> current = new HashMap<>();
		SENSORS.forEach((pos, radius) -> current.put(toWorldPosition(pos), radius));

		Map<WorldPosition, Integer> truthByWorldPosition = new HashMap<>();
		truth.forEach((pos, radius) -> truthByWorldPosition.put(toWorldPosition(pos), radius));

		Map<WorldPosition, Integer> reconciled = IndexReconciliation.apply(current, truthByWorldPosition,
				pos -> inRegion.test(new BlockPos(pos.x(), pos.y(), pos.z())));

		SENSORS.clear();
		reconciled.forEach((pos, radius) -> SENSORS.put(new BlockPos(pos.x(), pos.y(), pos.z()), radius));
	}

	private static WorldPosition toWorldPosition(BlockPos pos) {
		return new WorldPosition(pos.getX(), pos.getY(), pos.getZ());
	}

	private static void tryAdd(BlockEntity blockEntity) {
		if (blockEntity instanceof GameEventListener.Provider<?> provider
				&& DetectorType.of(blockEntity.getBlockState().getBlock()).isPresent()) {
			SENSORS.put(blockEntity.getBlockPos(), provider.getListener().getListenerRadius());
		}
	}
}
