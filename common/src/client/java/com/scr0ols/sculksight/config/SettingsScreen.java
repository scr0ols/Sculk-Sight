package com.scr0ols.sculksight.config;

import java.util.Locale;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * This mod's settings screen: a hand-rolled vanilla {@link Screen}, replacing the Cloth Config one
 * {@link ConfigScreens} used to build.
 *
 * <p><b>Layout only, no mutation logic.</b> Every control below calls straight through to one of
 * {@link ConfigScreens}'s static action methods and then refreshes whatever on screen needs to
 * reflect the new state - the render policy button re-labels itself, the opacity slider re-labels
 * itself, and {@link #sensorList} rebuilds its rows from live {@link ClientConfig} state. None of
 * that logic lives here twice; this class only knows where things sit on screen.
 *
 * <p><b>Header carries the appearance controls, contents carries the tracked-sensors list, footer
 * carries Done</b> - the same {@link HeaderAndFooterLayout} three-band shape vanilla's own
 * {@code OptionsSubScreen}/{@code KeyBindsScreen} use, and for the reason theirs does: a list that
 * fills the space between a fixed header and a fixed footer needs to be told its own height once
 * the screen's real size is known, not given one up front.
 */
final class SettingsScreen extends Screen {

	/** Title row plus the opacity slider and render-policy button, each {@value BUTTON_HEIGHT} tall. */
	private static final int HEADER_HEIGHT = 82;

	private static final int FOOTER_HEIGHT = 33;
	private static final int BUTTON_HEIGHT = 20;
	private static final int CONTROL_WIDTH = 300;
	private static final int DONE_BUTTON_WIDTH = 200;

	private final Screen parent;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, HEADER_HEIGHT, FOOTER_HEIGHT);

	private AbstractSliderButton opacitySlider;
	private CycleButton<RenderPolicy> renderPolicyButton;
	private TrackedSensorListWidget sensorList;

	SettingsScreen(Screen parent) {
		super(Component.translatable("sculksight.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		SculkSightConfig config = ClientConfig.get();

		LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(4));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(title, font));
		header.addChild(buildOpacitySlider(config.shellOpacityPercent()));
		header.addChild(buildRenderPolicyButton(config.renderPolicy()));

		sensorList = layout.addToContents(new TrackedSensorListWidget(
				minecraft, width, layout.getContentHeight(), layout.getHeaderHeight()));

		layout.addToFooter(Button.builder(Component.translatable("sculksight.config.done"),
						button -> onClose())
				.width(DONE_BUTTON_WIDTH)
				.build());

		layout.visitWidgets(this::addRenderableWidget);
		repositionElements();
	}

	private AbstractSliderButton buildOpacitySlider(int initialPercent) {
		opacitySlider = new AbstractSliderButton(0, 0, CONTROL_WIDTH, BUTTON_HEIGHT,
				opacityMessage(initialPercent), initialPercent / 100.0) {
			@Override
			protected void updateMessage() {
				setMessage(opacityMessage(percentFromSliderValue(value)));
			}

			@Override
			protected void applyValue() {
				ConfigScreens.setShellOpacityPercent(percentFromSliderValue(value));
			}
		};
		opacitySlider.setTooltip(Tooltip.create(
				Component.translatable("sculksight.config.shell_opacity.tooltip")));
		return opacitySlider;
	}

	private CycleButton<RenderPolicy> buildRenderPolicyButton(RenderPolicy initial) {
		renderPolicyButton = CycleButton.builder(
						(RenderPolicy policy) -> Component.translatable(
								"sculksight.config.render_policy." + policy.name().toLowerCase(Locale.ROOT)),
						initial)
				.withValues(RenderPolicy.values())
				.create(0, 0, CONTROL_WIDTH, BUTTON_HEIGHT,
						Component.translatable("sculksight.config.render_policy"),
						(button, value) -> ConfigScreens.setRenderPolicy(value));
		renderPolicyButton.setTooltip(Tooltip.create(
				Component.translatable("sculksight.config.render_policy.tooltip")));
		return renderPolicyButton;
	}

	private static Component opacityMessage(int percent) {
		return Component.translatable("sculksight.config.shell_opacity")
				.append(": ")
				.append(Component.translatable("sculksight.config.shell_opacity.value", percent));
	}

	private static int percentFromSliderValue(double value) {
		// The double overload, deliberately: SculkSightConfig.clampShellOpacityPercent(double)'s own
		// javadoc is why the clamp happens before narrowing to int rather than after.
		double rawPercent = Math.round(value * 100.0);
		return (int) SculkSightConfig.clampShellOpacityPercent(rawPercent);
	}

	@Override
	protected void repositionElements() {
		layout.arrangeElements();
		if (sensorList != null) {
			sensorList.updateSize(width, layout);
		}
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
