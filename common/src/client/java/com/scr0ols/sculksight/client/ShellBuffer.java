package com.scr0ols.sculksight.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;

final class ShellBuffer implements AutoCloseable {

	private final GpuBuffer buffer;

	private final int vertexCount;

	private ShellBuffer(GpuBuffer buffer, int vertexCount) {
		this.buffer = buffer;
		this.vertexCount = vertexCount;
	}

	static ShellBuffer upload(MeshData mesh) {
		RenderSystem.assertOnRenderThread();

		GpuBuffer buffer = RenderSystem.getDevice().createBuffer(
				() -> "Sculk Sight shell vertex buffer", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());

		return new ShellBuffer(buffer, mesh.drawState().vertexCount());
	}

	GpuBuffer buffer() {
		return buffer;
	}

	int vertexCount() {
		return vertexCount;
	}

	@Override
	public void close() {
		RenderSystem.assertOnRenderThread();
		buffer.close();
	}
}
