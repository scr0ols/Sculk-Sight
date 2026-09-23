package com.scr0ols.sculksight.client;

import java.util.Comparator;

final class DrawOrder {

	private DrawOrder() {
	}

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
