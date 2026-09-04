package com.scr0ols.sculksight.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.gameevent.GameEventListener;

/**
 * Modes B and C's sensor enumeration surface. `DECISIONS.md` ADR-038, `RESEARCH-LOG.md` R10.
 *
 * <p><b>Mechanism, exactly as decided.</b> A {@code Map<BlockPos, Integer>} - position to
 * radius - populated and maintained by callbacks a loader's own entrypoint registers against its
 * own event API and forwards here: a block entity loading or unloading, and a chunk loading or
 * unloading - never by a periodic or per-frame sweep. Radius is read once, at insert time, through
 * {@code GameEventListener.Provider#getListener().getListenerRadius()} - the same idiom
 * {@code ShellRenderer#toggle} uses and R1 point 3's trap forbids reading a stored constant
 * instead. No search-radius constant is introduced anywhere here: this class indexes every
 * game-event listener it is told about, regardless of its radius, and a query decides what is
 * near enough to matter.
 *
 * <p><b>No explicit sweep at world join, and that is a reading of ADR-038 rather than a
 * departure from it.</b> ADR-038's consequences describe "a one-time pass over all currently-
 * loaded chunk columns" for the case where the index starts observing after chunks already
 * exist - world join, or the mod enabled mid-session. Neither happens here, on either loader:
 * each loader's entrypoint registers this class's callbacks before that loader's client
 * initialisation completes, which always finishes before any {@code ClientLevel} exists - the
 * same ordering {@code ShellRenderer}'s own registration already relies on for its key mapping
 * and event registration. Every chunk that will ever load during this client session, including
 * the first ones on joining a world, therefore loads after this class's callbacks are registered,
 * and a chunk-load callback carries the same "one-time pass" this class would otherwise have to
 * perform by hand. A sweep would need to bound itself by some notion of "currently loaded", and
 * this project has no research-log entry for what that bound is in 26.2 (CONVENTIONS.md
 * section 6) - so the ordering guarantee is taken instead of guessing one.
 *
 * <p><b>What this leaves resting on R11.</b> ADR-038's own "R11 dependency" paragraph already
 * accepts that whether a live block-change fires this class's load/unload callbacks reliably on
 * every path - chunk-packet arrival as well as live placement and breaking - is unconfirmed; R10
 * point 10 read only Fabric's own event signatures for that half of the question. Nothing here
 * narrows that risk further or works around it; the "Revisit if" clause ADR-038 already carries
 * is this class's fallback plan too, not a new one.
 *
 * <p><b>Threading: no synchronisation, on the working assumption that every callback fires on
 * the client thread.</b> R10 point 4 places the live block-change path (the one the load/unload
 * callbacks sit on) inside {@code setBlockState}, which is client world mutation, and R13 point 4
 * establishes there is exactly one such thread. What R10 did not separately confirm is that
 * either loader's own event dispatch adds no thread hop of its own between a vanilla call site
 * and a registered callback - a fact about each loader API's own dispatch mechanism rather than
 * about Minecraft, and unread here for either loader. If that assumption is wrong, the failure
 * mode is a torn read of {@link #SENSORS} from two threads racing, not a wrong answer from a
 * stale one, which is why {@link #snapshot} exists rather than handing out the live map. No
 * worker executor is used regardless: `ARCHITECTURE.md` section 6.2's row is unblocked by R16 but
 * its own trigger has not fired, and starting it is out of this session's scope.
 *
 * <p><b>Loader-neutral since DECISIONS.md ADR-043's follow-up split.</b> This class no longer
 * registers itself against any event API; it exposes its state and its four callbacks as public
 * static methods instead, and each loader's own entrypoint is responsible for calling them from
 * whatever that loader's own event registration looks like. Fabric's own registration lives in
 * {@code fabric}'s {@code SculkSightClient}.
 */
public final class SensorIndex {

	private static final Map<BlockPos, Integer> SENSORS = new HashMap<>();

	private SensorIndex() {
	}

	/**
	 * A defensive copy for a caller to iterate against a possibly-changing map.
	 *
	 * <p>Everything that mutates {@link #SENSORS} runs on the client thread (see the class
	 * comment), and so does every caller of this method today, so nothing here is a thread-
	 * safety boundary. The copy exists so that a callback firing reentrantly mid-iteration -
	 * for instance a chunk unloading while {@code DetectionIndicator} is midway through a tick's
	 * worth of occlusion tests - cannot throw a {@code ConcurrentModificationException} out of
	 * unrelated code.
	 *
	 * <p>Public rather than package-private since 2026-09-04, for a second caller outside this
	 * package: {@code com.scr0ols.sculksight.verify.IndexVerificationCommandCore} reads it as the
	 * value under test, the same live contents `DECISIONS.md` ADR-041 requires checked against an
	 * independent sweep.
	 */
	public static Map<BlockPos, Integer> snapshot() {
		return Map.copyOf(SENSORS);
	}

	// ---------------------------------------------------------------- callbacks

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

	/**
	 * Removes every indexed sensor in the unloaded column, by position rather than by reading
	 * the chunk's block entity map. Called from a loader's own chunk-unload event, forwarded with
	 * the unloaded chunk.
	 *
	 * <p>Computed from {@code chunk.getPos()} rather than from {@code getBlockEntities()} at
	 * this event, because whether that map is still populated at the moment this fires - before
	 * or after whatever clears it - is not something this project has read. A geometric filter
	 * needs nothing from the chunk but its own coordinates, so it is exact regardless of that
	 * ordering, and it is a backstop independent of whether the unload callback also fired for
	 * the same sensors.
	 *
	 * <p>{@code ChunkPos} is a record - {@code x()} and {@code z()}, not fields - and the
	 * block-to-chunk-coordinate conversion is {@code SectionPos.blockToSectionCoord}, the same
	 * method {@code ChunkPos.containing} itself uses, rather than an inline shift this project
	 * would otherwise be asserting matches it by coincidence.
	 */
	public static void onChunkUnload(LevelChunk chunk) {
		int chunkX = chunk.getPos().x();
		int chunkZ = chunk.getPos().z();

		SENSORS.keySet().removeIf(pos ->
				SectionPos.blockToSectionCoord(pos.getX()) == chunkX
						&& SectionPos.blockToSectionCoord(pos.getZ()) == chunkZ);
	}

	/**
	 * Clears the whole index on a dimension change, the same rule ARCHITECTURE.md section 5
	 * rule 5 states for the shell cache and the same reason {@code SensorKey} gives for leaving
	 * dimension out of its own key: a stale entry naming a position in the wrong dimension is
	 * worse than an empty index that repopulates from the callbacks above. Called from a loader's
	 * own client-level-change event.
	 */
	public static void onLevelChanged() {
		SENSORS.clear();
	}

	/**
	 * Adds the block entity if it is a game-event listener, reading its radius through the same
	 * runtime idiom {@code ShellRenderer#toggle} uses.
	 */
	private static void tryAdd(BlockEntity blockEntity) {
		if (blockEntity instanceof GameEventListener.Provider<?> provider) {
			SENSORS.put(blockEntity.getBlockPos(), provider.getListener().getListenerRadius());
		}
	}
}
