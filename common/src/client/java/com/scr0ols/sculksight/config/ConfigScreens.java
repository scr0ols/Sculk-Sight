package com.scr0ols.sculksight.config;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;

import com.scr0ols.sculksight.client.ShellRenderer;

/**
 * Builds this mod's Cloth Config screen.
 *
 * <p>PLAN.md section 4 puts Cloth Config on the screen and this project's own persistence layer
 * underneath it, and this class is the whole of the seam between them: Cloth is asked for widgets
 * and told what to call on save, and {@link ClientConfig} is what the save actually reaches.
 * Nothing about the stored format, the file, the defaults or the validation is Cloth's - which is
 * what makes the mod's settings survive Cloth being absent, replaced, or dropped at v0.2.
 *
 * <p><b>Appearance and tracked sensors.</b> `VISUAL-SPEC.md`'s 2026-09-06 status line closed the last questions
 * blocking v0.1, and of their answers only ADR-022's opacity was a setting; {@link RenderPolicy}
 * joined it at v0.2 (ADR-034's M1) - see {@link SculkSightConfig} for the other v0.1 answers and
 * why none of them is here. The policy entry now drives the renderer: {@code ShellRenderer} reads
 * it to decide whether the tracked sensors below are drawn as one merged union shell or as
 * separate per-sensor shells, and each tracked sensor has an independent name and enabled toggle.
 *
 * <p><b>Loader-independent, and in {@code common}'s client source set for that reason.</b> Cloth
 * ships a separate artifact per loader, but the {@code me.shedaniel.clothconfig2.api} types this
 * class names are identical across both, so each loader module recompiles this file against its
 * own Cloth jar in the same way it already recompiles the rest of {@code common} against its own
 * Minecraft jar (see {@code common/build.gradle}). What is per loader is only how the screen is
 * reached: a ModMenu entrypoint on Fabric, an {@code IConfigScreenFactory} extension point on
 * NeoForge.
 */
public final class ConfigScreens {

	private ConfigScreens() {
	}

	/**
	 * The settings screen, ready to be shown.
	 *
	 * @param parent the screen to return to, which Cloth wires to both the cancel and the save
	 *        button
	 */
	public static Screen create(Screen parent) {
		SculkSightConfig config = ClientConfig.get();

		// Cloth hands each entry's value to its own save consumer and only then runs the saving
		// runnable, so the pending value has to be parked somewhere both can see. A holder rather
		// than a field: two screens open at once is not a state this mod should have opinions
		// about, and a local one cannot be left behind by a cancelled screen.
		AtomicInteger pendingOpacity = new AtomicInteger(config.shellOpacityPercent());
		AtomicReference<RenderPolicy> pendingRenderPolicy = new AtomicReference<>(config.renderPolicy());
		List<SensorDraft> pendingSensors = new ArrayList<>();
		for (TrackedSensor sensor : config.trackedSensors()) {
			pendingSensors.add(new SensorDraft(sensor));
		}

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable("sculksight.config.title"))
					.setSavingRunnable(() -> save(pendingOpacity.get(), pendingRenderPolicy.get(), pendingSensors));

		ConfigCategory appearance =
				builder.getOrCreateCategory(Component.translatable("sculksight.config.category.appearance"));

		appearance.addEntry(opacitySlider(builder.entryBuilder(), config, pendingOpacity));
		appearance.addEntry(renderPolicySelector(builder.entryBuilder(), config, pendingRenderPolicy));
		appearance.addEntry(trackedSensors(builder.entryBuilder(), config, pendingSensors));

		return builder.build();
	}

	private static AbstractConfigListEntry<List<AbstractConfigListEntry>> trackedSensors(
			ConfigEntryBuilder entries, SculkSightConfig config, List<SensorDraft> pending) {
		SubCategoryBuilder category = entries.startSubCategory(
				Component.translatable("sculksight.config.tracked_sensors"));
		for (int index = 0; index < config.trackedSensors().size(); index++) {
			TrackedSensor sensor = config.trackedSensors().get(index);
			SensorDraft draft = pending.get(index);
			category.add(entries.startStrField(
					Component.translatable("sculksight.config.tracked_sensors.name", sensor.x(), sensor.y(), sensor.z()),
					draft.name.get())
					.setSaveConsumer(draft.name::set)
					.build());
			category.add(entries.startBooleanToggle(
					Component.translatable("sculksight.config.tracked_sensors.enabled", sensor.name()),
					draft.enabled.get())
					.setSaveConsumer(draft.enabled::set)
					.build());
			category.add(entries.startBooleanToggle(
					Component.translatable("sculksight.config.tracked_sensors.remove"), false)
					.setTooltip(Component.translatable("sculksight.config.tracked_sensors.remove.tooltip"))
					.setSaveConsumer(draft.remove::set)
					.build());
		}
		category.setExpanded(true);
		return category.build();
	}

	private static AbstractConfigListEntry<Integer> opacitySlider(
			ConfigEntryBuilder entries, SculkSightConfig config, AtomicInteger pending) {
		return entries.startIntSlider(
						Component.translatable("sculksight.config.shell_opacity"),
						config.shellOpacityPercent(),
						SculkSightConfig.MIN_SHELL_OPACITY_PERCENT,
						SculkSightConfig.MAX_SHELL_OPACITY_PERCENT)
				.setDefaultValue(SculkSightConfig.DEFAULT_SHELL_OPACITY_PERCENT)
				.setTooltip(Component.translatable("sculksight.config.shell_opacity.tooltip"))
				.setTextGetter(percent ->
						Component.translatable("sculksight.config.shell_opacity.value", percent))
				.setSaveConsumer(pending::set)
				.build();
	}

	private static AbstractConfigListEntry<RenderPolicy> renderPolicySelector(
			ConfigEntryBuilder entries, SculkSightConfig config, AtomicReference<RenderPolicy> pending) {
		return entries.startEnumSelector(
						Component.translatable("sculksight.config.render_policy"),
						RenderPolicy.class,
						config.renderPolicy())
				.setDefaultValue(SculkSightConfig.DEFAULT_RENDER_POLICY)
				.setEnumNameProvider(policy -> Component.translatable(
						"sculksight.config.render_policy." + policy.name().toLowerCase(Locale.ROOT)))
				.setTooltip(Component.translatable("sculksight.config.render_policy.tooltip"))
				.setSaveConsumer(pending::set)
				.build();
	}

	/**
	 * Writes the new settings and tells the renderer to forget what it drew at the old ones.
	 *
	 * <p>Runs on the client thread, which is where a screen's own buttons run and also the render
	 * thread (R13 point 4) - the condition {@link ShellRenderer#onConfigChanged()} needs in order
	 * to close the cached shell's GPU resources.
	 */
	private static void save(int shellOpacityPercent, RenderPolicy renderPolicy,
			List<SensorDraft> pendingSensors) {
		List<TrackedSensor> sensors = new ArrayList<>();
		for (TrackedSensor live : ClientConfig.get().trackedSensors()) {
			SensorDraft draft = findDraft(pendingSensors, live);
			if (draft == null) {
				// Tracked (e.g. via the activate keybind) after this screen opened, so no widget
				// for it exists here - carry it through unedited instead of discarding it.
				sensors.add(live);
				continue;
			}
			if (draft.remove.get()) {
				continue;
			}
			String name = draft.name.get().strip();
			if (name.isEmpty()) {
				name = live.name();
			}
			try {
				sensors.add(new TrackedSensor(live.x(), live.y(), live.z(), name, draft.enabled.get()));
			} catch (IllegalArgumentException tooLong) {
				sensors.add(live);
			}
		}
		ClientConfig.set(ClientConfig.get()
				.withShellOpacityPercent(shellOpacityPercent)
				.withRenderPolicy(renderPolicy)
				.withTrackedSensors(sensors));

		ShellRenderer.onConfigChanged();
	}

	private static SensorDraft findDraft(List<SensorDraft> pendingSensors, TrackedSensor live) {
		for (SensorDraft draft : pendingSensors) {
			if (draft.x == live.x() && draft.y == live.y() && draft.z == live.z()) {
				return draft;
			}
		}
		return null;
	}

	private static final class SensorDraft {
		private final int x;
		private final int y;
		private final int z;
		private final AtomicReference<String> name;
		private final AtomicBoolean enabled;
		private final AtomicBoolean remove = new AtomicBoolean();

		private SensorDraft(TrackedSensor sensor) {
			x = sensor.x();
			y = sensor.y();
			z = sensor.z();
			name = new AtomicReference<>(sensor.name());
			enabled = new AtomicBoolean(sensor.enabled());
		}
	}
}
