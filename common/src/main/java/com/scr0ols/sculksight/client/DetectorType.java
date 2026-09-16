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

	/**
	 * Classifies the three detector blocks supported by the v0.2 palette, or empty for any other
	 * block. The sculk catalyst is deliberately excluded even though its block entity also
	 * implements {@code GameEventListener.Provider}: it reacts to nearby mob deaths by spawning
	 * sculk growth, not by emitting the vibration-frequency detections the sensor family reports,
	 * so it has no shell to draw.
	 */
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
