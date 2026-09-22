package com.scr0ols.sculksight.mesh;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.jspecify.annotations.Nullable;

import com.scr0ols.sculksight.solver.BoundaryFaceExtractor;
import com.scr0ols.sculksight.solver.DetectionSet;
import com.scr0ols.sculksight.solver.WorldDetectionSet;
import com.scr0ols.sculksight.client.DetectorType;

/** Turns a {@link DetectionSet} into sensor-relative quad geometry, one quad per boundary face. */
public final class ShellMeshBuilder {

	/** The topology the chosen pipeline family declares. */
	public static final PrimitiveTopology TOPOLOGY = PrimitiveTopology.QUADS;

	private static final int VERTICES_PER_FACE = 4;

	private ShellMeshBuilder() {
	}

	/** Builds the shell mesh for the given set into the caller-owned {@code storage}, or null if it has no faces. */
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

				buffer.addVertex(corners[base], corners[base + 1], corners[base + 2])
						.setColor(red, green, blue, alpha);
			}
		});

		return buffer.buildOrThrow();
	}

	/** The number of boundary faces the extractor will emit for this set. */
	public static int countBoundaryFaces(DetectionSet set) {
		int[] count = new int[1];
		BoundaryFaceExtractor.extract(set, (dx, dy, dz, face) -> count[0]++);
		return count[0];
	}

	/** Builds a union mesh, translating world coordinates into the supplied sensor-relative origin. */
	public static @Nullable MeshData build(WorldDetectionSet set, int originX, int originY, int originZ,
			VertexFormat format, ShellStyle style, ByteBufferBuilder storage) {
		int faces = countBoundaryFaces(set);
		if (faces == 0) {
			return null;
		}

		int alpha = style.encodedAlpha();
		float[] corners = new float[ShellQuad.FLOATS];
		Map<DetectorType, ShellStyle> detectorStyles = new EnumMap<>(DetectorType.class);
		for (DetectorType detector : DetectorType.values()) {
			detectorStyles.put(detector, style.withColour(detector.colour()));
		}
		BufferBuilder buffer = new BufferBuilder(storage, TOPOLOGY, format);
		set.extractBoundaryFaces((x, y, z, face, detector) -> {
			ShellQuad.corners(x - originX, y - originY, z - originZ, face, corners);
			ShellStyle detectorStyle = detectorStyles.get(detector);
			for (int corner = 0; corner < VERTICES_PER_FACE; corner++) {
				int base = corner * 3;
				buffer.addVertex(corners[base], corners[base + 1], corners[base + 2])
						.setColor(detectorStyle.red(face), detectorStyle.green(face), detectorStyle.blue(face), alpha);
			}
		});
		return buffer.buildOrThrow();
	}

	public static int countBoundaryFaces(WorldDetectionSet set) {
		int[] count = new int[1];
		set.extractBoundaryFaces((x, y, z, face) -> count[0]++);
		return count[0];
	}
}
