package com.scr0ols.sculksight.client;

import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import com.scr0ols.sculksight.SculkSight;
import com.scr0ols.sculksight.solver.SensorDetector;
import com.scr0ols.sculksight.solver.WorldView;

/**
 * Mode C: "am I detected". PLAN.md section 3.4, ARCHITECTURE.md section 10, DECISIONS.md
 * ADR-039.
 *
 * <p><b>No geometry, by design.</b> Unlike {@link ShellRenderer}, this class builds no
 * {@code DetectionSet}, no mesh, no GPU buffer, and touches no render pass. It asks
 * {@link SensorDetector#isDetectedAt} once per indexed sensor, once per client tick, while
 * enabled - the "N sensors, N raycasts" plan section 3.4 describes, run against
 * {@link SensorIndex}'s snapshot rather than against a fresh sweep, per ADR-038.
 *
 * <p><b>Client thread, synchronous, and deliberately so.</b> Every candidate sensor's occlusion
 * test reads the live {@code ClientLevel} through {@link LevelWorldView}, exactly as
 * {@link ShellRenderer#runSolve} still does under ADR-026's v0.0 narrowing. Moving this off a
 * worker thread would need the snapshot ARCHITECTURE.md section 6.2 and R16 require, and
 * starting that work is out of this session's scope (its trigger is a measured, accepted cost,
 * not its own availability - `NEXT-STEPS.md`). At the cost this indicator actually pays - a
 * handful of cheap range tests per tick, with rays cast only for the survivors - there is
 * nothing here to measure yet.
 *
 * <p><b>The indicator's own form was a decision no prior document had made.</b> `VISUAL-SPEC.md`
 * specifies the shell's fill, opacity and depth behaviour and says nothing about mode C, because
 * mode C draws no shell. What is built here - a toggle key, a chat line on the transition
 * between detected and not detected, nothing persistent - is argued in `DECISIONS.md` ADR-039
 * against the alternatives it considered and rejected.
 */
public final class DetectionIndicator {

	// KEY_J, not KEY_L: L collides with vanilla's own default key.advancements binding. See
	// DECISIONS.md ADR-039's 2026-09-04 addendum for how that was found and why J was chosen.
	private static final KeyMapping TOGGLE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.sculksight.toggle_detection", InputConstants.KEY_J, KeyMapping.Category.MISC));

	private static boolean enabled;

	/** Whether {@link #lastDetected} reflects a real reading yet, rather than its default. */
	private static boolean hasReading;

	private static boolean lastDetected;

	private DetectionIndicator() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(DetectionIndicator::onEndTick);

		// A level change - join, dimension change, or disconnect - makes the last reading stale
		// regardless of whether the indicator stays on, the same reasoning ShellRenderer applies
		// to its own cached shell. The toggle state itself is left alone: it is a standing player
		// preference, not tied to any one sensor the way mode A's aimed shell is.
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> hasReading = false);
	}

	private static void onEndTick(Minecraft client) {
		while (TOGGLE_KEY.consumeClick()) {
			toggle(client);
		}

		if (enabled) {
			tick(client);
		}
	}

	private static void toggle(Minecraft client) {
		enabled = !enabled;

		if (!enabled) {
			hasReading = false;
			say(client, "detection indicator off.");
			return;
		}

		// Report the current state immediately rather than waiting for the next transition, so
		// turning the indicator on is never silent about what it already knows.
		hasReading = false;
		tick(client);
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

	/**
	 * PLAN.md section 3.4's "N sensors, N raycasts", against {@link SensorIndex}'s snapshot.
	 *
	 * <p>Every entry is asked, not only the ones some pre-filter judged plausibly near: the
	 * cheap range test inside {@link SensorDetector#isDetectedAt} is what keeps a distant sensor
	 * from costing more than one comparison, the same ordering {@code ShellSolver} uses for the
	 * same reason (ARCHITECTURE.md section 4.2). Short-circuits on the first sensor found to
	 * detect the player, since the question is "am I detected", not "by how many".
	 */
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

	/**
	 * Duplicated from {@link ShellRenderer#say} rather than shared: that method also mirrors
	 * into {@code TimingLog} for ADR-031's instrument, a concern this class has no shell timing
	 * to report and no reason to carry.
	 */
	private static void say(Minecraft client, String message) {
		SculkSight.LOGGER.info("[sculksight] {}", message);

		if (client.gui != null) {
			client.gui.hud.getChat().addClientSystemMessage(Component.literal("[sculksight] " + message));
		}
	}
}
