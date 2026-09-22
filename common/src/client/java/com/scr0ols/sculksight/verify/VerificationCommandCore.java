package com.scr0ols.sculksight.verify;

import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import com.scr0ols.sculksight.SculkSight;
import com.scr0ols.sculksight.client.DetectorType;
import com.scr0ols.sculksight.client.LevelWorldView;
import com.scr0ols.sculksight.solver.ShellSolution;
import com.scr0ols.sculksight.solver.ShellSolver;

/** The dev-only differential verification mechanism behind {@code /sculksight-verify}. */
public final class VerificationCommandCore {

	private VerificationCommandCore() {
	}

	/** Runs one verification against the targeted detector, returning 1 for a clean run and 0 otherwise. */
	public static int run(Minecraft client, Consumer<String> feedback, String scene, int samples,
			Long seedOverride) {

		if (!client.hasSingleplayerServer()) {
			return fail(feedback, "no integrated server: this command cannot run against a remote server.");
		}

		IntegratedServer server = client.getSingleplayerServer();

		if (server == null) {
			return fail(feedback, "no integrated server.");
		}

		HitResult hit = client.hitResult;

		if (!(hit instanceof BlockHitResult blockHit)) {
			return fail(feedback, "not aiming at a block.");
		}

		BlockPos sensorPos = blockHit.getBlockPos();
		ClientLevel clientLevel = client.level;
		BlockEntity blockEntity = clientLevel != null ? clientLevel.getBlockEntity(sensorPos) : null;

		if (!(blockEntity instanceof GameEventListener.Provider<?> provider)
				|| DetectorType.of(blockEntity.getBlockState().getBlock()).isEmpty()) {
			return fail(feedback, "the targeted block is not a sculk sensor, calibrated sculk sensor or sculk shrieker.");
		}

		GameEventListener listener = provider.getListener();
		int radius = listener.getListenerRadius();

		ServerLevel serverLevel = server.getLevel(clientLevel.dimension());

		if (serverLevel == null) {
			return fail(feedback, "the integrated server has no level for this dimension.");
		}

		ShellSolution prediction = ShellSolver.solveDetailed(new LevelWorldView(clientLevel),
				sensorPos.getX(), sensorPos.getY(), sensorPos.getZ(), radius);

		long seed = seedOverride != null ? seedOverride : sensorPos.asLong();

		feedback.accept("[sculksight] solving radius " + radius + " at " + sensorPos + ": "
				+ prediction.accepted().size() + " positions predicted in range ("
				+ prediction.occludedOut().size() + " occluded out). Probing " + samples
				+ " with seed " + seed + "...");

		IntegratedServerSensorProbe probe = new IntegratedServerSensorProbe(serverLevel);

		VerificationReport report = server.submit(() -> {
			Optional<String> blocked = probe.blockedReason(sensorPos);

			if (blocked.isPresent()) {
				return null;
			}

			return DifferentialVerifier.verify(scene, prediction,
					sensorPos.getX(), sensorPos.getY(), sensorPos.getZ(),
					probe, samples, seed);
		}).join();

		if (report == null) {
			return fail(feedback, "detector not in a state to be probed. Wait for it to finish any "
					+ "current activation (sensor phase, or shrieker shriek) with no vibration in "
					+ "flight, then try again.");
		}

		feedback.accept("[sculksight] " + report.summary());
		SculkSight.LOGGER.info("[sculksight-verify] {}", report.summary());

		if (report.clean()) {
			feedback.accept("[sculksight] scene '" + scene + "' CLEAN over " + report.conclusive()
					+ " conclusive samples.");
		} else if (report.conclusive() == 0) {
			feedback.accept("[sculksight] scene '" + scene + "' proved NOTHING: every sample was inconclusive.");
		}

		return report.clean() ? 1 : 0;
	}

	private static int fail(Consumer<String> feedback, String message) {
		feedback.accept("[sculksight] " + message);
		return 0;
	}
}
