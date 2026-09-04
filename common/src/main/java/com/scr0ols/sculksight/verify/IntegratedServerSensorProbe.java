package com.scr0ols.sculksight.verify;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.GameEventTags;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationInfo;
import net.minecraft.world.level.gameevent.vibrations.VibrationSelector;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;

/**
 * The real {@link SensorProbe}: triggers a vibration on the integrated server and reads back
 * whether the sensor accepted it.
 *
 * <p>Development-environment only, per DECISIONS.md ADR-019. Everything this class touches is
 * public API, so the concession turns out to be smaller than ADR-019 reserved room for: no
 * mixin, no reflection, no access widener. What it is not is client-side — R8 point 2 and R2
 * point 3 together rule that out, because on a client the listener registry a dispatched event
 * would be offered to is {@code GameEventListenerRegistry.NOOP}.
 *
 * <p><b>Every method here must run on the server thread.</b> The caller is responsible for the
 * hop; see {@link VerificationCommandCore}, which makes it once for a whole run rather than once
 * per sample.
 *
 * <p>Mechanism and citations: RESEARCH-LOG.md R14.
 */
public final class IntegratedServerSensorProbe implements SensorProbe {

	/**
	 * The event fired at each sampled position, and the choice is forced by three separate
	 * constraints rather than picked (R14 point 5). It has vibration frequency 1, so it passes
	 * {@code canReceiveVibration}'s zero-frequency rejection. It is neither {@code BLOCK_PLACE}
	 * nor {@code BLOCK_DESTROY}, which that same method rejects outright at the sensor's own
	 * block — a special case that would otherwise produce one spurious disagreement at offset
	 * (0, 0, 0). And it carries the default notification radius of 16, which covers both sensor
	 * radii exactly (R14 point 4).
	 */
	private static final net.minecraft.core.Holder.Reference<GameEvent> PROBE_EVENT = GameEvent.STEP;

	/**
	 * No source entity and no affected state. This reduces {@code User#isValidVibration} to a
	 * single test — whether the event is in the listenable tag — because every other branch in
	 * it is guarded on one of those two being non-null (R14 point 5).
	 */
	private static final GameEvent.Context CONTEXT = new GameEvent.Context(null, null);

	private final ServerLevel level;

	public IntegratedServerSensorProbe(ServerLevel level) {
		this.level = level;
	}

	/**
	 * Checks, once before a run, that this sensor can answer at all.
	 *
	 * <p>Two gates make a sensor reject every event for reasons that have nothing to do with
	 * geometry, and both are checked here rather than being allowed to masquerade as
	 * disagreements. The sensor must be in the INACTIVE phase, because
	 * {@code canReceiveVibration} requires {@code SculkSensorBlock.canActivate}, which is
	 * exactly that test; a sensor mid-ACTIVE (30 ticks) or mid-COOLDOWN (10 ticks) refuses
	 * everything. And it must have no vibration already in flight, because that is the first
	 * thing {@code handleGameEvent} tests.
	 *
	 * <p>A third check has nothing to do with the sensor: whether the probe event is actually
	 * in {@code GameEventTags.VIBRATIONS}. That is tag data rather than source, so this project
	 * cannot verify it by reading (R14 point 5) — it is asserted at run time instead of assumed.
	 *
	 * @return an empty optional if the run may proceed, or the reason it may not
	 */
	public Optional<String> blockedReason(BlockPos sensorPos) {
		if (!PROBE_EVENT.is(GameEventTags.VIBRATIONS)) {
			return Optional.of("the probe event is not in the vibrations tag, so no sensor would ever listen to it");
		}

		if (VibrationSystem.getGameEventFrequency(PROBE_EVENT) == 0) {
			return Optional.of("the probe event has vibration frequency 0 and is rejected before any geometry is tested");
		}

		SculkSensorBlockEntity sensor = sensorAt(sensorPos);

		if (sensor == null) {
			return Optional.of("no sculk sensor block entity at " + sensorPos);
		}

		if (!SculkSensorBlock.canActivate(level.getBlockState(sensorPos))) {
			return Optional.of("the sensor is not INACTIVE (it is mid-active or in cooldown) and would reject every sample");
		}

		if (sensor.getVibrationData().getCurrentVibration() != null) {
			return Optional.of("the sensor already has a vibration in flight and would reject every sample");
		}

		return Optional.empty();
	}

	/**
	 * Triggers a vibration at the source position and reports whether the sensor accepted it.
	 *
	 * <p>The observation is not the one the R14 brief hypothesised, and the difference matters.
	 * Acceptance does <em>not</em> set {@code Data#getCurrentVibration()} — that is assigned
	 * only by the ticker, on a later tick. What acceptance does is add a candidate to the
	 * selector. So the sequence is: clear the selector, fire, ask the selector whether it now
	 * holds a candidate for this tick, then clear it again so the sensor is left exactly as it
	 * was found (R14 point 6).
	 *
	 * <p>{@code chosenCandidate} normally answers only for candidates from a previous tick, so
	 * it is asked about {@code gameTime + 1}. That is a pure read with no side effect, and it
	 * returns the {@code VibrationInfo}, whose position lets the observation be attributed to
	 * the sample that caused it rather than to a stray event that arrived in the same tick.
	 */
	@Override
	public Reaction test(int sensorX, int sensorY, int sensorZ, int sourceX, int sourceY, int sourceZ) {
		BlockPos sensorPos = new BlockPos(sensorX, sensorY, sensorZ);
		BlockPos sourcePos = new BlockPos(sourceX, sourceY, sourceZ);

		SculkSensorBlockEntity sensor = sensorAt(sensorPos);

		if (sensor == null) {
			return Reaction.INCONCLUSIVE;
		}

		VibrationSystem.Data data = sensor.getVibrationData();

		// Re-checked per sample, not only in blockedReason: a sample earlier in the run cannot
		// have left one behind, since each sample clears the selector, but a real event from
		// elsewhere in the world could have. Reporting that as "did not react" would invent a
		// disagreement.
		if (data.getCurrentVibration() != null) {
			return Reaction.INCONCLUSIVE;
		}

		VibrationSelector selector = data.getSelectionStrategy();
		selector.startOver();

		level.gameEvent(PROBE_EVENT, sourcePos, CONTEXT);

		Optional<VibrationInfo> candidate = selector.chosenCandidate(level.getGameTime() + 1);
		selector.startOver();

		if (candidate.isEmpty()) {
			return Reaction.DID_NOT_REACT;
		}

		// Attribution. A candidate whose origin is not the position just probed came from
		// somewhere else, and says nothing about this sample either way.
		BlockPos candidateOrigin = BlockPos.containing(candidate.get().pos());

		return candidateOrigin.equals(sourcePos) ? Reaction.REACTED : Reaction.INCONCLUSIVE;
	}

	private SculkSensorBlockEntity sensorAt(BlockPos pos) {
		return level.getBlockEntity(pos) instanceof SculkSensorBlockEntity sensor ? sensor : null;
	}
}
