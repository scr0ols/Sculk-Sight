package com.scr0ols.sculksight.mesh;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.jspecify.annotations.Nullable;

import com.scr0ols.sculksight.solver.CreaseEdgeExtractor;
import com.scr0ols.sculksight.solver.DetectionSet;

/**
 * The crease-edge encoder. c-docs/DECISIONS.md ADR-028.
 *
 * <p>The line counterpart of {@link ShellMeshBuilder}: it turns the crease edges of a
 * {@link DetectionSet} into line geometry in the same sensor-relative frame (ADR-014), consumed
 * through {@link CreaseEdgeExtractor}'s sink so that no per-edge object is allocated.
 *
 * <p>A separate class rather than a second method on the face encoder, because almost nothing is
 * shared: a different topology, a different vertex format, two extra per-vertex attributes, and a
 * different rule for how many vertices an emitted primitive costs. The one thing the two do share,
 * the caller-owned {@code ByteBufferBuilder}, is a parameter of both.
 *
 * <p><b>Runs off the render thread</b>, on the same grounds as the face encoder: R13 point 10
 * verified that {@code ByteBufferBuilder}, {@code BufferBuilder} and {@code MeshData} reference
 * nothing in the GPU layer.
 */
public final class ShellEdgeMeshBuilder {

	/**
	 * The topology the {@code LINES_SNIPPET} family declares (R15.7).
	 *
	 * <p>Named here once and used both for the {@code BufferBuilder} and for the shared index
	 * buffer the draw binds, which is the same discipline {@link ShellMeshBuilder#TOPOLOGY} follows
	 * and for the same reason: the encoder and the pipeline must not be able to drift apart.
	 */
	public static final PrimitiveTopology TOPOLOGY = PrimitiveTopology.LINES;

	/**
	 * How many stored vertices one crease edge costs.
	 *
	 * <p><b>Two authored, four stored, and the doubling is not this class doing it.</b>
	 * {@code core/rendertype_lines.vsh} expands each line into a screen-space quad by offsetting
	 * perpendicular to it and negating that offset for odd {@code gl_VertexID}, so both sides need
	 * a vertex. {@code BufferBuilder.endLastVertex()} supplies them: it branches on
	 * {@code primitiveTopology == PrimitiveTopology.LINES} and copies each vertex as it is
	 * finished, incrementing its own count a second time (R15.7). So this encoder calls
	 * {@code addVertex} twice per edge and the built {@code MeshData} reports four, which is what
	 * makes the renderer's vertex-count check read the same way for lines as for quads.
	 */
	public static final int VERTICES_PER_EDGE = 4;

	private ShellEdgeMeshBuilder() {
	}

	/**
	 * Builds the crease-edge mesh for the given set.
	 *
	 * <p>{@code storage} is caller-owned and is never closed here, for the reason R15.6 and
	 * ADR-017's 2026-09-01 addendum give in full on {@link ShellMeshBuilder#build}: a
	 * {@code MeshData} read after the hand-off needs its parent builder still open at that later
	 * point. Two meshes outstanding from one builder at once is what {@code ByteBufferBuilder}'s
	 * own result count is for, and closing either one first is safe: only the close that takes the
	 * count to zero compacts the builder, and by then neither mesh is being read.
	 *
	 * @return the mesh, or {@code null} if the set has no crease edges, which happens only for an
	 *         empty set. Any non-empty set has at least the twelve edges of one cube corner.
	 */
	public static @Nullable MeshData build(DetectionSet set, VertexFormat format, ShellStyle style,
			ByteBufferBuilder storage) {

		int edges = countCreaseEdges(set);

		if (edges == 0) {
			return null;
		}

		EdgeStyle edgeStyle = style.edges();

		int red = edgeStyle.red();
		int green = edgeStyle.green();
		int blue = edgeStyle.blue();
		int alpha = edgeStyle.encodedAlpha();
		float lineWidth = edgeStyle.lineWidth();

		float[] ends = new float[ShellEdge.FLOATS];

		BufferBuilder buffer = new BufferBuilder(storage, TOPOLOGY, format);

		CreaseEdgeExtractor.extract(set, (x, y, z, axis) -> {
			ShellEdge.endpoints(x, y, z, axis, ends);

			// setNormal takes the direction to the other endpoint, not a surface normal, and the
			// same value goes on both ends: the shader reads Position + Normal to find where the
			// line goes and derives its screen direction from the pair (R15.7). The axis step is
			// already a unit vector, which the RGBA8_SNORM attribute requires.
			float directionX = axis.stepX();
			float directionY = axis.stepY();
			float directionZ = axis.stepZ();

			buffer.addVertex(ends[0], ends[1], ends[2])
					.setColor(red, green, blue, alpha)
					.setNormal(directionX, directionY, directionZ)
					.setLineWidth(lineWidth);

			buffer.addVertex(ends[3], ends[4], ends[5])
					.setColor(red, green, blue, alpha)
					.setNormal(directionX, directionY, directionZ)
					.setLineWidth(lineWidth);
		});

		// buildOrThrow rather than build: edges > 0 was established above, so a null here would
		// mean the extractor and the counter disagreed, which is a bug worth an exception rather
		// than a silently absent seam.
		return buffer.buildOrThrow();
	}

	/**
	 * The number of crease edges the extractor will emit for this set.
	 *
	 * <p>A second full sweep, run before the encoding one, for the same reason
	 * {@link ShellMeshBuilder#countBoundaryFaces} exists: it gives the renderer a count it can
	 * compare against the encoded vertex count, so an encoder that dropped or duplicated an edge
	 * shows up as an arithmetic mismatch rather than as a picture someone has to notice is wrong.
	 */
	public static int countCreaseEdges(DetectionSet set) {
		int[] count = new int[1];
		CreaseEdgeExtractor.extract(set, (x, y, z, axis) -> count[0]++);
		return count[0];
	}
}
