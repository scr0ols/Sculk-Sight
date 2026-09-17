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
 */
public final class RadiusAuditClient {

	private RadiusAuditClient() {
	}

	/**
	 * @param report where a line of player-facing feedback goes
	 * @param radius the radius in blocks, as Brigadier parsed it
	 * @param detectorName the detector name the player typed, or {@code null} when omitted
	 * @return {@link RadiusAuditCommandCore#SUCCESS} or {@link RadiusAuditCommandCore#FAILURE}
	 */
	public static int run(Consumer<String> report, int radius, @Nullable String detectorName) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		LocalPlayer player = client.player;

		if (level == null || player == null) {
			report.accept("No level is loaded.");
			return RadiusAuditCommandCore.FAILURE;
		}

		BlockPos centre = player.blockPosition();

		return RadiusAuditCommandCore.run(report, radius, detectorName,
				centre.getX(), centre.getY(), centre.getZ(), candidatesFrom(level));
	}

	/**
	 * Every indexed sensor that still resolves to a {@link DetectorType} right now. Reading the
	 * type at query time, rather than trusting {@code SensorIndex} to have one on hand (ADR-038's
	 * index stores position to radius only, not type), mirrors how {@code ShellRenderer#syncEntries}
	 * already reads it - the seam ARCHITECTURE.md section 12.3 names as already matching this
	 * shape. A position that no longer classifies - the block changed since indexing, and this
	 * loader's reconciliation has not caught up yet - is skipped rather than guessed at.
	 */
	private static List<AuditedSensor> candidatesFrom(ClientLevel level) {
		List<AuditedSensor> candidates = new ArrayList<>();

		for (Map.Entry<BlockPos, Integer> sensor : SensorIndex.snapshot().entrySet()) {
			BlockPos pos = sensor.getKey();
			DetectorType.of(level.getBlockState(pos).getBlock()).ifPresent(type ->
					candidates.add(new AuditedSensor(SensorKey.of(pos), sensor.getValue(), type)));
		}

		return candidates;
	}
}
