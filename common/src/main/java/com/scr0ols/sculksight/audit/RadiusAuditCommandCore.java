package com.scr0ols.sculksight.audit;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import com.scr0ols.sculksight.audit.RadiusAudit.AuditedSensor;

/**
 * The loader-agnostic body of {@code /sculksight find <type> <radius> <mode>}. ARCHITECTURE.md
 * section 12.1.
 *
 * <p><b>The same split the verification commands already use</b>: a {@code *CommandCore} in
 * {@code common} holding everything that is not Brigadier, and one thin registration class per
 * loader above it. This core goes one step further than those three and names no Minecraft type
 * at all - it takes the parsed arguments, the centre and candidate set already reduced to plain
 * integers and {@link AuditedSensor}s, and a sink for its output - which is what puts it in
 * {@code common/src/main} rather than {@code common/src/client} and therefore inside the reach of
 * the JUnit suite.
 *
 * <p><b>Two entry points, one per {@link RadiusAuditMode}, rather than one method taking the
 * mode.</b> They validate and select identically ({@link #select}) and then diverge completely:
 * {@link #runLive} hands the request to {@link RadiusAuditController} and touches no saved state,
 * while {@link #runStatic} hands the selection to a pinner and activates nothing. Written as two
 * methods because their parameter lists genuinely differ - a live find has no use for a pinner, and
 * a mode-taking method would have had to accept one anyway and ignore it, which is the shape that
 * invites passing {@code null}.
 *
 * <p><b>What this still does not do.</b> The async solve itself is unchanged from mode A's own
 * (ARCHITECTURE.md section 6.2); section 12.5 lists what this module never owns. The centre
 * position, the candidate set and the configured cap itself are built by the client-side caller
 * (section 12.1's "trivial by construction" half, reading {@code SensorIndex} and
 * {@code ClientConfig}), never by this class - which is also why the cap arrives here as a plain
 * {@code int} rather than this class naming the config type. Writing the pinned sensors to disk is
 * the same kind of thing and arrives the same way, as {@link #runStatic}'s {@code pin} function.
 */
public final class RadiusAuditCommandCore {

	/** Brigadier's conventional "this command did something" result. */
	public static final int SUCCESS = 1;

	/** Brigadier's conventional "this command did not run" result. */
	public static final int FAILURE = 0;

	private RadiusAuditCommandCore() {
	}

	/**
	 * {@link RadiusAuditMode#LIVE}: validates, selects, reports, and makes the request the active
	 * one so the renderer keeps re-selecting against the player's current position every tick
	 * rather than this one invocation being a one-off report. The per-sensor cache and the per-tick
	 * population budget that makes a large, continuously-refreshed selection affordable live in
	 * {@code ShellRenderer} (client source set), reusing {@code ShellEntry} exactly as
	 * ARCHITECTURE.md section 12.3 describes.
	 *
	 * <p>Nothing is saved: a live find is a query, and {@link RadiusAuditController}'s own javadoc
	 * is where that is written down.
	 *
	 * @param report where a line of player-facing feedback goes; the loader supplies it, because
	 *     the two loaders' command sources send chat differently and neither type belongs here
	 * @param radius the radius in blocks, as Brigadier parsed it
	 * @param detectorName the detector name the player typed
	 * @param centreX the query centre - the player's position on the block granularity R12 fixes
	 * @param centreY see {@code centreX}
	 * @param centreZ see {@code centreX}
	 * @param candidates every sensor the caller's {@code SensorIndex} snapshot resolved to a
	 *     type; this class filters and orders them, it does not enumerate them
	 * @param cap section 12.4's cap, read by the caller from {@code ClientConfig} - the largest
	 *     number of sensors a single find reports, nearest first
	 * @return {@link #SUCCESS} when the arguments are accepted, {@link #FAILURE} when they are not
	 */
	public static int runLive(Consumer<String> report, int radius, @Nullable String detectorName,
			int centreX, int centreY, int centreZ, List<AuditedSensor> candidates, int cap) {

		Selected selected = select(report, radius, detectorName, centreX, centreY, centreZ,
				candidates, cap);
		if (selected == null) {
			return FAILURE;
		}

		// A successful live find becomes the active one: the renderer re-runs this same selection
		// every tick against the player's current position, per RadiusAuditController's javadoc.
		RadiusAuditController.activate(selected.request());

		report.accept("Live find: " + selected.request().describe()
				+ ". The shells follow you until you leave the world or run another find.");
		return SUCCESS;
	}

	/**
	 * {@link RadiusAuditMode#STATIC}: validates, selects once at the given centre, and pins the
	 * result through {@code pin} - after which no audit is active, because the tracked sensors
	 * <em>are</em> the result and there is nothing left to re-select.
	 *
	 * <p><b>Clears any live find that was already running.</b> Leaving one active would have the
	 * renderer keep re-selecting the same area around the player on top of the sensors this call
	 * just pinned, so the two would draw the same shells by two different routes and the player
	 * would have no way to tell which control governed what.
	 *
	 * @param pin applies the selection to the saved configuration and returns one line describing
	 *     what it did; the caller owns the {@code ClientConfig} read and write, and
	 *     {@link AuditPin} is the pure operation both this and the settings screen's Pin button
	 *     route through
	 * @see #runLive for every other parameter
	 */
	public static int runStatic(Consumer<String> report, int radius, @Nullable String detectorName,
			int centreX, int centreY, int centreZ, List<AuditedSensor> candidates, int cap,
			Function<List<AuditedSensor>, String> pin) {

		Selected selected = select(report, radius, detectorName, centreX, centreY, centreZ,
				candidates, cap);
		if (selected == null) {
			return FAILURE;
		}

		RadiusAuditController.clear();

		report.accept("Static find: " + selected.request().describe() + ".");
		report.accept(pin.apply(selected.selection().selected()));
		return SUCCESS;
	}

	/** A validated request and what it selected, or {@code null} once the problem is reported. */
	private record Selected(RadiusAuditRequest request, RadiusAudit.CappedSelection selection) {
	}

	private static @Nullable Selected select(Consumer<String> report, int radius,
			@Nullable String detectorName, int centreX, int centreY, int centreZ,
			List<AuditedSensor> candidates, int cap) {

		RadiusAuditRequest request;
		try {
			request = RadiusAuditRequest.of(radius, detectorName);
		} catch (RadiusAuditArgumentException problem) {
			report.accept(problem.getMessage());
			return null;
		}

		RadiusAudit.CappedSelection selection =
				RadiusAudit.selectWithCap(centreX, centreY, centreZ, request, candidates, cap);

		report.accept(describeSelection(selection.selected().size()));
		if (selection.capped()) {
			report.accept(describeCapWarning(selection.matchedCount(), cap));
		}

		return new Selected(request, selection);
	}

	private static String describeSelection(int count) {
		return switch (count) {
			case 0 -> "No sensors found in range.";
			case 1 -> "1 sensor found in range.";
			default -> count + " sensors found in range.";
		};
	}

	private static String describeCapWarning(int matchedCount, int cap) {
		return "Cap reached: " + matchedCount + " sensors matched, showing the nearest " + cap + ".";
	}
}
