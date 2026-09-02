package com.scr0ols.sculksight.client;

import com.scr0ols.sculksight.SculkSight;
import com.scr0ols.sculksight.verify.VerificationCommand;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

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
 */
public class SculkSightClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SculkSight.LOGGER.info("Sculk Sight client initialised.");

		// The renderer registers a key binding, a client tick handler and a level render callback.
		// It has to happen here rather than lazily: Fabric's key mapping registry and its event
		// registries are both read after client initialisation and not again afterwards.
		ShellRenderer.register();

		// DECISIONS.md ADR-019 permits the verification mechanism to reach server-side state
		// only in a development environment, and requires that the dev-only status be real
		// rather than intended. This is that gate: in a built jar the branch is not taken, so
		// nothing in com.scr0ols.sculksight.verify is ever reachable from a shipped mod.
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			VerificationCommand.register();
			SculkSight.LOGGER.info("Development environment: /sculksight-verify registered (ADR-019).");
		}
	}
}
