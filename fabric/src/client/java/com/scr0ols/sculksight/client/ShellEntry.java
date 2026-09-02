package com.scr0ols.sculksight.client;

import org.jspecify.annotations.Nullable;

import com.scr0ols.sculksight.solver.DetectionSet;

/**
 * One sensor's cached shell. ARCHITECTURE.md section 3.3, ADR-016.
 *
 * <p>v0.0 holds one of these at a time, because mode A draws the shell of the sensor being aimed
 * at (PLAN.md section 3.4). The type is keyed and shaped for many from the start, so modes B and C
 * add entries rather than a second mechanism.
 *
 * <p>{@code revision} is what makes the hand-off safe against a slow solve finishing after a newer
 * one; the mechanism is {@link ShellUploadSlot}.
 *
 * <p><b>Two GPU buffers since ADR-028</b>, the boundary faces and the crease edges, uploaded
 * together from the one pair the slot hands over and replaced together. They are separate buffers
 * rather than one because they carry different vertex formats and are drawn with different
 * pipelines; nothing else about the entry changes.
 *
 * <p><b>The detection set is retained after the meshes are built</b>, which it was not before
 * ADR-029. The renderer needs it every frame to ask whether the camera is inside the shell, which
 * is one bitset lookup and is the right test rather than a distance test: a camera in an occlusion
 * shadow inside the sphere is outside the shell, and that is exactly the scene this mod exists for.
 * The cost of keeping it is roughly 615 bytes at radius 8 and 4.5 KB at radius 16 (ADR-016).
 */
final class ShellEntry implements AutoCloseable {

	private final SensorKey sensor;

	private final int radius;

	private final ShellUploadSlot slot = new ShellUploadSlot();

	private long revision = 1L;

	private @Nullable DetectionSet set;

	private @Nullable ShellBuffer faceBuffer;

	private @Nullable ShellBuffer edgeBuffer;

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

	@Nullable DetectionSet set() {
		return set;
	}

	/**
	 * Records the set a solve produced, so that the per-frame inside test of ADR-029 has something
	 * to ask.
	 *
	 * <p>Set at solve time rather than at upload time, and that is deliberate: the set describes
	 * the shell the solve found, and the alternative would leave the previous solve's set answering
	 * questions about the current one during the frames between the two.
	 */
	void setSet(DetectionSet solved) {
		set = solved;
	}

	@Nullable ShellBuffer faceBuffer() {
		return faceBuffer;
	}

	@Nullable ShellBuffer edgeBuffer() {
		return edgeBuffer;
	}

	@Nullable ShellStats stats() {
		return stats;
	}

	/**
	 * Render thread. Replaces both live buffers, closing the ones they displace.
	 *
	 * <p>The old buffers are closed only after the new ones exist, so a failed upload leaves the
	 * previous shell drawing rather than leaving the entry with nothing. The two are replaced in one
	 * call rather than two, because a frame that saw new faces against an old seam would be drawing
	 * a shell that never existed.
	 */
	void setBuffers(ShellBuffer newFaces, ShellBuffer newEdges, ShellStats newStats) {
		ShellBuffer previousFaces = faceBuffer;
		ShellBuffer previousEdges = edgeBuffer;

		faceBuffer = newFaces;
		edgeBuffer = newEdges;
		stats = newStats;

		if (previousFaces != null) {
			previousFaces.close();
		}

		if (previousEdges != null) {
			previousEdges.close();
		}
	}

	/**
	 * Render thread only - both buffers close GL resources, and {@code GlBuffer.close} carries the
	 * same render-thread assertion as creation (ARCHITECTURE.md section 6.4, R13).
	 */
	@Override
	public void close() {
		slot.close();

		if (faceBuffer != null) {
			faceBuffer.close();
			faceBuffer = null;
		}

		if (edgeBuffer != null) {
			edgeBuffer.close();
			edgeBuffer = null;
		}

		set = null;
		stats = null;
	}
}
