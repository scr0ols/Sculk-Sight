package com.scr0ols.sculksight.client;

import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import com.scr0ols.sculksight.SculkSight;
import com.scr0ols.sculksight.solver.SensorDetector;
import com.scr0ols.sculksight.solver.WorldView;

/** Chat indicator that reports whether the player is within any sensor's detection range. */
public final class DetectionIndicator {

	public static final KeyMapping TOGGLE_KEY = new KeyMapping(
			"key.sculksight.toggle_detection", InputConstants.KEY_J, KeyMapping.Category.MISC);

	private static boolean enabled;

	private static boolean hasReading;

	private static boolean lastDetected;

	private DetectionIndicator() {
	}

	/** Called from a loader's own client tick event, once per tick. */
	public static void onEndTick(Minecraft client) {
		while (TOGGLE_KEY.consumeClick()) {
			toggle(client);
		}

		if (enabled) {
			tick(client);
		}
	}

	/** Clears the cached reading when the client level changes. */
	public static void onLevelChanged() {
		hasReading = false;
	}

	/** Toggles the detection indicator on or off. */
	public static void toggle(Minecraft client) {
		enabled = !enabled;

		if (!enabled) {
			hasReading = false;
			say(client, "detection indicator off.");
			return;
		}

		hasReading = false;
		tick(client);
	}

	/** Whether {@link #TOGGLE_KEY} (or the settings screen's button) is currently on. */
	public static boolean isEnabled() {
		return enabled;
	}

	private static void tick(Minecraft client) {
		ClientLevel level = client.level;
		LocalPlayer player = client.player;

		if (level == null || player == null) {
			return;
		}

		boolean detected = isDetected(level, player.blockPosition());

		if (hasReading && detected == lastDetected) {
			return;
		}

		hasReading = true;
		lastDetected = detected;

		say(client, detected
				? "you are now within a sensor's detection range."
				: "you are no longer within any sensor's detection range.");
	}

	private static boolean isDetected(ClientLevel level, BlockPos player) {
		WorldView world = new LevelWorldView(level);

		for (Map.Entry<BlockPos, Integer> sensor : SensorIndex.snapshot().entrySet()) {
			BlockPos pos = sensor.getKey();
			int radius = sensor.getValue();

			if (SensorDetector.isDetectedAt(world,
					player.getX(), player.getY(), player.getZ(),
					pos.getX(), pos.getY(), pos.getZ(),
					radius)) {
				return true;
			}
		}

		return false;
	}

	private static void say(Minecraft client, String message) {
		SculkSight.LOGGER.info("[sculksight] {}", message);

		if (client.gui != null) {
			client.gui.hud.getChat().addClientSystemMessage(Component.literal("[sculksight] " + message));
		}
	}
}
