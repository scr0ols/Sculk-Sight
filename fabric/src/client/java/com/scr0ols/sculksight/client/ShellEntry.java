package com.scr0ols.sculksight.client;

import org.jspecify.annotations.Nullable;

/**
 * One sensor's cached shell. ARCHITECTURE.md section 3.3, ADR-016.
 *
 * <p>v0.0 holds one of these at a time, because mode A draws the shell of the sensor being aimed
 * at (PLAN.md section 3.4). The type is keyed and shaped for many from the start, so modes B and C
 * add entries rather than a second mechanism.
 *
 * <p>{@code revision} is what makes the hand-off safe against a slow solve finishing after a newer
 * one; the mechanism is {@link ShellUploadSlot}.
 */
final class ShellEntry implements AutoCloseable {

	private final SensorKey sensor;

	private final int radius;

	private final ShellUploadSlot slot = new ShellUploadSlot();

	private long revision = 1L;

	private @Nullable ShellBuffer buffer;

	private @Nullable ShellStats stats;

	ShellEntry(SensorKey sensor, int radius) {
		this.sensor = sensor;
		this.radius = radius;
	}

	SensorKey sensor() {
		return sensor;
	}

	int radius() {
		return radius;
	}

	long revision() {
		return revision;
	}

	/** Bumps the revision and returns the new value, for the solve that is about to be scheduled. */
	long nextRevision() {
		return ++revision;
	}

	ShellUploadSlot slot() {
		return slot;
	}

	@Nullable ShellBuffer buffer() {
		return buffer;
	}

	@Nullable ShellStats stats() {
		return stats;
	}

	/**
	 * Render thread. Replaces the live buffer, closing the one it displaces.
	 *
	 * <p>The old buffer is closed only after the new one exists, so a failed upload leaves the
	 * previous shell drawing rather than leaving the entry with nothing.
	 */
	void setBuffer(ShellBuffer newBuffer, ShellStats newStats) {
		ShellBuffer previous = buffer;
		buffer = newBuffer;
		stats = newStats;

		if (previous != null) {
			previous.close();
		}
	}

	/**
	 * Render thread only - both members close GL resources, and {@code GlBuffer.close} carries the
	 * same render-thread assertion as creation (ARCHITECTURE.md section 6.4, R13).
	 */
	@Override
	public void close() {
		slot.close();

		if (buffer != null) {
			buffer.close();
			buffer = null;
		}

		stats = null;
	}
}
