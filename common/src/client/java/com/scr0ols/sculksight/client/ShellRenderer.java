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

/**
 * The bounded multi-sensor renderer: selected sensors are solved independently, then either
 * merged into one world-coordinate union or drawn as separate detector-coloured shells.
 *
 * <p>This class is the whole of ARCHITECTURE.md section 7 outside the solver - it resolves the
 * aimed sensor and its radius (step 1), owns the cache entry (step 2), runs the solve and the
 * encode (step 3), offers into the hand-off slot (step 4), consumes it on the render thread and
 * uploads (step 5), and draws every frame (step 6). Step 7 - a block change inside the cube - is
 * section 5's invalidation rule, whose notification channel is R11 and is still unanswered: an
 * already-tracked sensor's shell is only refreshed by {@link #onConfigChanged}, which a settings
 * screen save triggers regardless of what changed, not by anything that notices the change itself.
 * Step 8, dropping the entry when the sensor goes away, is answered by polling instead: {@link
 * #syncEntries} runs every tick and drops an entry whose block is no longer a detector.
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
 * first time here. {@link #runSolves} still runs on the client thread, but only for the first
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
 * <p><b>Mode B feeds the same {@link #entries} cache, ARCHITECTURE.md section 12.3.</b>
 * {@link #syncEntries} now reconciles two sources - {@code ClientConfig.trackedSensors()} and, if
 * one is active, {@link RadiusAuditController}'s selection - against the same per-sensor cache,
 * keeping an unchanged entry's buffer untouched rather than rebuilding the whole set on any
 * change (ARCHITECTURE.md section 12.3). {@link #dispatchBudgetedSolves} then bounds how many of
 * those new or changed entries a single tick may snapshot and dispatch (section 12.4's per-tick
 * budget), so a full recompute at mode B's scale (20+ sensors) spends that client-thread cost
 * over several ticks instead of stalling one; every other current entry keeps drawing what it
 * already has and still folds into a rebuilt union unchanged.
 *
 * <p><b>{@link #onRender}'s {@code PER_SENSOR} draw list is sorted back-to-front by camera
 * distance, ARCHITECTURE.md section 12.4 sub-problem 3.</b> {@link #entries} is a {@code
 * LinkedHashMap} in selection order, which has nothing to do with the camera, and overlapping
 * translucent shells composite in draw order (ADR-022's two low-alpha tiers are not
 * order-independent). {@link DrawOrder#backToFront} sorts farthest first so the nearest shell
 * draws last and composites on top, re-evaluated every frame since the camera moves. {@code
 * RenderPolicy.UNION} draws a single merged buffer and is untouched by this - there is nothing to
 * order among one buffer. The same section's sub-problem 1 (per-sensor draw cost, additive across
 * that loop) is what {@link DrawLoopTiming} measures, flushed alongside the existing tier 3
 * samples whenever {@link TimingGate#ENABLED}.
 *
 * <p><b>Loader-neutral since DECISIONS.md ADR-043's follow-up split.</b> {@link #ACTIVATE_KEY} is
 * constructed here but not registered - vanilla's {@code KeyMapping} constructor touches no
 * loader API, only a loader's own key-mapping registry does. {@link #onRender} takes the camera
 * position directly rather than a level-render-event object, because that object's own type is
 * per loader; {@link #onLevelChanged} and {@link #onClientStopping} replace what was one
 * {@code register()} method's worth of Fabric event registration, now done by each loader's own
 * entrypoint instead. Fabric's own registration lives in {@code fabric}'s {@code SculkSightClient}.
 */
public final class ShellRenderer {

	/**
	 * The shell's appearance, rebuilt from the player's settings the first time it is needed after
	 * they change.
	 *
	 * <p>Was {@code ShellStyle.v0()} in a final field until the v0.1 config screen (PLAN.md section
	 * 4). Held lazily rather than initialised eagerly so that this class carries no ordering
	 * requirement against {@code ClientConfig.load()}: the first read happens on a keypress at the
	 * earliest, long after either loader's entrypoint has run.
	 *
	 * <p><b>This field belongs to the client thread alone, and that is what makes a plain field
	 * enough.</b> It is written by {@link #onConfigChanged} and read by {@link #style()}, whose
	 * only callers are {@link #runSolves}, {@link #fillIsOff} and {@link #draw} - the client thread
	 * and the render thread, which RESEARCH-LOG.md R13 point 4 establishes are one thread. The
	 * {@link #fillIsOff} caller arrived with OPEN-QUESTIONS.md section 23 and does not weaken the
	 * argument, being on the render thread like the two in {@link #draw} beside it. The worker never
	 * touches it; it is handed the captured instance as a parameter instead. Until 2026-09-07 it
	 * did touch it, reading this field from {@link #solveAndEncode} with no happens-before edge
	 * against the write, so a save could leave a worker encoding at the old alpha - the outcome
	 * DECISIONS.md ADR-058 exists to prevent. OPEN-QUESTIONS.md section 22.1 is the finding.
	 */
	private static @Nullable ShellStyle style;


	/** Constructed, not registered - see the class javadoc. */
	public static final KeyMapping ACTIVATE_KEY = new KeyMapping(
			"key.sculksight.activate_sensor", InputConstants.KEY_K, KeyMapping.Category.MISC);

	/** A separate global switch; per-sensor enabled flags remain untouched when this is pressed. */
	public static final KeyMapping TOGGLE_RENDERING_KEY = new KeyMapping(
			"key.sculksight.toggle_rendering", InputConstants.KEY_G, KeyMapping.Category.MISC);

	/** H toggles numeric delay labels for the first enabled tracked sensor. */
	public static final KeyMapping TOGGLE_DELAY_HEATMAP_KEY = new KeyMapping(
			"key.sculksight.toggle_delay_heatmap", InputConstants.KEY_H, KeyMapping.Category.MISC);

	private static final int DELAY_TEXT_COLOUR = 0xFFFFFFFF;
	private static final float DELAY_TEXT_SCALE = 0.32F;
	private static final TextGizmo.Style DELAY_TEXT_STYLE = TextGizmo.Style
			.forColorAndCentered(DELAY_TEXT_COLOUR).withScale(DELAY_TEXT_SCALE);

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

	private static final Map<SensorKey, ShellEntry> entries = new LinkedHashMap<>();

	/**
	 * Section 12.4's per-tick budget, applied to whichever entries are new or changed this tick -
	 * tracked-sensor activations and mode B's audited selection both go through this same queue.
	 * Only the client-thread snapshot phase of {@link #runSolves} is what this bounds per tick
	 * (ARCHITECTURE.md section 6.2's fourth phase); an entry already solved keeps drawing its
	 * existing buffer and is never re-queued just because something else changed (section 12.3's
	 * per-sensor cache).
	 *
	 * <p>Not yet measured against a live scene - {@code TESTING-STRATEGY.md} section 4's profiling
	 * pass is where this constant should be revisited, the same way {@code ADR-036} treats the
	 * tier budgets themselves.
	 */
	private static final int PER_TICK_AUDIT_SOLVE_BUDGET = 4;

	/**
	 * Keys in {@link #entries} whose current {@link ShellEntry} has never been solved (or just
	 * replaced one that was), waiting for a future tick's share of {@link #PER_TICK_AUDIT_SOLVE_BUDGET}.
	 * A {@link LinkedHashSet} rather than a queue so a key changing twice before its turn comes up
	 * is only ever solved once, for whichever entry is current when that happens.
	 */
	private static final Set<SensorKey> pendingSolve = new LinkedHashSet<>();

	private static @Nullable ShellEntry unionEntry;

	private static boolean renderingEnabled = true;

	private static long solveGeneration;

	private static boolean delayHeatmap;

	/**
	 * ARCHITECTURE.md section 6.2's worker executor, DECISIONS.md ADR-046. Constructed here and
	 * shut down from {@link #onClientStopping}. {@link #solveAndEncode} is submitted to it since
	 * DECISIONS.md ADR-048's wiring; {@link #runSolves} itself stays on the client thread, for the
	 * snapshot phase only.
	 */
	private static final ShellWorkerExecutor WORKER = new ShellWorkerExecutor();

	/** Tier 3 samples for as long as the current shell is up. ADR-031. */
	private static final TierTiming.Frames FRAMES = new TierTiming.Frames();

	/**
	 * Section 12.4 sub-problem 1's per-frame draw-loop samples: the whole {@code PER_SENSOR} loop
	 * in {@link #onRender} (upload plus draw, summed across every currently-drawn entry), not just
	 * a single draw call. See {@link DrawLoopTiming}'s own javadoc for why this is kept as ongoing
	 * profiling rather than torn out before this task's PR merges.
	 */
	private static final DrawLoopTiming DRAW_LOOP = new DrawLoopTiming();

	/**
	 * When the current run of tier 3 samples started reporting, so that the periodic flush is paced
	 * by wall time rather than by a frame count. Zero when no run is in progress.
	 *
	 * <p>The pace has to be wall time because the pass runs with the frame cap off, and a frame
	 * count then means whatever the machine happens to be fast enough to draw (ADR-031's 2026-09-02
	 * second addendum, which is the run that showed it).
	 */
	private static long lastFlushNanos;

	/**
	 * Set when {@link #reconcileEntries} drops an entry, so the shared union mesh is re-encoded
	 * without that entry's contribution even though no <em>new</em> entry needs solving.
	 *
	 * <p><b>Without this the union would keep drawing a sensor that is no longer selected.</b>
	 * {@link #dispatchBudgetedSolves} is the only thing that ever re-encodes the union, and it used
	 * to run only when {@link #pendingSolve} was non-empty - which a pure removal never makes it.
	 * That gap was invisible for as long as every per-sensor settings change called
	 * {@link #onConfigChanged} and cleared the whole cache, because a wholesale rebuild always
	 * re-encoded the union on the way back. Dropping that clear (it re-solved every selected sensor
	 * over several ticks for a change that only ever removed one, which is the flicker the author
	 * reported on 2026-09-17) is what makes this flag load-bearing rather than redundant.
	 *
	 * <p>Only {@link RenderPolicy#UNION} actually reads a shared mesh, but the flag is set
	 * regardless of policy: a dispatch with nothing to solve costs one worker hand-off that the
	 * per-sensor branch of {@link #solveAndEncode} iterates zero times, and not tracking the policy
	 * here keeps the invalidation rule independent of a setting the player can change between the
	 * removal and the dispatch.
	 */
	private static boolean unionNeedsRebuild;

	private ShellRenderer() {
	}

	// ---------------------------------------------------------------- input

	/** Called from a loader's own client tick event, once per tick. */
	public static void onEndTick(Minecraft client) {
		// consumeClick rather than isDown: this is a toggle, and isDown would fire it on every
		// tick the key is held down.
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

	/**
	 * Also called directly by the settings screen's own "Delay overlay" button ({@link
	 * com.scr0ols.sculksight.config.SettingsScreen}), so a player has both the {@link
	 * #TOGGLE_DELAY_HEATMAP_KEY} keybind and a menu control for the same toggle - identical
	 * behaviour either way, since the button calls straight through to this method rather than
	 * reimplementing it.
	 */
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

	/** Also called directly by the settings screen's own "Global render" button - see {@link #toggleDelayHeatmap}. */
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

		// The radius is derived at runtime through vanilla's own idiom, and the idiom is deliberately
		// type-agnostic: it resolves a shrieker or calibrated sensor without copying game constants.
		if (!(blockEntity instanceof GameEventListener.Provider<?> provider)) {
			say(client, "the targeted block has no game event listener.");
			return;
		}

		// Some GameEventListener.Provider block entities (the sculk catalyst) are not detectors;
		// DetectorType.of's own doc explains why. This second gate is what keeps a shell from
		// being drawn around one.
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

		// The shell going away is the natural end of a tier 3 sample run: the frames it covers are
		// exactly the frames this shell was drawn for (DECISIONS.md ADR-031).
		flushFrames();
		lastFlushNanos = 0L;
	}

	/**
	 * The current style, built from the player's settings on first use and kept until they change.
	 *
	 * <p>One instance serves both the encode and the draw, and that is load-bearing rather than
	 * incidental: {@code ShellStyle.faceModulation} reaches every alpha but the encoded one by
	 * dividing by the alpha the mesh was built at, so a mesh encoded under one style and drawn
	 * under another would be modulated against the wrong denominator. {@link #onConfigChanged()}
	 * is what keeps that from happening - it drops the cached shell along with the style.
	 *
	 * <p><b>Client thread only</b>, which is also the render thread (R13 point 4): the callers are
	 * {@link #runSolves}, {@link #fillIsOff} and {@link #draw}. The encode does not call this -
	 * {@link #runSolves} captures the instance here and passes it to {@link #solveAndEncode} as a parameter, the way
	 * the snapshot already travels under R16 and DECISIONS.md ADR-048. That is what makes "one
	 * instance serves both the encode and the draw" a property of the code rather than a sentence
	 * asking the reader to trust it, and it is the fix OPEN-QUESTIONS.md section 22.1 named.
	 */
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

	/**
	 * The player saved new settings: forget the style built from the old ones, and drop the cached
	 * shell that was encoded at the old alpha.
	 *
	 * <p>Called from the config screen, which runs on the client thread - also the render thread
	 * (R13 point 4), which is what makes closing this entry's GPU resources legal here, exactly as
	 * in {@link #onLevelChanged()}.
	 *
	 * <p><b>Only by the two controls that actually invalidate an encoded mesh</b>, since
	 * 2026-09-17: the opacity slider (the mesh carries the encoded alpha, which is this method's
	 * whole subject) and the render-policy button (union and per-sensor meshes are encoded against
	 * different origins and cannot be reinterpreted as each other). The per-sensor controls -
	 * rename, the enabled toggle, remove - used to call it too, and that was the flicker the author
	 * reported: each click dropped every selected sensor's buffer, and {@link
	 * #dispatchBudgetedSolves} then re-solved the whole selection at
	 * {@link #PER_TICK_AUDIT_SOLVE_BUDGET} sensors per tick, so a 27-sensor audit visibly rebuilt
	 * itself over seven ticks in answer to a change that removed one shell. None of those three
	 * changes what a mesh was encoded at, and {@link #syncEntries} already reconciles the selection
	 * they do change on the very next tick, per-sensor cache intact ({@link #reconcileEntries}).
	 *
	 * <p><b>Dropping rather than re-modulating is the deliberate choice</b>, and dropping is all
	 * this method does: the shell disappears on save and comes back on the player's next keypress.
	 * Nothing is re-solved here. The alternative - keeping the mesh and changing only the uniform -
	 * would need the encoded alpha tracked separately from the target one, for a saving on an
	 * action a player takes seconds apart at most; dropping the mesh instead makes "a mesh drawn
	 * under a style it was not encoded at" unrepresentable rather than merely avoided.
	 *
	 * <p>This paragraph used to say "re-solving rather than re-modulating" and cite
	 * NEXT-STEPS-ARCHIVE.md Step 29's sub-millisecond solve as what made a re-solve affordable,
	 * describing work the method has never performed. Review found it on 2026-09-07
	 * (OPEN-QUESTIONS.md section 22.4); the visible behaviour was reconsidered at the same time and
	 * kept, so the comment moved rather than the code. DECISIONS.md ADR-058 carries both halves.
	 */
	public static void onConfigChanged() {
		style = null;
		clearRenderCaches();
	}

	/**
	 * Invalidates meshes after a successful radius-audit command rerun. The audit reads the live
	 * client level, while an existing shell is an encoded snapshot; retaining it when the sensor
	 * identity and radius are unchanged would show the previous occlusion geometry after a wall was
	 * placed or removed. The next client tick reconciles the same selection and dispatches fresh
	 * solves. Called on the client thread by {@link RadiusAuditClient}.
	 */
	public static void onRadiusAuditRerun() {
		clearRenderCaches();
	}

	/**
	 * A level change - join, dimension change, or disconnect - drops the cached shell, both of
	 * whose GPU resources are tied to the level that produced them. Called from a loader's own
	 * client-level-change event, which runs on the client thread - also the render thread
	 * (R13 point 4), which is what makes it legal to close a {@code GpuBuffer} from here at all
	 * (ARCHITECTURE.md section 6.4).
	 */
	public static void onLevelChanged() {
		clearRenderCaches();
		RadiusAuditController.clear();
	}

	/**
	 * Called from a loader's own client-stopping event, which runs on the client thread - see
	 * {@link #onLevelChanged}'s javadoc for why that makes closing GPU resources here legal.
	 */
	public static void onClientStopping() {
		clearRenderCaches();
		// The same point in the sequence Minecraft.close() itself uses for
		// Util.shutdownExecutors() (RESEARCH-LOG.md R18): after the shell's own GPU and native
		// resources are already gone, not before. WORKER.close() waits for a solve already in
		// flight to finish (RESEARCH-LOG.md R18 point 2), and that solve's own offer into a
		// by-then-closed slot is what frees its result rather than leaking it (DECISIONS.md
		// ADR-017, ADR-048).
		WORKER.close();
	}

	// ---------------------------------------------------------------- selection, solve and encode

	private record PendingSolve(ShellEntry target, SensorKey sensor, int radius,
			VolumeSnapshot snapshot, long snapshotNanos, ShellStyle shellStyle) {
	}

	/**
	 * One already-solved entry's contribution to a rebuilt union, captured on the client thread
	 * (ARCHITECTURE.md section 12.3's per-sensor cache) so {@link #solveAndEncode} does not have to
	 * re-solve an entry just because some other entry changed. {@code set} is read from
	 * {@link ShellEntry#set()}, which is {@code volatile} precisely so a thread other than the one
	 * that solved it - here, the worker rebuilding the union - may read it safely.
	 */
	private record CachedContribution(DetectionSet set, int occludedOut, SensorKey sensor, DetectorType detector) {
	}

	/**
	 * Reconciles both entry sources - the tracked-sensor list and, if one is active, mode B's
	 * radius audit (ARCHITECTURE.md section 12.3) - against live detector block entities, then
	 * dispatches whatever share of this tick's budget the result leaves pending.
	 */
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

	/** The player-curated half of {@code desired}: {@code ClientConfig.trackedSensors()}, live-checked. */
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

	/**
	 * Mode B's half: if {@link RadiusAuditController} has an active request, re-runs it against
	 * the player's current position and adds its selection to {@code desired} - ARCHITECTURE.md
	 * section 12.3's seam, "the audit's result is a set of entries like any other". A key the
	 * tracked list already claimed wins the collision, since it is the more deliberate of the two.
	 *
	 * <p><b>A position the player explicitly disabled in the tracked-sensor menu stays hidden</b>,
	 * even though {@link #desiredTrackedEntries} already left it out of {@code desired} (a
	 * disabled entry is simply skipped there, which only says "the tracked list is not asking for
	 * this one" - it says nothing about the audit). Without this check, disabling a sensor that an
	 * active audit also selects only removed it for as long as it took the very same tick's audit
	 * half to add it straight back, which read as the toggle flashing off and immediately on again.
	 *
	 * <p><b>So does a position the player hid in the settings screen's own audit section</b>, which
	 * is the far commoner case and the one the check above does not reach. An audited position is
	 * not in the tracked list at all, so it has no {@code enabled} flag to clear - hiding it is
	 * {@link RadiusAuditController#setHidden}'s session-only set instead, and this is where that set
	 * takes effect. Until it existed there was no way at all to switch off one render out of an
	 * audit's selection: the tracked list's own toggles could only reach the handful of positions
	 * the player had separately pressed K on, which on a 27-sensor audit was every render but the
	 * ones on screen (the 2026-09-17 report, whose logs show `skippedAsHidden=0` against five
	 * disabled tracked sensors - the two sets were disjoint).
	 *
	 * <p><b>Publishes the selection before filtering it.</b> The settings screen lists what the
	 * audit picked, hidden entries included, because a hidden entry still needs a row to carry the
	 * control that brings it back - see {@link RadiusAuditController#publishSelection}.
	 */
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

	/** Positions the tracked-sensor list itself holds with {@code enabled() == false}. */
	private static Set<SensorKey> explicitlyDisabledTrackedKeys() {
		Set<SensorKey> hidden = new LinkedHashSet<>();
		for (TrackedSensor tracked : ClientConfig.get().trackedSensors()) {
			if (!tracked.enabled()) {
				hidden.add(new SensorKey(tracked.x(), tracked.y(), tracked.z()));
			}
		}
		return hidden;
	}

	/**
	 * Diffs {@code desired} against {@link #entries} in place. An entry whose radius and detector
	 * are unchanged is left exactly as it is - untouched buffer, untouched cache, no re-queue -
	 * which is the whole of ARCHITECTURE.md section 12.3's per-sensor cache. Only new or changed
	 * keys join {@link #pendingSolve}; keys no longer desired are closed and dropped, per
	 * ARCHITECTURE.md section 5's rule 2 - which also marks the shared union stale, since a removal
	 * changes what the union should contain without putting anything on {@link #pendingSolve}
	 * ({@link #unionNeedsRebuild}).
	 */
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
			// Nothing left to encode a union from, and no union entry to encode into: the removal
			// that set this has already been answered by dropping the entry outright.
			unionNeedsRebuild = false;
			flushFrames();
			lastFlushNanos = 0L;
		} else if (unionEntry == null) {
			unionEntry = new ShellEntry(entries.values().iterator().next().sensor(), 0,
					DetectorType.NORMAL_SENSOR);
		}
	}

	/**
	 * Section 12.4's per-tick budget: takes at most {@link #PER_TICK_AUDIT_SOLVE_BUDGET} keys off
	 * {@link #pendingSolve} and dispatches only those, so a full recompute at mode B's scale (20+
	 * sensors) spends the client-thread snapshot cost of a handful of entries per tick rather than
	 * all of them in the one tick the selection changed. Every other current entry - already
	 * solved or still queued for a later tick - is left alone and, if already solved, still folds
	 * into a rebuilt union through {@link CachedContribution}, at no re-solve cost.
	 *
	 * <p><b>Also runs with nothing to solve at all</b>, when {@link #unionNeedsRebuild} says an
	 * entry was dropped: {@code toSolve} is then empty, every surviving entry arrives as a
	 * {@link CachedContribution}, and {@link #solveAndEncode} re-encodes the union from those alone
	 * - no snapshot, no solve, no ray cast. That is the cheap half of what the removed
	 * {@link #onConfigChanged} call used to achieve by re-solving everything.
	 */
	private static void dispatchBudgetedSolves(ClientLevel level) {
		List<ShellEntry> toSolve = new ArrayList<>();
		Iterator<SensorKey> queued = pendingSolve.iterator();
		while (queued.hasNext() && toSolve.size() < PER_TICK_AUDIT_SOLVE_BUDGET) {
			SensorKey key = queued.next();
			queued.remove();
			ShellEntry entry = entries.get(key);
			// A key can outlive its turn in the queue if the entry it named was replaced or
			// dropped by a later reconcile before this tick's budget reached it.
			if (entry != null) {
				toSolve.add(entry);
			}
		}

		if (toSolve.isEmpty() && !unionNeedsRebuild) {
			return;
		}

		// Cleared whether or not this dispatch is the one that was queued for the rebuild: either
		// way the union about to be encoded is built from the current entry set.
		unionNeedsRebuild = false;

		List<ShellEntry> alreadySolved = new ArrayList<>();
		for (ShellEntry entry : entries.values()) {
			if (!toSolve.contains(entry)) {
				alreadySolved.add(entry);
			}
		}

		runSolves(level, toSolve, alreadySolved);
	}

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
	 *
	 * <p><b>The style is captured here for the same reason and by the same rule</b>, though for a
	 * narrower one than the snapshot's: {@link #style} is not a live game object, it is a field the
	 * client thread writes on a settings save (ADR-058), and reading it from the worker was a data
	 * race with no happens-before edge - OPEN-QUESTIONS.md section 22.1. Capturing it beside the
	 * snapshot means the instance the worker encodes at is the instance that was current when the
	 * solve was dispatched, by construction rather than by timing.
	 *
	 * <p><b>{@code toSolve} is only this tick's budgeted share</b> ({@link #dispatchBudgetedSolves}),
	 * not necessarily every current entry, so {@link #solveGeneration} is bumped here rather than
	 * on a full clear - each dispatch needs its own strictly-increasing revision or
	 * {@link ShellUploadSlot#offer} would reject every batch after the first as stale
	 * (ShellUploadSlot's own javadoc). {@code alreadySolved} carries every other current entry
	 * so a union rebuild reflects the whole selection, not only what this call is solving; entries
	 * that have never been solved yet (still queued) are simply absent from the union until their
	 * own turn comes.
	 */
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
	 *
	 * <p><b>Everything this method reads arrives as a parameter, including the style.</b> It is a
	 * {@code static} method on a class whose mutable statics belong to the client thread, so the
	 * parameter list is the whole of what this thread is allowed to see. {@code style} shadows
	 * {@link #style} deliberately: the field is not reachable from this body by name, which is
	 * OPEN-QUESTIONS.md section 22.1's fix made structural rather than remembered.
	 *
	 * <p><b>{@code cached} is this tick's other entries, folded into the union unchanged.</b> Each
	 * one already carries a solved {@link DetectionSet} from a previous dispatch
	 * (ARCHITECTURE.md section 12.3's per-sensor cache); merging its bitset back in costs one
	 * iteration over its own accepted positions, not a re-solve.
	 *
	 * @param style the union appearance captured on the client thread in {@link #runSolves}, and the same
	 *        instance the draw will modulate against - never re-read from the field here
	 */
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
			// The mesh's vertex origin must be target.sensor() and nothing else: draw() always
			// translates by target.sensor() (fixed for the union entry's whole lifetime, set once
			// in reconcileEntries), so encoding relative to any other point - such as whichever
			// entry happened to be first in this particular incremental batch - drew the shell
			// offset by the difference between the two the moment a later, budgeted dispatch's
			// first entry differed from the first entry overall. That mismatch is what the author
			// saw as the union rendering centred on the player rather than on the sensors.
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
			// ARCHITECTURE.md section 12.4 sub-problem 3: overlapping translucent shells composite
			// in draw order (ADR-022's two low-alpha tiers are not order-independent), and entries
			// is a LinkedHashMap in selection order, not draw order. Sorting back-to-front by
			// squared distance from the camera - farthest first, nearest last - is the standard
			// technique for correct alpha compositing of overlapping translucent geometry: the
			// nearest shell draws last and composites on top of everything behind it. Re-sorted
			// every frame since the camera moves. UNION draws a single merged buffer, so ordering
			// does not apply there, and sorting an immutable single-element List.of() would throw.
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

		// ARCHITECTURE.md section 12.4 sub-problem 1: the per-sensor draw cost is additive across
		// this loop (consumePending's upload plus draw, per entry), and nothing before this task
		// measured the whole loop at mode B's scale - only a single draw call, and only ever for
		// one sensor. DRAW_LOOP's summary, flushed alongside FRAMES's, is what lets the author read
		// back real numbers against the v0.3 exit criterion at a live N of 20+; see DrawLoopTiming's
		// own javadoc for why this instrument is kept rather than removed before merge.
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

	/**
	 * Emits the numeric overlay into vanilla's per-frame gizmo collector.
	 *
	 * <p>This is called from the loader's gizmo-adjacent render hook. The normal gizmo path is
	 * intentionally used: it billboards the glyphs and depth-tests them against the terrain, so the
	 * player's view is the visibility rule without a second set of CPU raycasts. The collection
	 * scope is opened here as well so the method is safe on loaders whose hook is adjacent to, rather
	 * than nested inside, vanilla's own collector scope.
	 */
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
				// Occluded cells render no label at all rather than a distinct colour, per the
				// captain's ruling that a wool-tagged occluder should read as unreachable instead of
				// merely differently coloured. This is the same architectural gap documented on
				// ShellRendererStyleCaptureTest: the class only compiles under fabric/neoforge, which
				// have no test source sets, so this blank-on-occlusion behaviour is covered by the
				// surrounding unit/build checks and this note, not by a render or visual test.
				if (overlay.isSensorOccluded(index)) {
					continue;
				}

				Vec3 anchor = overlay.anchor(index);

				// A point frustum test avoids constructing an AABB for every label. Labels outside the
				// current view cannot contribute fragments, while all in-view labels remain uncapped.
				if (frustum != null && !frustum.pointInFrustum(anchor.x, anchor.y, anchor.z)) {
					continue;
				}

				Gizmos.billboardText(overlay.text(index), anchor, DELAY_TEXT_STYLE);
			}
		}
	}

	/**
	 * True when the player has turned the fill off, in which case there is nothing to draw and
	 * {@link #draw} must not be entered. OPEN-QUESTIONS.md section 23, decided as DECISIONS.md
	 * ADR-022's 2026-09-07 addendum.
	 *
	 * <p><b>Zero is a permitted setting and this is what it means.</b>
	 * {@code SculkSightConfig.MIN_SHELL_OPACITY_PERCENT} is 0, and its own javadoc promises that a
	 * player may turn the fill off and keep the mod loaded. At that setting
	 * {@code ShellStyle.encodedAlpha()} is 0, and {@code faceModulation} - which {@link #draw}
	 * calls twice per frame - divides by it: {@code ShellStyle.modulation} throws
	 * {@code IllegalStateException} for exactly that case. Without this method a player who moved
	 * the slider to zero and pressed the toggle key got an exception every frame instead of an
	 * absent fill.
	 *
	 * <p><b>The guard in {@code modulation} is deliberately left as it is.</b> It is right about
	 * its own arithmetic - ADR-022's modulation scheme (ARCHITECTURE.md section 4.3) reaches every
	 * alpha but the encoded one by dividing by it, and no factor turns an encoded zero into a
	 * visible anything. The fix is that the guard is no longer reached, not that it is weakened;
	 * an encoded zero arriving at {@code faceModulation} would still be a defect and should still
	 * throw.
	 *
	 * <p><b>Why the encoded alpha rather than the percentage.</b> The encoded alpha is the exact
	 * quantity the guard divides by, so testing it here cannot drift from what is tested there
	 * through a rounding step in between. The two agree in any case:
	 * {@code Alphas.toChannel} rounds {@code percent / 100} times 255, which is 0 at 0 and 3 at 1,
	 * and {@code ShellStyleTest} pins that boundary.
	 *
	 * <p>Client thread, which is also the render thread (R13 point 4) - the same condition every
	 * other {@link #style()} caller sits under.
	 */
	private static boolean fillIsOff() {
		return style().encodedAlpha() <= 0;
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

			// The second of the two phases PLAN.md section 3.3's per-tick budget is actually about,
			// the snapshot in runSolve being the first: both are on the client thread (the render
			// thread and the client thread being one, RESEARCH-LOG.md R13 point 4), and the upload is
			// the only part of a solve that has to happen here rather than wherever the producer runs.
			long uploadStart = TierTiming.start();
			ShellBuffer uploaded = ShellBuffer.upload(mesh);
			long uploadNanos = TierTiming.since(uploadStart);

			current.setBuffer(uploaded, stats);

			// Moved here from the worker, DECISIONS.md ADR-048: this is the first point after the
			// solve where the client thread - the only thread chat and the HUD are known to be
			// safe to touch from (CONVENTIONS.md section 6) - has its hands on the result.
			say(Minecraft.getInstance(), "solved " + stats.summary() + ".");

			if (TimingGate.ENABLED) {
				say(Minecraft.getInstance(),
						new ShellTimings(result.snapshotNanos(), encodeNanos, uploadNanos).summary());
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

	/**
	 * ARCHITECTURE.md section 7 step 6. Render thread, once per frame, touching no vertex data.
	 *
	 * <p>Two draws from one buffer in one render pass, see-through then depth-tested. The order is
	 * load-bearing: the depth-tested pass goes second because it is the one that reinforces the half
	 * with line of sight to the camera, so it composites on top (ADR-021). ADR-028 added two further
	 * draws for the crease-edge outline and ADR-030 removed them.
	 *
	 * <p><b>Never entered while the fill is off.</b> The two {@code faceModulation} calls below
	 * divide by the encoded alpha and throw when it is zero, which is a permitted slider position;
	 * {@link #fillIsOff} is the guard {@link #onRender} applies before reaching here, and its
	 * javadoc carries the argument. OPEN-QUESTIONS.md section 23.
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
		ShellStyle detectorStyle = style(current.detector());
		GpuBufferSlice[] uniforms = RenderSystem.getDynamicUniforms().writeTransforms(
				transform(modelView, detectorStyle.faceModulation(true, inside)),
				transform(modelView, detectorStyle.faceModulation(false, inside)));

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
