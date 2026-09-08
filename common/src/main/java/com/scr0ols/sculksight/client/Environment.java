package com.scr0ols.sculksight.client;

import java.nio.file.Path;

/**
 * The loader-neutral seam {@link TimingGate} and {@link TimingLog} read through, in place of
 * calling {@code FabricLoader} directly. `DECISIONS.md` ADR-043's own "what did not move"
 * consequence names exactly this: those two classes need what they read from {@code FabricLoader}
 * - the development-environment flag, and the game directory - through something {@code common}
 * can own, before either can move out of a Fabric-only source tree.
 *
 * <p><b>Three methods, one per thing a caller actually reads.</b> Nothing wider is introduced
 * speculatively (`CODING-STYLE`'s YAGNI): {@link TimingGate} needs only
 * {@link #isDevelopmentEnvironment()}, {@link TimingLog} needs only {@link #gameDir()}, and
 * {@code com.scr0ols.sculksight.config.ClientConfig} needs only {@link #configDir()}. That third
 * method is this seam's first consumer outside this package, added with the v0.1 config screen
 * (PLAN.md section 4), and it is what turned {@link ClientPlatform#get} from package-private to
 * public: YAGNI's answer stops applying the moment the second caller is real.
 *
 * <p>An implementation is supplied once per loader, through {@link ClientPlatform#set}, before
 * anything in this package that reads one runs - see that class's own javadoc for the ordering
 * this depends on and why it holds.
 */
public interface Environment {

	/**
	 * The same gate ADR-019 uses for the verification command's own registration guard, read
	 * here for the timing instrument instead (DECISIONS.md ADR-031).
	 */
	boolean isDevelopmentEnvironment();

	/** The game directory: the instance folder for an install, {@code run/} in a dev environment. */
	Path gameDir();

	/**
	 * The directory a loader puts mod configuration files in.
	 *
	 * <p>Both loaders resolve this to {@code <game dir>/config} in practice, but each is asked
	 * through its own API rather than derived from {@link #gameDir()} here: the loader is what
	 * owns the answer, and a player or launcher that has moved the directory has told the loader,
	 * not this mod.
	 */
	Path configDir();
}
