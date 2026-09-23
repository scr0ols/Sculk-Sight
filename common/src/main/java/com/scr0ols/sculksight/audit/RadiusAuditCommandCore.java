package com.scr0ols.sculksight.audit;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import com.scr0ols.sculksight.audit.RadiusAudit.AuditedSensor;

/** The loader-agnostic body of {@code /sculksight find <type> <radius> <mode>}. */
public final class RadiusAuditCommandCore {

	/** Brigadier's conventional "this command did something" result. */
	public static final int SUCCESS = 1;

	/** Brigadier's conventional "this command did not run" result. */
	public static final int FAILURE = 0;

	private RadiusAuditCommandCore() {
	}

	/** Runs a live find: validates, selects, reports, and makes the request the active one. */
	public static int runLive(Consumer<String> report, int radius, @Nullable String detectorName,
			int centreX, int centreY, int centreZ, List<AuditedSensor> candidates, int cap) {

		Selected selected = select(report, radius, detectorName, centreX, centreY, centreZ,
				candidates, cap);
		if (selected == null) {
			return FAILURE;
		}

		RadiusAuditController.activate(selected.request());

		report.accept("Live find: " + selected.request().describe()
				+ ". The shells follow you until you leave the world or run another find.");
		return SUCCESS;
	}

	/** Runs a static find: validates, selects once at the given centre, and pins the result. */
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
