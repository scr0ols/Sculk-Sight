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

/** Triggers a vibration on the integrated server and reads back whether the sensor accepted it. */
public final class IntegratedServerSensorProbe implements SensorProbe {

	private static final Holder.Reference<GameEvent> SENSOR_PROBE_EVENT = GameEvent.STEP;

	private static final GameEvent.Context SENSOR_CONTEXT = new GameEvent.Context(null, null);

	private final ServerLevel level;

	public IntegratedServerSensorProbe(ServerLevel level) {
		this.level = level;
	}

	private record Detector(DetectorType type, VibrationSystem system, BlockState state) {
	}

	private record ProbeStimulus(Holder.Reference<GameEvent> event, GameEvent.Context context) {
	}

	private Optional<ProbeStimulus> stimulusFor(DetectorType type) {
		return switch (type) {
			case NORMAL_SENSOR, CALIBRATED_SENSOR -> Optional.of(new ProbeStimulus(SENSOR_PROBE_EVENT, SENSOR_CONTEXT));
			case SHRIEKER -> level.players().stream().findFirst()
					.map(player -> new ProbeStimulus(GameEvent.SCULK_SENSOR_TENDRILS_CLICKING, GameEvent.Context.of(player)));
		};
	}

	static boolean requiresNonZeroFrequency(DetectorType type) {
		return type != DetectorType.SHRIEKER;
	}

	static boolean isReadyToActivate(DetectorType type, BlockState state) {
		return switch (type) {
			case NORMAL_SENSOR, CALIBRATED_SENSOR -> SculkSensorBlock.canActivate(state);
			case SHRIEKER -> !state.getValue(SculkShriekerBlock.SHRIEKING);
		};
	}

	/** Checks, once before a run, that this detector can answer at all. */
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

	/** Triggers a vibration at the source position and reports whether the sensor accepted it. */
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

		BlockPos candidateOrigin = BlockPos.containing(candidate.get().pos());

		return candidateOrigin.equals(sourcePos) ? Reaction.REACTED : Reaction.INCONCLUSIVE;
	}

	private Optional<Detector> detectorAt(BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof VibrationSystem system)) {
			return Optional.empty();
		}

		BlockState state = level.getBlockState(pos);
		return DetectorType.of(state.getBlock()).map(type -> new Detector(type, system, state));
	}
}
