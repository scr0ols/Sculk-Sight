package com.scr0ols.sculksight.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import com.scr0ols.sculksight.audit.RadiusAudit.AuditedSensor;
import com.scr0ols.sculksight.client.DetectorType;
import com.scr0ols.sculksight.client.SensorKey;
import com.scr0ols.sculksight.config.RenderPolicy;
import com.scr0ols.sculksight.config.SculkSightConfig;
import com.scr0ols.sculksight.config.TrackedSensor;

class AuditPinTest {

	private static SculkSightConfig configWith(TrackedSensor... tracked) {
		return new SculkSightConfig(SculkSightConfig.DEFAULT_SHELL_OPACITY_PERCENT,
				RenderPolicy.UNION, List.of(tracked), SculkSightConfig.DEFAULT_RADIUS_AUDIT_CAP);
	}

	private static AuditedSensor sensorAt(int x, int y, int z) {
		return new AuditedSensor(new SensorKey(x, y, z), 8, DetectorType.NORMAL_SENSOR);
	}

	@Test
	void pinningAnEmptySelectionChangesNothing() {
		SculkSightConfig config = configWith();

		AuditPin.Result result = AuditPin.pin(config, List.of());

		assertSame(config, result.config());
		assertEquals(0, result.added());
		assertEquals("sculksight.command.pin.nothing", contentsOf(AuditPin.describe(result)).getKey());
	}

	@Test
	void everySelectedPositionBecomesATrackedSensor() {
		AuditPin.Result result = AuditPin.pin(configWith(),
				List.of(sensorAt(1, 2, 3), sensorAt(4, 5, 6)));

		assertEquals(2, result.added());
		assertEquals(2, result.config().trackedSensors().size());
		assertTrue(result.config().trackedSensors().stream()
				.anyMatch(s -> s.x() == 1 && s.y() == 2 && s.z() == 3));
		assertTrue(result.config().trackedSensors().stream()
				.anyMatch(s -> s.x() == 4 && s.y() == 5 && s.z() == 6));
	}

	@Test
	void aPinnedSensorGetsTheSameDefaultNameAndEnabledStateAsAKeypressWould() {
		AuditPin.Result result = AuditPin.pin(configWith(), List.of(sensorAt(24, -60, 40)));

		TrackedSensor pinned = result.config().trackedSensors().get(0);
		assertEquals(TrackedSensor.selected(24, -60, 40), pinned);
		assertEquals("Sensor 24, -60, 40", pinned.name());
		assertTrue(pinned.enabled());
	}

	@Test
	void anAlreadyTrackedPositionKeepsItsNameAndDisabledState() {
		TrackedSensor renamedAndOff = new TrackedSensor(1, 2, 3, "Door trap", false, false);

		AuditPin.Result result = AuditPin.pin(configWith(renamedAndOff), List.of(sensorAt(1, 2, 3)));

		assertEquals(0, result.added());
		assertEquals(1, result.alreadyTracked());
		assertEquals(List.of(renamedAndOff), result.config().trackedSensors());
		assertEquals("Door trap", result.config().trackedSensors().get(0).name());
		assertFalse(result.config().trackedSensors().get(0).enabled());
	}

	@Test
	void aMixOfNewAndAlreadyTrackedPositionsIsCountedSeparately() {
		AuditPin.Result result = AuditPin.pin(configWith(TrackedSensor.selected(1, 2, 3)),
				List.of(sensorAt(1, 2, 3), sensorAt(4, 5, 6)));

		assertEquals(1, result.added());
		assertEquals(1, result.alreadyTracked());
		assertEquals(0, result.rejectedAtCap());
	}

	// ------------------------------------------------------- the cap

	@Test
	void pinningStopsAtTheTrackedCapAndCountsWhatDidNotFit() {
		List<AuditedSensor> selection = new ArrayList<>();
		for (int index = 0; index < SculkSightConfig.MAX_TRACKED_SENSORS + 3; index++) {
			selection.add(sensorAt(index, 0, 0));
		}

		AuditPin.Result result = AuditPin.pin(configWith(), selection);

		assertEquals(SculkSightConfig.MAX_TRACKED_SENSORS, result.added());
		assertEquals(3, result.rejectedAtCap());
		assertEquals(SculkSightConfig.MAX_TRACKED_SENSORS, result.config().trackedSensors().size());
	}

	@Test
	void theCapKeepsTheEarliestEntriesInTheGivenOrder() {
		List<AuditedSensor> selection = new ArrayList<>();
		for (int index = 0; index < SculkSightConfig.MAX_TRACKED_SENSORS + 1; index++) {
			selection.add(sensorAt(index, 0, 0));
		}

		AuditPin.Result result = AuditPin.pin(configWith(), selection);

		assertTrue(result.config().trackedSensors().stream().noneMatch(
				s -> s.x() == SculkSightConfig.MAX_TRACKED_SENSORS),
				"the last, furthest sensor is the one dropped");
		assertTrue(result.config().trackedSensors().stream().anyMatch(s -> s.x() == 0),
				"the nearest sensor is kept");
	}

	@Test
	void theDefaultAuditCapFitsInsideTheTrackedCap() {
		assertTrue(SculkSightConfig.DEFAULT_RADIUS_AUDIT_CAP <= SculkSightConfig.MAX_TRACKED_SENSORS,
				"a full default-capped find must fit the tracked list without truncation");
	}

	// ------------------------------------------------------- the reported wording

	@Test
	void theCommonCaseReadsAsOneClause() {
		AuditPin.Result result = AuditPin.pin(configWith(),
				List.of(sensorAt(1, 0, 0), sensorAt(2, 0, 0)));

		Component described = AuditPin.describe(result);
		TranslatableContents contents = contentsOf(described);
		assertEquals("sculksight.command.pin.added.many", contents.getKey());
		assertEquals(2, contents.getArgs()[0]);
		assertEquals(List.of(), translatableSiblingKeys(described));
	}

	@Test
	void oneSensorIsReportedInTheSingular() {
		AuditPin.Result result = AuditPin.pin(configWith(), List.of(sensorAt(1, 0, 0)));

		Component described = AuditPin.describe(result);
		assertEquals("sculksight.command.pin.added.one", contentsOf(described).getKey());
		assertEquals(List.of(), translatableSiblingKeys(described));
	}

	@Test
	void alreadyTrackedPositionsAreMentionedOnlyWhenThereAreSome() {
		AuditPin.Result result = AuditPin.pin(configWith(TrackedSensor.selected(1, 0, 0)),
				List.of(sensorAt(1, 0, 0), sensorAt(2, 0, 0)));

		Component described = AuditPin.describe(result);
		assertEquals("sculksight.command.pin.added.one", contentsOf(described).getKey());
		assertEquals(List.of("sculksight.command.pin.already_tracked"),
				translatableSiblingKeys(described));
		TranslatableContents alreadyTracked =
				(TranslatableContents) described.getSiblings().get(0).getContents();
		assertEquals(1, alreadyTracked.getArgs()[0]);
	}

	@Test
	void aTruncatedPinSaysWhatTheLimitWas() {
		List<AuditedSensor> selection = new ArrayList<>();
		for (int index = 0; index < SculkSightConfig.MAX_TRACKED_SENSORS + 1; index++) {
			selection.add(sensorAt(index, 0, 0));
		}

		Component described = AuditPin.describe(AuditPin.pin(configWith(), selection));

		assertEquals(List.of("sculksight.command.pin.rejected_cap"),
				translatableSiblingKeys(described));
		TranslatableContents rejected =
				(TranslatableContents) described.getSiblings().get(0).getContents();
		assertEquals(1, rejected.getArgs()[0]);
		assertEquals(SculkSightConfig.MAX_TRACKED_SENSORS, rejected.getArgs()[1]);
	}

	private static TranslatableContents contentsOf(Component component) {
		return (TranslatableContents) component.getContents();
	}

	private static List<String> translatableSiblingKeys(Component root) {
		List<String> keys = new ArrayList<>();
		for (Component sibling : root.getSiblings()) {
			if (sibling.getContents() instanceof TranslatableContents contents) {
				keys.add(contents.getKey());
			}
		}
		return keys;
	}
}
