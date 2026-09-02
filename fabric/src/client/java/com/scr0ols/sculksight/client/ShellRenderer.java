package com.scr0ols.sculksight.client;

import java.util.Optional;
import java.util.OptionalDouble;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import com.scr0ols.sculksight.SculkSight;
import com.scr0ols.sculksight.mesh.ShellEdgeMeshBuilder;
import com.scr0ols.sculksight.mesh.ShellMeshBuilder;
import com.scr0ols.sculksight.mesh.ShellStyle;
import com.scr0ols.sculksight.solver.DetectionSet;
import com.scr0ols.sculksight.solver.ShellSolution;
import com.scr0ols.sculksight.solver.ShellSolver;

/**
 * The v0.0 renderer: mode A, one shell, for the sensor the player is aiming at.
 *
 * <p>This class is the whole of ARCHITECTURE.md section 7 outside the solver - it resolves the
 * aimed sensor and its radius (step 1), owns the cache entry (step 2), runs the solve and the
 * encode (step 3), offers into the hand-off slot (step 4), consumes it on the render thread and
 * uploads (step 5), and draws every frame (step 6). Steps 7 and 8 - a block change inside the cube,
 * and dropping the entry when the sensor goes away - are section 5's invalidation rules, whose
 * notification channel is R11 and is still unanswered. So v0.0 re-solves when the player presses
 * the key again and makes no claim to notice changes on its own.
 *
 * <p><b>Four draws from two buffers since ADR-028.</b> The faces are drawn twice, see-through then
 * depth-tested (ADR-021), and the crease edges twice more in the same order, so that the black seam
 * composites over the amber fill rather than under it.
 *
 * <p><b>The solve runs on the client thread, not on a worker, and that is a deliberate v0.0
 * narrowing of ARCHITECTURE.md section 6.2 rather than an oversight.</b> Moving it off-thread
 * means reading the client level from a second thread while the main thread mutates it, and this
 * project has no research-log entry establishing that this is safe - vanilla's own chunk mesher
 * copies a region rather than reading the live level, which is evidence against rather than for.
 * Under CONVENTIONS.md section 6 an unbacked claim about Minecraft behaviour may not be written,
 * so the hand-off machinery is built and used exactly as section 6.3 specifies while the producer
 * stays on the client thread. The day that question is answered, the change is which executor
 * {@link #runSolve} is submitted to. See DECISIONS.md ADR-026.
 */
public final class ShellRenderer {

	private static final ShellStyle STYLE = ShellStyle.v0();

	private static final KeyMapping TOGGLE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.sculksight.toggle_shell", InputConstants.KEY_K, KeyMapping.Category.MISC));

	/**
	 * The native storage both mesh encoders write into, owned here rather than by either encoder
	 * (RESEARCH-LOG.md R15.6, DECISIONS.md ADR-017's 2026-09-01 addendum).
	 *
	 * <p>A {@code MeshData}'s {@code close()} does not release the {@code ByteBufferBuilder} that
	 * backs it - only the builder's own {@code close()} does, and doing that while a {@code MeshData}
	 * built from it is still unread throws when that mesh is finally consumed. v0.0 has exactly one
	 * shell in flight at a time, so one long-lived, growable builder - reused across every solve and
	 * compacted by each mesh's own {@code close()} - is safe and mirrors how vanilla's own chunk
	 * mesher holds its {@code ByteBufferBuilder}s in a pool outside any single compile call.
	 *
	 * <p><b>Both meshes of a solve come from this one builder, and that is safe by its own
	 * bookkeeping.</b> {@code build()} stamps each result with the builder's current generation and
	 * increments a result count; a result's {@code close()} decrements that count and only the close
	 * that takes it to zero compacts the builder and moves the generation on. Two outstanding
	 * results therefore coexist, and either may be closed first. A growth between the two builds is
	 * equally harmless: a result stores an offset and resolves the base pointer when its bytes are
	 * read, so a reallocation moves both (R15.6, R15.7).
	 *
	 * <p>65536 bytes covers the radius 8 open-air shell observed on the first live run (1182 faces,
	 * 4728 vertices) with room to spare; the crease edges and a radius 16 shell grow the buffer once,
	 * which is the same cost any first-time growth would be and is not a correctness concern.
	 */
	private static final ByteBufferBuilder MESH_STORAGE = new ByteBufferBuilder(65536);

	private static @Nullable ShellEntry entry;

	/**
	 * The stats belonging to the meshes currently sitting in the slot.
	 *
	 * <p>A field beside the slot rather than inside it, because {@link ShellUploadSlot}'s shape is
	 * fixed by ADR-017 and widening it to carry a payload would change a contract in order to
	 * report a number. This is correct while producer and consumer are the same thread, which is
	 * the v0.0 arrangement ADR-026 records; when the solve moves off-thread this has to travel
	 * through the slot alongside the meshes it describes.
	 */
	private static @Nullable ShellStats pendingStats;

	private ShellRenderer() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ShellRenderer::onEndTick);
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(ShellRenderer::onRender);

		// Both of these drop the entry, and both run on the client thread - which is also the
		// render thread (R13 point 4), and that is what makes it legal to close a GpuBuffer from
		// here at all (ARCHITECTURE.md section 6.4).
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> clear());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			clear();
			// MESH_STORAGE outlives any one entry by design (see its own comment); the game
			// shutting down is the one point where nothing will ever reuse it again.
			MESH_STORAGE.close();
		});
	}

	// ---------------------------------------------------------------- input

	private static void onEndTick(Minecraft client) {
		// consumeClick rather than isDown: this is a toggle, and isDown would fire it on every
		// tick the key is held down.
		while (TOGGLE_KEY.consumeClick()) {
			toggle(client);
		}
	}

	private static void toggle(Minecraft client) {
		if (entry != null) {
			clear();
			say(client, "shell cleared.");
			return;
		}

		ClientLevel level = client.level;

		if (level == null) {
			return;
		}

		if (!(client.hitResult instanceof BlockHitResult blockHit)) {
			say(client, "not aiming at a block.");
			return;
		}

		BlockPos pos = blockHit.getBlockPos();
		BlockEntity blockEntity = level.getBlockEntity(pos);

		// The radius is derived at runtime through vanilla's own idiom, and the idiom is
		// deliberately type-agnostic: GameEventListener.Provider is what EntityBlock#getListener
		// uses, so this resolves a shrieker or a calibrated sensor without naming either type. It
		// also never reads SculkSensorBlockEntity.VibrationUser.LISTENER_RANGE, which is a static
		// 8 that the calibrated sensor inherits while overriding the method to 16 (R1 point 3).
		// This is PLAN.md section 3.2's derive-constants-from-the-game rule applied literally.
		if (!(blockEntity instanceof GameEventListener.Provider<?> provider)) {
			say(client, "the targeted block has no game event listener.");
			return;
		}

		int radius = provider.getListener().getListenerRadius();
		ShellEntry created = new ShellEntry(SensorKey.of(pos), radius);
		entry = created;
		runSolve(client, level, created);
	}

	private static void clear() {
		if (entry != null) {
			entry.close();
			entry = null;
		}

		pendingStats = null;
	}

	// ---------------------------------------------------------------- solve and encode

	/**
	 * ARCHITECTURE.md section 7 steps 3 and 4: solve, extract, encode, offer.
	 *
	 * <p>{@code solveDetailed} rather than {@code solve}, at no extra ray cost, so the shell can
	 * report the same two numbers {@code /sculksight-verify} reports for the same sensor and the
	 * two mechanisms can be compared on one scene. Only {@code accepted()} reaches the meshes.
	 *
	 * <p>Two meshes are built from the one set: the boundary faces (ADR-021) and the crease edges
	 * (ADR-028). If either comes back null the pair is not offered, and the one that was built is
	 * closed here rather than leaked. In practice neither can be null unless the other is, since a
	 * non-empty set has both a surface and corners, but the encoders' contracts allow it and a
	 * half-built shell must not reach the slot.
	 */
	private static void runSolve(Minecraft client, ClientLevel level, ShellEntry target) {
		long revision = target.revision();
		SensorKey sensor = target.sensor();

		ShellSolution solution = ShellSolver.solveDetailed(new LevelWorldView(level),
				sensor.x(), sensor.y(), sensor.z(), target.radius());

		DetectionSet accepted = solution.accepted();
		target.setSet(accepted);

		int faces = ShellMeshBuilder.countBoundaryFaces(accepted);
		int edges = ShellEdgeMeshBuilder.countCreaseEdges(accepted);

		MeshData faceMesh = ShellMeshBuilder.build(accepted, DefaultVertexFormat.POSITION_COLOR, STYLE,
				MESH_STORAGE);
		MeshData edgeMesh = ShellEdgeMeshBuilder.build(accepted,
				DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH, STYLE, MESH_STORAGE);

		if (faceMesh == null || edgeMesh == null) {
			closeQuietly(faceMesh);
			closeQuietly(edgeMesh);
			say(client, "the solver returned an empty set: nothing to draw.");
			return;
		}

		ShellStats stats = new ShellStats(target.radius(), accepted.size(),
				solution.occludedOut().size(), faces, edges);

		pendingStats = stats;

		if (!target.slot().offer(revision, new ShellMeshes(faceMesh, edgeMesh))) {
			// The slot closed the pair; there is nothing further to do. In v0.0 this cannot
			// happen, since one solve runs at a time on one thread, but it is written as real code
			// rather than an assertion because it stops being unreachable the moment the solve
			// moves off-thread.
			pendingStats = null;
			return;
		}

		say(client, "solved " + stats.summary() + ".");
	}

	private static void closeQuietly(@Nullable MeshData mesh) {
		if (mesh != null) {
			mesh.close();
		}
	}

	// ---------------------------------------------------------------- upload and draw

	private static void onRender(LevelRenderContext context) {
		ShellEntry current = entry;

		if (current == null) {
			return;
		}

		consumePending(current);

		ShellBuffer faces = current.faceBuffer();
		ShellBuffer edges = current.edgeBuffer();

		if (faces == null || edges == null) {
			return;
		}

		draw(current, faces, edges, context.levelState().cameraRenderState.pos);
	}

	/** ARCHITECTURE.md section 7 step 5. Render thread. */
	private static void consumePending(ShellEntry current) {
		ShellMeshes meshes = current.slot().take();

		if (meshes == null) {
			return;
		}

		ShellStats stats = pendingStats;
		pendingStats = null;

		// The render thread owns this pair now, so it closes it - after createBuffer has copied
		// the bytes out (ARCHITECTURE.md section 6.3).
		try (meshes) {
			int expectedFaceVertices = stats == null ? -1 : stats.boundaryFaces() * 4;
			int expectedEdgeVertices = stats == null ? -1 : stats.creaseEdges() * ShellEdgeMeshBuilder.VERTICES_PER_EDGE;

			// The second v0.0 exit criterion, checked rather than assumed. Every boundary face is
			// one quad and every quad is four vertices; every crease edge is two authored vertices
			// that the lines topology stores as four (R15.7). An encoder that dropped or
			// duplicated a primitive shows up here as an exact arithmetic mismatch rather than as
			// a picture someone has to notice is wrong. Refusing to draw is the right response
			// under PLAN.md section 1: a shell that does not match the solver is the wrong shape,
			// and drawing the wrong shape is worse than drawing nothing.
			if (!vertexCountAgrees("faces", meshes.faces(), expectedFaceVertices)
					|| !vertexCountAgrees("crease edges", meshes.edges(), expectedEdgeVertices)) {
				return;
			}

			current.setBuffers(
					ShellBuffer.upload("Sculk Sight shell face buffer", meshes.faces()),
					ShellBuffer.upload("Sculk Sight shell edge buffer", meshes.edges()),
					stats);
		}
	}

	private static boolean vertexCountAgrees(String what, MeshData mesh, int expected) {
		int actual = mesh.drawState().vertexCount();

		if (actual == expected) {
			return true;
		}

		SculkSight.LOGGER.error("[sculksight] refusing to draw: encoded {} {} vertices, expected {}.",
				actual, what, expected);

		return false;
	}

	/**
	 * ARCHITECTURE.md section 7 step 6. Render thread, once per frame, touching no vertex data.
	 *
	 * <p>Four draws from two buffers in one render pass, in the order see-through faces,
	 * depth-tested faces, see-through edges, depth-tested edges. The order is load-bearing twice
	 * over. Within each geometry the depth-tested pass goes second because it is the one that
	 * reinforces the half with line of sight to the camera, so it composites on top (ADR-021). And
	 * the edges go after both face passes because the seam is what the player is meant to read
	 * first (ADR-028).
	 */
	private static void draw(ShellEntry current, ShellBuffer faces, ShellBuffer edges, Vec3 camera) {
		SensorKey sensor = current.sensor();

		// ADR-014: the cached vertices are sensor-relative and are never rebuilt because the
		// camera moved. The subtraction is done in double and only then narrowed, so it happens at
		// world precision and the float only ever holds a small offset.
		Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewStack());
		modelView.translate(
				(float) (sensor.x() - camera.x),
				(float) (sensor.y() - camera.y),
				(float) (sensor.z() - camera.z));

		boolean inside = cameraInside(current, camera);
		float edgeFade = STYLE.edges().distanceFade(cameraDistance(sensor, camera));

		// Written as one batch rather than as four calls, because the uniform storage can grow and
		// rebuild its ring buffer mid-frame, which would invalidate a slice handed out before the
		// growth. writeTransforms reserves every block together, so all four slices stay valid.
		//
		// Each mesh carries its own depth-tested alpha and every other value is reached by
		// modulating, since both fragment shaders in play multiply the vertex colour by
		// ColorModulator and ColorModulator is a member of the same DynamicTransforms block all
		// four passes bind (R15.4 for the faces, R15.7 for the lines). Two meshes, four draws.
		GpuBufferSlice[] uniforms = RenderSystem.getDynamicUniforms().writeTransforms(
				transform(modelView, STYLE.faceModulation(true, inside)),
				transform(modelView, STYLE.faceModulation(false, inside)),
				transform(modelView, STYLE.edgeModulation(true, inside, edgeFade)),
				transform(modelView, STYLE.edgeModulation(false, inside, edgeFade)));

		// Target selection copied from net.minecraft.client.renderer.rendertype.PreparedRenderType,
		// which is how every immediate-mode vanilla draw resolves it: the main target, unless
		// something further up the stack has installed an override, as the always-on-top pass does.
		RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		GpuTextureView colorTexture = RenderSystem.outputColorTextureOverride != null
				? RenderSystem.outputColorTextureOverride
				: target.getColorTextureView();
		GpuTextureView depthTexture = target.useDepth
				? (RenderSystem.outputDepthTextureOverride != null
						? RenderSystem.outputDepthTextureOverride
						: target.getDepthTextureView())
				: null;

		// Both index buffers are resolved before the render pass is opened, not inside it. The
		// shared sequential buffer grows on demand, and growing it allocates a GpuBuffer, which is
		// not a thing to do with a pass already open. DebugCrosshairRenderer resolves its own the
		// same way, before its try block (R15.7).
		Indexed faceIndices = Indexed.of(ShellMeshBuilder.TOPOLOGY, faces);
		Indexed edgeIndices = Indexed.of(ShellEdgeMeshBuilder.TOPOLOGY, edges);

		try (RenderPass pass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "Sculk Sight shell", colorTexture, Optional.empty(),
						depthTexture, OptionalDouble.empty())) {

			drawGeometry(pass, faces, faceIndices,
					ShellPipelines.FACES_SEE_THROUGH, uniforms[0],
					ShellPipelines.FACES_DEPTH_TESTED, uniforms[1]);

			drawGeometry(pass, edges, edgeIndices,
					ShellPipelines.EDGES_SEE_THROUGH, uniforms[2],
					ShellPipelines.EDGES_DEPTH_TESTED, uniforms[3]);
		}
	}

	/**
	 * One geometry's index buffer and the count to draw from it, resolved outside the render pass.
	 *
	 * <p>{@code indexCount} is the topology's own arithmetic over the stored vertex count, which is
	 * {@code vertexCount / 4 * 6} for both {@code QUADS} and {@code LINES} (R15.7). The two reach
	 * that shape differently: a quad is four authored vertices, while a line is two that
	 * {@code BufferBuilder} stores as four.
	 */
	private record Indexed(RenderSystem.AutoStorageIndexBuffer indices, GpuBuffer buffer, int count) {

		static Indexed of(PrimitiveTopology topology, ShellBuffer geometry) {
			int count = topology.indexCount(geometry.vertexCount());
			RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(topology);

			return new Indexed(indices, indices.getBuffer(count), count);
		}
	}

	/**
	 * One buffer, bound once and drawn twice with a different pipeline and modulator each time.
	 *
	 * <p>The vertex and index buffers are set once for the pair, since neither pass changes them;
	 * only the pipeline and the uniform differ. This is the shape {@code DebugCrosshairRenderer}
	 * uses for its own two passes over one buffer (R15.7).
	 */
	private static void drawGeometry(RenderPass pass, ShellBuffer buffer, Indexed indexed,
			RenderPipeline firstPipeline, GpuBufferSlice firstUniform,
			RenderPipeline secondPipeline, GpuBufferSlice secondUniform) {

		pass.setVertexBuffer(0, buffer.buffer().slice());
		pass.setIndexBuffer(indexed.buffer(), indexed.indices().type());

		pass.setPipeline(firstPipeline);
		RenderSystem.bindDefaultUniforms(pass);
		pass.setUniform("DynamicTransforms", firstUniform);
		pass.drawIndexed(indexed.count(), 1, 0, 0, 0);

		pass.setPipeline(secondPipeline);
		RenderSystem.bindDefaultUniforms(pass);
		pass.setUniform("DynamicTransforms", secondUniform);
		pass.drawIndexed(indexed.count(), 1, 0, 0, 0);
	}

	/**
	 * Whether the camera is enclosed by the shell. DECISIONS.md ADR-029.
	 *
	 * <p><b>A set membership test, not a distance test, and the difference is the whole point.</b>
	 * The shell is the boundary of the detection set, which occlusion has carved out of the sphere,
	 * so a camera standing in the shadow of a wool wall is inside the sphere and outside the shell.
	 * Comparing distance against the radius would get that case wrong in precisely the scene this
	 * mod exists for. {@code DetectionSet.contains} answers it exactly, in one bitset lookup, and
	 * returns false for anything outside the cube.
	 *
	 * <p>The camera's continuous position is floored to a block, which is the same reduction R12
	 * found vanilla making: both operands of the range check are floored before comparison, so a
	 * camera anywhere within a block is, for this purpose, at that block.
	 */
	private static boolean cameraInside(ShellEntry current, Vec3 camera) {
		DetectionSet set = current.set();

		if (set == null) {
			return false;
		}

		SensorKey sensor = current.sensor();

		return set.contains(
				Mth.floor(camera.x) - sensor.x(),
				Mth.floor(camera.y) - sensor.y(),
				Mth.floor(camera.z) - sensor.z());
	}

	/**
	 * Blocks from the camera to the sensor's own centre. DECISIONS.md ADR-028's 2026-09-02
	 * addendum, which is what consumes it.
	 *
	 * <p>The sensor rather than the nearest point of the shell, and its centre rather than its
	 * corner, because the fade is one value for the whole shell and the shell is centred on the
	 * sensor. A distance to the nearest surface would be a different number per line, which is
	 * exactly the per-line cost the addendum declined to pay.
	 *
	 * <p>The shell's radius does not enter, and its absence is the addendum's second revision
	 * rather than an omission: what fills the screen with line is the line's width in pixels
	 * against the spacing between lines in pixels, and adjacent creases are one block apart at
	 * every radius.
	 *
	 * <p>In double throughout. These are world coordinates, so the subtraction has to happen at
	 * world precision for the same reason the model-view translation above does; unlike that one,
	 * nothing here is ever narrowed to a float.
	 */
	private static double cameraDistance(SensorKey sensor, Vec3 camera) {
		double dx = sensor.x() + 0.5 - camera.x;
		double dy = sensor.y() + 0.5 - camera.y;
		double dz = sensor.z() + 0.5 - camera.z;

		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static DynamicUniforms.Transform transform(Matrix4f modelView, float alphaModulation) {
		// White in RGB so the directional shading the encoder wrote survives untouched, and the
		// modulation lands on alpha alone - the same discipline ADR-022 imposes on the shading
		// itself, applied from the other side.
		return new DynamicUniforms.Transform(modelView,
				new Vector4f(1.0F, 1.0F, 1.0F, alphaModulation),
				new Vector3f(),
				new Matrix4f());
	}

	/**
	 * Reports to the log and to the player's own chat.
	 *
	 * <p>Client-side chat rather than the HUD overlay, because the message that matters carries
	 * numbers to be compared against {@code /sculksight-verify}'s output on the same sensor,
	 * and the overlay fades. {@code addClientSystemMessage} is the local-only entry point -
	 * nothing is sent to a server.
	 */
	private static void say(Minecraft client, String message) {
		SculkSight.LOGGER.info("[sculksight] {}", message);

		if (client.gui != null) {
			client.gui.hud.getChat().addClientSystemMessage(Component.literal("[sculksight] " + message));
		}
	}
}
