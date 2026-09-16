package com.scr0ols.sculksight.verify;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationInfo;
import net.minecraft.world.level.gameevent.vibrations.VibrationSelector;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;

import com.scr0ols.sculksight.client.DetectorType;

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
	 * The event fired at a sensor or calibrated sensor, and the choice is forced by three
	 * separate constraints rather than picked (R14 point 5). It has vibration frequency 1, so it
	 * passes the sensor family's zero-frequency rejection. It is neither {@code BLOCK_PLACE} nor
	 * {@code BLOCK_DESTROY}, which that same method rejects outright at the sensor's own block —
	 * a special case that would otherwise produce one spurious disagreement at offset (0, 0, 0).
	 * And it carries the default notification radius of 16, which covers both sensor radii
	 * exactly (R14 point 4).
	 *
	 * <p>Not usable against a shrieker: a shrieker's {@code VibrationUser.getListenableEvents()}
	 * is {@code GameEventTags.SHRIEKER_CAN_LISTEN}, which the game's own tag data
	 * (`data/minecraft/tags/game_event/shrieker_can_listen.json`) restricts to
	 * {@code minecraft:sculk_sensor_tendrils_clicking} alone — {@code STEP} is not a member, so a
	 * shrieker would reject it before any geometry is tested. {@link #stimulusFor} picks the
	 * right event per detector rather than this one being reused for all three.
	 */
	private static final Holder.Reference<GameEvent> SENSOR_PROBE_EVENT = GameEvent.STEP;

	/**
	 * No source entity and no affected state. This reduces {@code User#isValidVibration} to a
	 * single test — whether the event is in the listenable tag — because every other branch in
	 * it is guarded on one of those two being non-null (R14 point 5). Fine for the sensor family,
	 * whose {@code canReceiveVibration} never inspects the source entity either.
	 */
	private static final GameEvent.Context SENSOR_CONTEXT = new GameEvent.Context(null, null);

	private final ServerLevel level;

	public IntegratedServerSensorProbe(ServerLevel level) {
		this.level = level;
	}

	/** A block entity classified as one of the three detector types, paired with its state. */
	private record Detector(DetectorType type, VibrationSystem system, BlockState state) {
	}

	/** What to fire at a sampled position, and the context it must carry to be accepted. */
	private record ProbeStimulus(Holder.Reference<GameEvent> event, GameEvent.Context context) {
	}

	/**
	 * Picks the event and context that can actually reach a given detector type's
	 * {@code canReceiveVibration}, established from source rather than assumed (RESEARCH-LOG.md
	 * R14's follow-up for shriekers).
	 *
	 * <p>The sensor family accepts a plain {@code STEP} with no source entity, exactly as before
	 * shriekers were supported. A shrieker is different on both axes: its listenable-events tag
	 * accepts only {@code sculk_sensor_tendrils_clicking} (see {@link #SENSOR_PROBE_EVENT}'s
	 * javadoc), and {@code SculkShriekerBlockEntity.VibrationUser.canReceiveVibration} separately
	 * requires {@code SculkShriekerBlockEntity.tryGetPlayer(context.sourceEntity()) != null} — a
	 * real player, directly or as a controlling passenger, projectile owner, or item owner. The
	 * integrated server always has the singleplayer client's own player loaded, so the first
	 * entry in {@code level.players()} supplies it; the optional is empty only if that is ever
	 * not true, which {@link #blockedReason} reports rather than risking a probe with no source.
	 */
	private Optional<ProbeStimulus> stimulusFor(DetectorType type) {
		return switch (type) {
			case NORMAL_SENSOR, CALIBRATED_SENSOR -> Optional.of(new ProbeStimulus(SENSOR_PROBE_EVENT, SENSOR_CONTEXT));
			case SHRIEKER -> level.players().stream().findFirst()
					.map(player -> new ProbeStimulus(GameEvent.SCULK_SENSOR_TENDRILS_CLICKING, GameEvent.Context.of(player)));
		};
	}

	/**
	 * Whether the stimulus a sensor-family event carries needs a non-zero vibration frequency to
	 * be accepted at all. Sensor and calibrated sensor's own {@code canReceiveVibration} rejects
	 * frequency 0 explicitly; a shrieker's does not test frequency, which matters because
	 * {@code sculk_sensor_tendrils_clicking} carries frequency 0 (it has no entry in
	 * {@code VibrationSystem.VIBRATION_FREQUENCY_FOR_EVENT}, whose map defaults unlisted events to
	 * 0) and would otherwise be wrongly rejected here before ever reaching the game.
	 */
	static boolean requiresNonZeroFrequency(DetectorType type) {
		return type != DetectorType.SHRIEKER;
	}

	/**
	 * The equivalent of {@code SculkSensorBlock.canActivate} for each detector type: whether the
	 * block is not already busy responding to an earlier trigger and would accept a new one.
	 * The sensor family answers through its {@code PHASE} property, established as
	 * {@code SculkSensorBlock.canActivate}. A shrieker has no phase; its own
	 * {@code VibrationUser.canReceiveVibration} instead rejects while
	 * {@code SculkShriekerBlock.SHRIEKING} is true, so that boolean state property is this
	 * project's equivalent readiness gate for it (established from source, not assumed).
	 */
	static boolean isReadyToActivate(DetectorType type, BlockState state) {
		return switch (type) {
			case NORMAL_SENSOR, CALIBRATED_SENSOR -> SculkSensorBlock.canActivate(state);
			case SHRIEKER -> !state.getValue(SculkShriekerBlock.SHRIEKING);
		};
	}

	/**
	 * Checks, once before a run, that this detector can answer at all.
	 *
	 * <p>Three gates make a detector reject every event for reasons that have nothing to do with
	 * geometry, and all three are checked here rather than being allowed to masquerade as
	 * disagreements. It must not already be mid-activation ({@link #isReadyToActivate}, the
	 * sensor family's INACTIVE phase or the shrieker's non-SHRIEKING state). It must have no
	 * vibration already in flight, because that is the first thing {@code handleGameEvent} tests
	 * for every detector type alike. And the chosen stimulus must actually be one this detector's
	 * own {@code VibrationUser.getListenableEvents()} tag accepts — read from the live user
	 * rather than a hardcoded tag, so this generalises to whichever detector is aimed at instead
	 * of assuming the sensor family's own {@code GameEventTags.VIBRATIONS}.
	 *
	 * @return an empty optional if the run may proceed, or the reason it may not
	 */
	public Optional<String> blockedReason(BlockPos sensorPos) {
		Optional<Detector> maybeDetector = detectorAt(sensorPos);

		if (maybeDetector.isEmpty()) {
			return Optional.of("no sculk sensor, calibrated sculk sensor or sculk shrieker block entity at " + sensorPos);
		}

		Detector detector = maybeDetector.get();
		Optional<ProbeStimulus> maybeStimulus = stimulusFor(detector.type());

		if (maybeStimulus.isEmpty()) {
			return Optional.of("no server player is loaded to source a shrieker probe event");
		}

		ProbeStimulus stimulus = maybeStimulus.get();

		if (!stimulus.event().is(detector.system().getVibrationUser().getListenableEvents())) {
			return Optional.of("the probe event is not in this detector's own listenable-events tag, so it would never listen to it");
		}

		if (requiresNonZeroFrequency(detector.type()) && VibrationSystem.getGameEventFrequency(stimulus.event()) == 0) {
			return Optional.of("the probe event has vibration frequency 0 and is rejected before any geometry is tested");
		}

		if (!isReadyToActivate(detector.type(), detector.state())) {
			return Optional.of("the detector is mid-activation (sensor phase, or shrieker still shrieking) and would reject every sample");
		}

		if (detector.system().getVibrationData().getCurrentVibration() != null) {
			return Optional.of("the detector already has a vibration in flight and would reject every sample");
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

		Optional<Detector> maybeDetector = detectorAt(sensorPos);

		if (maybeDetector.isEmpty()) {
			return Reaction.INCONCLUSIVE;
		}

		Detector detector = maybeDetector.get();
		Optional<ProbeStimulus> maybeStimulus = stimulusFor(detector.type());

		if (maybeStimulus.isEmpty()) {
			return Reaction.INCONCLUSIVE;
		}

		VibrationSystem.Data data = detector.system().getVibrationData();

		// Re-checked per sample, not only in blockedReason: a sample earlier in the run cannot
		// have left one behind, since each sample clears the selector, but a real event from
		// elsewhere in the world could have. Reporting that as "did not react" would invent a
		// disagreement.
		if (data.getCurrentVibration() != null) {
			return Reaction.INCONCLUSIVE;
		}

		VibrationSelector selector = data.getSelectionStrategy();
		selector.startOver();

		ProbeStimulus stimulus = maybeStimulus.get();
		level.gameEvent(stimulus.event(), sourcePos, stimulus.context());

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

	/**
	 * Classifies the block entity at {@code pos} into one of the three detector types this mod
	 * supports, through {@link DetectorType#of}, the same classification {@code ShellRenderer}
	 * and {@code SensorIndex} use — so a catalyst or any other {@code VibrationSystem} the game
	 * ever adds is excluded here exactly as it is excluded from a rendered shell.
	 */
	private Optional<Detector> detectorAt(BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof VibrationSystem system)) {
			return Optional.empty();
		}

		BlockState state = level.getBlockState(pos);
		return DetectorType.of(state.getBlock()).map(type -> new Detector(type, system, state));
	}
}
