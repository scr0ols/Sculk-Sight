package com.scr0ols.sculksight.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;

/**
 * One solve's complete output: the whole of what DECISIONS.md ADR-048 widens
 * {@link ShellUploadSlot}'s payload to carry - the mesh, the per-solve builder that backs its
 * native memory, and the two numbers {@link ShellRenderer} reports once the render thread has
 * taken this off the slot.
 *
 * <p><b>Why the builder travels with the mesh now, rather than staying the one long-lived field
 * the renderer used to own (the retired {@code MESH_STORAGE}).</b> RESEARCH-LOG.md R19 found
 * {@code ByteBufferBuilder} carries no synchronisation on any field, and the moment the encode
 * moves to a worker - this ADR's own second half - a single shared builder would be written by
 * that worker while the render thread frees results from it through ARCHITECTURE.md section 6.3's
 * own ownership rule: a native-memory race, not merely a stale read. Each solve now gets its own
 * builder, and {@link #close()} closes both the mesh and the builder together, which is what makes
 * one call site correct for the pair rather than two call sites that could disagree.
 *
 * <p><b>{@code stats} and {@code encodeNanos} travel here for the same reason DECISIONS.md
 * ADR-026 already named.</b> They were static fields beside {@link ShellUploadSlot} in
 * {@link ShellRenderer}, correct only while producer and consumer were the same thread; ADR-048
 * point 4 is where moving them into the payload was decided.
 *
 * <p>Implements {@link ShellUploadSlot.Payload} rather than a checked-exception-throwing
 * {@code AutoCloseable} directly, since neither {@code MeshData#close()} nor
 * {@code ByteBufferBuilder#close()} ever throws one, and the slot's own generic code would have no
 * useful way to handle one if a payload ever did.
 */
final class ShellSolveResult implements ShellUploadSlot.Payload {

	private final MeshData mesh;

	private final ByteBufferBuilder storage;

	private final ShellStats stats;

	private final long encodeNanos;

	ShellSolveResult(MeshData mesh, ByteBufferBuilder storage, ShellStats stats, long encodeNanos) {
		this.mesh = mesh;
		this.storage = storage;
		this.stats = stats;
		this.encodeNanos = encodeNanos;
	}

	MeshData mesh() {
		return mesh;
	}

	ShellStats stats() {
		return stats;
	}

	long encodeNanos() {
		return encodeNanos;
	}

	/**
	 * Closes the mesh, then the builder that backs it.
	 *
	 * <p>The mesh first: it does no freeing of its own here (RESEARCH-LOG.md R15.6 -
	 * {@code MeshData.close()} only decrements the builder's own result count and, once that
	 * reaches zero, compacts the builder for reuse), but closing it in this order means the
	 * builder's result count has already reached zero by the time {@code storage.close()} runs,
	 * for a per-solve builder that only ever holds this one result. Closing the builder itself is
	 * what actually releases the native memory.
	 */
	@Override
	public void close() {
		mesh.close();
		storage.close();
	}
}
