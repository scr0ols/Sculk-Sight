package com.scr0ols.sculksight.config;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import com.scr0ols.sculksight.audit.AuditPin;
import com.scr0ols.sculksight.audit.RadiusAuditController;
import com.scr0ols.sculksight.client.SensorKey;
import com.scr0ols.sculksight.client.ShellRenderer;

/** Reaches this mod's settings screen, and holds the mutation logic behind each of its controls. */
public final class ConfigScreens {

	/** The keybind that opens the settings screen directly from gameplay. */
	public static final KeyMapping OPEN_SETTINGS_KEY = new KeyMapping(
			"key.sculksight.open_settings", InputConstants.KEY_B, KeyMapping.Category.MISC);

	private ConfigScreens() {
	}

	/** The settings screen, ready to be shown. */
	public static Screen create(Screen parent) {
		return new SettingsScreen(parent);
	}

	/** Called once per tick from a loader's client tick event to service the settings keybind. */
	public static void onEndTick(Minecraft client) {
		while (OPEN_SETTINGS_KEY.consumeClick()) {
			if (client.gui.screen() == null) {
				client.gui.setScreen(create(null));
			}
		}
	}

	static void renameSensor(int x, int y, int z, String newName) {
		SculkSightConfig live = ClientConfig.get();
		TrackedSensor current = findSensor(live, x, y, z);
		if (current == null) {
			return;
		}

		String name = newName.strip();
		if (name.isEmpty()) {
			name = current.name();
		}

		TrackedSensor renamed;
		try {
			renamed = current.withName(name);
		} catch (IllegalArgumentException tooLong) {
			return;
		}

		replaceSensor(live, renamed);
	}

	static void setSensorEnabled(int x, int y, int z, boolean enabled) {
		SculkSightConfig live = ClientConfig.get();
		TrackedSensor current = findSensor(live, x, y, z);
		if (current == null) {
			return;
		}

		replaceSensor(live, current.withEnabled(enabled));
	}

	static void setSensorDelayOverlay(int x, int y, int z, boolean enabled) {
		SculkSightConfig live = ClientConfig.get();
		TrackedSensor current = findSensor(live, x, y, z);
		if (current == null) {
			return;
		}

		replaceSensor(live, current.withDelayOverlayEnabled(enabled));
	}

	static void setAuditSensorHidden(int x, int y, int z, boolean hidden) {
		RadiusAuditController.setHidden(new SensorKey(x, y, z), hidden);
	}

	static String pinAuditSelection() {
		AuditPin.Result result = AuditPin.pin(ClientConfig.get(), RadiusAuditController.selection());
		ClientConfig.set(result.config());
		RadiusAuditController.clear();
		return AuditPin.describe(result);
	}

	static void removeSensor(int x, int y, int z) {
		ClientConfig.set(ClientConfig.get().untrack(x, y, z));
	}

	static void setShellOpacityPercent(int percent) {
		ClientConfig.set(ClientConfig.get().withShellOpacityPercent(percent));
		ShellRenderer.onConfigChanged();
	}

	static void setRenderPolicy(RenderPolicy policy) {
		ClientConfig.set(ClientConfig.get().withRenderPolicy(policy));
		ShellRenderer.onConfigChanged();
	}

	private static void replaceSensor(SculkSightConfig live, TrackedSensor updated) {
		List<TrackedSensor> sensors = new ArrayList<>();
		for (TrackedSensor sensor : live.trackedSensors()) {
			boolean samePosition = sensor.x() == updated.x() && sensor.y() == updated.y() && sensor.z() == updated.z();
			sensors.add(samePosition ? updated : sensor);
		}

		ClientConfig.set(live.withTrackedSensors(sensors));
	}

	private static @Nullable TrackedSensor findSensor(SculkSightConfig config, int x, int y, int z) {
		for (TrackedSensor sensor : config.trackedSensors()) {
			if (sensor.x() == x && sensor.y() == y && sensor.z() == z) {
				return sensor;
			}
		}

		return null;
	}
}
