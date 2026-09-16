package com.scr0ols.sculksight.verify;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;

import com.scr0ols.sculksight.client.DetectorType;

/**
 * Covers {@link IntegratedServerSensorProbe}'s per-detector readiness gate, established from the
 * 26.2 source for {@code SculkSensorBlock.canActivate} and
 * {@code SculkShriekerBlockEntity.VibrationUser.canReceiveVibration} before this was written
 * (c-docs/sculk-v02-close-out.md Part 1). A full end-to-end probe run needs a live
 * {@code ServerLevel} and is a live in-game exit criterion, not a plain JVM test; what is tested
 * here is the pure per-type logic the probe uses to decide whether it is even worth firing a
 * stimulus at all.
 */
class IntegratedServerSensorProbeReadinessTest {

	@BeforeAll
	static void bootstrapRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void aSensorInEveryPhaseOtherThanInactiveIsNotReady() {
		BlockState inactive = Blocks.SCULK_SENSOR.defaultBlockState().setValue(SculkSensorBlock.PHASE, SculkSensorPhase.INACTIVE);
		BlockState active = Blocks.SCULK_SENSOR.defaultBlockState().setValue(SculkSensorBlock.PHASE, SculkSensorPhase.ACTIVE);
		BlockState cooldown = Blocks.SCULK_SENSOR.defaultBlockState().setValue(SculkSensorBlock.PHASE, SculkSensorPhase.COOLDOWN);

		assertTrue(IntegratedServerSensorProbe.isReadyToActivate(DetectorType.NORMAL_SENSOR, inactive));
		assertFalse(IntegratedServerSensorProbe.isReadyToActivate(DetectorType.NORMAL_SENSOR, active));
		assertFalse(IntegratedServerSensorProbe.isReadyToActivate(DetectorType.NORMAL_SENSOR, cooldown));
	}

	@Test
	void aCalibratedSensorSharesTheSamePhaseGateAsSculkSensorBlock() {
		BlockState inactive = Blocks.CALIBRATED_SCULK_SENSOR.defaultBlockState().setValue(SculkSensorBlock.PHASE, SculkSensorPhase.INACTIVE);
		BlockState active = Blocks.CALIBRATED_SCULK_SENSOR.defaultBlockState().setValue(SculkSensorBlock.PHASE, SculkSensorPhase.ACTIVE);

		assertTrue(IntegratedServerSensorProbe.isReadyToActivate(DetectorType.CALIBRATED_SENSOR, inactive));
		assertFalse(IntegratedServerSensorProbe.isReadyToActivate(DetectorType.CALIBRATED_SENSOR, active));
	}

	@Test
	void aShriekerIsReadyOnlyWhileNotShrieking() {
		BlockState notShrieking = Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(SculkShriekerBlock.SHRIEKING, false);
		BlockState shrieking = Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(SculkShriekerBlock.SHRIEKING, true);

		assertTrue(IntegratedServerSensorProbe.isReadyToActivate(DetectorType.SHRIEKER, notShrieking));
		assertFalse(IntegratedServerSensorProbe.isReadyToActivate(DetectorType.SHRIEKER, shrieking));
	}

	@Test
	void onlyTheSensorFamilyRequiresANonZeroVibrationFrequency() {
		assertTrue(IntegratedServerSensorProbe.requiresNonZeroFrequency(DetectorType.NORMAL_SENSOR));
		assertTrue(IntegratedServerSensorProbe.requiresNonZeroFrequency(DetectorType.CALIBRATED_SENSOR));

		// A shrieker's own canReceiveVibration never inspects frequency, and its only listenable
		// event (sculk_sensor_tendrils_clicking) is itself frequency 0 - see
		// IntegratedServerSensorProbe.requiresNonZeroFrequency's own javadoc for the source this
		// is read from.
		assertFalse(IntegratedServerSensorProbe.requiresNonZeroFrequency(DetectorType.SHRIEKER));
	}
}
