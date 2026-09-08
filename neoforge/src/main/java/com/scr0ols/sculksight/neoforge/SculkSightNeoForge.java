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
 * <p><b>The three dev-only verify commands are wired here too</b>, through
 * {@code RegisterClientCommandsEvent}, which fires on {@code NeoForge.EVENT_BUS} with a
 * {@code CommandDispatcher<CommandSourceStack>} rather than a client-only source type (read from
 * `neoforged/NeoForge`'s own source, DECISIONS.md ADR-044 point 5). Gated by
 * {@code !FMLEnvironment.isProduction()}, the same real ADR-019 check fabric's own
 * {@code SculkSightClient} applies through {@code FabricLoader.getInstance().isDevelopmentEnvironment()} -
 * so, as on Fabric, nothing in {@code com.scr0ols.sculksight.verify} is reachable from a shipped
 * jar. {@link VerificationCommand}, {@link DetectionVerificationCommand} and
 * {@link IndexVerificationCommand} are this loader's own thin Brigadier shims over the same
 * {@code common}-side cores Fabric's own commands call.
 *
 * <p><b>What NeoForge does not offer: a block-entity-specific load/unload event.</b>
 * {@code net.neoforged.neoforge.client.event} and {@code net.neoforged.neoforge.event.level}
 * were both enumerated in full against the source read above, and neither contains one - unlike
 * Fabric's {@code ClientBlockEntityEvents}. {@link SensorIndex} is therefore populated here from
 * {@link ChunkEvent.Load}/{@link ChunkEvent.Unload} alone, the same per-chunk sweep
 * {@code SensorIndex.onChunkLoad} already performs on Fabric's own chunk-load callback and the
 * same mechanism `IndexSweep`'s ground truth already proves safe independently. What this left
 * unreplicated was finer than R11 already accepts as unconfirmed on Fabric: a sensor placed or
 * broken without its containing chunk reloading was not caught here at all, where on Fabric it is
 * at least attempted through {@code BLOCK_ENTITY_LOAD}/{@code UNLOAD} - and was confirmed, by the
 * 2026-09-08 captain report, to actually happen rather than stay theoretical: a detection box
 * built and tested in one continuous session read "not detected" no matter where the player
 * stood, because the sensor never entered the index in the first place. {@link #resyncSensorIndexNearPlayer}
 * closes that gap now, periodically, for a bounded region around the player - see its own javadoc.
 * `ARCHITECTURE.md` §8 still carries the underlying API gap as a NeoForge-specific note next to
 * R11's existing one; it is the periodic workaround, not the gap itself, that is new here.
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

	/**
	 * How often {@link #resyncSensorIndexNearPlayer} re-derives the sensor index near the player,
	 * in ticks. Every tick would work too - {@link IndexSweep#sweep} costs one map lookup per
	 * chunk in range plus one {@code instanceof} per block entity found there, nothing like the
	 * "N sensors, N raycasts" {@code DetectionIndicator} itself budgets per tick - but there is no
	 * reason to pay even that every tick for a gap that only needs closing within about a second
	 * of it opening: a placed or broken sensor is invisible until this next runs, not detected
	 * wrongly forever the way the 2026-09-08 report this fixes describes.
	 */
	private static final int SENSOR_INDEX_RESYNC_INTERVAL_TICKS = 20;

	/**
	 * How far {@link #resyncSensorIndexNearPlayer} looks around the player, in chunks. Vanilla's
	 * two sculk sensor variants top out at listener radius 16; a player standing at the edge of
	 * that range can be up to one more chunk away from the sensor itself, so 2 chunks of margin on
	 * every side of the player's own chunk - a 5x5 chunk square - covers the whole reachable area
	 * with room to spare.
	 */
	private static final int SENSOR_INDEX_RESYNC_RADIUS_CHUNKS = 2;

	private static int sensorIndexResyncCountdown;

	/**
	 * @param container injected by FancyModLoader, which allows exactly four constructor argument
	 *        types and this among them ({@code FMLModContainer.constructMod} reads them from a map
	 *        of {@code IEventBus}, {@code ModContainer}, {@code FMLModContainer} and {@code Dist} -
	 *        read from the real loader-11.0.13 artifact on 2026-09-06, per CONVENTIONS.md section
	 *        6, rather than assumed). It is what a config screen is registered against.
	 */
	public SculkSightNeoForge(ModContainer container) {
		// Installed before anything else touches com.scr0ols.sculksight.client: see this class's
		// own javadoc for why the constructor is early enough.
		ClientPlatform.set(new NeoForgeEnvironment());

		// The v0.1 settings file (PLAN.md section 4), read once, here - after ClientPlatform.set
		// above, since ClientConfig asks it for the loader's own config directory, and before any
		// event below can reach something that reads a setting.
		ClientConfig.load();

		// How a NeoForge player reaches the screen: the loader's own mod list offers a config
		// button for any mod that has registered this extension point (IConfigScreenFactory's own
		// javadoc, read from neoforge-26.2.0.75-sources on 2026-09-06). No third-party mod is
		// involved, unlike Fabric's Mod Menu - see fabric's own ModMenuIntegration.
		// Held in a typed local first: ModContainer overloads registerExtensionPoint on the value and
		// on a Supplier of it, and a lambda passed inline matches both, which the compiler reports as
		// ambiguous rather than choosing for us.
		IConfigScreenFactory screens = (ignored, modListScreen) -> ConfigScreens.create(modListScreen);

		container.registerExtensionPoint(IConfigScreenFactory.class, screens);

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
		resyncSensorIndexNearPlayer();
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

	/**
	 * Closes the gap {@link SensorIndex}'s own "R11 dependency" paragraph documents for this
	 * loader specifically: NeoForge has no live block-entity add/remove event (this class's own
	 * javadoc, "What NeoForge does not offer" above), so a sensor placed or broken while its chunk
	 * stays loaded never reaches {@link SensorIndex#onBlockEntityLoad}/
	 * {@link SensorIndex#onBlockEntityUnload} here the way it does on Fabric - confirmed as the
	 * cause of the 2026-09-08 captain report: a detection box built and tested in one continuous
	 * session, its chunk never reloading in between, read "not detected" no matter where the
	 * player stood, because the sensor never entered the index at all.
	 *
	 * <p>Re-derives ground truth for a small, bounded region around the player with
	 * {@link IndexSweep#sweep} - the same independent sweep {@code /sculksight-verify-index}
	 * already uses, not a new mechanism - and applies it through {@link SensorIndex#reconcile}.
	 * Bounded to {@link #SENSOR_INDEX_RESYNC_RADIUS_CHUNKS} rather than every loaded chunk, so
	 * ADR-038's "no search-radius constant" rule still governs the index's own general bookkeeping;
	 * this is corrective maintenance for one loader's missing signal, not a replacement for
	 * {@link #onChunkLoad}/{@link #onChunkUnload} above, which still run and still matter for
	 * chunks the player has not been near recently.
	 */
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
		Map<WorldPosition, Integer> swept = IndexSweep.sweep(level, center, SENSOR_INDEX_RESYNC_RADIUS_CHUNKS);
		Map<BlockPos, Integer> truth = new HashMap<>();

		swept.forEach((pos, radius) -> truth.put(new BlockPos(pos.x(), pos.y(), pos.z()), radius));

		SensorIndex.reconcile(truth, pos -> IndexSweep.withinSweep(pos, center, SENSOR_INDEX_RESYNC_RADIUS_CHUNKS));
	}

	// ---------------------------------------------------------------- dev-only verify commands

	/**
	 * DECISIONS.md ADR-019 permits the verification mechanism to reach server-side state only in
	 * a development environment, and requires that the dev-only status be real rather than
	 * intended - the same gate {@code SculkSightClient} applies on Fabric through
	 * {@code FabricLoader}, applied here through {@code FMLEnvironment} directly rather than
	 * through the package-private {@code ClientPlatform}/{@code Environment} seam, which this
	 * class's own package cannot reach and which exists for {@code TimingGate}/{@code TimingLog}
	 * alone (see {@code Environment}'s own javadoc).
	 */
	@SubscribeEvent
	static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
		if (!FMLEnvironment.isProduction()) {
			VerificationCommand.register(event.getDispatcher());

			// Mode C's own gate: TESTING-STRATEGY.md section 4's v0.1 criterion names both modes,
			// and mode A's command asks a different piece of code. Same mechanism below the
			// prediction, same ADR-019 concession, a separate command for the reason
			// DetectionVerificationCommand's own javadoc gives.
			DetectionVerificationCommand.register(event.getDispatcher());

			// Mode C's index, checked against an independent sweep rather than against this mod's
			// own geometry: DECISIONS.md ADR-041, closing OPEN-QUESTIONS.md section 21. Same
			// ADR-019 concession, same reason - see the command's own javadoc for why it is a
			// third mechanism rather than an extension of either command above it.
			IndexVerificationCommand.register(event.getDispatcher());

			SculkSight.LOGGER.info("Development environment: /sculksight-verify, "
					+ "/sculksight-verify-detection and /sculksight-verify-index registered "
					+ "(ADR-019).");
		}
	}
}
