package com.scr0ols.sculksight.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * What the command body does with the arguments, and what it tells the player.
 *
 * <p>The core names no Minecraft type, so the whole of it is exercised here with an
 * {@code ArrayList} standing in for a chat window. What this cannot show is that the message
 * reaches chat on either loader: that is the two registration classes, and it is a live run.
 */
class RadiusAuditCommandCoreTest {

	private final List<String> reported = new ArrayList<>();

	@Test
	void acceptedArgumentsSucceedAndSayWhatWasUnderstood() {
		int result = RadiusAuditCommandCore.run(reported::add, 64, "calibrated");

		assertEquals(RadiusAuditCommandCore.SUCCESS, result);
		assertTrue(reported.stream().anyMatch(line -> line.contains("radius 64")),
				reported.toString());
		assertTrue(reported.stream().anyMatch(line -> line.contains("calibrated")),
				reported.toString());
	}

	@Test
	void anAcceptedRequestStillSaysNothingIsDrawnYet() {
		RadiusAuditCommandCore.run(reported::add, 64, null);

		assertTrue(reported.stream().anyMatch(line -> line.contains("not implemented")),
				reported.toString());
	}

	@Test
	void anUnknownDetectorFailsAndReportsTheProblemRatherThanThrowing() {
		int result = RadiusAuditCommandCore.run(reported::add, 64, "warden");

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
		int result = RadiusAuditCommandCore.run(reported::add, RadiusAuditRequest.MAX_RADIUS + 1, null);

		assertEquals(RadiusAuditCommandCore.FAILURE, result);
		assertTrue(reported.stream().noneMatch(line -> line.contains("accepted")),
				reported.toString());
	}
}
