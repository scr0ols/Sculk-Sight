package com.scr0ols.sculksight.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.scr0ols.sculksight.audit.RadiusAudit.AuditedSensor;
import com.scr0ols.sculksight.client.DetectorType;
import com.scr0ols.sculksight.client.SensorKey;
import com.scr0ols.sculksight.config.RenderPolicy;
import com.scr0ols.sculksight.config.SculkSightConfig;
import com.scr0ols.sculksight.config.TrackedSensor;

/**
 * Turning a find's selection into tracked sensors - the pure operation behind both
 * {@code /sculksight find <type> <n> static} and the settings screen's Pin button.
 */
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
		assertEquals("Nothing to pin.", AuditPin.describe(result));
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

	/** A pinned sensor must be indistinguishable from one tracked by pressing K. */
	@Test
	void aPinnedSensorGetsTheSameDefaultNameAndEnabledStateAsAKeypressWould() {
		AuditPin.Result result = AuditPin.pin(configWith(), List.of(sensorAt(24, -60, 40)));

		TrackedSensor pinned = result.config().trackedSensors().get(0);
		assertEquals(TrackedSensor.selected(24, -60, 40), pinned);
		assertEquals("Sensor 24, -60, 40", pinned.name());
		assertTrue(pinned.enabled());
	}

	/**
	 * The whole reason {@code track} preserves an existing entry: pinning the same find twice must
	 * not undo a rename or re-enable something the player switched off in between.
	 */
	@Test
	void anAlreadyTrackedPositionKeepsItsNameAndDisabledState() {
		TrackedSensor renamedAndOff = new TrackedSensor(1, 2, 3, "Door trap", false);

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

	/** Nearest-first order in, nearest-first kept: the truncation drops the furthest sensors. */
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

	/**
	 * A full find fits a full tracked list by construction, which is why the cap was raised to
	 * match. If these two constants ever drift apart again, a static find starts truncating.
	 */
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

		assertEquals("2 sensors added to tracked sensors.", AuditPin.describe(result));
	}

	@Test
	void oneSensorIsReportedInTheSingular() {
		AuditPin.Result result = AuditPin.pin(configWith(), List.of(sensorAt(1, 0, 0)));

		assertEquals("1 sensor added to tracked sensors.", AuditPin.describe(result));
	}

	@Test
	void alreadyTrackedPositionsAreMentionedOnlyWhenThereAreSome() {
		AuditPin.Result result = AuditPin.pin(configWith(TrackedSensor.selected(1, 0, 0)),
				List.of(sensorAt(1, 0, 0), sensorAt(2, 0, 0)));

		assertEquals("1 sensor added to tracked sensors; 1 already tracked.",
				AuditPin.describe(result));
	}

	@Test
	void aTruncatedPinSaysWhatTheLimitWas() {
		List<AuditedSensor> selection = new ArrayList<>();
		for (int index = 0; index < SculkSightConfig.MAX_TRACKED_SENSORS + 1; index++) {
			selection.add(sensorAt(index, 0, 0));
		}

		String line = AuditPin.describe(AuditPin.pin(configWith(), selection));

		assertTrue(line.contains("1 did not fit the tracked limit of "
				+ SculkSightConfig.MAX_TRACKED_SENSORS), line);
	}
}
