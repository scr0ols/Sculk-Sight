package com.scr0ols.sculksight.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.scr0ols.sculksight.audit.RadiusAudit.AuditedSensor;
import com.scr0ols.sculksight.client.DetectorType;
import com.scr0ols.sculksight.client.SensorKey;

class RadiusAuditCommandCoreTest {

	private final List<String> reported = new ArrayList<>();

	private final List<AuditedSensor> pinned = new ArrayList<>();

	private static final int GENEROUS_CAP = 100;

	@AfterEach
	void clearActiveAudit() {
		RadiusAuditController.clear();
	}

	private String recordPin(List<AuditedSensor> selected) {
		pinned.addAll(selected);
		return selected.size() + " pinned.";
	}

	// ------------------------------------------------------- selection and validation, shared

	@Test
	void acceptedArgumentsSucceedAndSayWhatWasUnderstood() {
		int result = RadiusAuditCommandCore.runLive(reported::add, 64, "calibrated", 0, 0, 0,
				List.of(), GENEROUS_CAP);

		assertEquals(RadiusAuditCommandCore.SUCCESS, result);
		assertTrue(reported.stream().anyMatch(line -> line.contains("radius 64")),
				reported.toString());
		assertTrue(reported.stream().anyMatch(line -> line.contains("calibrated")),
				reported.toString());
	}

	@Test
	void anEmptyCandidateSetReportsNoSensorsFound() {
		RadiusAuditCommandCore.runLive(reported::add, 64, "all", 0, 0, 0, List.of(), GENEROUS_CAP);

		assertTrue(reported.stream().anyMatch(line -> line.contains("No sensors found in range.")),
				reported.toString());
	}

	@Test
	void aQualifyingCandidateIsCountedInTheReport() {
		AuditedSensor sensor = new AuditedSensor(new SensorKey(10, 0, 0), 8, DetectorType.NORMAL_SENSOR);

		int result = RadiusAuditCommandCore.runLive(reported::add, 64, "all", 0, 0, 0,
				List.of(sensor), GENEROUS_CAP);

		assertEquals(RadiusAuditCommandCore.SUCCESS, result);
		assertTrue(reported.stream().anyMatch(line -> line.contains("1 sensor found in range.")),
				reported.toString());
	}

	@Test
	void anUnknownDetectorFailsAndReportsTheProblemRatherThanThrowing() {
		int result = RadiusAuditCommandCore.runLive(reported::add, 64, "warden", 0, 0, 0, List.of(),
				GENEROUS_CAP);

		assertEquals(RadiusAuditCommandCore.FAILURE, result);
		assertEquals(1, reported.size(), reported.toString());
		assertTrue(reported.get(0).contains("warden"), reported.toString());
	}

	@Test
	void anOutOfRangeRadiusFailsWithoutClaimingToHaveBeenAccepted() {
		int result = RadiusAuditCommandCore.runLive(reported::add, RadiusAuditRequest.MAX_RADIUS + 1,
				"all", 0, 0, 0, List.of(), GENEROUS_CAP);

		assertEquals(RadiusAuditCommandCore.FAILURE, result);
		assertTrue(reported.stream().noneMatch(line -> line.contains("accepted")),
				reported.toString());
	}

	@Test
	void aSelectionUnderTheCapReportsNoWarning() {
		AuditedSensor sensor = new AuditedSensor(new SensorKey(1, 0, 0), 8, DetectorType.NORMAL_SENSOR);

		RadiusAuditCommandCore.runLive(reported::add, 64, "all", 0, 0, 0, List.of(sensor), 1);

		assertTrue(reported.stream().noneMatch(line -> line.contains("Cap reached")),
				reported.toString());
	}

	@Test
	void aSelectionOverTheCapIsTruncatedAndWarnsVisibly() {
		AuditedSensor near = new AuditedSensor(new SensorKey(1, 0, 0), 8, DetectorType.NORMAL_SENSOR);
		AuditedSensor far = new AuditedSensor(new SensorKey(2, 0, 0), 8, DetectorType.NORMAL_SENSOR);

		int result = RadiusAuditCommandCore.runLive(reported::add, 64, "all", 0, 0, 0,
				List.of(far, near), 1);

		assertEquals(RadiusAuditCommandCore.SUCCESS, result);
		assertTrue(reported.stream().anyMatch(line -> line.contains("1 sensor found in range.")),
				reported.toString());
		assertTrue(reported.stream().anyMatch(line -> line.contains("Cap reached")
				&& line.contains("2") && line.contains("1")), reported.toString());
	}

	// ------------------------------------------------------- live mode

	@Test
	void aSuccessfulLiveRunActivatesTheController() {
		RadiusAuditCommandCore.runLive(reported::add, 32, "all", 0, 0, 0, List.of(), GENEROUS_CAP);

		assertNotNull(RadiusAuditController.activeRequest());
		assertEquals(32, RadiusAuditController.activeRequest().radius());
	}

	@Test
	void aFailedLiveRunActivatesNothing() {
		RadiusAuditCommandCore.runLive(reported::add, 32, "warden", 0, 0, 0, List.of(), GENEROUS_CAP);

		assertNull(RadiusAuditController.activeRequest());
	}

	@Test
	void aLiveRunSaysTheShellsWillFollowThePlayer() {
		RadiusAuditCommandCore.runLive(reported::add, 32, "all", 0, 0, 0, List.of(), GENEROUS_CAP);

		assertTrue(reported.stream().anyMatch(line -> line.contains("follow you")),
				reported.toString());
	}

	@Test
	void aLiveRunPinsNothing() {
		AuditedSensor sensor = new AuditedSensor(new SensorKey(1, 0, 0), 8, DetectorType.NORMAL_SENSOR);

		RadiusAuditCommandCore.runLive(reported::add, 64, "all", 0, 0, 0, List.of(sensor), GENEROUS_CAP);

		assertEquals(List.of(), pinned);
	}

	// ------------------------------------------------------- static mode

	@Test
	void aStaticRunHandsTheWholeSelectionToThePinner() {
		AuditedSensor near = new AuditedSensor(new SensorKey(1, 0, 0), 8, DetectorType.NORMAL_SENSOR);
		AuditedSensor far = new AuditedSensor(new SensorKey(9, 0, 0), 8, DetectorType.SHRIEKER);

		int result = RadiusAuditCommandCore.runStatic(reported::add, 64, "all", 0, 0, 0,
				List.of(far, near), GENEROUS_CAP, this::recordPin);

		assertEquals(RadiusAuditCommandCore.SUCCESS, result);
		assertEquals(List.of(near, far), pinned, "nearest first, the order AuditPin relies on");
	}

	@Test
	void aStaticRunReportsWhatThePinnerSaid() {
		AuditedSensor sensor = new AuditedSensor(new SensorKey(1, 0, 0), 8, DetectorType.NORMAL_SENSOR);

		RadiusAuditCommandCore.runStatic(reported::add, 64, "all", 0, 0, 0, List.of(sensor),
				GENEROUS_CAP, this::recordPin);

		assertTrue(reported.stream().anyMatch(line -> line.equals("1 pinned.")), reported.toString());
	}

	@Test
	void aStaticRunLeavesNoActiveAudit() {
		RadiusAuditCommandCore.runStatic(reported::add, 64, "all", 0, 0, 0, List.of(), GENEROUS_CAP,
				this::recordPin);

		assertNull(RadiusAuditController.activeRequest());
	}

	@Test
	void aStaticRunEndsALiveFindThatWasAlreadyRunning() throws RadiusAuditArgumentException {
		RadiusAuditController.activate(RadiusAuditRequest.of(64, "all"));

		RadiusAuditCommandCore.runStatic(reported::add, 16, "all", 0, 0, 0, List.of(), GENEROUS_CAP,
				this::recordPin);

		assertNull(RadiusAuditController.activeRequest());
	}

	@Test
	void aFailedStaticRunPinsNothingAndActivatesNothing() {
		int result = RadiusAuditCommandCore.runStatic(reported::add, 64, "warden", 0, 0, 0,
				List.of(), GENEROUS_CAP, this::recordPin);

		assertEquals(RadiusAuditCommandCore.FAILURE, result);
		assertEquals(List.of(), pinned);
		assertNull(RadiusAuditController.activeRequest());
	}

	@Test
	void aStaticRunPinsOnlyWhatTheCapAllowed() {
		AuditedSensor near = new AuditedSensor(new SensorKey(1, 0, 0), 8, DetectorType.NORMAL_SENSOR);
		AuditedSensor far = new AuditedSensor(new SensorKey(2, 0, 0), 8, DetectorType.NORMAL_SENSOR);

		RadiusAuditCommandCore.runStatic(reported::add, 64, "all", 0, 0, 0, List.of(far, near), 1,
				this::recordPin);

		assertEquals(List.of(near), pinned);
	}
}
