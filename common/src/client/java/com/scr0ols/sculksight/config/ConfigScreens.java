package com.scr0ols.sculksight.config;

import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;

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
 * <p><b>One entry, deliberately.</b> `VISUAL-SPEC.md`'s 2026-09-06 status line closed the last
 * questions blocking v0.1, and of their answers only ADR-022's opacity is a setting - see
 * {@link SculkSightConfig} for the other three and why none of them is here.
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

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable("sculksight.config.title"))
				.setSavingRunnable(() -> save(pendingOpacity.get()));

		ConfigCategory appearance =
				builder.getOrCreateCategory(Component.translatable("sculksight.config.category.appearance"));

		appearance.addEntry(opacitySlider(builder.entryBuilder(), config, pendingOpacity));

		return builder.build();
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

	/**
	 * Writes the new settings and tells the renderer to forget what it drew at the old ones.
	 *
	 * <p>Runs on the client thread, which is where a screen's own buttons run and also the render
	 * thread (R13 point 4) - the condition {@link ShellRenderer#onConfigChanged()} needs in order
	 * to close the cached shell's GPU resources.
	 */
	private static void save(int shellOpacityPercent) {
		ClientConfig.set(ClientConfig.get().withShellOpacityPercent(shellOpacityPercent));

		ShellRenderer.onConfigChanged();
	}
}
