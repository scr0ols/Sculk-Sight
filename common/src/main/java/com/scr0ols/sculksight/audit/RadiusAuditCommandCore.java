package com.scr0ols.sculksight.audit;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

/**
 * The loader-agnostic body of {@code /sculksight radius <n> [type]}. ARCHITECTURE.md section 12.1.
 *
 * <p><b>The same split the verification commands already use</b>: a {@code *CommandCore} in
 * {@code common} holding everything that is not Brigadier, and one thin registration class per
 * loader above it. This core goes one step further than those three and names no Minecraft type
 * at all - it takes the parsed arguments and a sink for its output - which is what puts it in
 * {@code common/src/main} rather than {@code common/src/client} and therefore inside the reach of
 * the JUnit suite.
 *
 * <p><b>The audit itself is not built.</b> This command validates its arguments and says so.
 * Selection over the sensor index, the async solve, the per-sensor cache, the per-tick budget and
 * the cap warning are all separate work; ARCHITECTURE.md section 12.4 is where the reason they are
 * separate is written down, and section 12.5 lists what this module does not own. Reporting an
 * accepted request rather than silently doing nothing is deliberate: it makes the registration and
 * the parsing verifiable in a live client before anything expensive exists behind them.
 */
public final class RadiusAuditCommandCore {

	/** Brigadier's conventional "this command did something" result. */
	public static final int SUCCESS = 1;

	/** Brigadier's conventional "this command did not run" result. */
	public static final int FAILURE = 0;

	private RadiusAuditCommandCore() {
	}

	/**
	 * Validates the arguments and reports the outcome.
	 *
	 * @param report where a line of player-facing feedback goes; the loader supplies it, because
	 *     the two loaders' command sources send chat differently and neither type belongs here
	 * @param radius the radius in blocks, as Brigadier parsed it
	 * @param detectorName the detector name the player typed, or {@code null} when omitted
	 * @return {@link #SUCCESS} when the arguments are accepted, {@link #FAILURE} when they are not
	 */
	public static int run(Consumer<String> report, int radius, @Nullable String detectorName) {
		RadiusAuditRequest request;
		try {
			request = RadiusAuditRequest.of(radius, detectorName);
		} catch (RadiusAuditArgumentException problem) {
			report.accept(problem.getMessage());
			return FAILURE;
		}

		report.accept("Radius audit accepted: " + request.describe() + ".");
		report.accept("The audit itself is not implemented yet, so nothing is drawn.");
		return SUCCESS;
	}
}
