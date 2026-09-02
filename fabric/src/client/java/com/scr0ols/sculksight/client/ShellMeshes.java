package com.scr0ols.sculksight.client;

import com.mojang.blaze3d.vertex.MeshData;

/**
 * One solve's two meshes: the shell's boundary faces and its crease edges.
 *
 * <p>The payload of {@link ShellUploadSlot} since ADR-028 gave the shell a second buffer. It is one
 * value rather than two independent slots because the two meshes come from the same solve and are
 * meaningless apart: handing them over separately would let one frame take the faces of a new solve
 * and the edges of the old one and draw a mismatched pair.
 *
 * <p><b>Closing this closes both, which is what keeps ADR-017's ownership rule intact.</b> That
 * rule is stated over the reference in the slot, not over a mesh, so widening the payload did not
 * reopen the reasoning about double-closes: the {@code AtomicReference} still hands a given
 * reference to exactly one caller, and that caller now closes a pair.
 *
 * <p>{@code edges} is never null in practice, since any non-empty set has crease edges, but the
 * encoder's contract allows null and this record does not narrow it.
 */
record ShellMeshes(MeshData faces, MeshData edges) implements AutoCloseable {

	@Override
	public void close() {
		// try-with-resources rather than two calls, so that a throw from the first still closes
		// the second. These hold native memory the garbage collector will not reclaim (R13 points
		// 7 and 10), so a leak here is a real leak.
		try (MeshData closingFaces = faces; MeshData closingEdges = edges) {
			// Nothing further: closing is the whole operation.
		}
	}
}
