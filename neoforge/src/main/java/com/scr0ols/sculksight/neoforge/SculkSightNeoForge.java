package com.scr0ols.sculksight.neoforge;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import com.scr0ols.sculksight.SculkSight;
import com.scr0ols.sculksight.audit.RadiusAuditCommand;
import com.scr0ols.sculksight.audit.RadiusAuditController;
import com.scr0ols.sculksight.audit.RadiusAuditRequest;
import com.scr0ols.sculksight.client.ClientPlatform;
import com.scr0ols.sculksight.client.DetectionIndicator;
import com.scr0ols.sculksight.client.SensorIndex;
import com.scr0ols.sculksight.client.ShellRenderer;
import com.scr0ols.sculksight.config.ClientConfig;
import com.scr0ols.sculksight.config.ConfigScreens;
import com.scr0ols.sculksight.verify.DetectionVerificationCommand;
import com.scr0ols.sculksight.verify.IndexSweep;
import com.scr0ols.sculksight.verify.IndexVerificationCommand;
import com.scr0ols.sculksight.verify.VerificationCommand;
import com.scr0ols.sculksight.verify.WorldPosition;

/** The NeoForge entrypoint and registration hub, wiring this mod's client features to NeoForge's event API. */
@Mod(value = SculkSight.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = SculkSight.MOD_ID, value = Dist.CLIENT)
public final class SculkSightNeoForge {

	private static final int SENSOR_INDEX_RESYNC_INTERVAL_TICKS = 20;

	private static final int SENSOR_INDEX_RESYNC_RADIUS_CHUNKS = 2;

	private static final int BLOCKS_PER_CHUNK = 16;

	private static int sensorIndexResyncCountdown;

	/** Creates the NeoForge client entrypoint and registers the config screen factory. */
	public SculkSightNeoForge(ModContainer container) {
		ClientPlatform.set(new NeoForgeEnvironment());

		ClientConfig.load();

		IConfigScreenFactory screens = (ignored, modListScreen) -> ConfigScreens.create(modListScreen);

		container.registerExtensionPoint(IConfigScreenFactory.class, screens);

		SculkSight.LOGGER.info("Sculk Sight (NeoForge) client initialised.");
	}

	// ---------------------------------------------------------------- mode A: ShellRenderer

	@SubscribeEvent
	static void registerShellRendererKey(RegisterKeyMappingsEvent event) {
		event.register(ShellRenderer.ACTIVATE_KEY);
		event.register(ShellRenderer.TOGGLE_RENDERING_KEY);
		event.register(ShellRenderer.TOGGLE_DELAY_HEATMAP_KEY);
	}

	@SubscribeEvent
	static void onEndTick(ClientTickEvent.Post event) {
		ShellRenderer.onEndTick(Minecraft.getInstance());
		DetectionIndicator.onEndTick(Minecraft.getInstance());
		ConfigScreens.onEndTick(Minecraft.getInstance());
		resyncSensorIndexNearPlayer();
	}

	// ---------------------------------------------------------------- settings screen

	@SubscribeEvent
	static void registerConfigScreensKey(RegisterKeyMappingsEvent event) {
		event.register(ConfigScreens.OPEN_SETTINGS_KEY);
	}

	@SubscribeEvent
	static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
		ShellRenderer.onRenderDelayOverlay(event.getLevelRenderer(),
				event.getLevelRenderState().cameraRenderState);
		ShellRenderer.onRender(event.getLevelRenderState().cameraRenderState.pos);
	}

	@SubscribeEvent
	static void onClientStopping(ClientStoppingEvent event) {
		ShellRenderer.onClientStopping();
	}

	// ---------------------------------------------------------------- mode C: SensorIndex, DetectionIndicator

	@SubscribeEvent
	static void registerDetectionIndicatorKey(RegisterKeyMappingsEvent event) {
		event.register(DetectionIndicator.TOGGLE_KEY);
	}

	@SubscribeEvent
	static void onLevelLoad(LevelEvent.Load event) {
		if (event.getLevel() instanceof ClientLevel) {
			ShellRenderer.onLevelChanged();
			SensorIndex.onLevelChanged();
			DetectionIndicator.onLevelChanged();
		}
	}

	@SubscribeEvent
	static void onLevelUnload(LevelEvent.Unload event) {
		if (event.getLevel() instanceof ClientLevel) {
			ShellRenderer.onLevelChanged();
			SensorIndex.onLevelChanged();
			DetectionIndicator.onLevelChanged();
		}
	}

	@SubscribeEvent
	static void onChunkLoad(ChunkEvent.Load event) {
		if (event.getLevel() instanceof ClientLevel) {
			SensorIndex.onChunkLoad(event.getChunk());
		}
	}

	@SubscribeEvent
	static void onChunkUnload(ChunkEvent.Unload event) {
		if (event.getLevel() instanceof ClientLevel) {
			SensorIndex.onChunkUnload(event.getChunk());
		}
	}

	private static void resyncSensorIndexNearPlayer() {
		if (--sensorIndexResyncCountdown > 0) {
			return;
		}

		sensorIndexResyncCountdown = SENSOR_INDEX_RESYNC_INTERVAL_TICKS;

		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		LocalPlayer player = client.player;

		if (level == null || player == null) {
			return;
		}

		BlockPos center = player.blockPosition();
		int radiusChunks = resyncRadiusChunks();
		Map<WorldPosition, Integer> swept = IndexSweep.sweep(level, center, radiusChunks);
		Map<BlockPos, Integer> truth = new HashMap<>();

		swept.forEach((pos, radius) -> truth.put(new BlockPos(pos.x(), pos.y(), pos.z()), radius));

		SensorIndex.reconcile(truth, pos -> IndexSweep.withinSweep(pos, center, radiusChunks));
	}

	private static int resyncRadiusChunks() {
		RadiusAuditRequest active = RadiusAuditController.activeRequest();
		if (active == null) {
			return SENSOR_INDEX_RESYNC_RADIUS_CHUNKS;
		}

		int chunksForRequest = (active.radius() + BLOCKS_PER_CHUNK - 1) / BLOCKS_PER_CHUNK + 1;
		return Math.max(SENSOR_INDEX_RESYNC_RADIUS_CHUNKS, chunksForRequest);
	}

	// ---------------------------------------------------------------- dev-only verify commands

	@SubscribeEvent
	static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
		RadiusAuditCommand.register(event.getDispatcher());

		if (!FMLEnvironment.isProduction()) {
			VerificationCommand.register(event.getDispatcher());

			DetectionVerificationCommand.register(event.getDispatcher());

			IndexVerificationCommand.register(event.getDispatcher());

			SculkSight.LOGGER.info("Development environment: /sculksight-verify, "
					+ "/sculksight-verify-detection and /sculksight-verify-index registered "
					+ "(ADR-019).");
		}
	}
}
