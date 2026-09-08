package com.scr0ols.sculksight.timing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import net.minecraft.core.BlockPos;

class DelayModelTest {

	@ParameterizedTest(name = "{0} to {1} is {2} tick(s)")
	@MethodSource("representativeDistances")
	void quantizesCentreToCentreDistance(double distance,
			int expectedTicks) {
		assertEquals(expectedTicks, DelayQuantizer.ticksForDistance(distance));
	}

	private static Stream<Arguments> representativeDistances() {
		return Stream.of(
				Arguments.of(0.0, 1),
				Arguments.of(0.999, 1),
				Arguments.of(1.0, 1),
				Arguments.of(Math.sqrt(2.0), 1),
				Arguments.of(Math.sqrt(13.0), 3),
				Arguments.of(3.0, 3),
				Arguments.of(5.0, 5),
				Arguments.of(9.999, 9),
				Arguments.of(10.0, 10));
	}

	@ParameterizedTest(name = "{0} to {1} is {2} tick(s)")
	@MethodSource("representativeBlockPositions")
	void computesDelayBetweenBlockCentres(BlockPos source, BlockPos sensor, int expectedTicks) {
		assertEquals(expectedTicks, DelayModel.ticksBetween(source, sensor));
	}

	private static Stream<Arguments> representativeBlockPositions() {
		return Stream.of(
				// Identical positions: zero centre-to-centre distance, corrected up to one tick.
				Arguments.of(new BlockPos(0, 0, 0), new BlockPos(0, 0, 0), 1),
				// Axis-aligned 3-4-0 triangle: exact centre-to-centre distance of 5.0.
				Arguments.of(new BlockPos(0, 0, 0), new BlockPos(3, 4, 0), 5),
				// Non-axis-aligned offset (2, 3, 0): distance sqrt(13) ~= 3.606, floored to 3.
				Arguments.of(new BlockPos(1, 5, 2), new BlockPos(3, 8, 2), 3));
	}
}
