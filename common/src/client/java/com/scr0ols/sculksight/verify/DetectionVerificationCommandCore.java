package com.scr0ols.sculksight.verify;

import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import com.scr0ols.sculksight.SculkSight;
import com.scr0ols.sculksight.client.LevelWorldView;
import com.scr0ols.sculksight.solver.ShellSolution;

/**
 * Mode C's differential verification mechanism, behind {@code /sculksight-verify-detection},
 * split out of the Fabric-only {@code DetectionVerificationCommand} by DECISIONS.md ADR-043's
 * follow-up split.
 *
 * <p>Aim at a sculk sensor and run it, exactly as for {@link VerificationCommandCore}. What
 * changes is which of this mod's answers is on trial: mode A's command asks {@code ShellSolver},
 * this one asks {@code SensorDetector}, through {@link DetectionScan}. That class's own javadoc
 * carries why the distinction is not pedantic and what a clean run here does and does not
 * establish. TESTING-STRATEGY.md section 4's v0.1 phase gate is what asks for this: it names both
 * modes, and mode A's passing runs say nothing about mode C's own code.
 *
 * <p><b>Everything below the prediction is mode A's mechanism unchanged, deliberately.</b> The
 * sampling, the three-way stratification, the outcome classification and the report are
 * {@link DifferentialVerifier}'s; the trigger and the observation are
 * {@link IntegratedServerSensorProbe}'s, so R14's answer is used rather than re-derived. Extending
 * the pattern rather than writing a second one means a fix to either half reaches both modes, and
 * means the two modes' evidence is comparable because it was produced the same way.
 *
 * <p><b>{@code client} and {@code feedback} stand in for a command source, the same way and for
 * the same reason {@link VerificationCommandCore}'s own javadoc explains.</b>
 */
public final class DetectionVerificationCommandCore {

	private DetectionVerificationCommandCore() {
	}

	/** @return a Brigadier-style status: 1 for a clean run, 0 for anything else. */
	public static int run(Minecraft client, Consumer<String> feedback, String scene, int samples,
			Long seedOverride) {

		// ADR-019's first constraint, enforced rather than documented, and hasSingleplayerServer()
		// for the reason VerificationCommandCore gives: it tests both isLocalServer and the field,
		// where a bare null check would not (R14 point 1).
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

		if (clientLevel == null || !(clientLevel.getBlockEntity(sensorPos) instanceof SculkSensorBlockEntity sensor)) {
			return fail(feedback, "the targeted block is not a sculk sensor.");
		}

		// Derived through vanilla's own idiom, never from the static LISTENER_RANGE the
		// calibrated sensor inherits while overriding the method (R1 point 3) - the same
		// derivation SensorIndex performs at insert time, so this command and the indicator agree
		// about the radius by construction rather than by coincidence.
		GameEventListener listener = sensor.getListener();
		int radius = listener.getListenerRadius();

		ServerLevel serverLevel = server.getLevel(clientLevel.dimension());

		if (serverLevel == null) {
			return fail(feedback, "the integrated server has no level for this dimension.");
		}

		// Scanned here, on the client thread, against the client's own view of the world: the
		// input the shipped indicator will have, and the thread R16 says a ClientLevel may be read
		// from. See DetectionScan for why the scan is eager rather than answering during the run.
		ShellSolution prediction = DetectionScan.scan(new LevelWorldView(clientLevel),
				sensorPos.getX(), sensorPos.getY(), sensorPos.getZ(), radius);

		long seed = seedOverride != null ? seedOverride : sensorPos.asLong();

		feedback.accept("[sculksight] mode C: scanning radius " + radius + " at " + sensorPos + ": "
				+ prediction.accepted().size() + " positions the detector calls detected ("
				+ prediction.occludedOut().size() + " occluded out). Probing " + samples
				+ " with seed " + seed + "...");

		IntegratedServerSensorProbe probe = new IntegratedServerSensorProbe(serverLevel);

		// One hop for the whole run, not one per sample, and the second of VerificationCommandCore's
		// two reasons is the one that matters here too: inside a single server task the run
		// happens between ticks, so no block entity ticks part-way through and the
		// in-flight-vibration gate stays open for every sample (R14 point 8).
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
			return fail(feedback, "sensor not in a state to be probed. Wait for it to return to "
					+ "INACTIVE with no vibration in flight, then try again.");
		}

		feedback.accept("[sculksight] " + report.summary());
		SculkSight.LOGGER.info("[sculksight-verify-detection] {}", report.summary());

		if (report.clean()) {
			feedback.accept("[sculksight] mode C scene '" + scene + "' CLEAN over " + report.conclusive()
					+ " conclusive samples.");
		} else if (report.conclusive() == 0) {
			feedback.accept("[sculksight] mode C scene '" + scene + "' proved NOTHING: every sample was "
					+ "inconclusive.");
		}

		return report.clean() ? 1 : 0;
	}

	private static int fail(Consumer<String> feedback, String message) {
		feedback.accept("[sculksight] " + message);
		return 0;
	}
}
