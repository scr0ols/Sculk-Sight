package com.scr0ols.sculksight.client;

import java.util.Comparator;

/**
 * ARCHITECTURE.md section 12.4 sub-problem 3: PER_SENSOR mode's per-frame draw list has no
 * defined order today - {@code entries} is a {@code LinkedHashMap} in selection order, which has
 * nothing to do with the camera. Overlapping translucent shells composite in the order they are
 * drawn (ADR-022's two low-alpha tiers are not order-independent), so the standard technique for
 * correct alpha compositing of overlapping translucent geometry applies here: sort back-to-front
 * by distance from the camera, farthest first, so the nearest shell draws last and composites on
 * top of everything behind it.
 *
 * <p>Extracted from {@code ShellRenderer} so the comparator itself is reachable from a plain JVM
 * test. {@link SensorKey} and this class both live in {@code common/src/main/java}, unlike {@code
 * ShellRenderer} itself, which is {@code src/client/java} and outside this module's own build
 * (ADR-044) - the same reason {@code RadiusAudit}'s own distance test lives where it does.
 */
final class DrawOrder {

	private DrawOrder() {
	}

	/**
	 * Back-to-front: the {@link SensorKey} farthest from {@code (cameraX, cameraY, cameraZ)} first,
	 * nearest last.
	 *
	 * <p><b>Re-evaluate every frame.</b> This comparator caches nothing and captures the camera
	 * position it was built with; the caller must build a fresh one each frame, since the camera
	 * moves and a stale comparator would sort correctly only for the frame it was built on.
	 */
	static Comparator<SensorKey> backToFront(double cameraX, double cameraY, double cameraZ) {
		return Comparator.comparingDouble(
				(SensorKey position) -> distanceSquared(position, cameraX, cameraY, cameraZ)).reversed();
	}

	private static double distanceSquared(SensorKey position, double cameraX, double cameraY, double cameraZ) {
		double dx = position.x() - cameraX;
		double dy = position.y() - cameraY;
		double dz = position.z() - cameraZ;
		return dx * dx + dy * dy + dz * dz;
	}
}
