package com.scr0ols.sculksight.client;

import com.scr0ols.sculksight.SculkSight;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint.
 *
 * <p>Empty by design. Phase v0.0 (PLAN.md section 5.1) has a single objective - prove the
 * solver is correct - and no mod logic may be written before ARCHITECTURE.md exists, which
 * is itself blocked on research questions R1 to R4 and R12. This class exists so that the
 * environment can be verified end to end: a mod that loads and logs is proof that the
 * toolchain works, and nothing more is claimed by it.
 */
public class SculkSightClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SculkSight.LOGGER.info("Sculk Sight client initialised.");
	}
}
