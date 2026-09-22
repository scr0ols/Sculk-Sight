package com.scr0ols.sculksight.audit;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.scr0ols.sculksight.audit.RadiusAudit.AuditedSensor;
import com.scr0ols.sculksight.client.SensorKey;

/** Session-only state for whether a radius audit is active, what it selected, and what the player hid. */
public final class RadiusAuditController {

	private static @Nullable RadiusAuditRequest activeRequest;

	private static final Set<SensorKey> hidden = new LinkedHashSet<>();

	private static List<AuditedSensor> selection = List.of();

	private RadiusAuditController() {
	}

	/** Makes {@code request} the active audit, replacing any prior query and its hides. */
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

	/** Publishes the renderer's current selection, including positions reported as hidden. */
	public static void publishSelection(List<AuditedSensor> selected) {
		selection = List.copyOf(selected);
	}

	/** What the active audit currently selects, nearest first, or empty when none is active. */
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
