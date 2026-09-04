package com.scr0ols.sculksight.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;

import com.scr0ols.sculksight.SculkSight;
import com.scr0ols.sculksight.verify.DetectionVerificationCommand;
import com.scr0ols.sculksight.verify.IndexVerificationCommand;
import com.scr0ols.sculksight.verify.VerificationCommand;

/**
 * Client entrypoint.
 *
 * <p>Phase v0.0 (PLAN.md section 5.1) has a single objective - prove the solver is correct - and
 * two exit criteria that between them need two mechanisms. {@link ShellRenderer} draws the shell
 * and is registered unconditionally, because it is the mod. The differential verification command
 * is registered only in a development environment, because it reaches server-side state and
 * ADR-019 permits that nowhere else.
 *
 * <p>A throwaway diagnostic command was briefly registered here to empirically confirm R8
 * (RESEARCH-LOG.md). It has been removed now that R8 is closed; its source is archived at
 * debug-tests/R08-DebugProbeCommand.java for the record.
 *
 * <p>A second throwaway diagnostic command was briefly registered here to empirically measure
 * R10 point 7's {@code B}. It has been removed now that B is measured in every required scene
 * (RESEARCH-LOG.md R10 point 11); its source is archived at
 * debug-tests/R10-BlockEntityCountProbe.java for the record.
 *
 * <p><b>The Fabric registration hub, since DECISIONS.md ADR-043's follow-up split.</b>
 * {@link ShellRenderer}, {@link SensorIndex} and {@link DetectionIndicator} moved to
 * {@code common} as loader-independent logic exposing public static handler methods; nothing in
 * {@code common} may call Fabric's event registries or key-mapping registry (ADR-018), so every
 * {@code .register(...)} call that used to live inside each of those classes' own {@code register()}
 * method now lives here instead, one per line, each simply forwarding to the handler it wires up.
 */
public class SculkSightClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Installed before anything else touches this package: TimingGate and TimingLog
		// (common/client) read it lazily on first use, well after this point, but nothing may run
		// before it that could touch either - see ClientPlatform's own javadoc for the ordering
		// this relies on.
		ClientPlatform.set(new FabricEnvironment());

		SculkSight.LOGGER.info("Sculk Sight client initialised.");

		registerShellRenderer();

		// The sensor index (ADR-038) must register before any ClientLevel exists, which
		// onInitializeClient always runs before - see SensorIndex's own class comment for why
		// that ordering is what lets it skip an explicit sweep at world join.
		registerSensorIndex();

		// Mode C (PLAN.md section 3.4, ADR-039): a toggle key and a per-tick check against the
		// sensor index above, independent of ShellRenderer's mode A shell.
		registerDetectionIndicator();

		// DECISIONS.md ADR-019 permits the verification mechanism to reach server-side state
		// only in a development environment, and requires that the dev-only status be real
		// rather than intended. This is that gate: in a built jar the branch is not taken, so
		// nothing in com.scr0ols.sculksight.verify is ever reachable from a shipped mod.
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			VerificationCommand.register();

			// Mode C's own gate: TESTING-STRATEGY.md section 4's v0.1 criterion names both modes,
			// and mode A's command asks a different piece of code. Same mechanism below the
			// prediction, same ADR-019 concession, a separate command for the reason
			// DetectionVerificationCommand's own javadoc gives.
			DetectionVerificationCommand.register();

			// Mode C's index, checked against an independent sweep rather than against this mod's
			// own geometry: DECISIONS.md ADR-041, closing OPEN-QUESTIONS.md section 21. Same
			// ADR-019 concession, same reason - see the command's own javadoc for why it is a
			// third mechanism rather than an extension of either command above it.
			IndexVerificationCommand.register();

			SculkSight.LOGGER.info("Development environment: /sculksight-verify, "
					+ "/sculksight-verify-detection and /sculksight-verify-index registered "
					+ "(ADR-019).");
		}
	}

	/**
	 * Everything {@code ShellRenderer.register()} did on Fabric before ADR-043's follow-up split,
	 * now here instead: a key-mapping registration and four event registrations, each a one-line
	 * forward to the handler {@code ShellRenderer} exposes.
	 */
	private static void registerShellRenderer() {
		KeyMappingHelper.registerKeyMapping(ShellRenderer.TOGGLE_KEY);

		ClientTickEvents.END_CLIENT_TICK.register(ShellRenderer::onEndTick);

		// LevelRenderContext is a Fabric-only type; ShellRenderer.onRender takes the vanilla
		// camera position it carries, not the context itself, so it needs no Fabric import at all.
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(
				context -> ShellRenderer.onRender(context.levelState().cameraRenderState.pos));

		// Both of these drop the entry, and both run on the client thread - which is also the
		// render thread (R13 point 4), and that is what makes it legal to close a GpuBuffer from
		// here at all (ARCHITECTURE.md section 6.4).
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> ShellRenderer.onLevelChanged());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ShellRenderer.onClientStopping());
	}

	/** Everything {@code SensorIndex.register()} did on Fabric before the split, now here instead. */
	private static void registerSensorIndex() {
		ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register(
				(blockEntity, level) -> SensorIndex.onBlockEntityLoad(blockEntity));
		ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register(
				(blockEntity, level) -> SensorIndex.onBlockEntityUnload(blockEntity));
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> SensorIndex.onChunkLoad(chunk));
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> SensorIndex.onChunkUnload(chunk));

		// A dimension change clears the whole index, the same rule ARCHITECTURE.md section 5
		// rule 5 states for the shell cache and the same reason SensorKey gives for leaving
		// dimension out of its own key: a stale entry naming a position in the wrong dimension
		// is worse than an empty index that repopulates from the callbacks above.
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> SensorIndex.onLevelChanged());
	}

	/** Everything {@code DetectionIndicator.register()} did on Fabric before the split, now here instead. */
	private static void registerDetectionIndicator() {
		KeyMappingHelper.registerKeyMapping(DetectionIndicator.TOGGLE_KEY);

		ClientTickEvents.END_CLIENT_TICK.register(DetectionIndicator::onEndTick);

		// A level change - join, dimension change, or disconnect - makes the last reading stale
		// regardless of whether the indicator stays on, the same reasoning ShellRenderer applies
		// to its own cached shell. The toggle state itself is left alone: it is a standing player
		// preference, not tied to any one sensor the way mode A's aimed shell is.
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> DetectionIndicator.onLevelChanged());
	}
}
