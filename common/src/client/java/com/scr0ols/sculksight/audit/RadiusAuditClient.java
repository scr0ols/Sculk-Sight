package com.scr0ols.sculksight.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

import com.scr0ols.sculksight.audit.RadiusAudit.AuditedSensor;
import com.scr0ols.sculksight.client.DetectorType;
import com.scr0ols.sculksight.client.SensorIndex;
import com.scr0ols.sculksight.client.SensorKey;
import com.scr0ols.sculksight.config.ClientConfig;

/**
 * Mode B's client-side half: turns {@link SensorIndex#snapshot()} and the player's own position
 * into the plain arguments {@link RadiusAuditCommandCore} takes, so that class can stay in
 * {@code common/src/main} and name no Minecraft type. ARCHITECTURE.md section 12.1's own words for
 * this conversion: "trivial by construction" - the same split {@code DetectionIndicator} draws
 * against {@link com.scr0ols.sculksight.solver.SensorDetector}, and the reason this class carries
 * no test of its own.
 *
 * <p>Both loaders' {@code RadiusAuditCommand} call this instead of {@link RadiusAuditCommandCore}
 * directly, so the conversion lives once rather than being copied into each registration class.
 * Choosing between that class's two mode entry points is part of the same conversion and happens
 * here too, for the same reason.
 */
public final class RadiusAuditClient {

	private RadiusAuditClient() {
	}

	/**
	 * Resolves the mode, the centre, the candidate set and the cap, then hands off to whichever of
	 * {@link RadiusAuditCommandCore}'s two entry points the mode names.
	 *
	 * <p><b>The mode is validated here rather than by Brigadier</b>, so an unknown word is reported
	 * by {@link RadiusAuditMode#of} with a message naming the modes it could have been - see that
	 * method for why validation sits at layer 1.
	 *
	 * @param report where a line of player-facing feedback goes
	 * @param detectorName the detector name the player typed
	 * @param radius the radius in blocks, as Brigadier parsed it
	 * @param modeName the mode the player typed; required, since neither mode is a default
	 * @return {@link RadiusAuditCommandCore#SUCCESS} or {@link RadiusAuditCommandCore#FAILURE}
	 */
	public static int run(Consumer<String> report, @Nullable String detectorName, int radius,
			@Nullable String modeName) {
		RadiusAuditMode mode;
		try {
			mode = RadiusAuditMode.of(modeName);
		} catch (RadiusAuditArgumentException problem) {
			report.accept(problem.getMessage());
			return RadiusAuditCommandCore.FAILURE;
		}

		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		LocalPlayer player = client.player;

		if (level == null || player == null) {
			report.accept("No level is loaded.");
			return RadiusAuditCommandCore.FAILURE;
		}

		BlockPos centre = player.blockPosition();
		List<AuditedSensor> candidates = candidatesFrom(level);
		int cap = ClientConfig.get().radiusAuditCap();

		return switch (mode) {
			case LIVE -> RadiusAuditCommandCore.runLive(report, radius, detectorName,
					centre.getX(), centre.getY(), centre.getZ(), candidates, cap);
			case STATIC -> RadiusAuditCommandCore.runStatic(report, radius, detectorName,
					centre.getX(), centre.getY(), centre.getZ(), candidates, cap,
					RadiusAuditClient::pinToConfig);
		};
	}

	/**
	 * The {@code ClientConfig} read and write around {@link AuditPin}'s pure operation - the half
	 * that cannot live in {@code common/src/main} beside the rest of the command's body, because
	 * {@code ClientConfig} names this mod's logger and so needs a launched game.
	 *
	 * <p>Shares {@link AuditPin#describe} with the settings screen's own Pin button, so a static
	 * find and a pinned live find report the same wording for the same outcome.
	 */
	private static String pinToConfig(List<AuditedSensor> selected) {
		AuditPin.Result result = AuditPin.pin(ClientConfig.get(), selected);
		ClientConfig.set(result.config());
		return AuditPin.describe(result);
	}

	/**
	 * Every indexed sensor that still resolves to a {@link DetectorType} right now. Reading the
	 * type at query time, rather than trusting {@code SensorIndex} to have one on hand (ADR-038's
	 * index stores position to radius only, not type), mirrors how {@code ShellRenderer#syncEntries}
	 * already reads it - the seam ARCHITECTURE.md section 12.3 names as already matching this
	 * shape. A position that no longer classifies - the block changed since indexing, and this
	 * loader's reconciliation has not caught up yet - is skipped rather than guessed at.
	 *
	 * <p><b>Public</b> so {@code ShellRenderer} can build the same candidate set when a radius
	 * audit is active, rather than this class and that one each reading {@code SensorIndex}
	 * differently.
	 */
	public static List<AuditedSensor> candidatesFrom(ClientLevel level) {
		List<AuditedSensor> candidates = new ArrayList<>();

		for (Map.Entry<BlockPos, Integer> sensor : SensorIndex.snapshot().entrySet()) {
			BlockPos pos = sensor.getKey();
			DetectorType.of(level.getBlockState(pos).getBlock()).ifPresent(type ->
					candidates.add(new AuditedSensor(SensorKey.of(pos), sensor.getValue(), type)));
		}

		return candidates;
	}
}
