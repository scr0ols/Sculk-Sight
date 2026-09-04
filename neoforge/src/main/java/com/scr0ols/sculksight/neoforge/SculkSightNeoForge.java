package com.scr0ols.sculksight.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import com.scr0ols.sculksight.SculkSight;

/**
 * The NeoForge entrypoint. This session's own deliverable, deliberately narrow: it proves the
 * toolchain (build.gradle, {@code neoforge.mods.toml}, this class) loads a bare mod, the same bar
 * `SESSION-KICKOFF.md`'s GROUNDWORK block sets for a brand new loader. It does not yet register
 * the key bindings, commands or render hooks {@code fabric/}'s {@code SculkSightClient} does.
 *
 * <p><b>Why nothing is ported here yet.</b> ARCHITECTURE.md section 2.1 puts registration, key
 * bindings, commands and render hooks in layer 4, the loader adapter - but on the Fabric side
 * today those are not cleanly separated from layer 3's logic; nine classes under
 * {@code fabric/src/client/java} mix Fabric API calls directly into methods that are otherwise
 * loader-independent (`c-docs/NEXT-STEPS.md` carries this as the next item for this build item).
 * Splitting each of those correctly, and finding NeoForge's real equivalent of every Fabric
 * event and registry this mod uses, is exactly the kind of claim `c-docs/CONVENTIONS.md` section 6
 * forbids writing from memory - Minecraft 26.2 and its NeoForge event API postdate any assistant's
 * training data the same way its toolchain versions do (see this project's own version-lookup
 * discipline in gradle.properties). That reading is unread and is future session work, not
 * something to guess at here.
 *
 * <p>{@code dist = Dist.CLIENT} matches {@code fabric.mod.json}'s {@code "environment": "client"}
 * (DECISIONS.md ADR-006): a single client-only {@code @Mod} class is sufficient and needs no
 * additional non-dist-specific class alongside it (confirmed against
 * docs.neoforged.net/docs/gettingstarted/modfiles/, read 2026-09-04).
 */
@Mod(value = SculkSight.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = SculkSight.MOD_ID, value = Dist.CLIENT)
public final class SculkSightNeoForge {

	public SculkSightNeoForge() {
	}

	@SubscribeEvent
	static void onClientSetup(FMLClientSetupEvent event) {
		SculkSight.LOGGER.info("Sculk Sight (NeoForge) client initialised.");
	}
}
