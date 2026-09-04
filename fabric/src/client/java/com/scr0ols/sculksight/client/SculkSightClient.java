package com.scr0ols.sculksight.client;

import com.scr0ols.sculksight.SculkSight;
import com.scr0ols.sculksight.verify.DetectionVerificationCommand;
import com.scr0ols.sculksight.verify.IndexVerificationCommand;
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
 *
 * <p>A second throwaway diagnostic command was briefly registered here to empirically measure
 * R10 point 7's {@code B}. It has been removed now that B is measured in every required scene
 * (RESEARCH-LOG.md R10 point 11); its source is archived at
 * debug-tests/R10-BlockEntityCountProbe.java for the record.
 */
public class SculkSightClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SculkSight.LOGGER.info("Sculk Sight client initialised.");

		// The renderer registers a key binding, a client tick handler and a level render callback.
		// It has to happen here rather than lazily: Fabric's key mapping registry and its event
		// registries are both read after client initialisation and not again afterwards.
		ShellRenderer.register();

		// The sensor index (ADR-038) must register before any ClientLevel exists, which
		// onInitializeClient always runs before - see SensorIndex's own class comment for why
		// that ordering is what lets it skip an explicit sweep at world join.
		SensorIndex.register();

		// Mode C (PLAN.md section 3.4, ADR-039): a toggle key and a per-tick check against the
		// sensor index above, independent of ShellRenderer's mode A shell.
		DetectionIndicator.register();

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
}
