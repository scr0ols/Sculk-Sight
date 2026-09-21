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
import com.scr0ols.sculksight.client.EventAwareQueryClient;
import com.scr0ols.sculksight.client.SensorIndex;
import com.scr0ols.sculksight.client.SensorKey;
import com.scr0ols.sculksight.client.ShellRenderer;
import com.scr0ols.sculksight.config.ClientConfig;

/** Turns the sensor index and the player's position into the plain arguments {@link RadiusAuditCommandCore} takes. */
public final class RadiusAuditClient {

	private RadiusAuditClient() {
	}

	/** Resolves the mode, centre, candidate set and cap, then hands off to the matching entry point. */
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
		report.accept(EventAwareQueryClient.describe(level, player, candidates));
		int cap = ClientConfig.get().radiusAuditCap();

		int result = switch (mode) {
			case LIVE -> RadiusAuditCommandCore.runLive(report, radius, detectorName,
					centre.getX(), centre.getY(), centre.getZ(), candidates, cap);
			case STATIC -> RadiusAuditCommandCore.runStatic(report, radius, detectorName,
					centre.getX(), centre.getY(), centre.getZ(), candidates, cap,
					RadiusAuditClient::pinToConfig);
		};
		if (result == RadiusAuditCommandCore.SUCCESS) {
			ShellRenderer.onRadiusAuditRerun();
		}
		return result;
	}

	private static String pinToConfig(List<AuditedSensor> selected) {
		AuditPin.Result result = AuditPin.pin(ClientConfig.get(), selected);
		ClientConfig.set(result.config());
		return AuditPin.describe(result);
	}

	/** Every indexed sensor that still resolves to a {@link DetectorType} right now. */
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
