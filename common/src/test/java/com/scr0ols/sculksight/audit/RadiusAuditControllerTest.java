package com.scr0ols.sculksight.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.scr0ols.sculksight.audit.RadiusAudit.AuditedSensor;
import com.scr0ols.sculksight.client.DetectorType;
import com.scr0ols.sculksight.client.SensorKey;

class RadiusAuditControllerTest {

	@AfterEach
	void clearSharedState() {
		RadiusAuditController.clear();
	}

	@Test
	void startsWithNoActiveRequest() {
		assertNull(RadiusAuditController.activeRequest());
	}

	@Test
	void activateRecordsTheRequest() throws RadiusAuditArgumentException {
		RadiusAuditRequest request = RadiusAuditRequest.of(32, "all");

		RadiusAuditController.activate(request);

		assertEquals(request, RadiusAuditController.activeRequest());
	}

	@Test
	void aLaterActivateReplacesTheEarlierRequest() throws RadiusAuditArgumentException {
		RadiusAuditController.activate(RadiusAuditRequest.of(8, "shrieker"));
		RadiusAuditRequest latest = RadiusAuditRequest.of(64, "all");

		RadiusAuditController.activate(latest);

		assertEquals(latest, RadiusAuditController.activeRequest());
	}

	@Test
	void clearDropsTheActiveRequest() throws RadiusAuditArgumentException {
		RadiusAuditController.activate(RadiusAuditRequest.of(16, "all"));

		RadiusAuditController.clear();

		assertNull(RadiusAuditController.activeRequest());
	}

	@Test
	void aSuccessfulLiveCommandRunActivatesTheController() {
		RadiusAuditCommandCore.runLive(message -> { }, 32, "all", 0, 0, 0, List.of(), 100);

		assertNotNull(RadiusAuditController.activeRequest());
		assertEquals(32, RadiusAuditController.activeRequest().radius());
	}

	// ------------------------------------------------------- the session-only per-sensor hide

	private static final SensorKey A = new SensorKey(24, -60, -8);
	private static final SensorKey B = new SensorKey(24, -60, 8);

	@Test
	void noPositionIsHiddenToBeginWith() {
		assertFalse(RadiusAuditController.isHidden(A));
	}

	@Test
	void setHiddenHidesOnlyTheNamedPosition() {
		RadiusAuditController.setHidden(A, true);

		assertTrue(RadiusAuditController.isHidden(A));
		assertFalse(RadiusAuditController.isHidden(B));
	}

	@Test
	void setHiddenFalseBringsAPositionBack() {
		RadiusAuditController.setHidden(A, true);

		RadiusAuditController.setHidden(A, false);

		assertFalse(RadiusAuditController.isHidden(A));
	}

	@Test
	void hidingTwiceIsNotUndoneByShowingOnce() {
		RadiusAuditController.setHidden(A, true);
		RadiusAuditController.setHidden(A, true);

		RadiusAuditController.setHidden(A, false);

		assertFalse(RadiusAuditController.isHidden(A));
	}

	@Test
	void showingAPositionThatWasNeverHiddenIsHarmless() {
		RadiusAuditController.setHidden(A, false);

		assertFalse(RadiusAuditController.isHidden(A));
	}

	@Test
	void activateDropsEveryHideFromThePriorQuery() throws RadiusAuditArgumentException {
		RadiusAuditController.activate(RadiusAuditRequest.of(64, "all"));
		RadiusAuditController.setHidden(A, true);

		RadiusAuditController.activate(RadiusAuditRequest.of(64, "all"));

		assertFalse(RadiusAuditController.isHidden(A));
	}

	@Test
	void clearDropsEveryHide() {
		RadiusAuditController.setHidden(A, true);

		RadiusAuditController.clear();

		assertFalse(RadiusAuditController.isHidden(A));
	}

	// ------------------------------------------------------- the published selection

	@Test
	void theSelectionStartsEmpty() {
		assertEquals(List.of(), RadiusAuditController.selection());
	}

	@Test
	void publishSelectionIsWhatTheSettingsScreenReadsBack() {
		AuditedSensor sensor = new AuditedSensor(A, 8, DetectorType.NORMAL_SENSOR);

		RadiusAuditController.publishSelection(List.of(sensor));

		assertEquals(List.of(sensor), RadiusAuditController.selection());
	}

	@Test
	void aHiddenPositionStaysInThePublishedSelection() {
		AuditedSensor sensor = new AuditedSensor(A, 8, DetectorType.NORMAL_SENSOR);
		RadiusAuditController.publishSelection(List.of(sensor));

		RadiusAuditController.setHidden(A, true);

		assertEquals(List.of(sensor), RadiusAuditController.selection());
		assertTrue(RadiusAuditController.isHidden(A));
	}

	@Test
	void thePublishedSelectionIsACopyTheCallerCannotMutateLater() {
		List<AuditedSensor> mutable = new ArrayList<>();
		mutable.add(new AuditedSensor(A, 8, DetectorType.NORMAL_SENSOR));
		RadiusAuditController.publishSelection(mutable);

		mutable.clear();

		assertEquals(1, RadiusAuditController.selection().size());
		assertThrows(UnsupportedOperationException.class,
				() -> RadiusAuditController.selection().clear());
	}

	@Test
	void activateAndClearBothDropThePublishedSelection() throws RadiusAuditArgumentException {
		RadiusAuditController.publishSelection(
				List.of(new AuditedSensor(A, 8, DetectorType.NORMAL_SENSOR)));

		RadiusAuditController.activate(RadiusAuditRequest.of(16, "all"));

		assertEquals(List.of(), RadiusAuditController.selection());

		RadiusAuditController.publishSelection(
				List.of(new AuditedSensor(B, 8, DetectorType.SHRIEKER)));

		RadiusAuditController.clear();

		assertEquals(List.of(), RadiusAuditController.selection());
	}
}
