package com.scr0ols.sculksight.audit;

import org.jspecify.annotations.Nullable;

/**
 * Mode B's "is an audit currently active, and for what" state. ARCHITECTURE.md section 12.3:
 * "the audit's result is a set of entries like any other" once selection is done - this class is
 * the seam that lets the renderer ask whether a selection should be running at all, without
 * naming a Minecraft type, the same split {@link RadiusAuditRequest} keeps.
 *
 * <p><b>Names no Minecraft type</b>, so it lives beside {@link RadiusAuditRequest} in
 * {@code common/src/main} rather than the client source set - {@link RadiusAuditCommandCore},
 * also Minecraft-free, is what calls {@link #activate} on a successful command, and the
 * client-side renderer reads {@link #activeRequest} each tick.
 *
 * <p><b>Session-only, unlike {@code ClientConfig.trackedSensors()}.</b> A radius audit is a query
 * the player ran, not a persisted preference: nothing here is written to disk, and a level change
 * clears it the same way the renderer's own cache is cleared (ARCHITECTURE.md section 5, rules 5
 * and 6).
 */
public final class RadiusAuditController {

	private static @Nullable RadiusAuditRequest activeRequest;

	private RadiusAuditController() {
	}

	/** Called on every successful {@code /sculksight radius} invocation; replaces any prior query. */
	public static void activate(RadiusAuditRequest request) {
		activeRequest = request;
	}

	/** A level change drops the active audit, the same as the renderer drops its own cache. */
	public static void clear() {
		activeRequest = null;
	}

	/** The most recently accepted request, or {@code null} if no audit is active. */
	public static @Nullable RadiusAuditRequest activeRequest() {
		return activeRequest;
	}
}
