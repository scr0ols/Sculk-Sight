package com.scr0ols.sculksight.verify;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEventListener;

/**
 * The independent ground truth DECISIONS.md ADR-041 requires: a direct chunk sweep, built without
 * going through {@code SensorIndex}'s own callback-maintained state.
 *
 * <p><b>Why independence is the whole point, not a style preference.</b> ADR-041 point 3:
 * {@code SensorIndex}'s risk is a missed or stale <em>callback</em>, a lifecycle bug, not a wrong
 * formula for what counts as a sensor. A ground truth built by calling back into
 * {@code SensorIndex}, or by sharing a helper method with it, could not catch that class of bug -
 * a callback that never fired and a check that correctly never ran because nothing invoked it look
 * identical from outside. This class therefore does not call {@code SensorIndex.tryAdd}, even
 * though what it does per block entity is necessarily the same shape:
 * {@code RESEARCH-LOG.md} R1 point 4 is the one public formula for a listener's radius, and there
 * is no second correct one to write instead.
 *
 * <p><b>The sweep mechanism itself is R10's, not a new one.</b> {@code LevelChunk#getBlockEntities()}
 * over a chunk fetched through {@code ChunkSource#getChunkNow}, bounded by
 * {@code ChunkPos#rangeClosed} - the same three calls R10 point 6 found vanilla's own
 * {@code TransportItemsBetweenContainers} making, read there in full. {@code getChunkNow} returns
 * null for a chunk that is out of the client's loaded range or simply absent (R10 point 5), and
 * this class treats that the same way the vanilla precedent does: skipped, not swept as empty.
 *
 * <p><b>Why a chunk radius is a required argument, with no default.</b> R10 point 8: the search
 * radius is the one number this mechanism cannot derive from the game. ADR-038 answered that
 * question for the production index by removing the need for a radius at all - the index tracks
 * every loaded chunk regardless of distance, "no search-radius constant introduced anywhere in
 * the mechanism". This class has no equivalent escape: it must bound a single sweep somehow, and
 * the client exposes no way to enumerate every currently-loaded chunk, only per-position lookup
 * (R10 point 5). The bound is therefore left to whoever runs the command each time, the same way
 * {@link DifferentialVerifier#verify} leaves the sample size to its caller rather than inventing
 * a default from nowhere.
 *
 * <p><b>Client thread only, and deliberately so.</b> Unlike {@link IntegratedServerSensorProbe},
 * nothing here crosses to the server thread, so R16's restriction on reading the live
 * {@code ClientLevel} from elsewhere does not apply: this runs exactly where {@code SensorIndex}'s
 * own callbacks do, reading the same live map (R10 point 3) they read and write.
 */
public final class IndexSweep {

	private IndexSweep() {
	}

	/**
	 * Sweeps every loaded chunk within {@code chunkRadius} chunks of {@code center}, in both
	 * axes, and returns every game-event listener found, keyed by its world position.
	 *
	 * <p>The filter and the radius read are the same one-line idiom {@code SensorIndex.tryAdd},
	 * {@code ShellRenderer.toggle} and {@code VerificationCommand} already use: an
	 * {@code instanceof GameEventListener.Provider<?>} test, then
	 * {@code getListener().getListenerRadius()} - never a stored constant, per R1 point 3's trap.
	 */
	public static Map<WorldPosition, Integer> sweep(ClientLevel level, BlockPos center, int chunkRadius) {
		Map<WorldPosition, Integer> found = new HashMap<>();
		ChunkPos centerChunk = ChunkPos.containing(center);

		for (ChunkPos chunkPos : ChunkPos.rangeClosed(centerChunk, chunkRadius).toList()) {
			LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x(), chunkPos.z());

			if (chunk == null) {
				continue;
			}

			for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
				if (blockEntity instanceof GameEventListener.Provider<?> provider) {
					BlockPos pos = blockEntity.getBlockPos();

					found.put(new WorldPosition(pos.getX(), pos.getY(), pos.getZ()),
							provider.getListener().getListenerRadius());
				}
			}
		}

		return found;
	}

	/**
	 * Whether {@code pos} lies within the same bound {@link #sweep} covers for {@code center} and
	 * {@code chunkRadius}, so a caller can filter {@code SensorIndex}'s snapshot down to a
	 * like-for-like comparison rather than diffing against entries the sweep never visited.
	 *
	 * <p>Expressed as the same inclusive square {@code ChunkPos.rangeClosed} builds (R10 point 6:
	 * {@code center.x - radius} to {@code center.x + radius} on each axis), rather than by
	 * materialising the chunk list a second time to test membership against it.
	 */
	public static boolean withinSweep(BlockPos pos, BlockPos center, int chunkRadius) {
		ChunkPos posChunk = ChunkPos.containing(pos);
		ChunkPos centerChunk = ChunkPos.containing(center);

		return Math.abs(posChunk.x() - centerChunk.x()) <= chunkRadius
				&& Math.abs(posChunk.z() - centerChunk.z()) <= chunkRadius;
	}
}
