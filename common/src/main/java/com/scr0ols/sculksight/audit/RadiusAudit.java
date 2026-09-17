package com.scr0ols.sculksight.audit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.scr0ols.sculksight.client.DetectorType;
import com.scr0ols.sculksight.client.SensorKey;

/**
 * Mode B's selection stage. ARCHITECTURE.md section 12.1: "Given a centre, a radius and an
 * optional type, decides which of a supplied set of sensors qualify, in a defined order, up to a
 * cap. Names no Minecraft type."
 *
 * <p><b>Layer 1, like {@link com.scr0ols.sculksight.solver.SensorDetector}.</b> This class takes
 * sensor positions as plain integers, the way that one does, so the whole of "which sensors does
 * {@code /sculksight find all 64 live} mean" is reachable from a plain JVM test with no {@code
 * ClientLevel} anywhere. The conversion from {@link com.scr0ols.sculksight.client.SensorIndex}'s
 * snapshot into {@link AuditedSensor} is the client-side half section 12.1 calls "trivial by
 * construction" and does not ask to be unit-tested.
 *
 * <p><b>The cap.</b> Section 12.1's own sentence names one, and section 12.4 commits to it being
 * enforced here, at this layer, before anything is solved or uploaded. {@link #select} itself
 * stays uncapped and ordered; {@link #selectWithCap} is the wrapper that truncates to it.
 */
public final class RadiusAudit {

	private RadiusAudit() {
	}

	/**
	 * One sensor as the audit sees it: a position, the listener radius read from the world at
	 * index time ({@link com.scr0ols.sculksight.client.SensorIndex}'s own value, not a lookup this
	 * class performs), and its detector type. The same shape serves both as this class's input -
	 * the supplied candidate set - and its output - the selected subset, in order.
	 *
	 * <p>Section 12.3: "The audit produces a set of {@code SensorKey} plus radius plus detector
	 * type. That is precisely the shape {@code syncEntries} builds today" - which is why this
	 * record carries all three rather than a bare {@link SensorKey}.
	 */
	public record AuditedSensor(SensorKey position, int listenerRadius, DetectorType type) {
	}

	/**
	 * Selects and orders the sensors in {@code candidates} that satisfy {@code request} around
	 * {@code (centreX, centreY, centreZ)}.
	 *
	 * <p><b>Qualification is proximity of the sensor's own position to the centre</b>, not a
	 * detection or occlusion test - {@link com.scr0ols.sculksight.solver.SensorDetector} answers a
	 * different question, "is this position within that sensor's range", and this one answers "is
	 * this sensor within my query radius". Inclusive at the boundary, the same convention R2 fixes
	 * for {@link com.scr0ols.sculksight.solver.SensorDetector#isInRange}: a sensor at exactly
	 * {@code distance == radius} qualifies.
	 *
	 * <p>An absent {@link RadiusAuditRequest#detector()} means every type - it is what the
	 * {@code all} argument parses to, not a missing value; see {@link RadiusAuditRequest}'s own
	 * javadoc.
	 *
	 * <p><b>The defined order is nearest-first</b>, by squared distance from the centre, with a
	 * position-coordinate tie-break so two sensors at the same distance still sort the same way on
	 * every call. Nearest-first is the natural reading of an audit centred on the player: the
	 * sensors closest to them are the ones most likely to matter, and it is also what makes a later
	 * cap (section 12.4) a meaningful truncation - keep the nearest N - rather than an arbitrary
	 * one.
	 *
	 * @return the qualifying sensors, nearest first, uncapped
	 */
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

	/**
	 * Section 12.4's cap, as this class's own result: the nearest-first selection {@link #select}
	 * produces, truncated to {@code cap} entries, plus whether truncation actually happened and how
	 * many candidates qualified before it did. The caller (the command core) uses {@link #capped}
	 * to decide whether a warning belongs in its report, and {@link #matchedCount} to say what was
	 * truncated away.
	 */
	public record CappedSelection(List<AuditedSensor> selected, boolean capped, int matchedCount) {
	}

	/**
	 * {@link #select}, with section 12.4's cap enforced before the result leaves this class - "at
	 * layer 1, before anything is solved or uploaded." The nearest {@code cap} sensors are kept,
	 * which is exactly what {@link #select}'s nearest-first order exists to make meaningful.
	 */
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
