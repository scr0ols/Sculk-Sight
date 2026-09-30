package com.scr0ols.sculksight.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import com.scr0ols.sculksight.audit.RadiusAudit.AuditedSensor;
import com.scr0ols.sculksight.client.DetectorType;
import com.scr0ols.sculksight.client.SensorKey;

class RadiusAuditCommandCoreTest {

	private final List<Component> reported = new ArrayList<>();

	private final List<AuditedSensor> pinned = new ArrayList<>();

	private static final int GENEROUS_CAP = 100;

	@AfterEach
	void clearActiveAudit() {
		RadiusAuditController.clear();
	}

	private Component recordPin(List<AuditedSensor> selected) {
		pinned.addAll(selected);
		return Component.literal(selected.size() + " pinned.");
	}

	// ------------------------------------------------------- key/argument lookup helpers

	private static TranslatableContents contentsOf(Component component) {
		return (TranslatableContents) component.getContents();
	}

	private static String keyOf(Component component) {
		return contentsOf(component).getKey();
	}

	private Optional<Component> firstWithKey(String key) {
		return reported.stream().filter(component -> key.equals(keyOf(component))).findFirst();
	}

	private boolean hasKey(String key) {
		return firstWithKey(key).isPresent();
	}

	private Component requireWithKey(String key) {
		return firstWithKey(key)
				.orElseThrow(() -> new AssertionError("no reported message with key " + key
						+ ": " + reported));
	}

	// ------------------------------------------------------- selection and validation, shared

	@Test
	void acceptedArgumentsSucceedAndSayWhatWasUnderstood() {
		int result = RadiusAuditCommandCore.runLive(reported::add, 64, "calibrated", 0, 0, 0,
				List.of(), GENEROUS_CAP);

		assertEquals(RadiusAuditCommandCore.SUCCESS, result);
		Component live = requireWithKey("sculksight.command.find.live");
		Component description = (Component) contentsOf(live).getArgs()[0];
		assertEquals(64, contentsOf(description).getArgs()[0]);
		Component detector = (Component) contentsOf(description).getArgs()[1];
		assertEquals("sculksight.command.find.description.detector", keyOf(detector));
		assertEquals("calibrated", contentsOf(detector).getArgs()[0]);
	}

	@Test
	void anEmptyCandidateSetReportsNoSensorsFound() {
		RadiusAuditCommandCore.runLive(reported::add, 64, "all", 0, 0, 0, List.of(), GENEROUS_CAP);

		assertTrue(hasKey("sculksight.command.find.selection.none"), reported.toString());
	}

	@Test
	void aQualifyingCandidateIsCountedInTheReport() {
		AuditedSensor sensor = new AuditedSensor(new SensorKey(10, 0, 0), 8, DetectorType.NORMAL_SENSOR);

		int result = RadiusAuditCommandCore.runLive(reported::add, 64, "all", 0, 0, 0,
				List.of(sensor), GENEROUS_CAP);

		assertEquals(RadiusAuditCommandCore.SUCCESS, result);
		assertTrue(hasKey("sculksight.command.find.selection.one"), reported.toString());
	}

	@Test
	void anUnknownDetectorFailsAndReportsTheProblemRatherThanThrowing() {
		int result = RadiusAuditCommandCore.runLive(reported::add, 64, "warden", 0, 0, 0, List.of(),
				GENEROUS_CAP);

		assertEquals(RadiusAuditCommandCore.FAILURE, result);
		assertEquals(1, reported.size(), reported.toString());
		assertEquals("sculksight.command.find.detector.unknown", keyOf(reported.get(0)));
		assertEquals("warden", contentsOf(reported.get(0)).getArgs()[0]);
	}

	@Test
	void anOutOfRangeRadiusFailsWithoutClaimingToHaveBeenAccepted() {
		int result = RadiusAuditCommandCore.runLive(reported::add, RadiusAuditRequest.MAX_RADIUS + 1,
				"all", 0, 0, 0, List.of(), GENEROUS_CAP);

		assertEquals(RadiusAuditCommandCore.FAILURE, result);
		assertTrue(hasKey("sculksight.command.find.radius.out_of_range"), reported.toString());
		assertFalse(hasKey("sculksight.command.find.selection.none"), reported.toString());
		assertFalse(hasKey("sculksight.command.find.selection.one"), reported.toString());
		assertFalse(hasKey("sculksight.command.find.selection.many"), reported.toString());
	}

	@Test
	void aSelectionUnderTheCapReportsNoWarning() {
		AuditedSensor sensor = new AuditedSensor(new SensorKey(1, 0, 0), 8, DetectorType.NORMAL_SENSOR);

		RadiusAuditCommandCore.runLive(reported::add, 64, "all", 0, 0, 0, List.of(sensor), 1);

		assertFalse(hasKey("sculksight.command.find.cap_reached"), reported.toString());
	}

	@Test
	void aSelectionOverTheCapIsTruncatedAndWarnsVisibly() {
		AuditedSensor near = new AuditedSensor(new SensorKey(1, 0, 0), 8, DetectorType.NORMAL_SENSOR);
		AuditedSensor far = new AuditedSensor(new SensorKey(2, 0, 0), 8, DetectorType.NORMAL_SENSOR);

		int result = RadiusAuditCommandCore.runLive(reported::add, 64, "all", 0, 0, 0,
				List.of(far, near), 1);

		assertEquals(RadiusAuditCommandCore.SUCCESS, result);
		assertTrue(hasKey("sculksight.command.find.selection.one"), reported.toString());
		Component capReached = requireWithKey("sculksight.command.find.cap_reached");
		assertEquals(2, contentsOf(capReached).getArgs()[0]);
		assertEquals(1, contentsOf(capReached).getArgs()[1]);
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

		assertTrue(hasKey("sculksight.command.find.live"), reported.toString());
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

		assertTrue(reported.stream().anyMatch(component -> component.equals(Component.literal("1 pinned."))),
				reported.toString());
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

	// ------------------------------------------------------- off mode

	@Test
	void anOffRunCancelsAnActiveLiveFind() throws RadiusAuditArgumentException {
		RadiusAuditController.activate(RadiusAuditRequest.of(64, "all"));

		int result = RadiusAuditCommandCore.runOff(reported::add);

		assertEquals(RadiusAuditCommandCore.SUCCESS, result);
		assertNull(RadiusAuditController.activeRequest());
	}

	@Test
	void anOffRunSucceedsWhenNoLiveFindWasRunning() {
		int result = RadiusAuditCommandCore.runOff(reported::add);

		assertEquals(RadiusAuditCommandCore.SUCCESS, result);
		assertNull(RadiusAuditController.activeRequest());
	}

	@Test
	void anOffRunPinsNothing() throws RadiusAuditArgumentException {
		RadiusAuditController.activate(RadiusAuditRequest.of(64, "all"));

		RadiusAuditCommandCore.runOff(reported::add);

		assertEquals(List.of(), pinned);
	}

	@Test
	void anOffRunReportsThatALiveFindWasCancelled() throws RadiusAuditArgumentException {
		RadiusAuditController.activate(RadiusAuditRequest.of(64, "all"));

		RadiusAuditCommandCore.runOff(reported::add);

		assertTrue(hasKey("sculksight.command.find.off.live_cancelled"), reported.toString());
	}

	@Test
	void anOffRunReportsWhenNoLiveFindWasRunning() {
		RadiusAuditCommandCore.runOff(reported::add);

		assertTrue(hasKey("sculksight.command.find.off.none_running"), reported.toString());
	}

	@Test
	void anOffRunAfterAStaticFindReportsStaticFindCancelled() {
		RadiusAuditCommandCore.runStatic(reported::add, 64, "all", 0, 0, 0, List.of(), GENEROUS_CAP,
				this::recordPin);
		reported.clear();

		RadiusAuditCommandCore.runOff(reported::add);

		assertTrue(hasKey("sculksight.command.find.off.static_cancelled"), reported.toString());
	}

	@Test
	void anOffRunAfterOffAlreadyRanFallsBackToNoLiveFind() {
		RadiusAuditCommandCore.runStatic(reported::add, 64, "all", 0, 0, 0, List.of(), GENEROUS_CAP,
				this::recordPin);
		reported.clear();

		RadiusAuditCommandCore.runOff(reported::add);
		assertTrue(hasKey("sculksight.command.find.off.static_cancelled"), reported.toString());
		reported.clear();

		RadiusAuditCommandCore.runOff(reported::add);

		assertTrue(hasKey("sculksight.command.find.off.none_running"), reported.toString());
	}
}
