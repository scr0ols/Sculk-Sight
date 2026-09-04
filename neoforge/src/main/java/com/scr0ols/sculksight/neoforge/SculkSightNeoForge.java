package com.scr0ols.sculksight.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import com.scr0ols.sculksight.SculkSight;
import com.scr0ols.sculksight.client.ClientPlatform;
import com.scr0ols.sculksight.client.DetectionIndicator;
import com.scr0ols.sculksight.client.SensorIndex;
import com.scr0ols.sculksight.client.ShellRenderer;

/**
 * The NeoForge entrypoint, and - since DECISIONS.md ADR-043's follow-up split - the NeoForge
 * registration hub, the same role {@code fabric}'s {@code SculkSightClient} plays there. Wires
 * mode A ({@link ShellRenderer}) and mode C ({@link DetectionIndicator}, {@link SensorIndex}) to
 * NeoForge's own real event API, read from `neoforged/NeoForge` at tag {@code 26.2.0-stable} and
 * `neoforged/FancyModLoader` at {@code main} on 2026-09-04 - never assumed from Fabric's shape,
 * per CONVENTIONS.md §6. `ARCHITECTURE.md` §8's "NeoForge's real event, key-binding and
 * client-command API for 26.2" row is what this class closes, for the two modes this session
 * scoped (`NEXT-STEPS.md`).
 *
 * <p><b>One class, {@code @EventBusSubscriber}-annotated, rather than one per concern.</b>
 * {@code EventBusSubscriber}'s own javadoc (FancyModLoader source) routes every static
 * {@code @SubscribeEvent} method automatically: an event implementing {@code IModBusEvent} - only
 * {@link RegisterKeyMappingsEvent} among the ones this class handles - goes to the mod's own
 * event bus, and everything else goes to {@code NeoForge.EVENT_BUS}. No manual
 * {@code IEventBus.register} call and no separate per-bus class are needed, unlike Fabric's
 * per-event-family registries.
 *
 * <p><b>{@code ClientPlatform.set} runs in the constructor</b>, NeoForge's own earliest mod
 * lifecycle point, ahead of every event below - {@link RegisterKeyMappingsEvent} included, which
 * fires well before {@code FMLClientSetupEvent} ever did on the version this project last
 * confirmed a client-setup hook against. See {@link ClientPlatform}'s own javadoc for why this
 * ordering is what the seam relies on.
 *
 * <p><b>What this class does not wire: the three dev-only verify commands.</b> Their mechanism -
 * {@code RegisterClientCommandsEvent}, firing on {@code NeoForge.EVENT_BUS} with a
 * {@code CommandDispatcher<CommandSourceStack>}, per the same source read - is now known and
 * unblocks that work, but wiring it is out of this session's scope (`NEXT-STEPS.md` names mode A
 * and mode C only). `ARCHITECTURE.md` §8 carries this row's remaining half.
 *
 * <p><b>What NeoForge does not offer: a block-entity-specific load/unload event.</b>
 * {@code net.neoforged.neoforge.client.event} and {@code net.neoforged.neoforge.event.level}
 * were both enumerated in full against the source read above, and neither contains one - unlike
 * Fabric's {@code ClientBlockEntityEvents}. {@link SensorIndex} is therefore populated here from
 * {@link ChunkEvent.Load}/{@link ChunkEvent.Unload} alone, the same per-chunk sweep
 * {@code SensorIndex.onChunkLoad} already performs on Fabric's own chunk-load callback and the
 * same mechanism `IndexSweep`'s ground truth already proves safe independently. What this leaves
 * unreplicated is finer than R11 already accepts as unconfirmed on Fabric: a sensor placed or
 * broken without its containing chunk reloading is not caught here at all, where on Fabric it is
 * at least attempted through {@code BLOCK_ENTITY_LOAD}/{@code UNLOAD}. `ARCHITECTURE.md` §8
 * carries this as a new, NeoForge-specific limitation next to R11's existing one.
 *
 * <p><b>Level-change handling fires on both {@link LevelEvent.Load} and {@link LevelEvent.Unload}</b>,
 * both filtered to {@link ClientLevel}, rather than on one event the way Fabric's single
 * {@code AFTER_CLIENT_LEVEL_CHANGE} does. Every handler this class forwards to - {@code onLevelChanged}
 * on {@link ShellRenderer}, {@link SensorIndex} and {@link DetectionIndicator} - is an idempotent
 * reset, so covering a level swap from both its old level's unload and its new level's load is a
 * safe superset rather than a double-clear worth avoiding.
 */
@Mod(value = SculkSight.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = SculkSight.MOD_ID, value = Dist.CLIENT)
public final class SculkSightNeoForge {

	public SculkSightNeoForge() {
		// Installed before anything else touches com.scr0ols.sculksight.client: see this class's
		// own javadoc for why the constructor is early enough.
		ClientPlatform.set(new NeoForgeEnvironment());

		SculkSight.LOGGER.info("Sculk Sight (NeoForge) client initialised.");
	}

	// ---------------------------------------------------------------- mode A: ShellRenderer

	@SubscribeEvent
	static void registerShellRendererKey(RegisterKeyMappingsEvent event) {
		event.register(ShellRenderer.TOGGLE_KEY);
	}

	@SubscribeEvent
	static void onEndTick(ClientTickEvent.Post event) {
		ShellRenderer.onEndTick(Minecraft.getInstance());
		DetectionIndicator.onEndTick(Minecraft.getInstance());
	}

	/**
	 * {@code AfterTranslucentBlocks}, matching Fabric's own
	 * {@code LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN} hook per ADR-021: the shell draws after
	 * translucent terrain, not before. {@code getLevelRenderState()} returns the same vanilla
	 * {@code net.minecraft.client.renderer.state.level.LevelRenderState} Fabric's own
	 * {@code LevelRenderContext.levelState()} does, confirmed by the import in NeoForge's own
	 * source for this event - so {@code cameraRenderState.pos} is read the identical way.
	 */
	@SubscribeEvent
	static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
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

	/** Fires on both logical sides (LevelEvent's own javadoc); filtered to the client here. */
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

	/** Fires on both logical sides (ChunkEvent's own javadoc); filtered to the client here. */
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
}
