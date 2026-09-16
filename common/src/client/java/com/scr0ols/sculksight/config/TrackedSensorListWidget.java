package com.scr0ols.sculksight.config;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

/**
 * The tracked-sensors list: one row per {@link TrackedSensor}, with independent Rename,
 * Enable/Disable and Remove controls that each apply the instant the player clicks them - see
 * {@link ConfigScreens}'s own javadoc for why there is no Save button here at all.
 *
 * <p><b>{@code ContainerObjectSelectionList}, not the plain {@code ObjectSelectionList}</b> the
 * initial design sketch named: a row here needs three independently clickable buttons and, mid
 * rename, a text field plus two more buttons, and only the "container" flavour of vanilla's
 * selection list gives an {@code Entry} the {@code children()}/{@code narratables()} pair that
 * routes clicks and keyboard focus down into widgets living inside it - plain
 * {@code ObjectSelectionList.Entry} is a bare {@code GuiEventListener} with no such routing.
 * Vanilla's own {@code KeyBindsList} (two {@code Button}s per row, position set and rendered from
 * inside {@code extractContent} every frame, exactly reproduced below) is the precedent this class
 * follows - confirmed against the decompiled 26.2 client (
 * {@code minecraft-clientOnly-*-sources.jar}) this whole redesign was checked against, not assumed
 * from an older Minecraft version's very different {@code render(PoseStack, ...)} widget API.
 *
 * <p><b>Rebuilt from live state after every mutation</b>, rather than patched in place: at most
 * {@link SculkSightConfig#MAX_TRACKED_SENSORS} rows exist, so {@link #refresh()} rebuilding all of
 * them from {@link ClientConfig#get()} costs nothing worth avoiding, and it is what keeps a row's
 * own fields (name, enabled) from ever drifting out of sync with the config a different row's
 * action just wrote.
 */
final class TrackedSensorListWidget extends ContainerObjectSelectionList<TrackedSensorListWidget.SensorEntry> {

	private static final int ITEM_HEIGHT = 24;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_WIDTH = 64;
	private static final int NAME_FIELD_WIDTH = 140;
	private static final int SPACING = 4;

	TrackedSensorListWidget(Minecraft minecraft, int width, int height, int y) {
		super(minecraft, width, height, y, ITEM_HEIGHT);
		refresh();
	}

	@Override
	public int getRowWidth() {
		return Math.min(400, this.width - 20);
	}

	/**
	 * Rebuilds every row from live {@link ClientConfig} state. The screen calls this after every
	 * button press that reaches {@link ConfigScreens}, immediate-apply's replacement for the old
	 * screen's single save-time rebuild.
	 */
	void refresh() {
		List<SensorEntry> entries = new ArrayList<>();
		for (TrackedSensor sensor : ClientConfig.get().trackedSensors()) {
			entries.add(new SensorEntry(sensor));
		}

		replaceEntries(entries);
	}

	final class SensorEntry extends ContainerObjectSelectionList.Entry<SensorEntry> {

		private final int x;
		private final int y;
		private final int z;
		private final String name;
		private final boolean enabled;

		private boolean editing;

		private final EditBox nameField;
		private final Button renameButton;
		private final Button enabledButton;
		private final Button removeButton;
		private final Button applyButton;
		private final Button cancelButton;

		private SensorEntry(TrackedSensor sensor) {
			this.x = sensor.x();
			this.y = sensor.y();
			this.z = sensor.z();
			this.name = sensor.name();
			this.enabled = sensor.enabled();

			this.nameField = new EditBox(TrackedSensorListWidget.this.minecraft.font, 0, 0,
					NAME_FIELD_WIDTH, BUTTON_HEIGHT,
					Component.translatable("sculksight.config.tracked_sensors.rename.field"));
			this.nameField.setMaxLength(TrackedSensor.MAX_NAME_LENGTH);
			this.nameField.setValue(name);

			this.renameButton = Button.builder(
							Component.translatable("sculksight.config.tracked_sensors.rename"),
							button -> startEditing())
					.size(BUTTON_WIDTH, BUTTON_HEIGHT)
					.build();
			this.enabledButton = Button.builder(enabledLabel(), button -> {
						ConfigScreens.setSensorEnabled(x, y, z, !enabled);
						TrackedSensorListWidget.this.refresh();
					})
					.size(BUTTON_WIDTH, BUTTON_HEIGHT)
					.build();
			this.removeButton = Button.builder(
							Component.translatable("sculksight.config.tracked_sensors.remove"),
							button -> {
								ConfigScreens.removeSensor(x, y, z);
								TrackedSensorListWidget.this.refresh();
							})
					.tooltip(Tooltip.create(
							Component.translatable("sculksight.config.tracked_sensors.remove.tooltip")))
					.size(BUTTON_WIDTH, BUTTON_HEIGHT)
					.build();
			this.applyButton = Button.builder(
							Component.translatable("sculksight.config.tracked_sensors.rename.apply"),
							button -> {
								ConfigScreens.renameSensor(x, y, z, nameField.getValue());
								TrackedSensorListWidget.this.refresh();
							})
					.size(BUTTON_WIDTH, BUTTON_HEIGHT)
					.build();
			this.cancelButton = Button.builder(
							Component.translatable("sculksight.config.tracked_sensors.rename.cancel"),
							button -> stopEditing())
					.size(BUTTON_WIDTH, BUTTON_HEIGHT)
					.build();
		}

		private Component enabledLabel() {
			Component state = Component.translatable(enabled
					? "sculksight.config.tracked_sensors.enabled.yes"
					: "sculksight.config.tracked_sensors.enabled.no");
			return Component.translatable("sculksight.config.tracked_sensors.enabled", state);
		}

		private void startEditing() {
			editing = true;
			nameField.setValue(name);
			nameField.setFocused(true);
		}

		private void stopEditing() {
			editing = false;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
				boolean hovered, float partialTick) {
			if (editing) {
				extractEditingRow(graphics, mouseX, mouseY, partialTick);
			} else {
				extractDisplayRow(graphics, mouseX, mouseY, partialTick);
			}
		}

		private void extractEditingRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			nameField.setPosition(getContentX(), getContentY());
			nameField.extractRenderState(graphics, mouseX, mouseY, partialTick);

			applyButton.setPosition(nameField.getX() + nameField.getWidth() + SPACING, getContentY());
			applyButton.extractRenderState(graphics, mouseX, mouseY, partialTick);

			cancelButton.setPosition(applyButton.getX() + applyButton.getWidth() + SPACING, getContentY());
			cancelButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
		}

		private void extractDisplayRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			graphics.text(TrackedSensorListWidget.this.minecraft.font,
					Component.translatable("sculksight.config.tracked_sensors.name", name, x, y, z),
					getContentX(), getContentYMiddle() - 4, -1);

			removeButton.setPosition(getContentRight() - removeButton.getWidth(), getContentY());
			removeButton.extractRenderState(graphics, mouseX, mouseY, partialTick);

			enabledButton.setPosition(removeButton.getX() - SPACING - enabledButton.getWidth(), getContentY());
			enabledButton.extractRenderState(graphics, mouseX, mouseY, partialTick);

			renameButton.setPosition(enabledButton.getX() - SPACING - renameButton.getWidth(), getContentY());
			renameButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return editing ? List.of(nameField, applyButton, cancelButton)
					: List.of(renameButton, enabledButton, removeButton);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return editing ? List.of(nameField, applyButton, cancelButton)
					: List.of(renameButton, enabledButton, removeButton);
		}
	}
}
