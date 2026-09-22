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

import com.scr0ols.sculksight.client.DetectionIndicator;
import com.scr0ols.sculksight.client.ShellRenderer;

final class SettingsScreen extends Screen {

	private static final int HEADER_HEIGHT = 82;

	private static final int FOOTER_HEIGHT = 33;
	private static final int BUTTON_HEIGHT = 20;
	private static final int CONTROL_WIDTH = 300;
	private static final int DONE_BUTTON_WIDTH = 200;

	private static final int TOGGLE_ROW_SPACING = 4;

	private static final int TOGGLE_BUTTON_WIDTH = (CONTROL_WIDTH - 3 * TOGGLE_ROW_SPACING) / 4;

	private final Screen parent;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, HEADER_HEIGHT, FOOTER_HEIGHT);

	private AbstractSliderButton opacitySlider;
	private CycleButton<RenderPolicy> renderPolicyButton;
	private SensorListWidget sensorList;

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
		buildToggleRow(header, config.renderPolicy());

		sensorList = layout.addToContents(new SensorListWidget(
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
				.create(0, 0, TOGGLE_BUTTON_WIDTH, BUTTON_HEIGHT,
						Component.translatable("sculksight.config.render_policy"),
						(button, value) -> ConfigScreens.setRenderPolicy(value));
		renderPolicyButton.setTooltip(Tooltip.create(
				Component.translatable("sculksight.config.render_policy.tooltip")));
		return renderPolicyButton;
	}

	private void buildToggleRow(LinearLayout header, RenderPolicy initialRenderPolicy) {
		LinearLayout row = header.addChild(LinearLayout.horizontal().spacing(TOGGLE_ROW_SPACING));
		row.addChild(buildRenderPolicyButton(initialRenderPolicy));
		row.addChild(buildGlobalRenderButton());
		row.addChild(buildDelayOverlayButton());
		row.addChild(buildDetectionIndicatorButton());
	}

	private Button buildGlobalRenderButton() {
		Button button = Button.builder(globalRenderLabel(), pressed -> {
					ShellRenderer.toggleRendering(minecraft);
					pressed.setMessage(globalRenderLabel());
				})
				.size(TOGGLE_BUTTON_WIDTH, BUTTON_HEIGHT)
				.build();
		button.setTooltip(Tooltip.create(Component.translatable("sculksight.config.global_render.tooltip")));
		return button;
	}

	private Button buildDelayOverlayButton() {
		Button button = Button.builder(delayOverlayLabel(), pressed -> {
					ShellRenderer.toggleDelayHeatmap(minecraft);
					pressed.setMessage(delayOverlayLabel());
				})
				.size(TOGGLE_BUTTON_WIDTH, BUTTON_HEIGHT)
				.build();
		button.setTooltip(Tooltip.create(Component.translatable("sculksight.config.delay_overlay.tooltip")));
		return button;
	}

	private Button buildDetectionIndicatorButton() {
		Button button = Button.builder(detectionIndicatorLabel(), pressed -> {
					DetectionIndicator.toggle(minecraft);
					pressed.setMessage(detectionIndicatorLabel());
				})
				.size(TOGGLE_BUTTON_WIDTH, BUTTON_HEIGHT)
				.build();
		button.setTooltip(Tooltip.create(
				Component.translatable("sculksight.config.detection_indicator.tooltip")));
		return button;
	}

	private static Component globalRenderLabel() {
		Component state = Component.translatable(ShellRenderer.isRenderingEnabled()
				? "sculksight.config.on" : "sculksight.config.off");
		return Component.translatable("sculksight.config.global_render", state);
	}

	private static Component delayOverlayLabel() {
		Component state = Component.translatable(ShellRenderer.isDelayHeatmapEnabled()
				? "sculksight.config.on" : "sculksight.config.off");
		return Component.translatable("sculksight.config.delay_overlay", state);
	}

	private static Component detectionIndicatorLabel() {
		Component state = Component.translatable(DetectionIndicator.isEnabled()
				? "sculksight.config.on" : "sculksight.config.off");
		return Component.translatable("sculksight.config.detection_indicator", state);
	}

	private static Component opacityMessage(int percent) {
		return Component.translatable("sculksight.config.shell_opacity")
				.append(": ")
				.append(Component.translatable("sculksight.config.shell_opacity.value", percent));
	}

	private static int percentFromSliderValue(double value) {
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
