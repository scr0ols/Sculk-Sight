package com.scr0ols.sculksight.client;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Whether the tier instrument of DECISIONS.md ADR-031 is on.
 *
 * <p><b>A type of its own, and the reason is a build failure rather than tidiness.</b> The gate
 * reads {@code FabricLoader}, which cannot be initialised outside a launched game: a plain JUnit
 * test that touched any member of the class holding it failed with
 * {@code ExceptionInInitializerError} before this was split out. Keeping the gate here leaves
 * {@link TierTiming} free of it, so the arithmetic that decides what a budget number says is
 * testable in an ordinary JVM. Anything calling {@link #ENABLED}, directly or through
 * {@code TierTiming.start}, still needs a running game.
 */
final class TimingGate {

	/**
	 * Turns the instrument on in an ordinary installed instance, where the development gate below
	 * is false.
	 *
	 * <p>This exists for TESTING-STRATEGY.md section 7's profiling pass, whose two jars run as
	 * normal installs. Without it the budget numbers would have to come from a separately launched
	 * dev client, which is not the artifact the pass is comparing.
	 */
	static final String PROPERTY = "sculksight.timing";

	/**
	 * The development environment, or the property above set true.
	 *
	 * <p>{@code isDevelopmentEnvironment} is the same gate ADR-019 uses for the verification
	 * command; confirmed present on Fabric Loader 0.19.3, the version this build resolves
	 * (RESEARCH-LOG.md E7).
	 *
	 * <p>Read once into a {@code static final boolean}, so the disabled path allocates nothing,
	 * reads no clock, and is a constant condition the JIT folds away. That is what makes the
	 * instrument acceptable on the per-frame path.
	 */
	static final boolean ENABLED = FabricLoader.getInstance().isDevelopmentEnvironment()
			|| Boolean.getBoolean(PROPERTY);

	private TimingGate() {
	}
}
