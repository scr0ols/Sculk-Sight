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
import com.scr0ols.sculksight.solver.ShellSolver;

/**
 * The dev-only differential verification command: {@code /sculksight-verify <scene> [samples]}.
 *
 * <p>Aim at a sculk sensor and run it. The solver predicts, the game is asked, and any
 * disagreement is reported. This is the mechanism PLAN.md §5.1 calls "the single thing standing
 * between this mod and one that lies convincingly", and ADR-007 makes a passing run a v0.0 exit
 * gate.
 *
 * <p><b>Registered only in a development environment</b>, per ADR-019 — the gate is a real
 * check rather than an intention, so this command cannot exist in a shipped jar.
 *
 * <p><b>The solve reads the client level and the probe reads the server level, and that split
 * is the point rather than an accident.</b> Solving against the server world would test the
 * geometry while quietly assuming the client sees the same blocks. Solving against the client
 * world — which is what the mod will actually ship — means a disagreement catches a wrong shape
 * <em>or</em> a client that does not know what the server knows, and both of those would draw a
 * lie for the player.
 *
 * <p><b>The seed defaults to the sensor's position, and can be overridden.</b> Without an
 * explicit seed, {@link DifferentialVerifier} is seeded from the sensor's own packed block
 * position, so a run against the same sensor is reproducible — and, as a direct consequence,
 * re-running the same scene at the same sensor without an override probes the identical
 * positions rather than fresh ones (`OPEN-QUESTIONS.md` section 13). The optional third argument
 * exists so a scene can be resampled without moving the sensor, which is part of this
 * mechanism's evidentiary value rather than a convenience unrelated to it.
 */
public final class VerificationCommand {

	private VerificationCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> registerCommand(dispatcher));
	}

	private static void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(
				ClientCommands.literal("sculksight-verify")
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

		// ADR-019's first constraint, enforced rather than documented. hasSingleplayerServer()
		// is the right guard: it tests both isLocalServer and the field, where a bare null check
		// would not (R14 point 1).
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

		// The radius is derived through vanilla's own idiom and never read from
		// SculkSensorBlockEntity.VibrationUser.LISTENER_RANGE, which is a static 8 that the
		// calibrated sensor inherits while overriding the method to 16 (R1 point 3).
		GameEventListener listener = sensor.getListener();
		int radius = listener.getListenerRadius();

		ServerLevel serverLevel = server.getLevel(clientLevel.dimension());

		if (serverLevel == null) {
			return fail(source, "the integrated server has no level for this dimension.");
		}

		// Solved here, on the client thread, against the client's own view of the world - the
		// input the shipped mod will have. Detailed rather than plain: the verifier needs to
		// know which excluded positions were removed by occlusion rather than by range, and
		// ShellSolver computes that at no extra cost (see ShellSolution's javadoc).
		ShellSolution prediction = ShellSolver.solveDetailed(new LevelWorldView(clientLevel),
				sensorPos.getX(), sensorPos.getY(), sensorPos.getZ(), radius);

		// Defaults to the sensor's own position, exactly as before this argument existed, so an
		// unqualified run stays reproducible without anyone having to think about seeds. An
		// override lets the same scene be resampled at fresh positions without moving the sensor.
		long seed = seedOverride != null ? seedOverride : sensorPos.asLong();

		source.sendFeedback(Component.literal(
				"[sculksight] solving radius " + radius + " at " + sensorPos + ": "
						+ prediction.accepted().size() + " positions predicted in range ("
						+ prediction.occludedOut().size() + " occluded out). Probing " + samples
						+ " with seed " + seed + "..."));

		IntegratedServerSensorProbe probe = new IntegratedServerSensorProbe(serverLevel);

		// One hop for the whole run, not one per sample. Two reasons, and the second is the one
		// that makes the exit criteria affordable: crossing threads 200 times would be wasteful,
		// and running inside a single server task means the run happens between ticks, so the
		// game time is frozen and no block entity ticks part-way through. That is what keeps the
		// in-flight-vibration gate open for every sample (R14 point 8).
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
			// Re-asked on this side purely to get the reason text into the message; the run
			// itself already declined.
			return fail(source, "sensor not in a state to be probed. Wait for it to return to "
					+ "INACTIVE with no vibration in flight, then try again.");
		}

		source.sendFeedback(Component.literal("[sculksight] " + report.summary()));
		SculkSight.LOGGER.info("[sculksight-verify] {}", report.summary());

		if (report.clean()) {
			source.sendFeedback(Component.literal(
					"[sculksight] scene '" + scene + "' CLEAN over " + report.conclusive() + " conclusive samples."));
		} else if (report.conclusive() == 0) {
			source.sendFeedback(Component.literal(
					"[sculksight] scene '" + scene + "' proved NOTHING: every sample was inconclusive."));
		}

		return report.clean() ? 1 : 0;
	}

	private static int fail(FabricClientCommandSource source, String message) {
		source.sendFeedback(Component.literal("[sculksight] " + message));
		return 0;
	}
}
