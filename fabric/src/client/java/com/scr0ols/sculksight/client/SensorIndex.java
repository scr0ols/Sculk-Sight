package com.scr0ols.sculksight.client;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.gameevent.GameEventListener;

/**
 * Modes B and C's sensor enumeration surface. `DECISIONS.md` ADR-038, `RESEARCH-LOG.md` R10.
 *
 * <p><b>Mechanism, exactly as decided.</b> A {@code Map<BlockPos, Integer>} - position to
 * radius - populated and maintained by {@code ClientBlockEntityEvents.BLOCK_ENTITY_LOAD} /
 * {@code UNLOAD} and {@code ClientChunkEvents.CHUNK_LOAD} / {@code UNLOAD}, never by a periodic
 * or per-frame sweep. Radius is read once, at insert time, through
 * {@code GameEventListener.Provider#getListener().getListenerRadius()} - the same idiom
 * {@link ShellRenderer#toggle} uses and R1 point 3's trap forbids reading a stored constant
 * instead. No search-radius constant is introduced anywhere here: this class indexes every
 * game-event listener it is told about, regardless of its radius, and a query decides what is
 * near enough to matter.
 *
 * <p><b>No explicit sweep at world join, and that is a reading of ADR-038 rather than a
 * departure from it.</b> ADR-038's consequences describe "a one-time pass over all currently-
 * loaded chunk columns" for the case where the index starts observing after chunks already
 * exist - world join, or the mod enabled mid-session. Neither happens here: {@link #register}
 * runs once, from {@link SculkSightClient#onInitializeClient}, which always completes before
 * any {@code ClientLevel} exists - the same ordering {@link ShellRenderer#register} already
 * relies on for its own key-mapping and event registration. Every chunk that will ever load
 * during this client session, including the first ones on joining a world, therefore loads
 * after this class's callbacks are registered, and {@code CHUNK_LOAD} carries the same "one-
 * time pass" this class would otherwise have to perform by hand. A sweep would need to bound
 * itself by some notion of "currently loaded", and this project has no research-log entry for
 * what that bound is in 26.2 (CONVENTIONS.md section 6) - so the ordering guarantee is taken
 * instead of guessing one.
 *
 * <p><b>What this leaves resting on R11.</b> ADR-038's own "R11 dependency" paragraph already
 * accepts that whether {@code ClientBlockEntityEvents} fires reliably on every path - chunk-
 * packet arrival as well as live placement and breaking - is unconfirmed; R10 point 10 read
 * only the event signatures. Nothing here narrows that risk further or works around it; the
 * "Revisit if" clause ADR-038 already carries is this class's fallback plan too, not a new one.
 *
 * <p><b>Threading: no synchronisation, on the working assumption that every callback fires on
 * the client thread.</b> R10 point 4 places the live block-change path (the one
 * {@code BLOCK_ENTITY_LOAD}/{@code UNLOAD} sit on) inside {@code setBlockState}, which is client
 * world mutation, and R13 point 4 establishes there is exactly one such thread. What R10 did not
 * separately confirm is that Fabric's own event dispatch adds no thread hop of its own between a
 * vanilla call site and a registered callback - a fact about the Fabric API's own dispatch
 * mechanism rather than about Minecraft, and unread here. If that assumption is wrong, the
 * failure mode is a torn read of {@link #SENSORS} from two threads racing, not a wrong answer
 * from a stale one, which is why {@link #snapshot} exists rather than handing out the live map.
 * No worker executor is used regardless: `ARCHITECTURE.md` section 6.2's row is unblocked by
 * R16 but its own trigger has not fired, and starting it is out of this session's scope.
 */
public final class SensorIndex {

	private static final Map<BlockPos, Integer> SENSORS = new HashMap<>();

	private SensorIndex() {
	}

	public static void register() {
		ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register(SensorIndex::onBlockEntityLoad);
		ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register(SensorIndex::onBlockEntityUnload);
		ClientChunkEvents.CHUNK_LOAD.register(SensorIndex::onChunkLoad);
		ClientChunkEvents.CHUNK_UNLOAD.register(SensorIndex::onChunkUnload);

		// A dimension change clears the whole index, the same rule ARCHITECTURE.md section 5
		// rule 5 states for the shell cache and the same reason SensorKey gives for leaving
		// dimension out of its own key: a stale entry naming a position in the wrong dimension
		// is worse than an empty index that repopulates from the callbacks above.
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> SENSORS.clear());
	}

	/**
	 * A defensive copy for a caller to iterate against a possibly-changing map.
	 *
	 * <p>Everything that mutates {@link #SENSORS} runs on the client thread (see the class
	 * comment), and so does every caller of this method today, so nothing here is a thread-
	 * safety boundary. The copy exists so that a callback firing reentrantly mid-iteration -
	 * for instance a chunk unloading while {@link DetectionIndicator} is midway through a tick's
	 * worth of occlusion tests - cannot throw a {@code ConcurrentModificationException} out of
	 * unrelated code.
	 *
	 * <p>Public rather than package-private since 2026-09-04, for a second caller outside this
	 * package: {@code com.scr0ols.sculksight.verify.IndexVerificationCommand} reads it as the
	 * value under test, the same live contents `DECISIONS.md` ADR-041 requires checked against an
	 * independent sweep.
	 */
	public static Map<BlockPos, Integer> snapshot() {
		return Map.copyOf(SENSORS);
	}

	// ---------------------------------------------------------------- callbacks

	private static void onBlockEntityLoad(BlockEntity blockEntity, ClientLevel level) {
		tryAdd(blockEntity);
	}

	private static void onBlockEntityUnload(BlockEntity blockEntity, ClientLevel level) {
		SENSORS.remove(blockEntity.getBlockPos());
	}

	private static void onChunkLoad(ClientLevel level, LevelChunk chunk) {
		for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
			tryAdd(blockEntity);
		}
	}

	/**
	 * Removes every indexed sensor in the unloaded column, by position rather than by reading
	 * the chunk's block entity map.
	 *
	 * <p>Computed from {@code chunk.getPos()} rather than from {@code getBlockEntities()} at
	 * this event, because whether that map is still populated at the moment {@code CHUNK_UNLOAD}
	 * fires - before or after whatever clears it - is not something this project has read. A
	 * geometric filter needs nothing from the chunk but its own coordinates, so it is exact
	 * regardless of that ordering, and it is a backstop independent of whether
	 * {@code BLOCK_ENTITY_UNLOAD} also fired for the same sensors.
	 *
	 * <p>{@code ChunkPos} is a record - {@code x()} and {@code z()}, not fields - and the
	 * block-to-chunk-coordinate conversion is {@code SectionPos.blockToSectionCoord}, the same
	 * method {@code ChunkPos.containing} itself uses, rather than an inline shift this project
	 * would otherwise be asserting matches it by coincidence.
	 */
	private static void onChunkUnload(ClientLevel level, LevelChunk chunk) {
		int chunkX = chunk.getPos().x();
		int chunkZ = chunk.getPos().z();

		SENSORS.keySet().removeIf(pos ->
				SectionPos.blockToSectionCoord(pos.getX()) == chunkX
						&& SectionPos.blockToSectionCoord(pos.getZ()) == chunkZ);
	}

	/**
	 * Adds the block entity if it is a game-event listener, reading its radius through the same
	 * runtime idiom {@link ShellRenderer#toggle} uses.
	 */
	private static void tryAdd(BlockEntity blockEntity) {
		if (blockEntity instanceof GameEventListener.Provider<?> provider) {
			SENSORS.put(blockEntity.getBlockPos(), provider.getListener().getListenerRadius());
		}
	}
}
