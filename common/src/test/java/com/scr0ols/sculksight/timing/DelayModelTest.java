package com.scr0ols.sculksight.timing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DelayModelTest {
	@Test
	void theSensorPositionStillTakesOneTick() {
		BlockPos sensor = new BlockPos(10, 64, -4);
		assertEquals(1, DelayModel.ticksBetween(sensor, sensor));
	}

	@Test
	void distanceIsFlooredAfterUsingBlockCentres() {
		BlockPos sensor = new BlockPos(0, 0, 0);
		assertEquals(1, DelayModel.ticksBetween(new BlockPos(1, 1, 1), sensor));
		assertEquals(5, DelayModel.ticksBetween(new BlockPos(3, 4, 0), sensor));
	}

	@Test
	void theQuantizerKeepsItsOneTickMinimum() {
		assertEquals(1, DelayQuantizer.ticksForDistance(0.0));
		assertEquals(3, DelayQuantizer.ticksForDistance(3.99));
	}

	@ParameterizedTest
	@MethodSource("representativeDistances")
	void quantizesCentreToCentreDistance(double distance, int expectedTicks) {
		assertEquals(expectedTicks, DelayQuantizer.ticksForDistance(distance));
	}

	private static Stream<Arguments> representativeDistances() {
		return Stream.of(Arguments.of(0.0, 1), Arguments.of(0.999, 1), Arguments.of(1.0, 1),
				Arguments.of(Math.sqrt(2.0), 1), Arguments.of(Math.sqrt(13.0), 3),
				Arguments.of(3.0, 3), Arguments.of(5.0, 5), Arguments.of(9.999, 9),
				Arguments.of(10.0, 10));
	}

	@ParameterizedTest
	@MethodSource("representativeBlockPositions")
	void computesDelayBetweenBlockCentres(BlockPos source, BlockPos sensor, int expectedTicks) {
		assertEquals(expectedTicks, DelayModel.ticksBetween(source, sensor));
	}

	private static Stream<Arguments> representativeBlockPositions() {
		return Stream.of(Arguments.of(new BlockPos(0, 0, 0), new BlockPos(0, 0, 0), 1),
				Arguments.of(new BlockPos(0, 0, 0), new BlockPos(3, 4, 0), 5),
				Arguments.of(new BlockPos(1, 5, 2), new BlockPos(3, 8, 2), 3));
	}
}
