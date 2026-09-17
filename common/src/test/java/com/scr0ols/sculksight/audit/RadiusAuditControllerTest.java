package com.scr0ols.sculksight.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The state RadiusAuditCommandCore hands to the renderer. No Minecraft type in sight. */
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
		RadiusAuditRequest request = RadiusAuditRequest.of(32, null);

		RadiusAuditController.activate(request);

		assertEquals(request, RadiusAuditController.activeRequest());
	}

	@Test
	void aLaterActivateReplacesTheEarlierRequest() throws RadiusAuditArgumentException {
		RadiusAuditController.activate(RadiusAuditRequest.of(8, "shrieker"));
		RadiusAuditRequest latest = RadiusAuditRequest.of(64, null);

		RadiusAuditController.activate(latest);

		assertEquals(latest, RadiusAuditController.activeRequest());
	}

	@Test
	void clearDropsTheActiveRequest() throws RadiusAuditArgumentException {
		RadiusAuditController.activate(RadiusAuditRequest.of(16, null));

		RadiusAuditController.clear();

		assertNull(RadiusAuditController.activeRequest());
	}

	@Test
	void aSuccessfulCommandRunActivatesTheController() {
		RadiusAuditCommandCore.run(message -> { }, 32, null, 0, 0, 0, List.of(), 100);

		assertNotNull(RadiusAuditController.activeRequest());
		assertEquals(32, RadiusAuditController.activeRequest().radius());
	}
}
