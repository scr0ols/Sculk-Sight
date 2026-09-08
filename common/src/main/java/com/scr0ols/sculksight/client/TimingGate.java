package com.scr0ols.sculksight.client;

/**
 * Whether the tier instrument of DECISIONS.md ADR-031 is on.
 *
 * <p><b>A type of its own, and the reason is a build failure rather than tidiness.</b> The gate
 * reads {@link ClientPlatform#get}, which throws until a loader's entrypoint has called
 * {@link ClientPlatform#set}: a plain JUnit test that touched any member of the class holding it
 * failed with an initialisation error before this was split out - originally
 * {@code ExceptionInInitializerError} from touching {@code FabricLoader} directly, and the same
 * failure shape now comes from {@link ClientPlatform#get} instead, for the same reason (see that
 * class's own javadoc). Keeping the gate here leaves {@link TierTiming} free of it, so the
 * arithmetic that decides what a budget number says is testable in an ordinary JVM. Anything
 * calling {@link #ENABLED}, directly or through {@code TierTiming.start}, still needs a running,
 * initialised game.
 *
 * <p><b>Moved here from {@code fabric}'s client source set, DECISIONS.md ADR-043's own
 * "what did not move" consequence.</b> {@link ClientPlatform} is the loader-neutral seam that ADR
 * named as the prerequisite; the class otherwise reads exactly as it did on Fabric.
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
	 * <p>{@link Environment#isDevelopmentEnvironment} is the same gate ADR-019 uses for the
	 * verification command, through whichever loader's own implementation
	 * {@link ClientPlatform#set} was given.
	 *
	 * <p>Read once into a {@code static final boolean}, so the disabled path allocates nothing,
	 * reads no clock, and is a constant condition the JIT folds away. That is what makes the
	 * instrument acceptable on the per-frame path.
	 */
	static final boolean ENABLED = ClientPlatform.get().isDevelopmentEnvironment()
			|| Boolean.getBoolean(PROPERTY);

	private TimingGate() {
	}
}
