package com.scr0ols.sculksight.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;

final class ShellSolveResult implements ShellUploadSlot.Payload {

	private final MeshData mesh;

	private final ByteBufferBuilder storage;

	private final ShellStats stats;

	private final long snapshotNanos;

	private final long encodeNanos;

	ShellSolveResult(MeshData mesh, ByteBufferBuilder storage, ShellStats stats, long snapshotNanos,
			long encodeNanos) {
		this.mesh = mesh;
		this.storage = storage;
		this.stats = stats;
		this.snapshotNanos = snapshotNanos;
		this.encodeNanos = encodeNanos;
	}

	MeshData mesh() {
		return mesh;
	}

	ShellStats stats() {
		return stats;
	}

	long snapshotNanos() {
		return snapshotNanos;
	}

	long encodeNanos() {
		return encodeNanos;
	}

	/** Closes the mesh and then the builder that backs it. */
	@Override
	public void close() {
		mesh.close();
		storage.close();
	}
}
