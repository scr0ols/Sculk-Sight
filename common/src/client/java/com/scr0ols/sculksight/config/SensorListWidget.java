package com.scr0ols.sculksight.config;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import com.scr0ols.sculksight.audit.AuditPin;
import com.scr0ols.sculksight.audit.RadiusAudit.AuditedSensor;
import com.scr0ols.sculksight.audit.RadiusAuditController;
import com.scr0ols.sculksight.audit.RadiusAuditMode;
import com.scr0ols.sculksight.audit.RadiusAuditRequest;
import com.scr0ols.sculksight.client.SensorKey;

final class SensorListWidget extends ContainerObjectSelectionList<SensorListWidget.Row> {

	private static final int ITEM_HEIGHT = 24;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_WIDTH = 84;
	private static final int NAME_FIELD_WIDTH = 140;
	private static final int SPACING = 4;

	private static final int TEXT_BASELINE_OFFSET = 4;

	private static final int HEADING_COLOUR = 0xFFA0A0A0;

	private static final int LABEL_COLOUR = -1;

	private @Nullable EditingState editing;

	SensorListWidget(Minecraft minecraft, int width, int height, int y) {
		super(minecraft, width, height, y, ITEM_HEIGHT);
		refresh();
	}

	@Override
	public int getRowWidth() {
		return Math.min(400, this.width - 20);
	}

	void refresh() {
		List<Row> rows = new ArrayList<>();

		rows.add(new SectionRow(Component.translatable("sculksight.config.tracked_sensors")));
		for (TrackedSensor sensor : ClientConfig.get().trackedSensors()) {
			rows.add(new TrackedRow(sensor));
		}

		addAuditRows(rows);

		replaceEntries(rows);
	}

	private void addAuditRows(List<Row> rows) {
		RadiusAuditRequest request = RadiusAuditController.activeRequest();
		if (request == null) {
			return;
		}

		List<AuditedSensor> selection = RadiusAuditController.selection();
		rows.add(new AuditHeadingRow(Component.translatable("sculksight.config.audit_sensors",
				request.describe(), selection.size()), !selection.isEmpty()));

		if (selection.isEmpty()) {
			rows.add(new SectionRow(Component.translatable("sculksight.config.audit_sensors.empty")));
			return;
		}

		for (AuditedSensor sensor : selection) {
			rows.add(new AuditRow(sensor));
		}
	}

	private static final class EditingState {
		private final int x;
		private final int y;
		private final int z;
		private String text;

		private EditingState(int x, int y, int z, String text) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.text = text;
		}

		private boolean matches(int otherX, int otherY, int otherZ) {
			return x == otherX && y == otherY && z == otherZ;
		}
	}

	abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
	}

	final class SectionRow extends Row {

		private final Component label;

		private SectionRow(Component label) {
			this.label = label;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
				boolean hovered, float partialTick) {
			graphics.text(SensorListWidget.this.minecraft.font, label, getContentX(),
					getContentYMiddle() - TEXT_BASELINE_OFFSET, HEADING_COLOUR);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of();
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of();
		}
	}

	final class AuditHeadingRow extends Row {

		private final Component label;
		private final @Nullable Button pinButton;

		private AuditHeadingRow(Component label, boolean pinnable) {
			this.label = label;
			this.pinButton = pinnable ? buildPinButton() : null;
		}

		private Button buildPinButton() {
			return Button.builder(Component.translatable("sculksight.config.audit_sensors.pin"),
							button -> {
								String outcome = ConfigScreens.pinAuditSelection();
								SensorListWidget.this.minecraft.gui.hud.getChat()
										.addClientSystemMessage(Component.literal("[sculksight] " + outcome));
								SensorListWidget.this.refresh();
							})
					.tooltip(Tooltip.create(
							Component.translatable("sculksight.config.audit_sensors.pin.tooltip")))
					.size(BUTTON_WIDTH, BUTTON_HEIGHT)
					.build();
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
				boolean hovered, float partialTick) {
			if (pinButton != null) {
				pinButton.setPosition(getContentRight() - pinButton.getWidth(), getContentY());
				pinButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
			}

			graphics.text(SensorListWidget.this.minecraft.font, label, getContentX(),
					getContentYMiddle() - TEXT_BASELINE_OFFSET, HEADING_COLOUR);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return pinButton == null ? List.of() : List.of(pinButton);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return pinButton == null ? List.of() : List.of(pinButton);
		}
	}

	final class AuditRow extends Row {

		private final SensorKey position;
		private final boolean hidden;
		private final Button visibilityButton;

		private AuditRow(AuditedSensor sensor) {
			this.position = sensor.position();
			this.hidden = RadiusAuditController.isHidden(position);
			this.visibilityButton = Button.builder(visibilityLabel(), button -> {
						ConfigScreens.setAuditSensorHidden(position.x(), position.y(), position.z(), !hidden);
						SensorListWidget.this.refresh();
					})
					.tooltip(Tooltip.create(
							Component.translatable("sculksight.config.audit_sensors.shown.tooltip")))
					.size(BUTTON_WIDTH, BUTTON_HEIGHT)
					.build();
		}

		private Component visibilityLabel() {
			Component state = Component.translatable(hidden
					? "sculksight.config.audit_sensors.shown.no"
					: "sculksight.config.audit_sensors.shown.yes");
			return Component.translatable("sculksight.config.audit_sensors.shown", state);
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
				boolean hovered, float partialTick) {
			visibilityButton.setPosition(getContentRight() - visibilityButton.getWidth(), getContentY());

			graphics.text(SensorListWidget.this.minecraft.font,
					Component.translatable("sculksight.config.audit_sensors.position",
							position.x(), position.y(), position.z()),
					getContentX(), getContentYMiddle() - TEXT_BASELINE_OFFSET, LABEL_COLOUR);

			visibilityButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of(visibilityButton);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(visibilityButton);
		}
	}

	final class TrackedRow extends Row {

		private final int x;
		private final int y;
		private final int z;
		private final String name;
		private final boolean enabled;

		private final boolean editingRow;

		private final EditBox nameField;
		private final Button renameButton;
		private final Button enabledButton;
		private final Button removeButton;
		private final Button applyButton;
		private final Button cancelButton;

		private TrackedRow(TrackedSensor sensor) {
			this.x = sensor.x();
			this.y = sensor.y();
			this.z = sensor.z();
			this.name = sensor.name();
			this.enabled = sensor.enabled();

			EditingState state = SensorListWidget.this.editing;
			this.editingRow = state != null && state.matches(x, y, z);

			this.nameField = new EditBox(SensorListWidget.this.minecraft.font, 0, 0,
					NAME_FIELD_WIDTH, BUTTON_HEIGHT,
					Component.translatable("sculksight.config.tracked_sensors.rename.field"));
			this.nameField.setMaxLength(TrackedSensor.MAX_NAME_LENGTH);
			this.nameField.setValue(editingRow ? state.text : name);
			this.nameField.setResponder(value -> {
				if (SensorListWidget.this.editing != null
						&& SensorListWidget.this.editing.matches(x, y, z)) {
					SensorListWidget.this.editing.text = value;
				}
			});
			if (editingRow) {
				this.nameField.setFocused(true);
			}

			this.renameButton = Button.builder(
							Component.translatable("sculksight.config.tracked_sensors.rename"),
							button -> startEditing())
					.tooltip(Tooltip.create(
							Component.translatable("sculksight.config.tracked_sensors.rename.tooltip")))
					.size(BUTTON_WIDTH, BUTTON_HEIGHT)
					.build();
			this.enabledButton = Button.builder(enabledLabel(), button -> {
						ConfigScreens.setSensorEnabled(x, y, z, !enabled);
						SensorListWidget.this.refresh();
					})
					.tooltip(Tooltip.create(
							Component.translatable("sculksight.config.tracked_sensors.enabled.tooltip")))
					.size(BUTTON_WIDTH, BUTTON_HEIGHT)
					.build();
			this.removeButton = Button.builder(
							Component.translatable("sculksight.config.tracked_sensors.remove"),
							button -> {
								ConfigScreens.removeSensor(x, y, z);
								SensorListWidget.this.refresh();
							})
					.tooltip(Tooltip.create(
							Component.translatable("sculksight.config.tracked_sensors.remove.tooltip")))
					.size(BUTTON_WIDTH, BUTTON_HEIGHT)
					.build();
			this.applyButton = Button.builder(
							Component.translatable("sculksight.config.tracked_sensors.rename.apply"),
							button -> applyRename())
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
			SensorListWidget.this.editing = new EditingState(x, y, z, name);
			SensorListWidget.this.refresh();
		}

		private void applyRename() {
			ConfigScreens.renameSensor(x, y, z, nameField.getValue());
			SensorListWidget.this.editing = null;
			SensorListWidget.this.refresh();
		}

		private void stopEditing() {
			SensorListWidget.this.editing = null;
			SensorListWidget.this.refresh();
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			if (editingRow) {
				int keyCode = event.key();
				if (keyCode == InputConstants.KEY_ESCAPE) {
					stopEditing();
					return true;
				}
				if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
					applyRename();
					return true;
				}
			}
			return super.keyPressed(event);
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
				boolean hovered, float partialTick) {
			if (editingRow) {
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
			removeButton.setPosition(getContentRight() - removeButton.getWidth(), getContentY());
			enabledButton.setPosition(removeButton.getX() - SPACING - enabledButton.getWidth(), getContentY());
			renameButton.setPosition(enabledButton.getX() - SPACING - renameButton.getWidth(), getContentY());

			int nameMaxWidth = renameButton.getX() - SPACING - getContentX();
			graphics.text(SensorListWidget.this.minecraft.font,
					displayName(nameMaxWidth), getContentX(),
					getContentYMiddle() - TEXT_BASELINE_OFFSET, LABEL_COLOUR);

			removeButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
			enabledButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
			renameButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
		}

		private Component displayName(int maxWidth) {
			Font font = SensorListWidget.this.minecraft.font;
			Component full = Component.translatable("sculksight.config.tracked_sensors.name", name, x, y, z);
			if (maxWidth <= 0 || font.width(full) <= maxWidth) {
				return full;
			}

			String shortened = name;
			while (!shortened.isEmpty()) {
				shortened = shortened.substring(0, shortened.length() - 1);
				Component candidate = Component.translatable(
						"sculksight.config.tracked_sensors.name", shortened + "…", x, y, z);
				if (font.width(candidate) <= maxWidth) {
					return candidate;
				}
			}
			return full;
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return editingRow ? List.of(nameField, applyButton, cancelButton)
					: List.of(renameButton, enabledButton, removeButton);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return editingRow ? List.of(nameField, applyButton, cancelButton)
					: List.of(renameButton, enabledButton, removeButton);
		}
	}
}
