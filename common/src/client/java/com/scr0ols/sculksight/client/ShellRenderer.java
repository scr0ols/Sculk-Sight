package com.scr0ols.sculksight.client;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
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
import com.scr0ols.sculksight.audit.RadiusAudit;
import com.scr0ols.sculksight.audit.RadiusAudit.AuditedSensor;
import com.scr0ols.sculksight.audit.RadiusAuditClient;
import com.scr0ols.sculksight.audit.RadiusAuditController;
import com.scr0ols.sculksight.audit.RadiusAuditRequest;
import com.scr0ols.sculksight.config.ClientConfig;
import com.scr0ols.sculksight.config.SculkSightConfig;
import com.scr0ols.sculksight.config.TrackedSensor;
import com.scr0ols.sculksight.config.RenderPolicy;
import com.scr0ols.sculksight.mesh.ShellMeshBuilder;
import com.scr0ols.sculksight.mesh.ShellStyle;
import com.scr0ols.sculksight.solver.DetectionSet;
import com.scr0ols.sculksight.solver.ShellSolution;
import com.scr0ols.sculksight.solver.ShellSolver;
import com.scr0ols.sculksight.solver.WorldDetectionSet;

/** Renders detection shells for the selected sculk sensors, solved off-thread and drawn each frame. */
public final class ShellRenderer {

	private static @Nullable ShellStyle style;


	/** The key that activates the shell for the sensor the player is aiming at. */
	public static final KeyMapping ACTIVATE_KEY = new KeyMapping(
			"key.sculksight.activate_sensor", InputConstants.KEY_K, KeyMapping.Category.MISC);

	/** The key that toggles shell rendering globally. */
	public static final KeyMapping TOGGLE_RENDERING_KEY = new KeyMapping(
			"key.sculksight.toggle_rendering", InputConstants.KEY_G, KeyMapping.Category.MISC);

	/** H toggles numeric delay labels for the first enabled tracked sensor. */
	public static final KeyMapping TOGGLE_DELAY_HEATMAP_KEY = new KeyMapping(
			"key.sculksight.toggle_delay_heatmap", InputConstants.KEY_H, KeyMapping.Category.MISC);

	private static final int DELAY_TEXT_COLOUR = 0xFFFFFFFF;
	private static final float DELAY_TEXT_SCALE = 0.32F;
	private static final TextGizmo.Style DELAY_TEXT_STYLE = TextGizmo.Style
			.forColorAndCentered(DELAY_TEXT_COLOUR).withScale(DELAY_TEXT_SCALE);

	private static final int INITIAL_STORAGE_BYTES = 65536;

	private static final Map<SensorKey, ShellEntry> entries = new LinkedHashMap<>();

	private static final int PER_TICK_AUDIT_SOLVE_BUDGET = 4;

	private static final Set<SensorKey> pendingSolve = new LinkedHashSet<>();

	private static @Nullable ShellEntry unionEntry;

	private static boolean renderingEnabled = true;

	private static long solveGeneration;

	private static boolean delayHeatmap;

	private static final ShellWorkerExecutor WORKER = new ShellWorkerExecutor();

	private static final TierTiming.Frames FRAMES = new TierTiming.Frames();

	private static final DrawLoopTiming DRAW_LOOP = new DrawLoopTiming();

	private static long lastFlushNanos;

	private static boolean unionNeedsRebuild;

	private ShellRenderer() {
	}

	// ---------------------------------------------------------------- input

	/** Called from a loader's own client tick event, once per tick. */
	public static void onEndTick(Minecraft client) {
		while (ACTIVATE_KEY.consumeClick()) {
			activate(client);
		}

		while (TOGGLE_RENDERING_KEY.consumeClick()) {
			toggleRendering(client);
		}

		while (TOGGLE_DELAY_HEATMAP_KEY.consumeClick()) {
			toggleDelayHeatmap(client);
		}

		syncEntries(client);
	}

	/** Toggles the numeric delay overlay on or off. */
	public static void toggleDelayHeatmap(Minecraft client) {
		if (entries.isEmpty() && unionEntry == null) {
			say(client, "select a shell first.");
			return;
		}

		delayHeatmap = !delayHeatmap;
		say(client, delayHeatmap ? "delay overlay on." : "delay overlay off.");
	}

	/** Whether {@link #TOGGLE_DELAY_HEATMAP_KEY} (or the settings screen's button) is currently on. */
	public static boolean isDelayHeatmapEnabled() {
		return delayHeatmap;
	}

	/** Toggles all shell rendering on or off. */
	public static void toggleRendering(Minecraft client) {
		renderingEnabled = !renderingEnabled;
		if (!renderingEnabled) {
			clearRenderCaches();
		}
		say(client, renderingEnabled ? "sensor rendering on." : "sensor rendering off.");
	}

	/** Whether {@link #TOGGLE_RENDERING_KEY} (or the settings screen's button) is currently on. */
	public static boolean isRenderingEnabled() {
		return renderingEnabled;
	}

	private static void activate(Minecraft client) {
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

		if (!(blockEntity instanceof GameEventListener.Provider<?> provider)) {
			say(client, "the targeted block has no game event listener.");
			return;
		}

		Optional<DetectorType> detector = DetectorType.of(level.getBlockState(pos).getBlock());
		if (detector.isEmpty()) {
			say(client, "the targeted block is not a detector.");
			return;
		}

		SculkSightConfig current = ClientConfig.get();
		TrackedSensor selected = TrackedSensor.selected(pos.getX(), pos.getY(), pos.getZ());
		SculkSightConfig updated = current.track(selected);
		if (updated == current) {
			if (current.trackedSensors().size() >= SculkSightConfig.MAX_TRACKED_SENSORS) {
				say(client, "the tracked sensor limit is " + SculkSightConfig.MAX_TRACKED_SENSORS + ".");
			} else {
				say(client, "sensor already tracked.");
			}
			return;
		}
		ClientConfig.set(updated);
		say(client, "sensor tracked.");
		clearRenderCaches();
		syncEntries(client);
	}

	private static void clearRenderCaches() {
		delayHeatmap = false;
		for (ShellEntry entry : entries.values()) {
			entry.close();
		}
		entries.clear();
		pendingSolve.clear();
		unionNeedsRebuild = false;
		if (unionEntry != null) {
			unionEntry.close();
			unionEntry = null;
		}

		flushFrames();
		lastFlushNanos = 0L;
	}

	private static ShellStyle style() {
		ShellStyle current = style;

		if (current == null) {
			current = ShellStyle.fromConfig(ClientConfig.get());
			style = current;
		}

		return current;
	}

	private static ShellStyle style(DetectorType detector) {
		return style().withColour(detector.colour());
	}

	/** Drops the cached style and shells after the player saves new settings. */
	public static void onConfigChanged() {
		style = null;
		clearRenderCaches();
	}

	/** Invalidates the cached meshes after a radius-audit rerun. */
	public static void onRadiusAuditRerun() {
		clearRenderCaches();
	}

	/** Drops the cached shells when the client level changes. */
	public static void onLevelChanged() {
		clearRenderCaches();
		RadiusAuditController.clear();
	}

	/** Releases the cached shells and shuts down the solve worker. */
	public static void onClientStopping() {
		clearRenderCaches();
		WORKER.close();
	}

	// ---------------------------------------------------------------- selection, solve and encode

	private record PendingSolve(ShellEntry target, SensorKey sensor, int radius,
			VolumeSnapshot snapshot, long snapshotNanos, ShellStyle shellStyle) {
	}

	private record CachedContribution(DetectionSet set, int occludedOut, SensorKey sensor, DetectorType detector) {
	}

	private static void syncEntries(Minecraft client) {
		if (!renderingEnabled || client.level == null) {
			return;
		}

		Map<SensorKey, ShellEntry> desired = desiredTrackedEntries(client.level);
		mergeAuditedEntries(client, desired);

		reconcileEntries(desired);

		if (!pendingSolve.isEmpty() || unionNeedsRebuild) {
			dispatchBudgetedSolves(client.level);
		}
	}

	private static Map<SensorKey, ShellEntry> desiredTrackedEntries(ClientLevel level) {
		Map<SensorKey, ShellEntry> desired = new LinkedHashMap<>();
		for (TrackedSensor tracked : ClientConfig.get().trackedSensors()) {
			if (!tracked.enabled()) {
				continue;
			}
			BlockPos pos = new BlockPos(tracked.x(), tracked.y(), tracked.z());
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (!(blockEntity instanceof GameEventListener.Provider<?> provider)) {
				continue;
			}
			Optional<DetectorType> detector = DetectorType.of(level.getBlockState(pos).getBlock());
			if (detector.isEmpty()) {
				continue;
			}
			SensorKey key = SensorKey.of(pos);
			desired.put(key, new ShellEntry(key, provider.getListener().getListenerRadius(), detector.orElseThrow()));
		}
		return desired;
	}

	private static void mergeAuditedEntries(Minecraft client, Map<SensorKey, ShellEntry> desired) {
		RadiusAuditRequest request = RadiusAuditController.activeRequest();
		LocalPlayer player = client.player;
		if (request == null || player == null) {
			return;
		}

		Set<SensorKey> explicitlyHidden = explicitlyDisabledTrackedKeys();
		BlockPos centre = player.blockPosition();
		List<AuditedSensor> candidates = RadiusAuditClient.candidatesFrom(client.level);
		RadiusAudit.CappedSelection selection = RadiusAudit.selectWithCap(
				centre.getX(), centre.getY(), centre.getZ(), request, candidates,
				ClientConfig.get().radiusAuditCap());

		RadiusAuditController.publishSelection(selection.selected());

		for (AuditedSensor sensor : selection.selected()) {
			if (explicitlyHidden.contains(sensor.position())
					|| RadiusAuditController.isHidden(sensor.position())) {
				continue;
			}
			desired.putIfAbsent(sensor.position(),
					new ShellEntry(sensor.position(), sensor.listenerRadius(), sensor.type()));
		}
	}

	private static Set<SensorKey> explicitlyDisabledTrackedKeys() {
		Set<SensorKey> hidden = new LinkedHashSet<>();
		for (TrackedSensor tracked : ClientConfig.get().trackedSensors()) {
			if (!tracked.enabled()) {
				hidden.add(new SensorKey(tracked.x(), tracked.y(), tracked.z()));
			}
		}
		return hidden;
	}

	private static void reconcileEntries(Map<SensorKey, ShellEntry> desired) {
		for (Iterator<Map.Entry<SensorKey, ShellEntry>> iterator = entries.entrySet().iterator();
				iterator.hasNext();) {
			Map.Entry<SensorKey, ShellEntry> current = iterator.next();
			if (!desired.containsKey(current.getKey())) {
				current.getValue().close();
				iterator.remove();
				unionNeedsRebuild = true;
			}
		}

		for (Map.Entry<SensorKey, ShellEntry> wanted : desired.entrySet()) {
			ShellEntry current = entries.get(wanted.getKey());
			ShellEntry spec = wanted.getValue();
			if (current != null && current.radius() == spec.radius() && current.detector() == spec.detector()) {
				continue;
			}
			if (current != null) {
				current.close();
			}
			entries.put(wanted.getKey(), spec);
			pendingSolve.add(wanted.getKey());
		}

		if (entries.isEmpty()) {
			if (unionEntry != null) {
				unionEntry.close();
				unionEntry = null;
			}
			pendingSolve.clear();
			unionNeedsRebuild = false;
			flushFrames();
			lastFlushNanos = 0L;
		} else if (unionEntry == null) {
			unionEntry = new ShellEntry(entries.values().iterator().next().sensor(), 0,
					DetectorType.NORMAL_SENSOR);
		}
	}

	private static void dispatchBudgetedSolves(ClientLevel level) {
		List<ShellEntry> toSolve = new ArrayList<>();
		Iterator<SensorKey> queued = pendingSolve.iterator();
		while (queued.hasNext() && toSolve.size() < PER_TICK_AUDIT_SOLVE_BUDGET) {
			SensorKey key = queued.next();
			queued.remove();
			ShellEntry entry = entries.get(key);
			if (entry != null) {
				toSolve.add(entry);
			}
		}

		if (toSolve.isEmpty() && !unionNeedsRebuild) {
			return;
		}

		unionNeedsRebuild = false;

		List<ShellEntry> alreadySolved = new ArrayList<>();
		for (ShellEntry entry : entries.values()) {
			if (!toSolve.contains(entry)) {
				alreadySolved.add(entry);
			}
		}

		runSolves(level, toSolve, alreadySolved);
	}

	private static void runSolves(ClientLevel level, List<ShellEntry> toSolve, List<ShellEntry> alreadySolved) {
		long generation = ++solveGeneration;
		List<PendingSolve> pending = new ArrayList<>();
		for (ShellEntry target : toSolve) {
			SensorKey sensor = target.sensor();
			long snapshotStart = TierTiming.start();
			VolumeSnapshot snapshot = VolumeSnapshot.of(level, sensor.x(), sensor.y(), sensor.z(), target.radius());
			pending.add(new PendingSolve(target, sensor, target.radius(), snapshot,
					TierTiming.since(snapshotStart), style(target.detector())));
		}

		List<CachedContribution> cached = new ArrayList<>();
		for (ShellEntry entry : alreadySolved) {
			DetectionSet set = entry.set();
			if (set != null) {
				cached.add(new CachedContribution(set, entry.occludedOut(), entry.sensor(), entry.detector()));
			}
		}

		ShellStyle unionStyle = style(DetectorType.NORMAL_SENSOR);
		ShellEntry unionTarget = unionEntry;
		RenderPolicy policy = ClientConfig.get().renderPolicy();
		WORKER.execute(() -> solveAndEncode(generation, pending, policy, unionTarget, unionStyle, cached));
	}

	private static void solveAndEncode(long generation, List<PendingSolve> pending,
			RenderPolicy policy, ShellEntry unionTarget, ShellStyle style, List<CachedContribution> cached) {
		long encodeStart = TierTiming.start();
		WorldDetectionSet union = new WorldDetectionSet();
		List<ShellSolution> solutions = new ArrayList<>();
		for (PendingSolve solve : pending) {
			ShellSolution solution = ShellSolver.solveDetailed(new LevelWorldView(solve.snapshot()),
					solve.sensor().x(), solve.sensor().y(), solve.sensor().z(), solve.radius());
			solutions.add(solution);
			solve.target().setSolution(solution);
			union.add(solution.accepted(), solve.sensor().x(), solve.sensor().y(), solve.sensor().z(),
					solve.target().detector());
		}
		for (CachedContribution contribution : cached) {
			union.add(contribution.set(), contribution.sensor().x(), contribution.sensor().y(),
					contribution.sensor().z(), contribution.detector());
		}

		long encodeNanos = TierTiming.since(encodeStart);
		if (policy == RenderPolicy.UNION) {
			if (unionTarget == null || (pending.isEmpty() && cached.isEmpty())) {
				return;
			}
			ShellEntry target = unionTarget;
			SensorKey origin = target.sensor();
			target.setWorldSolution(union);
			ByteBufferBuilder storage = new ByteBufferBuilder(INITIAL_STORAGE_BYTES);
			MeshData mesh = ShellMeshBuilder.build(union, origin.x(), origin.y(), origin.z(),
					DefaultVertexFormat.POSITION_COLOR, style, storage);
			if (mesh == null) {
				storage.close();
				return;
			}
			int faces = ShellMeshBuilder.countBoundaryFaces(union);
			int occluded = solutions.stream().mapToInt(solution -> solution.occludedOut().size()).sum()
					+ cached.stream().mapToInt(CachedContribution::occludedOut).sum();
			target.slot().offer(generation, new ShellSolveResult(mesh, storage,
					new ShellStats(0, union.size(), occluded, faces), 0L, encodeNanos));
			return;
		}

		for (PendingSolve solve : pending) {
			ShellSolution solution = solutions.get(pending.indexOf(solve));
			ByteBufferBuilder storage = new ByteBufferBuilder(INITIAL_STORAGE_BYTES);
			MeshData mesh = ShellMeshBuilder.build(solution.accepted(), DefaultVertexFormat.POSITION_COLOR,
					solve.shellStyle(), storage);
			if (mesh == null) {
				storage.close();
				continue;
			}
			int faces = ShellMeshBuilder.countBoundaryFaces(solution.accepted());
			solve.target().slot().offer(generation, new ShellSolveResult(mesh, storage,
					new ShellStats(solve.radius(), solution.accepted().size(), solution.occludedOut().size(), faces),
					solve.snapshotNanos(), encodeNanos));
		}
	}

	// ---------------------------------------------------------------- upload and draw

	/** Uploads pending meshes and draws the shells; render thread, once per frame. */
	public static void onRender(Vec3 cameraPos) {
		if (!SensorRenderState.shouldRender(renderingEnabled, true)) {
			return;
		}

		if (fillIsOff()) {
			return;
		}

		RenderPolicy policy = ClientConfig.get().renderPolicy();
		List<ShellEntry> toDraw = policy == RenderPolicy.UNION
				? (unionEntry == null ? List.of() : List.of(unionEntry)) : new ArrayList<>(entries.values());

		if (policy != RenderPolicy.UNION) {
			toDraw.sort(Comparator.comparing(ShellEntry::sensor,
					DrawOrder.backToFront(cameraPos.x, cameraPos.y, cameraPos.z)));
		}

		long loopStart = TierTiming.start();
		for (ShellEntry current : toDraw) {
			consumePending(current);
			ShellBuffer faces = current.buffer();
			if (faces == null) {
				continue;
			}
			long drawStart = TierTiming.start();
			draw(current, faces, cameraPos);
			if (TimingGate.ENABLED) {
				FRAMES.record(TierTiming.since(drawStart));
			}
		}

		if (TimingGate.ENABLED && !toDraw.isEmpty()) {
			DRAW_LOOP.record(toDraw.size(), TierTiming.since(loopStart));

			long now = System.nanoTime();
			if (lastFlushNanos == 0L) {
				lastFlushNanos = now;
			} else if (now - lastFlushNanos >= TierTiming.FLUSH_INTERVAL_NANOS) {
				flushFrames();
			}
		}
	}

	/** Emits the numeric delay labels into vanilla's per-frame gizmo collector. */
	public static void onRenderDelayOverlay(LevelRenderer levelRenderer, CameraRenderState camera) {
		if (!delayHeatmap || entries.isEmpty() || levelRenderer == null || camera == null || !camera.initialized) {
			return;
		}

		ShellEntry first = entries.values().iterator().next();
		DelayOverlay overlay = first.delayOverlay();

		if (overlay == null) {
			return;
		}

		Frustum frustum = camera.cullFrustum;

		try (Gizmos.TemporaryCollection ignored = levelRenderer.collectPerFrameRenderThreadGizmos()) {
			for (int index = 0; index < overlay.size(); index++) {
				if (overlay.isSensorOccluded(index)) {
					continue;
				}

				Vec3 anchor = overlay.anchor(index);

				if (frustum != null && !frustum.pointInFrustum(anchor.x, anchor.y, anchor.z)) {
					continue;
				}

				Gizmos.billboardText(overlay.text(index), anchor, DELAY_TEXT_STYLE);
			}
		}
	}

	private static boolean fillIsOff() {
		return style().encodedAlpha() <= 0;
	}

	private static void consumePending(ShellEntry current) {
		ShellSolveResult result = current.slot().take();

		if (result == null) {
			return;
		}

		ShellStats stats = result.stats();
		long encodeNanos = result.encodeNanos();

		try (result) {
			MeshData mesh = result.mesh();
			int expectedVertices = stats.boundaryFaces() * 4;

			if (!vertexCountAgrees("faces", mesh, expectedVertices)) {
				return;
			}

			long uploadStart = TierTiming.start();
			ShellBuffer uploaded = ShellBuffer.upload(mesh);
			long uploadNanos = TierTiming.since(uploadStart);

			current.setBuffer(uploaded, stats);

			say(Minecraft.getInstance(), "solved " + stats.summary() + ".");

			if (TimingGate.ENABLED) {
				say(Minecraft.getInstance(),
						new ShellTimings(result.snapshotNanos(), encodeNanos, uploadNanos).summary());
			}
		}
	}

	private static void flushFrames() {
		if (!TimingGate.ENABLED || (FRAMES.isEmpty() && DRAW_LOOP.isEmpty())) {
			return;
		}

		if (!FRAMES.isEmpty()) {
			say(Minecraft.getInstance(), FRAMES.summary());
			FRAMES.reset();
		}

		if (!DRAW_LOOP.isEmpty()) {
			say(Minecraft.getInstance(), DRAW_LOOP.summary());
			DRAW_LOOP.reset();
		}

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

	private static void draw(ShellEntry current, ShellBuffer faces, Vec3 camera) {
		SensorKey sensor = current.sensor();

		Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewStack());
		modelView.translate(
				(float) (sensor.x() - camera.x),
				(float) (sensor.y() - camera.y),
				(float) (sensor.z() - camera.z));

		boolean inside = cameraInside(current, camera);

		ShellStyle detectorStyle = style(current.detector());
		GpuBufferSlice[] uniforms = RenderSystem.getDynamicUniforms().writeTransforms(
				transform(modelView, detectorStyle.faceModulation(true, inside)),
				transform(modelView, detectorStyle.faceModulation(false, inside)));

		RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		GpuTextureView colorTexture = RenderSystem.outputColorTextureOverride != null
				? RenderSystem.outputColorTextureOverride
				: target.getColorTextureView();
		GpuTextureView depthTexture = target.useDepth
				? (RenderSystem.outputDepthTextureOverride != null
						? RenderSystem.outputDepthTextureOverride
						: target.getDepthTextureView())
				: null;

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

	private record Indexed(RenderSystem.AutoStorageIndexBuffer indices, GpuBuffer buffer, int count) {

		static Indexed of(PrimitiveTopology topology, ShellBuffer geometry) {
			int count = topology.indexCount(geometry.vertexCount());
			RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(topology);

			return new Indexed(indices, indices.getBuffer(count), count);
		}
	}

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

	private static boolean cameraInside(ShellEntry current, Vec3 camera) {
		WorldDetectionSet worldSet = current.worldSet();
		if (worldSet != null) {
			return worldSet.contains(Mth.floor(camera.x), Mth.floor(camera.y), Mth.floor(camera.z));
		}
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
		return new DynamicUniforms.Transform(modelView,
				new Vector4f(1.0F, 1.0F, 1.0F, alphaModulation),
				new Vector3f(),
				new Matrix4f());
	}

	private static void say(Minecraft client, String message) {
		SculkSight.LOGGER.info("[sculksight] {}", message);

		TimingLog.append(message);

		if (client.gui != null) {
			client.gui.hud.getChat().addClientSystemMessage(Component.literal("[sculksight] " + message));
		}
	}
}
