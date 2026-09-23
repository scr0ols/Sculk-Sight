package com.scr0ols.sculksight.config;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * A single tracked sensor's controls, reached from its "Options" button on the settings screen.
 * Every control here applies immediately, the same as every other control
 * {@link ConfigScreens} reaches - see that class for why there is no separate save step.
 */
final class SensorOptionsScreen extends Screen {

	private static final int HEADER_HEIGHT = 130;
	private static final int FOOTER_HEIGHT = 33;
	private static final int BUTTON_HEIGHT = 20;
	private static final int CONTROL_WIDTH = 240;
	private static final int RENAME_APPLY_BUTTON_WIDTH = 60;
	private static final int DONE_BUTTON_WIDTH = 200;
	private static final int ROW_SPACING = 4;

	private final Screen parent;
	private final Runnable onDone;
	private final int x;
	private final int y;
	private final int z;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, HEADER_HEIGHT, FOOTER_HEIGHT);

	private EditBox nameField;
	private boolean enabled;
	private boolean delayOverlayEnabled;

	SensorOptionsScreen(Screen parent, TrackedSensor sensor, Runnable onDone) {
		super(Component.translatable("sculksight.config.tracked_sensors.options.title",
				sensor.x(), sensor.y(), sensor.z()));
		this.parent = parent;
		this.onDone = onDone;
		this.x = sensor.x();
		this.y = sensor.y();
		this.z = sensor.z();
		this.enabled = sensor.enabled();
		this.delayOverlayEnabled = sensor.delayOverlayEnabled();
	}

	@Override
	protected void init() {
		TrackedSensor current = currentSensor();

		LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(ROW_SPACING));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(title, font));
		header.addChild(buildRenameRow(current.name()));
		header.addChild(buildEnabledButton());
		header.addChild(buildDelayOverlayButton());

		layout.addToFooter(Button.builder(Component.translatable("sculksight.config.done"),
						button -> onClose())
				.width(DONE_BUTTON_WIDTH)
				.build());

		layout.visitWidgets(this::addRenderableWidget);
		repositionElements();
	}

	private LinearLayout buildRenameRow(String initialName) {
		nameField = new EditBox(font, 0, 0, CONTROL_WIDTH - RENAME_APPLY_BUTTON_WIDTH - ROW_SPACING,
				BUTTON_HEIGHT, Component.translatable("sculksight.config.tracked_sensors.rename.field"));
		nameField.setMaxLength(TrackedSensor.MAX_NAME_LENGTH);
		nameField.setValue(initialName);

		Button applyButton = Button.builder(
						Component.translatable("sculksight.config.tracked_sensors.rename.apply"),
						button -> applyRename())
				.tooltip(Tooltip.create(
						Component.translatable("sculksight.config.tracked_sensors.rename.tooltip")))
				.size(RENAME_APPLY_BUTTON_WIDTH, BUTTON_HEIGHT)
				.build();

		LinearLayout row = LinearLayout.horizontal().spacing(ROW_SPACING);
		row.addChild(nameField);
		row.addChild(applyButton);
		return row;
	}

	private void applyRename() {
		ConfigScreens.renameSensor(x, y, z, nameField.getValue());
		nameField.setValue(currentSensor().name());
	}

	private Button buildEnabledButton() {
		return Button.builder(enabledLabel(), button -> {
					enabled = !enabled;
					ConfigScreens.setSensorEnabled(x, y, z, enabled);
					button.setMessage(enabledLabel());
				})
				.tooltip(Tooltip.create(
						Component.translatable("sculksight.config.tracked_sensors.enabled.tooltip")))
				.size(CONTROL_WIDTH, BUTTON_HEIGHT)
				.build();
	}

	private Component enabledLabel() {
		Component state = Component.translatable(enabled
				? "sculksight.config.tracked_sensors.enabled.yes"
				: "sculksight.config.tracked_sensors.enabled.no");
		return Component.translatable("sculksight.config.tracked_sensors.enabled", state);
	}

	private Button buildDelayOverlayButton() {
		return Button.builder(delayOverlayLabel(), button -> {
					delayOverlayEnabled = !delayOverlayEnabled;
					ConfigScreens.setSensorDelayOverlay(x, y, z, delayOverlayEnabled);
					button.setMessage(delayOverlayLabel());
				})
				.tooltip(Tooltip.create(
						Component.translatable("sculksight.config.tracked_sensors.delay_overlay.tooltip")))
				.size(CONTROL_WIDTH, BUTTON_HEIGHT)
				.build();
	}

	private Component delayOverlayLabel() {
		Component state = Component.translatable(delayOverlayEnabled
				? "sculksight.config.on"
				: "sculksight.config.off");
		return Component.translatable("sculksight.config.tracked_sensors.delay_overlay", state);
	}

	private TrackedSensor currentSensor() {
		for (TrackedSensor sensor : ClientConfig.get().trackedSensors()) {
			if (sensor.x() == x && sensor.y() == y && sensor.z() == z) {
				return sensor;
			}
		}
		// Removed from another screen while this one was open (e.g. Remove pressed elsewhere in
		// the same session is not possible today, but a future removal path might add one) -
		// fall back to an inert placeholder rather than crashing this screen.
		return new TrackedSensor(x, y, z, TrackedSensor.defaultName(x, y, z), enabled, delayOverlayEnabled);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (nameField != null && nameField.isFocused() && isEnterKey(event.key())) {
			applyRename();
			return true;
		}
		return super.keyPressed(event);
	}

	private static boolean isEnterKey(int keyCode) {
		return keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER;
	}

	@Override
	protected void repositionElements() {
		layout.arrangeElements();
	}

	@Override
	public void onClose() {
		onDone.run();
		minecraft.gui.setScreen(parent);
	}
}
