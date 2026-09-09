package com.scr0ols.sculksight.config;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.scr0ols.sculksight.client.ClientPlatform;
import com.scr0ols.sculksight.client.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the config-screen save fixed by the "config-save-drops-sensor-tracked-
 * mid-screen" decision: a sensor tracked through {@code ShellRenderer}'s activate keybind while
 * the Cloth Config settings screen is already open must survive that screen's own save, even
 * though no widget for it exists on the screen.
 *
 * <p>{@code ConfigScreens} lives in {@code common}'s {@code src/client/java}, which is a plain
 * source artifact rather than a compiled sourceSet of {@code common} itself (see
 * {@code common/build.gradle}'s own comment on {@code commonClientJava}) - so nothing on
 * {@code common}'s test classpath can reach it. This module recompiles that source against a real
 * Minecraft and Cloth Config classpath the same way it does for the mod jar itself, which is what
 * lets this test call the real, compiled {@code save} logic - through reflection, because both
 * {@code save} and the {@code SensorDraft} it takes are private to {@code ConfigScreens} - rather
 * than re-describing that logic in the test.
 */
class ConfigScreensSaveTest {

	@TempDir
	Path tempDir;

	@Test
	void saveKeepsASensorTrackedAfterTheScreenOpened() throws Exception {
		ClientPlatform.set(new TestEnvironment(tempDir));
		ClientConfig.load();

		TrackedSensor original = new TrackedSensor(1, 2, 3, "Original", true);
		ClientConfig.set(new SculkSightConfig(SculkSightConfig.DEFAULT_SHELL_OPACITY_PERCENT,
				RenderPolicy.UNION, List.of(original)));

		// Mirrors ConfigScreens.create(): one draft per sensor tracked when the screen opened, and
		// the player edits that draft's widget (the name field) before saving.
		Object originalDraft = newSensorDraft(original);
		setDraftName(originalDraft, "Renamed while screen was open");
		List<Object> pendingSensors = new ArrayList<>();
		pendingSensors.add(originalDraft);

		// Mirrors ShellRenderer.activate(): the player presses the activate keybind while the
		// screen is still open, tracking a second sensor that has no draft or widget on this screen.
		TrackedSensor trackedMidScreen = new TrackedSensor(4, 5, 6, "Sensor 4, 5, 6", true);
		ClientConfig.set(ClientConfig.get().track(trackedMidScreen));

		invokeSave(SculkSightConfig.DEFAULT_SHELL_OPACITY_PERCENT, RenderPolicy.UNION, pendingSensors);

		List<TrackedSensor> saved = ClientConfig.get().trackedSensors();
		assertEquals(2, saved.size(), "the sensor tracked mid-screen must survive the screen's save");
		assertTrue(
				saved.stream().anyMatch(s -> s.x() == 4 && s.y() == 5 && s.z() == 6
						&& s.name().equals("Sensor 4, 5, 6")),
				"the sensor tracked mid-screen must still be present, unedited, after save");
		assertTrue(
				saved.stream().anyMatch(s -> s.x() == 1 && s.y() == 2 && s.z() == 3
						&& s.name().equals("Renamed while screen was open")),
				"an edit made through the screen's own widget must still be applied on save");
	}

	@Test
	void saveRemoveControlDeletesTheTrackedRenderFromConfiguration() throws Exception {
		ClientPlatform.set(new TestEnvironment(tempDir));
		ClientConfig.load();

		TrackedSensor removed = new TrackedSensor(1, 2, 3, "Remove me", true);
		TrackedSensor retained = new TrackedSensor(4, 5, 6, "Keep me", true);
		ClientConfig.set(new SculkSightConfig(SculkSightConfig.DEFAULT_SHELL_OPACITY_PERCENT,
				RenderPolicy.UNION, List.of(removed, retained)));

		Object removedDraft = newSensorDraft(removed);
		setDraftRemove(removedDraft, true);
		List<Object> pendingSensors = new ArrayList<>();
		pendingSensors.add(removedDraft);
		pendingSensors.add(newSensorDraft(retained));

		invokeSave(SculkSightConfig.DEFAULT_SHELL_OPACITY_PERCENT, RenderPolicy.UNION, pendingSensors);

		assertEquals(List.of(retained), ClientConfig.get().trackedSensors());
	}

	private static Object newSensorDraft(TrackedSensor sensor) throws ReflectiveOperationException {
		Class<?> draftClass = Class.forName("com.scr0ols.sculksight.config.ConfigScreens$SensorDraft");
		Constructor<?> constructor = draftClass.getDeclaredConstructor(TrackedSensor.class);
		constructor.setAccessible(true);
		return constructor.newInstance(sensor);
	}

	@SuppressWarnings("unchecked")
	private static void setDraftName(Object draft, String name) throws ReflectiveOperationException {
		Field nameField = draft.getClass().getDeclaredField("name");
		nameField.setAccessible(true);
		((AtomicReference<String>) nameField.get(draft)).set(name);
	}

	@SuppressWarnings("unchecked")
	private static void setDraftRemove(Object draft, boolean remove) throws ReflectiveOperationException {
		Field removeField = draft.getClass().getDeclaredField("remove");
		removeField.setAccessible(true);
		((AtomicBoolean) removeField.get(draft)).set(remove);
	}

	private static void invokeSave(int shellOpacityPercent, RenderPolicy renderPolicy,
			List<Object> pendingSensors) throws ReflectiveOperationException {
		Method save = ConfigScreens.class.getDeclaredMethod("save", int.class, RenderPolicy.class,
				List.class);
		save.setAccessible(true);
		save.invoke(null, shellOpacityPercent, renderPolicy, pendingSensors);
	}

	private record TestEnvironment(Path dir) implements Environment {
		@Override
		public boolean isDevelopmentEnvironment() {
			return true;
		}

		@Override
		public Path gameDir() {
			return dir;
		}

		@Override
		public Path configDir() {
			return dir;
		}
	}
}
