package com.scr0ols.sculksight.audit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.scr0ols.sculksight.client.DetectorType;
import com.scr0ols.sculksight.client.SensorKey;

/** Selects which of a supplied set of sensors fall within a radius audit's query. */
public final class RadiusAudit {

	private RadiusAudit() {
	}

	/** One sensor as the audit sees it: position, listener radius and detector type. */
	public record AuditedSensor(SensorKey position, int listenerRadius, DetectorType type) {
	}

	/** Selects and orders, nearest first, the sensors in {@code candidates} that satisfy {@code request}. */
	public static List<AuditedSensor> select(int centreX, int centreY, int centreZ,
			RadiusAuditRequest request, List<AuditedSensor> candidates) {

		long radiusSqr = (long) request.radius() * request.radius();

		List<AuditedSensor> selected = new ArrayList<>();
		for (AuditedSensor candidate : candidates) {
			if (request.detector().isPresent() && request.detector().get() != candidate.type()) {
				continue;
			}
			if (distanceSquared(centreX, centreY, centreZ, candidate.position()) > radiusSqr) {
				continue;
			}
			selected.add(candidate);
		}

		selected.sort(Comparator
				.comparingLong((AuditedSensor sensor) ->
						distanceSquared(centreX, centreY, centreZ, sensor.position()))
				.thenComparingInt(sensor -> sensor.position().x())
				.thenComparingInt(sensor -> sensor.position().y())
				.thenComparingInt(sensor -> sensor.position().z()));

		return List.copyOf(selected);
	}

	/** A nearest-first selection truncated to a cap, with whether truncation happened and how many matched. */
	public record CappedSelection(List<AuditedSensor> selected, boolean capped, int matchedCount) {
	}

	/** {@link #select} with the cap enforced, keeping the nearest {@code cap} sensors. */
	public static CappedSelection selectWithCap(int centreX, int centreY, int centreZ,
			RadiusAuditRequest request, List<AuditedSensor> candidates, int cap) {

		List<AuditedSensor> matched = select(centreX, centreY, centreZ, request, candidates);

		if (matched.size() <= cap) {
			return new CappedSelection(matched, false, matched.size());
		}

		return new CappedSelection(List.copyOf(matched.subList(0, cap)), true, matched.size());
	}

	private static long distanceSquared(int x, int y, int z, SensorKey pos) {
		long dx = x - pos.x();
		long dy = y - pos.y();
		long dz = z - pos.z();
		return dx * dx + dy * dy + dz * dz;
	}
}
