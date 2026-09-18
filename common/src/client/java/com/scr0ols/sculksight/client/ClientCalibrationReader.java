package com.scr0ols.sculksight.client;

import com.scr0ols.sculksight.event.CalibratedTuning;
import com.scr0ols.sculksight.event.CalibratedTuningInference;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Reads the calibrated sensor's opposite-facing client-visible neighbor signal. */
public final class ClientCalibrationReader {
	private ClientCalibrationReader() {
	}

	public static CalibratedTuning read(ClientLevel level, BlockPos sensorPos, Direction facing,
			boolean synchronizedState) {
		Direction inputFace = facing.getOpposite();
		BlockPos inputPos = sensorPos.relative(inputFace);
		boolean loaded = level != null && level.hasChunkAt(inputPos);
		return CalibratedTuningInference.read(loaded, synchronizedState,
				level == null ? null : () -> level.getSignal(inputPos, inputFace));
	}
}
