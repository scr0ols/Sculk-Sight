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

	/**
	 * Title row, the opacity slider, and one more {@value BUTTON_HEIGHT}-tall row holding every
	 * remaining control side by side - the render-policy cycle button and the three session toggles
	 * (global render, delay overlay, detection indicator) that mirror keys G, H and J. See
	 * {@link #buildGlobalRenderButton()}'s own javadoc for why those three exist as buttons at all,
	 * and {@link #TOGGLE_ROW_SPACING} for why they sit in one row rather than one each.
	 */
	private static final int HEADER_HEIGHT = 82;

	private static final int FOOTER_HEIGHT = 33;
	private static final int BUTTON_HEIGHT = 20;
	private static final int CONTROL_WIDTH = 300;
	private static final int DONE_BUTTON_WIDTH = 200;

	/** Gap between the four buttons sharing {@link #buildToggleRow}'s single row. */
	private static final int TOGGLE_ROW_SPACING = 4;

	/**
	 * Individual widths for the four buttons on the toggle row, each sized to its own (deliberately
	 * short - see the caption comment on each builder method below) label rather than split evenly,
	 * since "Mode: Per-sensor" needs meaningfully more room than "Delay: Off" does.
	 */
	private static final int RENDER_POLICY_BUTTON_WIDTH = 110;
	private static final int GLOBAL_RENDER_BUTTON_WIDTH = 80;
	private static final int DELAY_OVERLAY_BUTTON_WIDTH = 75;
	private static final int DETECTION_INDICATOR_BUTTON_WIDTH = 80;

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
		buildToggleRow(header, config.renderPolicy());

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
				.create(0, 0, RENDER_POLICY_BUTTON_WIDTH, BUTTON_HEIGHT,
						Component.translatable("sculksight.config.render_policy"),
						(button, value) -> ConfigScreens.setRenderPolicy(value));
		renderPolicyButton.setTooltip(Tooltip.create(
				Component.translatable("sculksight.config.render_policy.tooltip")));
		return renderPolicyButton;
	}

	/**
	 * {@link ShellRenderer#TOGGLE_RENDERING_KEY} (key G), {@link ShellRenderer#TOGGLE_DELAY_HEATMAP_KEY}
	 * (key H) and {@link DetectionIndicator#TOGGLE_KEY} (key J) toggle session state that used to be
	 * reachable only by keybind - a player who forgot which key does what, or is not at a keyboard
	 * layout where G/H/J are convenient, had no other way to reach them. This row gives the render
	 * policy above it and the same three toggles a menu control apiece, side by side in one row
	 * rather than one full-width row each, so the header does not grow by three more button heights.
	 * Each button calls straight through to the same method its keybind already calls (see e.g.
	 * {@link ShellRenderer#toggleRendering}'s own javadoc), so pressing the key and clicking the
	 * button do exactly the same thing rather than two independently-maintained toggles that could
	 * drift apart.
	 *
	 * <p>Captions on this row are deliberately terse - "Mode", "Global", "Delay", "Detect" rather
	 * than each button's full name - because four buttons sharing one row leaves each one only as
	 * wide as its own text needs to be, not the {@value CONTROL_WIDTH}px the opacity slider and the
	 * old one-button-per-row layout could afford. Every button keeps its full tooltip, matching the
	 * Remove button's own short-caption-plus-tooltip pattern in {@link TrackedSensorListWidget}.
	 *
	 * <p>Unlike the opacity slider above, none of the three session toggles are persisted
	 * {@link SculkSightConfig} settings - they reset to their defaults every session, exactly as they
	 * did before this row existed. Each button's own re-render-on-click pattern is enough to keep its
	 * label correct without a {@link ConfigScreens} action method, since there is no saved config for
	 * one to write to.
	 */
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
				.size(GLOBAL_RENDER_BUTTON_WIDTH, BUTTON_HEIGHT)
				.build();
		button.setTooltip(Tooltip.create(Component.translatable("sculksight.config.global_render.tooltip")));
		return button;
	}

	private Button buildDelayOverlayButton() {
		Button button = Button.builder(delayOverlayLabel(), pressed -> {
					ShellRenderer.toggleDelayHeatmap(minecraft);
					pressed.setMessage(delayOverlayLabel());
				})
				.size(DELAY_OVERLAY_BUTTON_WIDTH, BUTTON_HEIGHT)
				.build();
		button.setTooltip(Tooltip.create(Component.translatable("sculksight.config.delay_overlay.tooltip")));
		return button;
	}

	private Button buildDetectionIndicatorButton() {
		Button button = Button.builder(detectionIndicatorLabel(), pressed -> {
					DetectionIndicator.toggle(minecraft);
					pressed.setMessage(detectionIndicatorLabel());
				})
				.size(DETECTION_INDICATOR_BUTTON_WIDTH, BUTTON_HEIGHT)
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
