package com.scr0ols.sculksight.config;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.screens.Screen;

import com.scr0ols.sculksight.client.ShellRenderer;

/**
 * Reaches this mod's settings screen, and holds the pure mutation logic behind every one of its
 * controls.
 *
 * <p><b>No more Cloth Config, no more Save button.</b> Up to v0.2 this class built a Cloth Config
 * screen and parked every edit in a draft object until one distant Save button ran {@code save()}
 * - including "remove", which was a checkbox that only took effect then. That batching was a real
 * UX problem in its own right (a rename or a removal a player just clicked did not visibly happen
 * until they found and pressed Save), so the replacement removes both the dependency and the
 * batching at once: {@link SettingsScreen} is a hand-rolled vanilla {@link Screen}, and every
 * action below applies to {@link ClientConfig} - and asks {@link ShellRenderer#onConfigChanged()}
 * to forget the old style - the instant it runs, not on some later save.
 *
 * <p><b>Loader-independent, and in {@code common}'s client source set for that reason.</b> Nothing
 * here names a Cloth or ModMenu type, but the class still lives beside the vanilla {@code Screen}
 * types it does name, which are identical across both loaders' own Minecraft artifacts - so each
 * loader module recompiles this file (and {@link SettingsScreen}, {@link TrackedSensorListWidget})
 * against its own Minecraft jar the same way it already recompiles the rest of {@code common}. What
 * is per loader is only how the screen is reached: a ModMenu entrypoint on Fabric, an
 * {@code IConfigScreenFactory} extension point on NeoForge - both untouched by this change, since
 * both call only {@link #create(Screen)}, whose signature has not moved.
 *
 * <p><b>These action methods are the pure, testable unit</b> this class now exposes in place of the
 * old private {@code save}/{@code SensorDraft}: no {@code Minecraft} instance, no widget, just
 * {@link ClientConfig} read, a {@code with*}/{@code track}/{@code untrack} call on the immutable
 * {@link SculkSightConfig} it returns, and {@link ClientConfig#set}. {@code fabric}'s own
 * {@code ConfigScreensActionsTest} calls them directly, the same way it once drove {@code save}
 * through reflection.
 */
public final class ConfigScreens {

	private ConfigScreens() {
	}

	/** The settings screen, ready to be shown. */
	public static Screen create(Screen parent) {
		return new SettingsScreen(parent);
	}

	/**
	 * Renames a tracked sensor immediately. A blank (or all-whitespace) name is not an edit - it
	 * keeps the sensor's existing name, matching the old Cloth screen's own save-time fallback -
	 * and a name past {@link TrackedSensor#MAX_NAME_LENGTH} is rejected the same quiet way that
	 * fallback rejected one: the sensor is left unrenamed rather than dropped.
	 */
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

	/** Toggles a tracked sensor's enabled state immediately. */
	static void setSensorEnabled(int x, int y, int z, boolean enabled) {
		SculkSightConfig live = ClientConfig.get();
		TrackedSensor current = findSensor(live, x, y, z);
		if (current == null) {
			return;
		}

		replaceSensor(live, current.withEnabled(enabled));
	}

	/** Removes a tracked sensor immediately - no confirmation, no save button, per the redesign. */
	static void removeSensor(int x, int y, int z) {
		ClientConfig.set(ClientConfig.get().untrack(x, y, z));
		ShellRenderer.onConfigChanged();
	}

	/** Applies a new shell opacity immediately, the same instant a slider drag changes it. */
	static void setShellOpacityPercent(int percent) {
		ClientConfig.set(ClientConfig.get().withShellOpacityPercent(percent));
		ShellRenderer.onConfigChanged();
	}

	/** Applies a new render policy immediately, the same instant the cycle button changes it. */
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
		ShellRenderer.onConfigChanged();
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
