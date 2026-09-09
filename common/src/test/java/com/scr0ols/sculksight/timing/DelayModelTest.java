package com.scr0ols.sculksight.timing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

class DelayModelTest {

	@Test
	void theSensorPositionStillTakesOneTick() {
		BlockPos sensor = new BlockPos(10, 64, -4);

		assertEquals(1, DelayModel.ticksBetween(sensor, sensor));
	}

	@Test
	void distanceIsFlooredAfterUsingBlockCentres() {
		BlockPos sensor = new BlockPos(0, 0, 0);

		assertEquals(1, DelayModel.ticksBetween(new BlockPos(1, 1, 1), new BlockPos(0, 0, 0)));
		assertEquals(5, DelayModel.ticksBetween(new BlockPos(3, 4, 0), new BlockPos(0, 0, 0)));
	}

	@Test
	void theQuantizerKeepsItsOneTickMinimum() {
		assertEquals(1, DelayQuantizer.ticksForDistance(0.0));
		assertEquals(3, DelayQuantizer.ticksForDistance(3.99));
	}
}
