package com.scr0ols.sculksight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;

class DetectorTypeTest {

	@BeforeAll
	static void bootstrapRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void blockMetadataSelectsEachPaletteEntry() {
		assertEquals(Optional.of(DetectorType.NORMAL_SENSOR), DetectorType.of(Blocks.SCULK_SENSOR));
		assertEquals(Optional.of(DetectorType.CALIBRATED_SENSOR), DetectorType.of(Blocks.CALIBRATED_SCULK_SENSOR));
		assertEquals(Optional.of(DetectorType.SHRIEKER), DetectorType.of(Blocks.SCULK_SHRIEKER));
	}

	@Test
	void catalystAndOtherBlocksAreNotDetectors() {
		assertEquals(Optional.empty(), DetectorType.of(Blocks.SCULK_CATALYST));
		assertEquals(Optional.empty(), DetectorType.of(Blocks.STONE));
	}
}
