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

class ConfigScreensActionsTest {

	@TempDir
	Path tempDir;

	@Test
	void renameSensorUpdatesOnlyThatSensorsName() {
		ClientPlatform.set(new TestEnvironment(tempDir));
		ClientConfig.load();

		TrackedSensor first = new TrackedSensor(1, 2, 3, "First", true, false);
		TrackedSensor second = new TrackedSensor(4, 5, 6, "Second", true, false);
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

		TrackedSensor sensor = new TrackedSensor(1, 2, 3, "Original", true, false);
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

		TrackedSensor sensor = new TrackedSensor(1, 2, 3, "Sensor", true, false);
		ClientConfig.set(new SculkSightConfig(SculkSightConfig.DEFAULT_SHELL_OPACITY_PERCENT,
				RenderPolicy.UNION, List.of(sensor)));

		ConfigScreens.setSensorEnabled(1, 2, 3, false);
		assertEquals(false, ClientConfig.get().trackedSensors().get(0).enabled());

		ConfigScreens.setSensorEnabled(1, 2, 3, true);
		assertEquals(true, ClientConfig.get().trackedSensors().get(0).enabled());
	}

	@Test
	void setSensorDelayOverlayRoundTripsBothDirectionsWithoutTouchingOtherSensors() {
		ClientPlatform.set(new TestEnvironment(tempDir));
		ClientConfig.load();

		TrackedSensor sensor = new TrackedSensor(1, 2, 3, "Sensor", true, false);
		TrackedSensor other = new TrackedSensor(4, 5, 6, "Other", true, false);
		ClientConfig.set(new SculkSightConfig(SculkSightConfig.DEFAULT_SHELL_OPACITY_PERCENT,
				RenderPolicy.UNION, List.of(sensor, other)));

		ConfigScreens.setSensorDelayOverlay(1, 2, 3, true);
		List<TrackedSensor> afterEnabling = ClientConfig.get().trackedSensors();
		assertEquals(true, afterEnabling.get(0).delayOverlayEnabled());
		assertEquals(false, afterEnabling.get(1).delayOverlayEnabled(),
				"another sensor's delay-overlay flag must be left untouched");

		ConfigScreens.setSensorDelayOverlay(1, 2, 3, false);
		assertEquals(false, ClientConfig.get().trackedSensors().get(0).delayOverlayEnabled());
	}

	@Test
	void setSensorDelayOverlayTouchesOnlyTheTargetedSensorsFlag() {
		ClientPlatform.set(new TestEnvironment(tempDir));
		ClientConfig.load();

		TrackedSensor sensor = new TrackedSensor(1, 2, 3, "Sensor", true, false);
		ClientConfig.set(new SculkSightConfig(60, RenderPolicy.PER_SENSOR, List.of(sensor)));

		ConfigScreens.setSensorDelayOverlay(1, 2, 3, true);

		SculkSightConfig after = ClientConfig.get();
		assertEquals(60, after.shellOpacityPercent(),
				"the per-sensor delay-overlay flag is not a global setting - it must not touch "
						+ "shell opacity or any other config-wide field");
		assertEquals(RenderPolicy.PER_SENSOR, after.renderPolicy(),
				"the per-sensor delay-overlay flag must not touch the render policy either");
		assertEquals(true, after.trackedSensors().get(0).enabled(),
				"toggling the delay-overlay flag must not touch the sensor's own enabled flag");
	}

	@Test
	void removeSensorDropsOnlyTheMatchingPosition() {
		ClientPlatform.set(new TestEnvironment(tempDir));
		ClientConfig.load();

		TrackedSensor removed = new TrackedSensor(1, 2, 3, "Remove me", true, false);
		TrackedSensor retained = new TrackedSensor(4, 5, 6, "Keep me", true, false);
		ClientConfig.set(new SculkSightConfig(SculkSightConfig.DEFAULT_SHELL_OPACITY_PERCENT,
				RenderPolicy.UNION, List.of(removed, retained)));

		ConfigScreens.removeSensor(1, 2, 3);

		assertEquals(List.of(retained), ClientConfig.get().trackedSensors());
	}

	@Test
	void setAuditSensorHiddenTogglesSessionStateWithoutTouchingTheSavedConfig() {
		ClientPlatform.set(new TestEnvironment(tempDir));
		ClientConfig.load();

		TrackedSensor untouched = new TrackedSensor(1, 2, 3, "Tracked", true, false);
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
