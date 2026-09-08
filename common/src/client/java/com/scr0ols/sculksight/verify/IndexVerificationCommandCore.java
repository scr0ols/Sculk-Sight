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

/**
 * The dev-only mechanism DECISIONS.md ADR-041 requires, behind {@code /sculksight-verify-index},
 * split out of the Fabric-only {@code IndexVerificationCommand} by DECISIONS.md ADR-043's
 * follow-up split.
 *
 * <p>Stand anywhere and run it. Unlike {@link VerificationCommandCore} and
 * {@link DetectionVerificationCommandCore}, this command does not aim at one sensor and does not
 * put this mod's geometry on trial at all - {@code SensorDetector} and {@code DetectionScan} are
 * not touched anywhere on this path. What it checks is {@code SensorIndex} itself: does the
 * mod-side cache of "which sensors exist and at what radius" agree with a sensor-by-sensor sweep
 * of the live world around the player, built independently of the cache. ADR-041 point 3 explains
 * why this needed a mechanism of its own rather than being read off a clean {@code SensorDetector}
 * run (`ARCHITECTURE.md` §10.5): a missed or stale index callback is a lifecycle bug that geometry
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
 * <p><b>{@code client} and {@code feedback} stand in for a command source, the same way and for
 * the same reason {@link VerificationCommandCore}'s own javadoc explains.</b>
 */
public final class IndexVerificationCommandCore {

	// An arbitrary safety cap on how much a single invocation sweeps, not a game-derived
	// constant - IndexSweep's own javadoc explains why chunkRadius has no default to fall back to
	// instead, so a ceiling on the explicit argument is what keeps a mistyped huge radius from
	// walking an unreasonable number of columns.
	public static final int MAX_CHUNK_RADIUS = 32;

	private IndexVerificationCommandCore() {
	}

	/** @return a Brigadier-style status: 1 for a clean run, 0 for anything else. */
	public static int run(Minecraft client, Consumer<String> feedback, int chunkRadius) {
		LocalPlayer player = client.player;
		ClientLevel level = client.level;

		if (player == null || level == null) {
			return fail(feedback, "no player or level.");
		}

		BlockPos center = player.blockPosition();

		feedback.accept("[sculksight] sweeping " + chunkRadius + " chunks around " + center + "...");

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
