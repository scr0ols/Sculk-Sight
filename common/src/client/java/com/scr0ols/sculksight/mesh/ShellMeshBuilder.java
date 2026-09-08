package com.scr0ols.sculksight.mesh;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.jspecify.annotations.Nullable;

import com.scr0ols.sculksight.solver.BoundaryFaceExtractor;
import com.scr0ols.sculksight.solver.DetectionSet;

/**
 * The mesh encoder. ARCHITECTURE.md section 4.3.
 *
 * <p>Turns a {@link DetectionSet} into sensor-relative quad geometry, one quad per boundary face
 * (ADR-014). It is the only consumer of {@link BoundaryFaceExtractor} in the render path, and it
 * consumes it through the sink so that no per-face object is allocated on the worker thread.
 *
 * <p><b>Runs off the render thread.</b> R13 point 10 verified that {@code ByteBufferBuilder},
 * {@code BufferBuilder} and {@code MeshData} reference nothing in {@code com.mojang.blaze3d.systems}
 * or {@code .opengl}, so the whole vertex-building layer is GPU-free and the render-thread
 * constraint bites only at the later {@code createBuffer} call.
 *
 * <p><b>The caller owns the returned {@link MeshData} and must close it</b> - ownership after that
 * point is ARCHITECTURE.md section 6.3's rule, not this method's business. That close releases no
 * native memory by itself, though (R15.6): the memory backing the mesh's bytes belongs to the
 * {@code ByteBufferBuilder} passed into {@link #build}, which the caller also owns and must keep
 * open for at least as long as the mesh, and must close itself when it is truly done being reused.
 */
public final class ShellMeshBuilder {

	/**
	 * The topology the chosen pipeline family declares, read rather than inferred from the name.
	 *
	 * <p>ARCHITECTURE.md section 8 listed this as an implementation lookup precisely because
	 * {@code DEBUG_QUADS} and {@code DEBUG_FILLED_BOX} differ only in {@code location} (R15.4), so
	 * the word "quads" in the name was not evidence. It was read on 2026-09-01 from
	 * {@code RenderPipelines.DEBUG_FILLED_SNIPPET}, which calls
	 * {@code withPrimitiveTopology(PrimitiveTopology.QUADS)}. The encoder's topology has to match
	 * what the pipeline declares, so it is named here once and used for both the
	 * {@code BufferBuilder} and the shared index buffer the draw binds.
	 */
	public static final PrimitiveTopology TOPOLOGY = PrimitiveTopology.QUADS;

	private static final int VERTICES_PER_FACE = 4;

	private ShellMeshBuilder() {
	}

	/**
	 * Builds the shell mesh for the given set.
	 *
	 * <p><b>{@code storage} is caller-owned and is never closed here.</b> A {@code MeshData}'s
	 * {@code close()} only closes its {@code ByteBufferBuilder.Result} - bookkeeping inside the
	 * still-open builder (it decrements a result count and, once that reaches zero, compacts the
	 * builder for reuse) - and does not release the builder's own native memory. Only
	 * {@code ByteBufferBuilder.close()} does that, and it invalidates every outstanding
	 * {@code Result} when it runs (confirmed 2026-09-01 by direct read of
	 * {@code ByteBufferBuilder.java} and {@code MeshData.java}, RESEARCH-LOG.md R15.6). Because
	 * this mod defers reading the mesh's bytes past the point where {@code build} returns - across
	 * the worker-to-render hand-off, ARCHITECTURE.md section 6.3 - closing a builder scoped to this
	 * method call before returning would invalidate the very {@code MeshData} being handed back.
	 * {@code storage} therefore has to outlive this call and be closed by the caller once the mesh
	 * has actually been read (DECISIONS.md ADR-048: since the encode moved to a worker thread, that
	 * caller opens one fresh builder per solve and closes it alongside the mesh once the render
	 * thread has uploaded it - {@code ShellSolveResult} is where the two travel together - rather
	 * than reusing a single long-lived builder across every solve, which is how this method's own
	 * javadoc read before that ADR. Vanilla's own precedent for a caller-owned builder outliving one
	 * build call is the same either way: {@code SectionCompiler} receives its builders from a pool
	 * held outside {@code compile()} and never closes them itself (R15.6).
	 *
	 * @return the mesh, or {@code null} if the set has no boundary faces at all - which happens
	 *         only for an empty set, since any non-empty set has a surface. Returning null rather
	 *         than an empty mesh mirrors {@code BufferBuilder.build()}, whose contract this cannot
	 *         improve on, and the caller has to handle the case either way.
	 */
	public static @Nullable MeshData build(DetectionSet set, VertexFormat format, ShellStyle style,
			ByteBufferBuilder storage) {
		int faces = countBoundaryFaces(set);

		if (faces == 0) {
			return null;
		}

		int alpha = style.encodedAlpha();
		float[] corners = new float[ShellQuad.FLOATS];

		BufferBuilder buffer = new BufferBuilder(storage, TOPOLOGY, format);

		BoundaryFaceExtractor.extract(set, (dx, dy, dz, face) -> {
			ShellQuad.corners(dx, dy, dz, face, corners);

			int red = style.red(face);
			int green = style.green(face);
			int blue = style.blue(face);

			for (int corner = 0; corner < VERTICES_PER_FACE; corner++) {
				int base = corner * 3;

				// setColor(r, g, b, a) rather than setColor(packed): the four-argument form
				// writes the bytes in RGBA order with no packing convention in between, which
				// is one fewer thing to get silently backwards in a format whose Color element
				// is RGBA8_UNORM (R15.4).
				buffer.addVertex(corners[base], corners[base + 1], corners[base + 2])
						.setColor(red, green, blue, alpha);
			}
		});

		// buildOrThrow rather than build: faces > 0 was established above, so a null here
		// would mean the extractor and the counter disagreed, which is a bug worth an
		// exception rather than a silently absent shell.
		return buffer.buildOrThrow();
	}

	/**
	 * The number of boundary faces the extractor will emit for this set.
	 *
	 * <p>A second full extraction pass, run before the encoding one. It costs six bitset lookups
	 * per member and no allocation, and it gives the renderer a face count it can compare against
	 * the encoded vertex count - which is how the v0.0 exit criterion is checked in game rather
	 * than assumed.
	 */
	public static int countBoundaryFaces(DetectionSet set) {
		int[] count = new int[1];
		BoundaryFaceExtractor.extract(set, (dx, dy, dz, face) -> count[0]++);
		return count[0];
	}
}
