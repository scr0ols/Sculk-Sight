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

/**
 * What the command body does with the arguments, and what it tells the player.
 *
 * <p>The core names no Minecraft type, so the whole of it is exercised here with an
 * {@code ArrayList} standing in for a chat window and plain {@link AuditedSensor}s standing in
 * for a {@code SensorIndex} snapshot. {@link RadiusAuditCommandCore#runStatic}'s pinner is a
 * lambda here for the same reason: writing the pinned sensors to disk is the caller's job, and
 * {@code AuditPinTest} covers the pure operation the real caller routes through.
 *
 * <p>What this cannot show is that the message reaches chat on either loader, or that the candidate
 * set the client actually builds matches this shape: that is the two registration classes and
 * {@code RadiusAuditClient}, and it is a live run.
 */
class RadiusAuditCommandCoreTest {

	private final List<String> reported = new ArrayList<>();

	/** What a pinner was handed, so a static run can be checked without a config anywhere near it. */
	private final List<AuditedSensor> pinned = new ArrayList<>();

	/** Generous enough that none of the tests not concerned with the cap ever reach it. */
	private static final int GENEROUS_CAP = 100;

	/** A successful live run activates RadiusAuditController; it is global state, so tests do not leak into each other. */
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

	/**
	 * Both loader classes bound the radius with Brigadier's own {@code IntegerArgumentType}, so
	 * this path is not normally reachable from chat. It is still the core's answer if it ever is,
	 * and a failing request must not claim to have been accepted.
	 */
	@Test
	void anOutOfRangeRadiusFailsWithoutClaimingToHaveBeenAccepted() {
		int result = RadiusAuditCommandCore.runLive(reported::add, RadiusAuditRequest.MAX_RADIUS + 1,
				"all", 0, 0, 0, List.of(), GENEROUS_CAP);

		assertEquals(RadiusAuditCommandCore.FAILURE, result);
		assertTrue(reported.stream().noneMatch(line -> line.contains("accepted")),
				reported.toString());
	}

	/** Section 12.4: below the cap, nothing about it is ever mentioned. */
	@Test
	void aSelectionUnderTheCapReportsNoWarning() {
		AuditedSensor sensor = new AuditedSensor(new SensorKey(1, 0, 0), 8, DetectorType.NORMAL_SENSOR);

		RadiusAuditCommandCore.runLive(reported::add, 64, "all", 0, 0, 0, List.of(sensor), 1);

		assertTrue(reported.stream().noneMatch(line -> line.contains("Cap reached")),
				reported.toString());
	}

	/**
	 * Section 12.4's commitment: exceeding the cap truncates the report to the cap and adds a
	 * visible warning, rather than either silently dropping sensors or reporting every match.
	 */
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

	/** The sensors are the result, so there is nothing left to re-select every tick. */
	@Test
	void aStaticRunLeavesNoActiveAudit() {
		RadiusAuditCommandCore.runStatic(reported::add, 64, "all", 0, 0, 0, List.of(), GENEROUS_CAP,
				this::recordPin);

		assertNull(RadiusAuditController.activeRequest());
	}

	/** Two routes to the same shells, with only one of them controllable, is the case this prevents. */
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

	/** The cap truncates before the pinner is reached, not after it has written everything. */
	@Test
	void aStaticRunPinsOnlyWhatTheCapAllowed() {
		AuditedSensor near = new AuditedSensor(new SensorKey(1, 0, 0), 8, DetectorType.NORMAL_SENSOR);
		AuditedSensor far = new AuditedSensor(new SensorKey(2, 0, 0), 8, DetectorType.NORMAL_SENSOR);

		RadiusAuditCommandCore.runStatic(reported::add, 64, "all", 0, 0, 0, List.of(far, near), 1,
				this::recordPin);

		assertEquals(List.of(near), pinned);
	}
}
