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
 * {@code /sculksight radius 64} mean" is reachable from a plain JVM test with no {@code
 * ClientLevel} anywhere. The conversion from {@link com.scr0ols.sculksight.client.SensorIndex}'s
 * snapshot into {@link AuditedSensor} is the client-side half section 12.1 calls "trivial by
 * construction" and does not ask to be unit-tested.
 *
 * <p><b>What this does not yet do.</b> The cap named in section 12.1's own sentence is a separate
 * task (section 12.4): this class selects and orders, and enforces no limit on how many results it
 * returns.
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
	 * <p>An absent {@link RadiusAuditRequest#detector()} means every type, matching {@link
	 * RadiusAuditRequest}'s own javadoc for the omitted argument.
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

	private static long distanceSquared(int x, int y, int z, SensorKey pos) {
		long dx = x - pos.x();
		long dy = y - pos.y();
		long dz = z - pos.z();
		return dx * dx + dy * dy + dz * dz;
	}
}
