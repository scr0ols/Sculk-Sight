package com.scr0ols.sculksight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class DrawOrderTest {

	@Test
	void sortsFarthestFirstAndNearestLast() {
		SensorKey near = new SensorKey(1, 0, 0);
		SensorKey middle = new SensorKey(10, 0, 0);
		SensorKey far = new SensorKey(100, 0, 0);
		List<SensorKey> positions = new ArrayList<>(List.of(middle, far, near));

		positions.sort(DrawOrder.backToFront(0, 0, 0));

		assertEquals(List.of(far, middle, near), positions);
	}

	@Test
	void orderTracksWhicheverCameraPositionIsPassedRatherThanACachedOne() {
		SensorKey a = new SensorKey(0, 0, 0);
		SensorKey b = new SensorKey(20, 0, 0);
		List<SensorKey> positions = new ArrayList<>(List.of(a, b));

		positions.sort(DrawOrder.backToFront(19, 0, 0));

		assertEquals(List.of(a, b), positions);
	}

	@Test
	void continuousCameraCoordinatesAreHonoured() {
		SensorKey left = new SensorKey(-5, 64, 0);
		SensorKey right = new SensorKey(5, 64, 0);
		List<SensorKey> positions = new ArrayList<>(List.of(left, right));

		positions.sort(DrawOrder.backToFront(-4.5, 64.0, 0.0));

		assertEquals(List.of(right, left), positions);
	}

	@Test
	void equalDistancesLeaveRelativeOrderStable() {
		SensorKey east = new SensorKey(5, 0, 0);
		SensorKey north = new SensorKey(0, 0, 5);
		List<SensorKey> positions = new ArrayList<>(List.of(east, north));

		positions.sort(DrawOrder.backToFront(0, 0, 0));

		assertEquals(List.of(east, north), positions);
	}
}
