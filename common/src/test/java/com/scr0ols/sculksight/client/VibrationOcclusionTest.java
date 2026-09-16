package com.scr0ols.sculksight.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;

class VibrationOcclusionTest {

	@BeforeAll
	static void bootstrapRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void allWoolCarpetsAreOccluders() {
		Blocks.CARPET.asList().forEach(carpet ->
				assertTrue(VibrationOcclusion.isOccluder(carpet.defaultBlockState()), carpet.toString()));
	}

	@Test
	void woolAndCarpetsAreDampenersForSourceBelowRule() {
		Blocks.WOOL.asList().forEach(wool ->
				assertTrue(VibrationOcclusion.isDampener(wool.defaultBlockState()), wool.toString()));
		Blocks.CARPET.asList().forEach(carpet ->
				assertTrue(VibrationOcclusion.isDampener(carpet.defaultBlockState()), carpet.toString()));
	}

	@Test
	void unrelatedBlocksAreNotAddedToThePredicate() {
		assertFalse(VibrationOcclusion.isOccluder(Blocks.STONE.defaultBlockState()));
		assertFalse(VibrationOcclusion.isOccluder(Blocks.MOSS_BLOCK.defaultBlockState()));
	}
}
