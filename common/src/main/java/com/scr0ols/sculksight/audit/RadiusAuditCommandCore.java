package com.scr0ols.sculksight.audit;

import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.scr0ols.sculksight.audit.RadiusAudit.AuditedSensor;

/**
 * The loader-agnostic body of {@code /sculksight radius <n> [type]}. ARCHITECTURE.md section 12.1.
 *
 * <p><b>The same split the verification commands already use</b>: a {@code *CommandCore} in
 * {@code common} holding everything that is not Brigadier, and one thin registration class per
 * loader above it. This core goes one step further than those three and names no Minecraft type
 * at all - it takes the parsed arguments, the centre and candidate set already reduced to plain
 * integers and {@link AuditedSensor}s, and a sink for its output - which is what puts it in
 * {@code common/src/main} rather than {@code common/src/client} and therefore inside the reach of
 * the JUnit suite.
 *
 * <p><b>What this still does not do.</b> {@link RadiusAudit#select} enforces no cap yet - section
 * 12.4 is that separate task - so a very large candidate set is reported in full. The async solve,
 * the per-sensor cache, the per-tick budget and the cap warning remain later work too; section
 * 12.5 lists what this module does not own. The centre position and the candidate set themselves
 * are built by the client-side caller (section 12.1's "trivial by construction" half, reading
 * {@code SensorIndex}), never by this class.
 */
public final class RadiusAuditCommandCore {

	/** Brigadier's conventional "this command did something" result. */
	public static final int SUCCESS = 1;

	/** Brigadier's conventional "this command did not run" result. */
	public static final int FAILURE = 0;

	private RadiusAuditCommandCore() {
	}

	/**
	 * Validates the arguments, runs {@link RadiusAudit#select} against {@code candidates} and
	 * reports the outcome.
	 *
	 * @param report where a line of player-facing feedback goes; the loader supplies it, because
	 *     the two loaders' command sources send chat differently and neither type belongs here
	 * @param radius the radius in blocks, as Brigadier parsed it
	 * @param detectorName the detector name the player typed, or {@code null} when omitted
	 * @param centreX the query centre - the player's position on the block granularity R12 fixes
	 * @param centreY see {@code centreX}
	 * @param centreZ see {@code centreX}
	 * @param candidates every sensor the caller's {@code SensorIndex} snapshot resolved to a
	 *     type; this class filters and orders them, it does not enumerate them
	 * @return {@link #SUCCESS} when the arguments are accepted, {@link #FAILURE} when they are not
	 */
	public static int run(Consumer<String> report, int radius, @Nullable String detectorName,
			int centreX, int centreY, int centreZ, List<AuditedSensor> candidates) {

		RadiusAuditRequest request;
		try {
			request = RadiusAuditRequest.of(radius, detectorName);
		} catch (RadiusAuditArgumentException problem) {
			report.accept(problem.getMessage());
			return FAILURE;
		}

		List<AuditedSensor> selected =
				RadiusAudit.select(centreX, centreY, centreZ, request, candidates);

		report.accept("Radius audit accepted: " + request.describe() + ".");
		report.accept(describeSelection(selected.size()));
		return SUCCESS;
	}

	private static String describeSelection(int count) {
		return switch (count) {
			case 0 -> "No sensors found in range.";
			case 1 -> "1 sensor found in range.";
			default -> count + " sensors found in range.";
		};
	}
}
