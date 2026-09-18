package com.scr0ols.sculksight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * ARCHITECTURE.md section 12.4 sub-problem 3: back-to-front ordering by camera distance, the
 * comparator {@code ShellRenderer.onRender} applies to {@code PER_SENSOR} mode's per-frame draw
 * list so overlapping translucent shells composite with the nearest one on top.
 *
 * <p>{@link ShellRendererDrawOrderTest} confirms {@code onRender} actually wires this in and only
 * to the branch it applies to; this class covers the comparator's own arithmetic.
 */
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

		// The camera sits close to b (distance 1) and far from a (distance 19), so a - the farther
		// one from THIS camera position - must sort first. A comparator built from a stale camera
		// position would get this backwards the moment the camera moved.
		positions.sort(DrawOrder.backToFront(19, 0, 0));

		assertEquals(List.of(a, b), positions);
	}

	@Test
	void continuousCameraCoordinatesAreHonoured() {
		SensorKey left = new SensorKey(-5, 64, 0);
		SensorKey right = new SensorKey(5, 64, 0);
		List<SensorKey> positions = new ArrayList<>(List.of(left, right));

		// The camera's own position is never block-aligned. Sitting 0.5 blocks from left and 9.5
		// from right must still resolve left as nearer, so it sorts last.
		positions.sort(DrawOrder.backToFront(-4.5, 64.0, 0.0));

		assertEquals(List.of(right, left), positions);
	}

	@Test
	void equalDistancesLeaveRelativeOrderStable() {
		SensorKey east = new SensorKey(5, 0, 0);
		SensorKey north = new SensorKey(0, 0, 5);
		List<SensorKey> positions = new ArrayList<>(List.of(east, north));

		positions.sort(DrawOrder.backToFront(0, 0, 0));

		// Both are exactly 5 blocks from the camera; List.sort is a stable sort and must not
		// reorder two elements the comparator treats as equal.
		assertEquals(List.of(east, north), positions);
	}
}
