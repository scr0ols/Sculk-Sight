package com.scr0ols.sculksight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

class NearCameraFadeTest {

	@BeforeAll
	static void bootstrapRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	// ---------------------------------------------------------------- factorFor(double), pure math

	@Test
	void rightAtTheFloorThePassIsFullySuppressed() {
		assertEquals(0.0F, NearCameraFade.factorFor(0.0), 1.0E-6F);
		assertEquals(0.0F, NearCameraFade.factorFor(NearCameraFade.FULLY_SUPPRESSED_BLOCKS), 1.0E-6F);
	}

	@Test
	void atOrBeyondTheUnaffectedDistanceThePassIsAtFullStrength() {
		assertEquals(1.0F, NearCameraFade.factorFor(NearCameraFade.UNAFFECTED_BLOCKS), 1.0E-6F);
		assertEquals(1.0F, NearCameraFade.factorFor(NearCameraFade.UNAFFECTED_BLOCKS + 10.0), 1.0E-6F);
	}

	@Test
	void betweenTheTwoThresholdsTheFactorRampsLinearly() {
		double midpoint = (NearCameraFade.FULLY_SUPPRESSED_BLOCKS + NearCameraFade.UNAFFECTED_BLOCKS) / 2.0;

		assertEquals(0.5F, NearCameraFade.factorFor(midpoint), 1.0E-6F);
	}

	@Test
	void theFactorNeverLeavesTheZeroToOneRange() {
		for (double distance = -5.0; distance <= NearCameraFade.UNAFFECTED_BLOCKS + 5.0; distance += 0.25) {
			float factor = NearCameraFade.factorFor(distance);

			assertTrue(factor >= 0.0F && factor <= 1.0F, "factor out of range at distance " + distance);
		}
	}

	// ---------------------------------------------------------------- factorForCamera, real raycast

	@Test
	void aFloorRightUnderTheCameraFullySuppressesThePass() {
		BlockGetter level = new FlatFloor(Blocks.STONE.defaultBlockState(), 63);
		Vec3 camera = new Vec3(0.5, 64.1, 0.5);

		assertEquals(0.0F, NearCameraFade.factorForCamera(level, camera), 1.0E-6F);
	}

	@Test
	void aFloorWellBeyondTheUnaffectedDistanceLeavesThePassAtFullStrength() {
		BlockGetter level = new FlatFloor(Blocks.STONE.defaultBlockState(), 0);
		Vec3 camera = new Vec3(0.5, 100.0, 0.5);

		assertEquals(1.0F, NearCameraFade.factorForCamera(level, camera), 1.0E-6F);
	}

	@Test
	void noFloorAtAllLeavesThePassAtFullStrength() {
		BlockGetter level = new FlatFloor(Blocks.AIR.defaultBlockState(), 63);
		Vec3 camera = new Vec3(0.5, 64.1, 0.5);

		assertEquals(1.0F, NearCameraFade.factorForCamera(level, camera), 1.0E-6F);
	}

	@Test
	void aFlowerHasNoCollisionSoTheRayPassesThroughToWhateverIsUnderIt() {
		// A flower has an empty collision shape - nothing would actually stand on it - so it must
		// not be mistaken for a floor.
		BlockGetter level = new FlatFloor(Blocks.POPPY.defaultBlockState(), 63);
		Vec3 camera = new Vec3(0.5, 64.1, 0.5);

		assertEquals(1.0F, NearCameraFade.factorForCamera(level, camera), 1.0E-6F);
	}

	@Test
	void glassHasCollisionSoItCountsAsAFloorLikeAnyOtherSolidBlock() {
		BlockGetter level = new FlatFloor(Blocks.GLASS.defaultBlockState(), 63);
		Vec3 camera = new Vec3(0.5, 64.1, 0.5);

		assertEquals(0.0F, NearCameraFade.factorForCamera(level, camera), 1.0E-6F);
	}

	/** A world that is entirely air except for one flat, infinite floor at {@code floorY}. */
	private static final class FlatFloor implements BlockGetter {

		private final BlockState floorState;
		private final int floorY;

		FlatFloor(BlockState floorState, int floorY) {
			this.floorState = floorState;
			this.floorY = floorY;
		}

		@Override
		public BlockEntity getBlockEntity(BlockPos pos) {
			return null;
		}

		@Override
		public BlockState getBlockState(BlockPos pos) {
			return pos.getY() == floorY ? floorState : Blocks.AIR.defaultBlockState();
		}

		@Override
		public FluidState getFluidState(BlockPos pos) {
			return getBlockState(pos).getFluidState();
		}

		@Override
		public int getHeight() {
			return 384;
		}

		@Override
		public int getMinY() {
			return -64;
		}
	}
}
