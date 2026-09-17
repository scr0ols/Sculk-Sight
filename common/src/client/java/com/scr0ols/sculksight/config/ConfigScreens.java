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
 * action below applies to {@link ClientConfig} the instant it runs, not on some later save.
 *
 * <p><b>Two of the actions below also drop the renderer's cache, and the rest deliberately do
 * not.</b> {@link ShellRenderer#onConfigChanged()} forgets the cached style and every encoded mesh
 * with it, which is what the opacity slider and the render-policy button need and what nothing else
 * here does: the renderer's own per-tick reconcile already notices a changed selection without
 * being told. Calling it from the per-sensor actions too - as this class did until 2026-09-17 - is
 * what made one click re-solve an entire radius audit; that method's javadoc carries the detail.
 *
 * <p><b>Loader-independent, and in {@code common}'s client source set for that reason.</b> Nothing
 * here names a Cloth or ModMenu type, but the class still lives beside the vanilla {@code Screen}
 * types it does name, which are identical across both loaders' own Minecraft artifacts - so each
 * loader module recompiles this file (and {@link SettingsScreen}, {@link SensorListWidget})
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

	/**
	 * Opens the settings screen directly from gameplay, without going through Mod Menu (Fabric) or
	 * the mod list (NeoForge) - both of which stay as they were, unaffected by this. Loader-neutral
	 * for the same reason {@link ShellRenderer}'s and {@code DetectionIndicator}'s own keys are:
	 * constructed here, but registered and ticked by each loader's own entrypoint, since only that
	 * registers a {@code KeyMapping} against real input or drives {@link #onEndTick} from a client
	 * tick event.
	 *
	 * <p>No default-key collision with any other binding this mod defines (K/G/H/J are already
	 * taken) has been checked against a live client the way {@code DetectionIndicator}'s own
	 * TOGGLE_KEY javadoc records doing for L - so, as with any new default keybind, whether B
	 * collides with something else entirely (vanilla's own bindings, another installed mod) still
	 * needs a live-client check. Any collision is cosmetic, not a functional bug: every
	 * {@code KeyMapping} is rebindable through vanilla's own Controls screen regardless.
	 */
	public static final KeyMapping OPEN_SETTINGS_KEY = new KeyMapping(
			"key.sculksight.open_settings", InputConstants.KEY_B, KeyMapping.Category.MISC);

	private ConfigScreens() {
	}

	/** The settings screen, ready to be shown. */
	public static Screen create(Screen parent) {
		return new SettingsScreen(parent);
	}

	/**
	 * Called from a loader's own client tick event, once per tick. Opens the settings screen with
	 * no parent - the same as pressing Escape to leave it does with nothing further to return to -
	 * since a key pressed during gameplay has no menu screen to treat as the parent.
	 */
	public static void onEndTick(Minecraft client) {
		while (OPEN_SETTINGS_KEY.consumeClick()) {
			if (client.gui.screen() == null) {
				client.gui.setScreen(create(null));
			}
		}
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

	/**
	 * Shows or hides one sensor the active radius audit selected - the audit section's equivalent of
	 * {@link #setSensorEnabled} above, and the only way to switch off a single audited render.
	 *
	 * <p><b>Writes no config and saves nothing</b>, unlike every other action on this class. A live
	 * find's position is not in {@code ClientConfig.trackedSensors()} - a live find is a query, not
	 * a curated list - so there is no persisted entry to carry an {@code enabled} flag, and
	 * manufacturing one would make looking at an area rewrite {@code config/sculksight.json}.
	 * {@link RadiusAuditController#setHidden} holds it for the session instead, exactly as the three
	 * keybind toggles on {@link SettingsScreen}'s own header row hold theirs. {@link
	 * #pinAuditSelection} is how a player asks for the persisted form instead.
	 *
	 * <p>No {@link ShellRenderer#onConfigChanged()} either, and for the reason that method's own
	 * javadoc now gives: nothing here invalidates an encoded mesh, and the renderer's next-tick
	 * reconcile picks the change up on its own.
	 */
	static void setAuditSensorHidden(int x, int y, int z, boolean hidden) {
		RadiusAuditController.setHidden(new SensorKey(x, y, z), hidden);
	}

	/**
	 * Promotes the live find's whole current selection into the tracked-sensor list, then ends the
	 * find - the settings-screen half of what {@code /sculksight find <type> <n> static} does as it
	 * runs, for a player who wanted to walk around and look first.
	 *
	 * <p><b>The find has to end here, not merely be left running.</b> Its sensors are now tracked
	 * entries, and an audit still re-selecting the same area would draw them a second time by a
	 * second route - so the settings screen would show one row per sensor while a control the player
	 * could not see governed an identical shell. {@link RadiusAuditController#clear} is what keeps
	 * "pinned" and "being audited" mutually exclusive, the same way
	 * {@code RadiusAuditCommandCore.runStatic} does for the command.
	 *
	 * <p>Positions the player hid in the audit section are pinned along with the rest, and
	 * deliberately: hiding was a visibility choice about a transient query, and the tracked row that
	 * replaces it carries its own Enabled toggle to express the same thing in a form that persists.
	 * Hiding something is not asking for it to be forgotten.
	 *
	 * @return {@link AuditPin#describe}'s one line about what was pinned, for the caller to show -
	 *     the identical wording the command reports, since both route through {@link AuditPin}
	 */
	static String pinAuditSelection() {
		AuditPin.Result result = AuditPin.pin(ClientConfig.get(), RadiusAuditController.selection());
		ClientConfig.set(result.config());
		RadiusAuditController.clear();
		return AuditPin.describe(result);
	}

	/** Removes a tracked sensor immediately - no confirmation, no save button, per the redesign. */
	static void removeSensor(int x, int y, int z) {
		ClientConfig.set(ClientConfig.get().untrack(x, y, z));
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
