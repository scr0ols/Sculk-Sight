package com.scr0ols.sculksight.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;

/**
 * Owns one uploaded {@link GpuBuffer} of shell vertices. ARCHITECTURE.md section 6.4.
 *
 * <p><b>Every method on this class must run on the render thread</b>, and that is two separate
 * facts rather than one. Creation is render-thread-only on both graphics backends: on OpenGL
 * {@code GlDevice.createBuffer} opens with {@code GlStateManager.clearGlErrors()}, whose first
 * statement is {@code RenderSystem.assertOnRenderThread()}; on Vulkan there is no assertion at all
 * but {@code createCommandEncoder()} returns a shared unsynchronised singleton, so the same call
 * races silently instead of throwing (R13 points 2 and 13). Destruction is the same layer with the
 * same assertion - {@code GlBuffer.close} goes through {@code GlStateManager._glDeleteBuffers}.
 *
 * <p>Because Vulkan hides the mistake that OpenGL throws on, a threading error here is found on
 * OpenGL and not on Vulkan; testing should be done on OpenGL (R13, implications).
 *
 * <p>The upload recipe is R9 point 10's, read from
 * {@code net.minecraft.client.renderer.DebugCrosshairRenderer}.
 */
final class ShellBuffer implements AutoCloseable {

	private final GpuBuffer buffer;

	private final int vertexCount;

	private ShellBuffer(GpuBuffer buffer, int vertexCount) {
		this.buffer = buffer;
		this.vertexCount = vertexCount;
	}

	/**
	 * Render thread. Copies the mesh's bytes into a fresh GPU buffer under the given debug label.
	 *
	 * <p>Does not close the mesh: ownership of the {@link MeshData} stays with the caller, per
	 * ARCHITECTURE.md section 6.3's rule that whoever removed the reference from the slot closes
	 * it. Splitting that responsibility here would give two places a claim on the same native
	 * memory, which is the exact failure the rule exists to prevent.
	 */
	static ShellBuffer upload(String label, MeshData mesh) {
		RenderSystem.assertOnRenderThread();

		GpuBuffer buffer = RenderSystem.getDevice().createBuffer(
				() -> label, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());

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
