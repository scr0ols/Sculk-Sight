package com.scr0ols.sculksight.audit;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.scr0ols.sculksight.audit.RadiusAudit.AuditedSensor;
import com.scr0ols.sculksight.client.SensorKey;

/**
 * Mode B's "is an audit currently active, for what, what did it pick, and which of those has the
 * player hidden" state. ARCHITECTURE.md section 12.3: "the audit's result is a set of entries like
 * any other" once selection is done - this class is the seam that lets the renderer ask whether a
 * selection should be running at all, without naming a Minecraft type, the same split
 * {@link RadiusAuditRequest} keeps.
 *
 * <p><b>Names no Minecraft type</b>, so it lives beside {@link RadiusAuditRequest} in
 * {@code common/src/main} rather than the client source set - {@link RadiusAuditCommandCore},
 * also Minecraft-free, is what calls {@link #activate} on a successful command, and the
 * client-side renderer reads {@link #activeRequest} each tick. {@link SensorKey} is the one type
 * that crosses in, and it crosses the same way it already does into {@link RadiusAudit} beside
 * this file: as three {@code int}s in a record, not as a {@code BlockPos}.
 *
 * <p><b>Session-only, unlike {@code ClientConfig.trackedSensors()}.</b> A radius audit is a query
 * the player ran, not a persisted preference: nothing here is written to disk, and a level change
 * clears it the same way the renderer's own cache is cleared (ARCHITECTURE.md section 5, rules 5
 * and 6). That applies to {@link #hidden} as much as to the request itself - see its own javadoc.
 *
 * <p><b>The published selection is how the settings screen sees the audit at all.</b> The
 * selection itself is re-derived every tick by the renderer, against the player's current
 * position, so it is not something a screen can compute for itself without duplicating that work
 * and disagreeing with the renderer the moment the player moves. The renderer therefore publishes
 * what it selected through {@link #publishSelection}, and
 * {@code com.scr0ols.sculksight.config.SensorListWidget} lists that - one writer, one reader, and
 * no second selection pass.
 */
public final class RadiusAuditController {

	private static @Nullable RadiusAuditRequest activeRequest;

	/**
	 * Positions the player has switched off in the settings screen's audit section.
	 *
	 * <p><b>Separate from a tracked sensor's own {@code enabled} flag, and deliberately so.</b> A
	 * live find's position is not in {@code ClientConfig.trackedSensors()} at all - a live find is a
	 * query rather than a curated list - so there is no persisted record to hang an {@code enabled}
	 * flag off, and writing one would make looking at an area rewrite the player's config file as a
	 * side effect. This set is that flag's session-only equivalent. A player who wants the
	 * persisted form asks for it, through {@code /sculksight find <type> <n> static} or the
	 * settings screen's Pin button; {@link com.scr0ols.sculksight.audit.AuditPin} is the seam.
	 *
	 * <p><b>Cleared by {@link #activate}, not carried across queries.</b> A fresh
	 * {@code /sculksight find} is a fresh question, and answering it with some of its own results
	 * already suppressed - by a hide the player applied to a different query, possibly at a
	 * different radius or detector type - would read as the command silently under-reporting.
	 */
	private static final Set<SensorKey> hidden = new LinkedHashSet<>();

	/** The most recent selection the renderer derived, for the settings screen to list. */
	private static List<AuditedSensor> selection = List.of();

	private RadiusAuditController() {
	}

	/**
	 * Called on every successful {@code /sculksight find ... live} invocation; replaces any prior query,
	 * and drops any per-sensor hide that belonged to it - see {@link #hidden}.
	 */
	public static void activate(RadiusAuditRequest request) {
		activeRequest = request;
		hidden.clear();
		selection = List.of();
	}

	/** A level change drops the active audit, the same as the renderer drops its own cache. */
	public static void clear() {
		activeRequest = null;
		hidden.clear();
		selection = List.of();
	}

	/** The most recently accepted request, or {@code null} if no audit is active. */
	public static @Nullable RadiusAuditRequest activeRequest() {
		return activeRequest;
	}

	/**
	 * The renderer's own current selection, published once per tick from {@code ShellRenderer}'s
	 * merge step. Includes positions {@link #isHidden} reports as hidden: the settings screen has
	 * to list one to offer a control that turns it back on.
	 */
	public static void publishSelection(List<AuditedSensor> selected) {
		selection = List.copyOf(selected);
	}

	/**
	 * What the active audit currently selects, nearest first, or empty when no audit is active or
	 * the renderer has not run its merge step yet this session.
	 */
	public static List<AuditedSensor> selection() {
		return selection;
	}

	/** Whether the player has switched this audited position off for the current query. */
	public static boolean isHidden(SensorKey position) {
		return hidden.contains(position);
	}

	/** Switches one audited position off ({@code true}) or back on ({@code false}). */
	public static void setHidden(SensorKey position, boolean hide) {
		if (hide) {
			hidden.add(position);
		} else {
			hidden.remove(position);
		}
	}
}
