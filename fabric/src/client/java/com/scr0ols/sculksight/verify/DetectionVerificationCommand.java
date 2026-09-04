package com.scr0ols.sculksight.verify;

import java.util.Optional;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import com.scr0ols.sculksight.SculkSight;
import com.scr0ols.sculksight.client.LevelWorldView;
import com.scr0ols.sculksight.solver.ShellSolution;

/**
 * Mode C's differential verification command:
 * {@code /sculksight-verify-detection <scene> [samples] [seed]}.
 *
 * <p>Aim at a sculk sensor and run it, exactly as for {@link VerificationCommand}. What changes
 * is which of this mod's answers is on trial: mode A's command asks {@code ShellSolver}, this one
 * asks {@code SensorDetector}, through {@link DetectionScan}. That class's own javadoc carries
 * why the distinction is not pedantic and what a clean run here does and does not establish.
 * TESTING-STRATEGY.md section 4's v0.1 phase gate is what asks for this: it names both modes, and
 * mode A's passing runs say nothing about mode C's own code.
 *
 * <p><b>Everything below the prediction is mode A's mechanism unchanged, deliberately.</b> The
 * sampling, the three-way stratification, the outcome classification and the report are
 * {@link DifferentialVerifier}'s; the trigger and the observation are
 * {@link IntegratedServerSensorProbe}'s, so R14's answer is used rather than re-derived. Extending
 * the pattern rather than writing a second one means a fix to either half reaches both modes, and
 * means the two modes' evidence is comparable because it was produced the same way.
 *
 * <p><b>Registered only in a development environment</b>, per ADR-019, for the same reason and
 * through the same gate as mode A's command: the probe reaches server-side state, which this mod's
 * client-side-only distribution model (ADR-006) permits nowhere else.
 *
 * <p><b>Separate command rather than an argument on the existing one.</b> Mode A's command and its
 * recorded runs are this project's evidence that the shell is correct, and its argument shape is
 * quoted in the archive's evidence tables. A new mode taking a slot in it would change the shape
 * of a command whose past invocations are part of the record, for no benefit over a second name.
 */
public final class DetectionVerificationCommand {

	private DetectionVerificationCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> registerCommand(dispatcher));
	}

	private static void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(
				ClientCommands.literal("sculksight-verify-detection")
						.then(ClientCommands.argument("scene", StringArgumentType.word())
								.executes(context -> run(context.getSource(),
										StringArgumentType.getString(context, "scene"), 200, null))
								.then(ClientCommands.argument("samples", IntegerArgumentType.integer(2, 20000))
										.executes(context -> run(context.getSource(),
												StringArgumentType.getString(context, "scene"),
												IntegerArgumentType.getInteger(context, "samples"), null))
										.then(ClientCommands.argument("seed", LongArgumentType.longArg())
												.executes(context -> run(context.getSource(),
														StringArgumentType.getString(context, "scene"),
														IntegerArgumentType.getInteger(context, "samples"),
														LongArgumentType.getLong(context, "seed")))))));
	}

	private static int run(FabricClientCommandSource source, String scene, int samples, Long seedOverride) {
		Minecraft client = source.getClient();

		// ADR-019's first constraint, enforced rather than documented, and hasSingleplayerServer()
		// for the reason VerificationCommand gives: it tests both isLocalServer and the field,
		// where a bare null check would not (R14 point 1).
		if (!client.hasSingleplayerServer()) {
			return fail(source, "no integrated server: this command cannot run against a remote server.");
		}

		IntegratedServer server = client.getSingleplayerServer();

		if (server == null) {
			return fail(source, "no integrated server.");
		}

		HitResult hit = client.hitResult;

		if (!(hit instanceof BlockHitResult blockHit)) {
			return fail(source, "not aiming at a block.");
		}

		BlockPos sensorPos = blockHit.getBlockPos();
		ClientLevel clientLevel = source.getLevel();

		if (!(clientLevel.getBlockEntity(sensorPos) instanceof SculkSensorBlockEntity sensor)) {
			return fail(source, "the targeted block is not a sculk sensor.");
		}

		// Derived through vanilla's own idiom, never from the static LISTENER_RANGE the
		// calibrated sensor inherits while overriding the method (R1 point 3) - the same
		// derivation SensorIndex performs at insert time, so this command and the indicator agree
		// about the radius by construction rather than by coincidence.
		GameEventListener listener = sensor.getListener();
		int radius = listener.getListenerRadius();

		ServerLevel serverLevel = server.getLevel(clientLevel.dimension());

		if (serverLevel == null) {
			return fail(source, "the integrated server has no level for this dimension.");
		}

		// Scanned here, on the client thread, against the client's own view of the world: the
		// input the shipped indicator will have, and the thread R16 says a ClientLevel may be read
		// from. See DetectionScan for why the scan is eager rather than answering during the run.
		ShellSolution prediction = DetectionScan.scan(new LevelWorldView(clientLevel),
				sensorPos.getX(), sensorPos.getY(), sensorPos.getZ(), radius);

		long seed = seedOverride != null ? seedOverride : sensorPos.asLong();

		source.sendFeedback(Component.literal(
				"[sculksight] mode C: scanning radius " + radius + " at " + sensorPos + ": "
						+ prediction.accepted().size() + " positions the detector calls detected ("
						+ prediction.occludedOut().size() + " occluded out). Probing " + samples
						+ " with seed " + seed + "..."));

		IntegratedServerSensorProbe probe = new IntegratedServerSensorProbe(serverLevel);

		// One hop for the whole run, not one per sample, and the second of VerificationCommand's
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
			return fail(source, "sensor not in a state to be probed. Wait for it to return to "
					+ "INACTIVE with no vibration in flight, then try again.");
		}

		source.sendFeedback(Component.literal("[sculksight] " + report.summary()));
		SculkSight.LOGGER.info("[sculksight-verify-detection] {}", report.summary());

		if (report.clean()) {
			source.sendFeedback(Component.literal(
					"[sculksight] mode C scene '" + scene + "' CLEAN over " + report.conclusive()
							+ " conclusive samples."));
		} else if (report.conclusive() == 0) {
			source.sendFeedback(Component.literal(
					"[sculksight] mode C scene '" + scene + "' proved NOTHING: every sample was inconclusive."));
		}

		return report.clean() ? 1 : 0;
	}

	private static int fail(FabricClientCommandSource source, String message) {
		source.sendFeedback(Component.literal("[sculksight] " + message));
		return 0;
	}
}
