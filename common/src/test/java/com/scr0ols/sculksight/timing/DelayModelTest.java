package com.scr0ols.sculksight.timing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
}
