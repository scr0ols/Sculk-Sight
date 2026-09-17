package com.scr0ols.sculksight.config;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.scr0ols.sculksight.audit.RadiusAuditController;
import com.scr0ols.sculksight.client.ClientPlatform;
import com.scr0ols.sculksight.client.Environment;
import com.scr0ols.sculksight.client.SensorKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link ConfigScreens}'s package-private action methods - the pure mutation logic
 * behind {@link SettingsScreen}'s buttons, now that every one of them applies to
 * {@link ClientConfig} the instant it is clicked instead of waiting for a Save button.
 *
 * <p>Up to v0.2 this class was {@code ConfigScreensSaveTest}, and reached {@code ConfigScreens}'s
 * private {@code save} method and its {@code SensorDraft} holder through reflection - both existed
 * only because Cloth Config batched every edit behind one distant save. Removing Cloth removed the
 * batching along with it: there is no draft state left to reconcile, no mid-screen-drop bug for a
 * save to reintroduce, and so no reflection either. {@link ConfigScreens#renameSensor},
 * {@link ConfigScreens#setSensorEnabled}, {@link ConfigScreens#setAuditSensorHidden},
 * {@link ConfigScreens#removeSensor}, {@link ConfigScreens#setShellOpacityPercent} and
 * {@link ConfigScreens#setRenderPolicy} are called directly, the same package-private methods
 * {@link SettingsScreen}'s widgets call.
 *
 * <p>{@code ConfigScreens} lives in {@code common}'s {@code src/client/java}, which is a plain
 * source artifact rather than a compiled sourceSet of {@code common} itself (see
 * {@code common/build.gradle}'s own comment on {@code commonClientJava}) - so nothing on
 * {@code common}'s test classpath can reach it. This module recompiles that source against a real
 * Minecraft classpath the same way it does for the mod jar itself, which is what lets this test
 * call the real, compiled action methods rather than re-describing their logic here.
 *
 * <p>{@link SculkSightConfig#untrack} already has its own coverage in {@code common}'s
 * {@code SculkSightConfigTest}, so {@link #removeSensorDropsOnlyTheMatchingPosition()} below checks
 * only that {@link ConfigScreens#removeSensor} calls through to it correctly, not {@code untrack}'s
 * own list-preserving behaviour a second time.
 */
class ConfigScreensActionsTest {

	@TempDir
	Path tempDir;

	@Test
	void renameSensorUpdatesOnlyThatSensorsName() {
		ClientPlatform.set(new TestEnvironment(tempDir));
		ClientConfig.load();

		TrackedSensor first = new TrackedSensor(1, 2, 3, "First", true);
		TrackedSensor second = new TrackedSensor(4, 5, 6, "Second", true);
		ClientConfig.set(new SculkSightConfig(SculkSightConfig.DEFAULT_SHELL_OPACITY_PERCENT,
				RenderPolicy.UNION, List.of(first, second)));

		ConfigScreens.renameSensor(1, 2, 3, "Renamed");

		List<TrackedSensor> sensors = ClientConfig.get().trackedSensors();
		assertEquals(2, sensors.size());
		assertTrue(sensors.stream().anyMatch(s -> s.x() == 1 && s.y() == 2 && s.z() == 3
				&& s.name().equals("Renamed")), "the targeted sensor must be renamed");
		assertTrue(sensors.stream().anyMatch(s -> s.x() == 4 && s.y() == 5 && s.z() == 6
				&& s.name().equals("Second")), "every other sensor must be left untouched");
	}

	@Test
	void renameSensorWithABlankNameKeepsTheExistingName() {
		ClientPlatform.set(new TestEnvironment(tempDir));
		ClientConfig.load();

		TrackedSensor sensor = new TrackedSensor(1, 2, 3, "Original", true);
		ClientConfig.set(new SculkSightConfig(SculkSightConfig.DEFAULT_SHELL_OPACITY_PERCENT,
				RenderPolicy.UNION, List.of(sensor)));

		ConfigScreens.renameSensor(1, 2, 3, "   ");

		assertEquals("Original", ClientConfig.get().trackedSensors().get(0).name(),
				"a blank name is not an edit - the old name survives, matching the old Cloth "
						+ "screen's own save-time fallback");
	}

	@Test
	void setSensorEnabledRoundTripsBothDirections() {
		ClientPlatform.set(new TestEnvironment(tempDir));
		ClientConfig.load();

		TrackedSensor sensor = new TrackedSensor(1, 2, 3, "Sensor", true);
		ClientConfig.set(new SculkSightConfig(SculkSightConfig.DEFAULT_SHELL_OPACITY_PERCENT,
				RenderPolicy.UNION, List.of(sensor)));

		ConfigScreens.setSensorEnabled(1, 2, 3, false);
		assertEquals(false, ClientConfig.get().trackedSensors().get(0).enabled());

		ConfigScreens.setSensorEnabled(1, 2, 3, true);
		assertEquals(true, ClientConfig.get().trackedSensors().get(0).enabled());
	}

	@Test
	void removeSensorDropsOnlyTheMatchingPosition() {
		ClientPlatform.set(new TestEnvironment(tempDir));
		ClientConfig.load();

		TrackedSensor removed = new TrackedSensor(1, 2, 3, "Remove me", true);
		TrackedSensor retained = new TrackedSensor(4, 5, 6, "Keep me", true);
		ClientConfig.set(new SculkSightConfig(SculkSightConfig.DEFAULT_SHELL_OPACITY_PERCENT,
				RenderPolicy.UNION, List.of(removed, retained)));

		ConfigScreens.removeSensor(1, 2, 3);

		assertEquals(List.of(retained), ClientConfig.get().trackedSensors());
	}

	/**
	 * The audit section's own toggle, and the one action here that deliberately persists nothing:
	 * an audited position is not in {@code trackedSensors()} at all, so its visibility lives in
	 * {@link RadiusAuditController}'s session-only set. {@code RadiusAuditControllerTest} covers
	 * that set's own behaviour; what this checks is that {@code ConfigScreens} reaches it with the
	 * position the clicked row named, and leaves the saved config alone doing so.
	 */
	@Test
	void setAuditSensorHiddenTogglesSessionStateWithoutTouchingTheSavedConfig() {
		ClientPlatform.set(new TestEnvironment(tempDir));
		ClientConfig.load();

		TrackedSensor untouched = new TrackedSensor(1, 2, 3, "Tracked", true);
		ClientConfig.set(new SculkSightConfig(SculkSightConfig.DEFAULT_SHELL_OPACITY_PERCENT,
				RenderPolicy.UNION, List.of(untouched)));
		try {
			ConfigScreens.setAuditSensorHidden(7, 8, 9, true);
			assertTrue(RadiusAuditController.isHidden(new SensorKey(7, 8, 9)));

			ConfigScreens.setAuditSensorHidden(7, 8, 9, false);
			assertFalse(RadiusAuditController.isHidden(new SensorKey(7, 8, 9)));

			assertEquals(List.of(untouched), ClientConfig.get().trackedSensors(),
					"hiding an audited sensor must not rewrite the player's tracked list");
		} finally {
			RadiusAuditController.clear();
		}
	}

	@Test
	void setShellOpacityPercentPersistsToClientConfig() {
		ClientPlatform.set(new TestEnvironment(tempDir));
		ClientConfig.load();

		ConfigScreens.setShellOpacityPercent(60);

		assertEquals(60, ClientConfig.get().shellOpacityPercent());
	}

	@Test
	void setRenderPolicyPersistsToClientConfig() {
		ClientPlatform.set(new TestEnvironment(tempDir));
		ClientConfig.load();

		ConfigScreens.setRenderPolicy(RenderPolicy.PER_SENSOR);

		assertEquals(RenderPolicy.PER_SENSOR, ClientConfig.get().renderPolicy());
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
