package com.scr0ols.sculksight.client;

import org.jspecify.annotations.Nullable;

import com.scr0ols.sculksight.solver.DetectionSet;
import com.scr0ols.sculksight.solver.ShellSolution;
import com.scr0ols.sculksight.solver.WorldDetectionSet;

final class ShellEntry implements AutoCloseable {

	private final SensorKey sensor;

	private final DetectorType detector;

	private final int radius;

	private final ShellUploadSlot<ShellSolveResult> slot = new ShellUploadSlot<>();

	private long revision = 1L;

	private volatile @Nullable DetectionSet set;

	private volatile int occludedOut;

	private volatile @Nullable WorldDetectionSet worldSet;

	private volatile @Nullable DelayOverlay delayOverlay;

	private @Nullable ShellBuffer buffer;

	private @Nullable ShellStats stats;

	ShellEntry(SensorKey sensor, int radius, DetectorType detector) {
		this.sensor = sensor;
		this.radius = radius;
		this.detector = detector;
	}

	SensorKey sensor() {
		return sensor;
	}

	int radius() {
		return radius;
	}

	DetectorType detector() {
		return detector;
	}

	long revision() {
		return revision;
	}

	long nextRevision() {
		return ++revision;
	}

	ShellUploadSlot<ShellSolveResult> slot() {
		return slot;
	}

	@Nullable DetectionSet set() {
		return set;
	}

	void setSolution(ShellSolution solved) {
		delayOverlay = DelayOverlay.from(sensor, solved);
		worldSet = null;
		set = solved.accepted();
		occludedOut = solved.occludedOut().size();
	}

	int occludedOut() {
		return occludedOut;
	}

	void setWorldSolution(WorldDetectionSet solved) {
		worldSet = solved;
		set = null;
		delayOverlay = null;
	}

	@Nullable WorldDetectionSet worldSet() {
		return worldSet;
	}

	@Nullable DelayOverlay delayOverlay() {
		return delayOverlay;
	}

	@Nullable ShellBuffer buffer() {
		return buffer;
	}

	@Nullable ShellStats stats() {
		return stats;
	}

	void setBuffer(ShellBuffer newBuffer, ShellStats newStats) {
		ShellBuffer previous = buffer;
		buffer = newBuffer;
		stats = newStats;

		if (previous != null) {
			previous.close();
		}
	}

	/** Releases the upload slot and GPU buffer; render thread only. */
	@Override
	public void close() {
		slot.close();

		if (buffer != null) {
			buffer.close();
			buffer = null;
		}

		set = null;
		worldSet = null;
		delayOverlay = null;
		stats = null;
	}
}
