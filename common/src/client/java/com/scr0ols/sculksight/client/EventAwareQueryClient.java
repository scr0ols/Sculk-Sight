package com.scr0ols.sculksight.client;

import java.util.ArrayList;
import java.util.List;

import com.scr0ols.sculksight.audit.RadiusAudit.AuditedSensor;
import com.scr0ols.sculksight.event.CalibratedTuning;
import com.scr0ols.sculksight.event.EventAwareQueryCore;
import com.scr0ols.sculksight.event.EventAwareSensorCandidate;
import com.scr0ols.sculksight.event.VibrationEventType;
import com.scr0ols.sculksight.event.VibrationSourceContext;
import com.scr0ols.sculksight.event.VibrationSourceKind;
import com.scr0ols.sculksight.solver.SensorDetector;
import com.scr0ols.sculksight.solver.WorldView;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.CalibratedSculkSensorBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Builds the event-aware preview shown by the existing find command. */
public final class EventAwareQueryClient {
	private EventAwareQueryClient() {
	}

	public static String describe(ClientLevel level, LocalPlayer player, List<AuditedSensor> sensors) {
		WorldView world = new LevelWorldView(level);
		VibrationSourceKind sourceKind = player.isSpectator()
				? VibrationSourceKind.SPECTATOR : VibrationSourceKind.NONE;
		VibrationSourceContext source = new VibrationSourceContext(false, sourceKind, player.isCrouching());
		List<EventAwareSensorCandidate> candidates = new ArrayList<>();

		for (AuditedSensor sensor : sensors) {
			BlockPos pos = new BlockPos(sensor.position().x(), sensor.position().y(), sensor.position().z());
			boolean geometryReachable = SensorDetector.isDetectedAt(world,
					player.blockPosition().getX(), player.blockPosition().getY(), player.blockPosition().getZ(),
					pos.getX(), pos.getY(), pos.getZ(), sensor.listenerRadius());
			CalibratedTuning tuning = null;
			if (sensor.type() == DetectorType.CALIBRATED_SENSOR) {
				BlockState state = level.getBlockState(pos);
				tuning = ClientCalibrationReader.read(level, pos,
						state.getValue(CalibratedSculkSensorBlock.FACING), true);
			}
			candidates.add(new EventAwareSensorCandidate(distanceSquared(player, pos), geometryReachable,
					sensor.type() == DetectorType.CALIBRATED_SENSOR, tuning, source));
		}

		EventAwareQueryCore.Summary summary = EventAwareQueryCore.summarize(
				VibrationEventType.BOUNCE.metadata(), candidates);
		return "Bounce preview (event layer only): " + summary.accepted() + " eligible, "
				+ summary.geometryBlocked() + " geometry-blocked, " + summary.invalidSource()
				+ " invalid source, " + summary.outsideNotificationReach() + " outside notification reach, "
				+ summary.frequencyMismatch() + " frequency-mismatched, " + summary.unavailable()
				+ " unknown. This assumes the loaded client state is synchronized; it is not confirmation of vanilla activation.";
	}

	private static double distanceSquared(LocalPlayer player, BlockPos sensor) {
		double dx = player.getX() - (sensor.getX() + 0.5);
		double dy = player.getY() - (sensor.getY() + 0.5);
		double dz = player.getZ() - (sensor.getZ() + 0.5);
		return dx * dx + dy * dy + dz * dz;
	}
}
