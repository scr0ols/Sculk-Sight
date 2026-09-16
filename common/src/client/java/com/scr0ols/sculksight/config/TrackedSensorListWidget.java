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
	/**
	 * Wide enough that "Enabled: Yes"/"Enabled: No" and "Remove tracked render" keep their text
	 * centred in roughly the same place as their label changes, rather than visibly shifting inside
	 * a tightly-fitted button.
	 */
	private static final int BUTTON_WIDTH = 84;
	private static final int NAME_FIELD_WIDTH = 140;
	private static final int SPACING = 4;

	/**
	 * Which row, if any, currently has its rename field open, and what the player has typed into it
	 * so far. Rows are rebuilt wholesale from {@link ClientConfig} on every mutation (see
	 * {@link #refresh()}'s own javadoc), which would otherwise silently discard an in-progress
	 * rename on a <em>different</em> row the instant the player clicked Enable/Disable or Remove on
	 * this one. Tracking it here, keyed by position rather than by row index, lets {@link #refresh()}
	 * put the same row back into edit mode with the same typed text after every rebuild.
	 */
	private @Nullable EditingState editing;

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
	 * screen's single save-time rebuild. Also called directly by a row starting or leaving edit
	 * mode, so that {@link #editing} stays the single source of truth for which row (if any) is
	 * mid-rename - opening Rename on one row always closes any other row's open field.
	 */
	void refresh() {
		List<SensorEntry> entries = new ArrayList<>();
		for (TrackedSensor sensor : ClientConfig.get().trackedSensors()) {
			entries.add(new SensorEntry(sensor));
		}

		replaceEntries(entries);
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

	final class SensorEntry extends ContainerObjectSelectionList.Entry<SensorEntry> {

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

		private SensorEntry(TrackedSensor sensor) {
			this.x = sensor.x();
			this.y = sensor.y();
			this.z = sensor.z();
			this.name = sensor.name();
			this.enabled = sensor.enabled();

			EditingState state = TrackedSensorListWidget.this.editing;
			this.editingRow = state != null && state.matches(x, y, z);

			this.nameField = new EditBox(TrackedSensorListWidget.this.minecraft.font, 0, 0,
					NAME_FIELD_WIDTH, BUTTON_HEIGHT,
					Component.translatable("sculksight.config.tracked_sensors.rename.field"));
			this.nameField.setMaxLength(TrackedSensor.MAX_NAME_LENGTH);
			this.nameField.setValue(editingRow ? state.text : name);
			this.nameField.setResponder(value -> {
				if (TrackedSensorListWidget.this.editing != null
						&& TrackedSensorListWidget.this.editing.matches(x, y, z)) {
					TrackedSensorListWidget.this.editing.text = value;
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
						TrackedSensorListWidget.this.refresh();
					})
					.tooltip(Tooltip.create(
							Component.translatable("sculksight.config.tracked_sensors.enabled.tooltip")))
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

		/** Opens this row's rename field, closing any other row's first - see {@link #editing}. */
		private void startEditing() {
			TrackedSensorListWidget.this.editing = new EditingState(x, y, z, name);
			TrackedSensorListWidget.this.refresh();
		}

		/** Applies the typed name immediately, matching every other row action's instant-apply. */
		private void applyRename() {
			ConfigScreens.renameSensor(x, y, z, nameField.getValue());
			TrackedSensorListWidget.this.editing = null;
			TrackedSensorListWidget.this.refresh();
		}

		private void stopEditing() {
			TrackedSensorListWidget.this.editing = null;
			TrackedSensorListWidget.this.refresh();
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
			graphics.text(TrackedSensorListWidget.this.minecraft.font,
					displayName(nameMaxWidth), getContentX(), getContentYMiddle() - 4, -1);

			removeButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
			enabledButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
			renameButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
		}

		/**
		 * The row's label, shortened with an ellipsis when {@code name} is long enough to run into
		 * the Rename/Enabled/Remove buttons - up to {@link TrackedSensor#MAX_NAME_LENGTH} characters
		 * of player-chosen text sharing a 24px-tall row with three buttons is exactly the case a
		 * fixed-width layout like this one cannot assume away.
		 */
		private Component displayName(int maxWidth) {
			Font font = TrackedSensorListWidget.this.minecraft.font;
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
