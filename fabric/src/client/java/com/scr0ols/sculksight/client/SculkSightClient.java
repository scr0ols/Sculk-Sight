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
import com.scr0ols.sculksight.audit.RadiusAuditCommand;
import com.scr0ols.sculksight.config.ClientConfig;
import com.scr0ols.sculksight.config.ConfigScreens;
import com.scr0ols.sculksight.verify.DetectionVerificationCommand;
import com.scr0ols.sculksight.verify.IndexVerificationCommand;
import com.scr0ols.sculksight.verify.VerificationCommand;

/** Fabric client entrypoint: installs the platform environment and registers keys, events and commands. */
public class SculkSightClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlatform.set(new FabricEnvironment());

		SculkSight.LOGGER.info("Sculk Sight client initialised.");

		ClientConfig.load();

		registerShellRenderer();

		registerConfigScreensKey();

		registerSensorIndex();

		registerDetectionIndicator();

		RadiusAuditCommand.register();

		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			VerificationCommand.register();

			DetectionVerificationCommand.register();

			IndexVerificationCommand.register();

			SculkSight.LOGGER.info("Development environment: /sculksight-verify, "
					+ "/sculksight-verify-detection and /sculksight-verify-index registered "
					+ "(ADR-019).");
		}
	}

	private static void registerShellRenderer() {
		KeyMappingHelper.registerKeyMapping(ShellRenderer.ACTIVATE_KEY);
		KeyMappingHelper.registerKeyMapping(ShellRenderer.TOGGLE_RENDERING_KEY);
		KeyMappingHelper.registerKeyMapping(ShellRenderer.TOGGLE_DELAY_HEATMAP_KEY);

		ClientTickEvents.END_CLIENT_TICK.register(ShellRenderer::onEndTick);

		LevelRenderEvents.BEFORE_GIZMOS.register(
				context -> ShellRenderer.onRenderDelayOverlay(context.levelRenderer(),
						context.levelState().cameraRenderState));

		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(
				context -> ShellRenderer.onRender(context.levelState().cameraRenderState.pos));

		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> ShellRenderer.onLevelChanged());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ShellRenderer.onClientStopping());
	}

	private static void registerConfigScreensKey() {
		KeyMappingHelper.registerKeyMapping(ConfigScreens.OPEN_SETTINGS_KEY);
		ClientTickEvents.END_CLIENT_TICK.register(ConfigScreens::onEndTick);
	}

	private static void registerSensorIndex() {
		ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register(
				(blockEntity, level) -> SensorIndex.onBlockEntityLoad(blockEntity));
		ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register(
				(blockEntity, level) -> SensorIndex.onBlockEntityUnload(blockEntity));
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> SensorIndex.onChunkLoad(chunk));
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> SensorIndex.onChunkUnload(chunk));

		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> SensorIndex.onLevelChanged());
	}

	private static void registerDetectionIndicator() {
		KeyMappingHelper.registerKeyMapping(DetectionIndicator.TOGGLE_KEY);

		ClientTickEvents.END_CLIENT_TICK.register(DetectionIndicator::onEndTick);

		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> DetectionIndicator.onLevelChanged());
	}
}
