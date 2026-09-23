package com.scr0ols.sculksight.config;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
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
	private static final int SPACING = 4;

	private static final int TEXT_BASELINE_OFFSET = 4;

	private static final int HEADING_COLOUR = 0xFFA0A0A0;

	private static final int LABEL_COLOUR = -1;

	private final Screen owner;

	SensorListWidget(Minecraft minecraft, int width, int height, int y, Screen owner) {
		super(minecraft, width, height, y, ITEM_HEIGHT);
		this.owner = owner;
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

		private final Button optionsButton;
		private final Button removeButton;

		private TrackedRow(TrackedSensor sensor) {
			this.x = sensor.x();
			this.y = sensor.y();
			this.z = sensor.z();
			this.name = sensor.name();

			this.optionsButton = Button.builder(
							Component.translatable("sculksight.config.tracked_sensors.options"),
							button -> openOptions(sensor))
					.tooltip(Tooltip.create(
							Component.translatable("sculksight.config.tracked_sensors.options.tooltip")))
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
		}

		private void openOptions(TrackedSensor sensor) {
			SensorListWidget.this.minecraft.gui.setScreen(
					new SensorOptionsScreen(SensorListWidget.this.owner, sensor, SensorListWidget.this::refresh));
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
				boolean hovered, float partialTick) {
			removeButton.setPosition(getContentRight() - removeButton.getWidth(), getContentY());
			optionsButton.setPosition(removeButton.getX() - SPACING - optionsButton.getWidth(), getContentY());

			int nameMaxWidth = optionsButton.getX() - SPACING - getContentX();
			graphics.text(SensorListWidget.this.minecraft.font,
					displayName(nameMaxWidth), getContentX(),
					getContentYMiddle() - TEXT_BASELINE_OFFSET, LABEL_COLOUR);

			removeButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
			optionsButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
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
			return List.of(optionsButton, removeButton);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(optionsButton, removeButton);
		}
	}
}
