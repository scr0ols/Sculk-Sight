package com.scr0ols.sculksight.verify;

import java.util.HashMap;
import java.util.Map;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import com.scr0ols.sculksight.SculkSight;
import com.scr0ols.sculksight.client.SensorIndex;

/**
 * The dev-only mechanism DECISIONS.md ADR-041 requires:
 * {@code /sculksight-verify-index <chunkRadius>}.
 *
 * <p>Stand anywhere and run it. Unlike {@link VerificationCommand} and
 * {@link DetectionVerificationCommand}, this command does not aim at one sensor and does not put
 * this mod's geometry on trial at all - {@code SensorDetector} and {@code DetectionScan} are not
 * touched anywhere on this path. What it checks is {@code SensorIndex} itself: does the mod-side
 * cache of "which sensors exist and at what radius" agree with a sensor-by-sensor sweep of the
 * live world around the player, built independently of the cache. ADR-041 point 3 explains why
 * this needed a mechanism of its own rather than being read off a clean {@code SensorDetector} run
 * (`ARCHITECTURE.md` §10.5): a missed or stale index callback is a lifecycle bug that geometry
 * verification cannot see, because it produces "you are not detected" - output identical to a
 * correct negative.
 *
 * <p><b>Deterministic, not sampled.</b> {@link DifferentialVerifier}'s mechanism draws a random
 * subset because probing the live game is comparatively expensive (a server round trip per
 * sample). Sweeping and diffing two in-memory maps is not, so this command checks every entry
 * within {@code chunkRadius} rather than a sample of them - there is no seed argument because
 * there is nothing stochastic to reproduce.
 *
 * <p><b>What is verified, and what is not, stated because it is easy to conflate the two
 * differential mechanisms this mod now has.</b> A clean run here is evidence that
 * {@code SensorIndex}'s live contents matched an independent sweep at the moment the command ran.
 * It is not evidence about {@code SensorDetector}'s geometry - that is §10.5's job - and it is not
 * evidence about {@code DetectionIndicator}'s aggregation, which ADR-041 point 3 keeps deliberately
 * out of scope for any live-world mechanism.
 *
 * <p><b>Registered only in a development environment</b>, per ADR-019 and ADR-041 point 4, the
 * same gate and the same shape {@code /sculksight-verify} and {@code /sculksight-verify-detection}
 * already use for their own dev-only mechanisms.
 */
public final class IndexVerificationCommand {

	// An arbitrary safety cap on how much a single invocation sweeps, not a game-derived
	// constant - IndexSweep's own javadoc explains why chunkRadius has no default to fall back to
	// instead, so a ceiling on the explicit argument is what keeps a mistyped huge radius from
	// walking an unreasonable number of columns.
	private static final int MAX_CHUNK_RADIUS = 32;

	private IndexVerificationCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> registerCommand(dispatcher));
	}

	private static void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(
				ClientCommands.literal("sculksight-verify-index")
						.then(ClientCommands.argument("chunkRadius", IntegerArgumentType.integer(1, MAX_CHUNK_RADIUS))
								.executes(context -> run(context.getSource(),
										IntegerArgumentType.getInteger(context, "chunkRadius")))));
	}

	private static int run(FabricClientCommandSource source, int chunkRadius) {
		Minecraft client = source.getClient();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;

		if (player == null || level == null) {
			return fail(source, "no player or level.");
		}

		BlockPos center = player.blockPosition();

		source.sendFeedback(Component.literal(
				"[sculksight] sweeping " + chunkRadius + " chunks around " + center + "..."));

		// The ground truth: a fresh sweep, built without going through SensorIndex at all. See
		// IndexSweep's own javadoc for why that independence is the whole point of this command.
		Map<WorldPosition, Integer> groundTruth = IndexSweep.sweep(level, center, chunkRadius);

		// SensorIndex filtered to the same bound the sweep actually covered, so an entry the
		// index legitimately holds outside this run's radius is not reported as stale.
		Map<WorldPosition, Integer> index = new HashMap<>();

		for (Map.Entry<BlockPos, Integer> entry : SensorIndex.snapshot().entrySet()) {
			BlockPos pos = entry.getKey();

			if (IndexSweep.withinSweep(pos, center, chunkRadius)) {
				index.put(new WorldPosition(pos.getX(), pos.getY(), pos.getZ()), entry.getValue());
			}
		}

		IndexVerificationReport report = IndexVerifier.diff(groundTruth, index);

		source.sendFeedback(Component.literal("[sculksight] " + report.summary()));
		SculkSight.LOGGER.info("[sculksight-verify-index] {}", report.summary());

		if (report.clean()) {
			source.sendFeedback(Component.literal(
					"[sculksight] index CLEAN over " + report.sweptSensors() + " swept sensors within "
							+ chunkRadius + " chunks."));
		} else if (report.sweptSensors() == 0) {
			source.sendFeedback(Component.literal(
					"[sculksight] proved NOTHING: the sweep found no sensors within " + chunkRadius
							+ " chunks."));
		}

		return report.clean() ? 1 : 0;
	}

	private static int fail(FabricClientCommandSource source, String message) {
		source.sendFeedback(Component.literal("[sculksight] " + message));
		return 0;
	}
}
