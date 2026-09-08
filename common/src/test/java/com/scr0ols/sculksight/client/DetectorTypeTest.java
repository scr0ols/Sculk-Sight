package com.scr0ols.sculksight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.world.level.block.Blocks;

class DetectorTypeTest {

	@Test
	void blockMetadataSelectsEachPaletteEntry() {
		assertEquals(DetectorType.NORMAL_SENSOR, DetectorType.of(Blocks.SCULK_SENSOR));
		assertEquals(DetectorType.CALIBRATED_SENSOR, DetectorType.of(Blocks.CALIBRATED_SCULK_SENSOR));
		assertEquals(DetectorType.SHRIEKER, DetectorType.of(Blocks.SCULK_SHRIEKER));
	}
}
