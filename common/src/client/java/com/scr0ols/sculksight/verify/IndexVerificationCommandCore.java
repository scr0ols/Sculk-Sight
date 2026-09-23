package com.scr0ols.sculksight.verify;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

import com.scr0ols.sculksight.SculkSight;
import com.scr0ols.sculksight.client.SensorIndex;

/** The dev-only index verification mechanism behind {@code /sculksight-verify-index}. */
public final class IndexVerificationCommandCore {

	public static final int MAX_CHUNK_RADIUS = 32;

	private IndexVerificationCommandCore() {
	}

	/** Sweeps around the player and diffs the result against the index, returning 1 for a clean run and 0 otherwise. */
	public static int run(Minecraft client, Consumer<String> feedback, int chunkRadius) {
		LocalPlayer player = client.player;
		ClientLevel level = client.level;

		if (player == null || level == null) {
			return fail(feedback, "no player or level.");
		}

		BlockPos center = player.blockPosition();

		feedback.accept("[sculksight] sweeping " + chunkRadius + " chunks around " + center + "...");

		Map<WorldPosition, Integer> groundTruth = IndexSweep.sweep(level, center, chunkRadius);

		Map<WorldPosition, Integer> index = new HashMap<>();

		for (Map.Entry<BlockPos, Integer> entry : SensorIndex.snapshot().entrySet()) {
			BlockPos pos = entry.getKey();

			if (IndexSweep.withinSweep(pos, center, chunkRadius)) {
				index.put(new WorldPosition(pos.getX(), pos.getY(), pos.getZ()), entry.getValue());
			}
		}

		IndexVerificationReport report = IndexVerifier.diff(groundTruth, index);

		feedback.accept("[sculksight] " + report.summary());
		SculkSight.LOGGER.info("[sculksight-verify-index] {}", report.summary());

		if (report.clean()) {
			feedback.accept("[sculksight] index CLEAN over " + report.sweptSensors() + " swept sensors within "
					+ chunkRadius + " chunks.");
		} else if (report.sweptSensors() == 0) {
			feedback.accept("[sculksight] proved NOTHING: the sweep found no sensors within " + chunkRadius
					+ " chunks.");
		}

		return report.clean() ? 1 : 0;
	}

	private static int fail(Consumer<String> feedback, String message) {
		feedback.accept("[sculksight] " + message);
		return 0;
	}
}
