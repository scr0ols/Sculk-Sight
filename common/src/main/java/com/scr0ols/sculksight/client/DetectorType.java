package com.scr0ols.sculksight.client;

import java.util.Optional;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** The detector kind carried from the aimed block to its rendered shell. */
public enum DetectorType {
	NORMAL_SENSOR(0xFFA300),
	CALIBRATED_SENSOR(0x99CCFF),
	SHRIEKER(0x8B0025);

	private final int colour;

	DetectorType(int colour) {
		this.colour = colour;
	}

	public int colour() {
		return colour;
	}

	/** Classifies the three detector blocks supported by the v0.2 palette. */
	public static Optional<DetectorType> of(Block block) {
		if (block == Blocks.CALIBRATED_SCULK_SENSOR) {
			return Optional.of(CALIBRATED_SENSOR);
		}
		if (block == Blocks.SCULK_SHRIEKER) {
			return Optional.of(SHRIEKER);
		}
		if (block == Blocks.SCULK_SENSOR) {
			return Optional.of(NORMAL_SENSOR);
		}
		return Optional.empty();
	}
}
