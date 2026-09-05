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
 * <p><b>Two draws from one buffer.</b> The faces are drawn see-through and then depth-tested
 * (ADR-021). ADR-028 briefly added two more for a black crease-edge outline; ADR-030 superseded it
 * after the live look, and the shell is fill alone again.
 *
 * <p><b>It times itself, in a development environment or under {@code -Dsculksight.timing=true},
 * and not otherwise.</b> The clocks sit at PLAN.md section 3.3's two budget lines rather than at
 * its tier boundaries: the encode and the upload around the hand-off slot, whose sum is the
 * per-tick budget, and the draw, which is the per-frame one. See {@link TierTiming} for what that
 * placement buys and what the numbers do not cover, and DECISIONS.md ADR-031 for why it is not
 * blocked on the threading question ADR-026 leaves open.
 *
 * <p><b>The solve now runs on a worker thread, not the client thread - DECISIONS.md ADR-046
 * through ADR-048.</b> ARCHITECTURE.md section 6.2's four phases are wired end to end for the
 * first time here. {@link #runSolve} still runs on the client thread, but only for the first
 * phase: taking a snapshot of the sensor's bounding cube (RESEARCH-LOG.md R16 found a worker may
 * not read the live level; the snapshot type is ADR-047's {@link VolumeSnapshot}). Everything
 * after that - the solve, the boundary extraction, the encode and the offer into
 * {@link ShellEntry#slot()} - runs on {@link #WORKER}'s one thread instead, which is what
 * DECISIONS.md ADR-046 built and ADR-048 made safe to actually use. The render thread still does
 * the upload and the draw, on the render callback, exactly as ARCHITECTURE.md section 7 describes.
 *
 * <p><b>The encode's own {@code ByteBufferBuilder} now travels with its mesh through the slot, one
 * per solve, rather than living in one long-lived field here.</b> RESEARCH-LOG.md R19 found that a
 * single builder shared across solves would be written by the worker while the render thread frees
 * results from it - a native-memory race, not merely a stale read. {@link ShellSolveResult} is the
 * pair's shape and DECISIONS.md ADR-048 is the decision.
 *
 * <p><b>Loader-neutral since DECISIONS.md ADR-043's follow-up split.</b> {@link #TOGGLE_KEY} is
 * constructed here but not registered - vanilla's {@code KeyMapping} constructor touches no
 * loader API, only a loader's own key-mapping registry does. {@link #onRender} takes the camera
 * position directly rather than a level-render-event object, because that object's own type is
 * per loader; {@link #onLevelChanged} and {@link #onClientStopping} replace what was one
 * {@code register()} method's worth of Fabric event registration, now done by each loader's own
 * entrypoint instead. Fabric's own registration lives in {@code fabric}'s {@code SculkSightClient}.
 */
public final class ShellRenderer {

	private static final ShellStyle STYLE = ShellStyle.v0();

	/** Constructed, not registered - see the class javadoc. */
	public static final KeyMapping TOGGLE_KEY = new KeyMapping(
			"key.sculksight.toggle_shell", InputConstants.KEY_K, KeyMapping.Category.MISC);

	/**
	 * The initial size of each solve's own {@code ByteBufferBuilder} (DECISIONS.md ADR-048).
	 *
	 * <p>65536 bytes covers the radius 8 open-air shell observed on the first live run (1182 faces,
	 * 4728 vertices) with room to spare; a radius 16 shell grows its builder once, which is the same
	 * cost any first-time growth would be and is not a correctness concern. Every solve gets a fresh
	 * builder now rather than reusing one long-lived instance - see {@link ShellSolveResult}'s
	 * javadoc for why a shared builder stopped being safe the moment the encode left this thread.
	 */
	private static final int INITIAL_STORAGE_BYTES = 65536;

	private static @Nullable ShellEntry entry;

	/**
	 * ARCHITECTURE.md section 6.2's worker executor, DECISIONS.md ADR-046. Constructed here and
	 * shut down from {@link #onClientStopping}. {@link #solveAndEncode} is submitted to it since
	 * DECISIONS.md ADR-048's wiring; {@link #runSolve} itself stays on the client thread, for the
	 * snapshot phase only.
	 */
	private static final ShellWorkerExecutor WORKER = new ShellWorkerExecutor();

	/** Tier 3 samples for as long as the current shell is up. ADR-031. */
	private static final TierTiming.Frames FRAMES = new TierTiming.Frames();

	/**
	 * When the current run of tier 3 samples started reporting, so that the periodic flush is paced
	 * by wall time rather than by a frame count. Zero when no run is in progress.
	 *
	 * <p>The pace has to be wall time because the pass runs with the frame cap off, and a frame
	 * count then means whatever the machine happens to be fast enough to draw (ADR-031's 2026-09-02
	 * second addendum, which is the run that showed it).
	 */
	private static long lastFlushNanos;

	private ShellRenderer() {
	}

	// ---------------------------------------------------------------- input

	/** Called from a loader's own client tick event, once per tick. */
	public static void onEndTick(Minecraft client) {
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
		runSolve(level, created);
	}

	private static void clear() {
		if (entry != null) {
			// entry.close() closes the slot (ARCHITECTURE.md section 6.4), which drains and closes
			// whatever ShellSolveResult is pending - the mesh and its per-solve builder together
			// (DECISIONS.md ADR-048) - rather than leaving that for a worker still in flight to
			// discover on its own next offer.
			entry.close();
			entry = null;
		}

		// The shell going away is the natural end of a tier 3 sample run: the frames it covers are
		// exactly the frames this shell was drawn for (DECISIONS.md ADR-031).
		flushFrames();
		lastFlushNanos = 0L;
	}

	/**
	 * A level change - join, dimension change, or disconnect - drops the cached shell, both of
	 * whose GPU resources are tied to the level that produced them. Called from a loader's own
	 * client-level-change event, which runs on the client thread - also the render thread
	 * (R13 point 4), which is what makes it legal to close a {@code GpuBuffer} from here at all
	 * (ARCHITECTURE.md section 6.4).
	 */
	public static void onLevelChanged() {
		clear();
	}

	/**
	 * Called from a loader's own client-stopping event, which runs on the client thread - see
	 * {@link #onLevelChanged}'s javadoc for why that makes closing GPU resources here legal.
	 */
	public static void onClientStopping() {
		clear();
		// The same point in the sequence Minecraft.close() itself uses for
		// Util.shutdownExecutors() (RESEARCH-LOG.md R18): after the shell's own GPU and native
		// resources are already gone, not before. WORKER.close() waits for a solve already in
		// flight to finish (RESEARCH-LOG.md R18 point 2), and that solve's own offer into a
		// by-then-closed slot is what frees its result rather than leaking it (DECISIONS.md
		// ADR-017, ADR-048).
		WORKER.close();
	}

	// ---------------------------------------------------------------- solve and encode

	/**
	 * ARCHITECTURE.md section 6.2's snapshot phase, then the dispatch that starts the other three.
	 * Client thread - but only this method's own body runs on it now.
	 *
	 * <p><b>The snapshot must be taken here, before anything is handed to the worker.</b>
	 * RESEARCH-LOG.md R16 found a worker may not read the live {@code ClientLevel}, and the copy
	 * is only legal from the thread that is also free to mutate the level (DECISIONS.md ADR-047).
	 * Everything after the copy - the solve, the boundary extraction, the encode and the offer
	 * into the slot - runs on {@link #WORKER}'s one thread instead of here, in
	 * {@link #solveAndEncode}, which is what DECISIONS.md ADR-046 built and ADR-048 made safe to
	 * actually use.
	 */
	private static void runSolve(ClientLevel level, ShellEntry target) {
		long revision = target.revision();
		SensorKey sensor = target.sensor();
		int radius = target.radius();

		VolumeSnapshot snapshot = VolumeSnapshot.of(level, sensor.x(), sensor.y(), sensor.z(), radius);

		WORKER.execute(() -> solveAndEncode(target, revision, sensor, radius, snapshot));
	}

	/**
	 * ARCHITECTURE.md section 6.2's second phase, and the encode with it: solve, extract, encode,
	 * offer. Worker thread, DECISIONS.md ADR-046 and ADR-048.
	 *
	 * <p>{@code solveDetailed} rather than {@code solve}, at no extra ray cost, so the shell can
	 * report the same two numbers {@code /sculksight-verify} reports for the same sensor and the
	 * two mechanisms can be compared on one scene. Only {@code accepted()} reaches the mesh.
	 *
	 * <p>One mesh is built from the set, the boundary faces (ADR-021). ADR-028 built a second for
	 * the crease-edge outline and ADR-030 removed it; the crease geometry is still solved for in
	 * {@code CreaseEdgeExtractor} and still tested, and is not called from here.
	 *
	 * <p><b>Reports nothing to chat itself.</b> DECISIONS.md ADR-048 moves that to
	 * {@link #consumePending}, on the render thread, once the result offered here has actually
	 * been taken and uploaded. Chat and the HUD are not known to be safe to touch from any thread
	 * but the client's own, and CONVENTIONS.md section 6 forbids assuming they are without a
	 * research-log entry; {@link SculkSight#LOGGER} is the one channel already used from this
	 * executor's thread (DECISIONS.md ADR-046 point 4), so it is the only one used here too.
	 */
	private static void solveAndEncode(ShellEntry target, long revision, SensorKey sensor, int radius,
			VolumeSnapshot snapshot) {

		// DECISIONS.md ADR-031 times from here to the end of the encode. This is tier 1 plus
		// everything the producer does before the slot - the half of PLAN.md section 3.3's
		// per-tick budget that now genuinely belongs to a worker rather than to this thread.
		long encodeStart = TierTiming.start();

		ShellSolution solution = ShellSolver.solveDetailed(new LevelWorldView(snapshot),
				sensor.x(), sensor.y(), sensor.z(), radius);

		DetectionSet accepted = solution.accepted();

		int faces = ShellMeshBuilder.countBoundaryFaces(accepted);

		// DECISIONS.md ADR-048: a fresh builder per solve now that the encode runs on a worker.
		// The one long-lived, shared builder this class used to own is retired - RESEARCH-LOG.md
		// R19 found ByteBufferBuilder carries no synchronisation on any field, so a worker writing
		// into a shared builder while the render thread frees results from it would be a
		// native-memory race, not merely a stale read.
		ByteBufferBuilder storage = new ByteBufferBuilder(INITIAL_STORAGE_BYTES);

		MeshData faceMesh = ShellMeshBuilder.build(accepted, DefaultVertexFormat.POSITION_COLOR, STYLE,
				storage);

		long encodeNanos = TierTiming.since(encodeStart);

		// Bookkeeping rather than budgeted work, so it sits outside the timed region above.
		target.setSet(accepted);

		if (faceMesh == null) {
			storage.close();
			SculkSight.LOGGER.info("[sculksight] the solver returned an empty set: nothing to draw.");
			return;
		}

		ShellStats stats = new ShellStats(radius, accepted.size(), solution.occludedOut().size(), faces);

		// If this returns false the slot has already closed the mesh and the builder together
		// (DECISIONS.md ADR-048 point 2); there is nothing further to do. In v0.0 this cannot
		// happen from a newer revision racing this one, since one sensor has one solve in flight
		// at a time, but it is real code rather than an assertion: a world unload racing this
		// solve reaches exactly this path.
		target.slot().offer(revision, new ShellSolveResult(faceMesh, storage, stats, encodeNanos));
	}

	// ---------------------------------------------------------------- upload and draw

	/**
	 * ARCHITECTURE.md section 7 steps 5 and 6. Render thread, once per frame.
	 *
	 * <p>Takes the camera position directly rather than a level-render-event object: that
	 * object's own type - {@code LevelRenderContext} on Fabric - is per loader, and the camera
	 * position, a vanilla {@link Vec3}, is the only thing this method ever read out of it. Each
	 * loader's own registration extracts that position from whatever its own render event hands
	 * it and calls this method with it.
	 */
	public static void onRender(Vec3 cameraPos) {
		ShellEntry current = entry;

		if (current == null) {
			return;
		}

		consumePending(current);

		ShellBuffer faces = current.buffer();

		if (faces == null) {
			return;
		}

		long drawStart = TierTiming.start();

		draw(current, faces, cameraPos);

		if (TimingGate.ENABLED) {
			// One clock read serves as both the end of this sample and the flush check, which is
			// the only place in this class where a second read would be per frame.
			long now = System.nanoTime();
			FRAMES.record(now - drawStart);

			if (lastFlushNanos == 0L) {
				lastFlushNanos = now;
			} else if (now - lastFlushNanos >= TierTiming.FLUSH_INTERVAL_NANOS) {
				flushFrames();
			}
		}
	}

	/** ARCHITECTURE.md section 7 step 5. Render thread. */
	private static void consumePending(ShellEntry current) {
		ShellSolveResult result = current.slot().take();

		if (result == null) {
			return;
		}

		ShellStats stats = result.stats();
		long encodeNanos = result.encodeNanos();

		// The render thread owns this result now, so it closes both the mesh and the per-solve
		// builder that backs it, together - after createBuffer has copied the mesh's bytes out
		// (ARCHITECTURE.md section 6.3, widened to the pair by DECISIONS.md ADR-048).
		try (result) {
			MeshData mesh = result.mesh();
			int expectedVertices = stats.boundaryFaces() * 4;

			// The second v0.0 exit criterion, checked rather than assumed. Every boundary face is
			// one quad and every quad is four vertices. An encoder that dropped or duplicated a
			// face shows up here as an exact arithmetic mismatch rather than as a picture someone
			// has to notice is wrong. Refusing to draw is the right response under PLAN.md
			// section 1: a shell that does not match the solver is the wrong shape, and drawing
			// the wrong shape is worse than drawing nothing.
			if (!vertexCountAgrees("faces", mesh, expectedVertices)) {
				return;
			}

			// The other half of PLAN.md section 3.3's per-tick budget, and the only part of a
			// solve that has to happen here rather than wherever the producer runs (ADR-031).
			long uploadStart = TierTiming.start();
			ShellBuffer uploaded = ShellBuffer.upload(mesh);
			long uploadNanos = TierTiming.since(uploadStart);

			current.setBuffer(uploaded, stats);

			// Moved here from the worker, DECISIONS.md ADR-048: this is the first point after the
			// solve where the client thread - the only thread chat and the HUD are known to be
			// safe to touch from (CONVENTIONS.md section 6) - has its hands on the result.
			say(Minecraft.getInstance(), "solved " + stats.summary() + ".");

			if (TimingGate.ENABLED) {
				say(Minecraft.getInstance(), new ShellTimings(encodeNanos, uploadNanos).summary());
			}
		}
	}

	/**
	 * Prints the tier 3 aggregate and starts a fresh run. DECISIONS.md ADR-031.
	 *
	 * <p>Called when the shell is cleared, so that a run of samples covers exactly the frames one
	 * shell was drawn for, and periodically while it stays up, so that a long look still reports.
	 * Client thread, which is where every one of its samples was taken.
	 */
	private static void flushFrames() {
		if (!TimingGate.ENABLED || FRAMES.isEmpty()) {
			return;
		}

		say(Minecraft.getInstance(), FRAMES.summary());
		FRAMES.reset();
		lastFlushNanos = System.nanoTime();
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
	 * <p>Two draws from one buffer in one render pass, see-through then depth-tested. The order is
	 * load-bearing: the depth-tested pass goes second because it is the one that reinforces the half
	 * with line of sight to the camera, so it composites on top (ADR-021). ADR-028 added two further
	 * draws for the crease-edge outline and ADR-030 removed them.
	 */
	private static void draw(ShellEntry current, ShellBuffer faces, Vec3 camera) {
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

		// Written as one batch rather than as two calls, because the uniform storage can grow and
		// rebuild its ring buffer mid-frame, which would invalidate a slice handed out before the
		// growth. writeTransforms reserves every block together, so both slices stay valid.
		//
		// The mesh carries the depth-tested alpha and the see-through value is reached by
		// modulating, since the fragment shader multiplies the vertex colour by ColorModulator and
		// ColorModulator is a member of the same DynamicTransforms block both passes bind (R15.4).
		GpuBufferSlice[] uniforms = RenderSystem.getDynamicUniforms().writeTransforms(
				transform(modelView, STYLE.faceModulation(true, inside)),
				transform(modelView, STYLE.faceModulation(false, inside)));

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

		try (RenderPass pass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "Sculk Sight shell", colorTexture, Optional.empty(),
						depthTexture, OptionalDouble.empty())) {

			drawGeometry(pass, faces, faceIndices,
					ShellPipelines.FACES_SEE_THROUGH, uniforms[0],
					ShellPipelines.FACES_DEPTH_TESTED, uniforms[1]);
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
	 * Reports to the log, to the player's own chat, and, while the instrument of ADR-031 is on, to
	 * {@link TimingLog}.
	 *
	 * <p>Client-side chat rather than the HUD overlay, because the message that matters carries
	 * numbers to be compared against {@code /sculksight-verify}'s output on the same sensor,
	 * and the overlay fades. {@code addClientSystemMessage} is the local-only entry point -
	 * nothing is sent to a server.
	 */
	private static void say(Minecraft client, String message) {
		SculkSight.LOGGER.info("[sculksight] {}", message);

		// A chat line cannot be copied and latest.log interleaves these with everything else, so
		// while the instrument is on they are mirrored into a file of their own (ADR-031's
		// 2026-09-02 addendum). Off by default, and it costs nothing when off.
		TimingLog.append(message);

		if (client.gui != null) {
			client.gui.hud.getChat().addClientSystemMessage(Component.literal("[sculksight] " + message));
		}
	}
}
