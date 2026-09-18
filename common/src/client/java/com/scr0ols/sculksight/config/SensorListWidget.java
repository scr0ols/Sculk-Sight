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

/**
 * The settings screen's sensor list: the player's tracked sensors, and - while a
 * {@code /sculksight find ... live} is active - what that audit currently selects, under a heading
 * of its own. Every control applies the instant the player clicks it; see {@link ConfigScreens}'s
 * own javadoc for why there is no Save button here at all.
 *
 * <p><b>Two sections in one scrolling list rather than two lists.</b> {@link SettingsScreen}'s
 * {@code HeaderAndFooterLayout} gives the contents band to a single widget, and the two sections
 * are read together - "which of these renders is the one I want gone" spans both - so splitting
 * them across two independently-scrolling lists, or behind a button to a second screen, would put a
 * scrollbar between two halves of one question. {@link Row} is therefore the list's element type
 * and has four shapes: {@link SectionRow} for a plain heading or an empty-section line,
 * {@link AuditHeadingRow} for the live find's heading and its Pin control, {@link TrackedRow} for a
 * persisted tracked sensor, and {@link AuditRow} for one of a live find's own hits.
 *
 * <p><b>Why the audit section exists at all.</b> An audited position is not in
 * {@code ClientConfig.trackedSensors()} - the audit is a query, not a curated list - so until this
 * section was added the screen could not show it, and the tracked list's own Enabled toggles could
 * only reach positions the player had separately pressed K on. On the 2026-09-17 report's own scene
 * that was five rows against twenty-seven renders, with no overlap: every toggle in the menu was
 * switching off something that was not on screen, and every render on screen had no row. See
 * {@link RadiusAuditController#setHidden} for where an {@link AuditRow}'s state actually lives.
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
 * {@link SculkSightConfig#MAX_TRACKED_SENSORS} tracked rows and
 * {@code SculkSightConfig.radiusAuditCap()} audit rows exist, so {@link #refresh()} rebuilding all
 * of them from {@link ClientConfig#get()} and {@link RadiusAuditController#selection()} costs
 * nothing worth avoiding, and it is what keeps a row's own fields (name, enabled, hidden) from ever
 * drifting out of sync with the state a different row's action just wrote.
 */
final class SensorListWidget extends ContainerObjectSelectionList<SensorListWidget.Row> {

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

	/** Vertical offset that centres one line of text on a {@link #ITEM_HEIGHT}-tall row. */
	private static final int TEXT_BASELINE_OFFSET = 4;

	/** Section headings, and a section's "nothing here" line, are dimmer than a row's own label. */
	private static final int HEADING_COLOUR = 0xFFA0A0A0;

	private static final int LABEL_COLOUR = -1;

	/**
	 * Which row, if any, currently has its rename field open, and what the player has typed into it
	 * so far. Rows are rebuilt wholesale from live state on every mutation (see {@link #refresh()}'s
	 * own javadoc), which would otherwise silently discard an in-progress rename on a
	 * <em>different</em> row the instant the player clicked Enable/Disable or Remove on this one.
	 * Tracking it here, keyed by position rather than by row index, lets {@link #refresh()} put the
	 * same row back into edit mode with the same typed text after every rebuild.
	 */
	private @Nullable EditingState editing;

	SensorListWidget(Minecraft minecraft, int width, int height, int y) {
		super(minecraft, width, height, y, ITEM_HEIGHT);
		refresh();
	}

	@Override
	public int getRowWidth() {
		return Math.min(400, this.width - 20);
	}

	/**
	 * Rebuilds every row from live {@link ClientConfig} and {@link RadiusAuditController} state. The
	 * screen calls this after every button press that reaches {@link ConfigScreens}, immediate-apply's
	 * replacement for the old screen's single save-time rebuild. Also called directly by a row
	 * starting or leaving edit mode, so that {@link #editing} stays the single source of truth for
	 * which row (if any) is mid-rename - opening Rename on one row always closes any other row's open
	 * field.
	 *
	 * <p><b>Not called every tick</b>, even though a live find re-selects every tick: the player is
	 * standing still while they read this screen, so the selection cannot change under them, and a
	 * per-tick rebuild would discard and rebuild every row's widgets for a set that was already
	 * right. {@link SettingsScreen}'s own javadoc carries the same note from the other side.
	 *
	 * <p>Scroll position survives a rebuild. {@code AbstractSelectionList.clearEntries} clears the
	 * children and the selection and does not touch the scroll amount - read from the decompiled 26.2
	 * client rather than assumed, because a Pin or Shown click on row 20 of 27 silently jumping the
	 * list back to the top would be its own bug.
	 *
	 * <p><b>An empty tracked list gets its heading and nothing else</b> - no placeholder line. The
	 * heading already says what the section is, so a line underneath saying it is empty only repeats
	 * what the blank space says, and the how-to it used to carry ("press K") is a keybind the
	 * Controls screen and the README both document where a player goes looking for it. The audit
	 * section below is the deliberate exception, for the reason in {@link #addAuditRows}: there,
	 * empty and absent are two different answers and the section has to tell them apart.
	 */
	void refresh() {
		List<Row> rows = new ArrayList<>();

		rows.add(new SectionRow(Component.translatable("sculksight.config.tracked_sensors")));
		for (TrackedSensor sensor : ClientConfig.get().trackedSensors()) {
			rows.add(new TrackedRow(sensor));
		}

		addAuditRows(rows);

		replaceEntries(rows);
	}

	/**
	 * The audit section, present only while an audit is active - with no audit there is nothing to
	 * head and nothing to list, and an empty section would suggest the feature had failed rather
	 * than that it had not been asked for.
	 *
	 * <p>A selection that is active but still empty <em>is</em> shown, with its own line: "the
	 * command ran and found nothing in range here" is a different answer from "no command has run",
	 * and the heading names the query so the player can see which radius produced it.
	 */
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

	/**
	 * One line in the list. Abstract rather than an interface because
	 * {@code ContainerObjectSelectionList.Entry} is itself an abstract class, and self-referential
	 * in its own type parameter - so this type, not each concrete row, is what the list is
	 * parameterised on, and every row below is substitutable for every other.
	 */
	abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
	}

	/**
	 * A heading, or a section's "nothing here" line: text only, no controls. Returns no children,
	 * so vanilla's own click and focus routing skips it rather than letting a heading be tabbed to.
	 */
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

	/**
	 * The live find's heading, with the control that turns the whole selection into tracked
	 * sensors - {@link RadiusAuditMode#STATIC}'s outcome, reached after the fact.
	 *
	 * <p><b>On the heading rather than on each sensor row, because it is not a per-sensor act.</b>
	 * Pinning ends the find, so a Pin button on one row would silently dispose of the other
	 * twenty-six rows around it; the heading is the one place on screen that already stands for the
	 * selection as a whole. The button is absent while the selection is empty, there being nothing
	 * to pin and no useful message to give for pressing it.
	 *
	 * <p>Reports through chat rather than on screen: the pinned rows appearing and the find's
	 * section vanishing is the visible half, but the counts {@link AuditPin#describe} carries - how
	 * many were already tracked, how many did not fit - need words, and this screen has nowhere to
	 * put a line of them. Chat is still there when the player closes the screen, and it is the same
	 * channel the command itself reports through, so both routes read identically.
	 */
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

		/**
		 * The heading is generated text of a known, short shape ("radius 64, all detectors (27
		 * selected)"), not a player-chosen name of up to {@link TrackedSensor#MAX_NAME_LENGTH}
		 * characters - so it is not ellipsised the way {@link TrackedRow}'s own label has to be.
		 */
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

	/**
	 * One sensor the active audit selected, with a single control: show it or hide it. No rename
	 * (the position has no stored name to rename) and no remove (the audit decides its own
	 * membership; removing a hit would only last until the next tick re-selected it - hiding is
	 * what survives, because {@code ShellRenderer} consults the hidden set on every merge).
	 */
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

		/** Opens this row's rename field, closing any other row's first - see {@link #editing}. */
		private void startEditing() {
			SensorListWidget.this.editing = new EditingState(x, y, z, name);
			SensorListWidget.this.refresh();
		}

		/** Applies the typed name immediately, matching every other row action's instant-apply. */
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

		/**
		 * The row's label, shortened with an ellipsis when {@code name} is long enough to run into
		 * the Rename/Enabled/Remove buttons - up to {@link TrackedSensor#MAX_NAME_LENGTH} characters
		 * of player-chosen text sharing a 24px-tall row with three buttons is exactly the case a
		 * fixed-width layout like this one cannot assume away.
		 */
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
