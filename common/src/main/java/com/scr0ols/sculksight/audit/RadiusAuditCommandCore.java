package com.scr0ols.sculksight.audit;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;

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
	public static int runLive(Consumer<Component> report, int radius, @Nullable String detectorName,
			int centreX, int centreY, int centreZ, List<AuditedSensor> candidates, int cap) {

		Selected selected = select(report, radius, detectorName, centreX, centreY, centreZ,
				candidates, cap);
		if (selected == null) {
			return FAILURE;
		}

		RadiusAuditController.activate(selected.request());

		report.accept(Component.translatable(
				"sculksight.command.find.live", selected.request().describe()));
		return SUCCESS;
	}

	/** Runs a static find: validates, selects once at the given centre, and pins the result. */
	public static int runStatic(Consumer<Component> report, int radius, @Nullable String detectorName,
			int centreX, int centreY, int centreZ, List<AuditedSensor> candidates, int cap,
			Function<List<AuditedSensor>, Component> pin) {

		Selected selected = select(report, radius, detectorName, centreX, centreY, centreZ,
				candidates, cap);
		if (selected == null) {
			return FAILURE;
		}

		RadiusAuditController.clear();

		report.accept(Component.translatable(
				"sculksight.command.find.static", selected.request().describe()));
		report.accept(pin.apply(selected.selection().selected()));
		RadiusAuditController.markStaticFindCompleted();
		return SUCCESS;
	}

	/** Stops an active live find and clears its audit renders, touching no tracked or pinned sensor. */
	public static int runOff(Consumer<Component> report) {
		boolean wasActive = RadiusAuditController.activeRequest() != null;
		boolean wasStaticCompleted = RadiusAuditController.staticFindCompleted();

		RadiusAuditController.clear();

		report.accept(Component.translatable(wasActive
				? "sculksight.command.find.off.live_cancelled"
				: wasStaticCompleted
						? "sculksight.command.find.off.static_cancelled"
						: "sculksight.command.find.off.none_running"));
		return SUCCESS;
	}

	private record Selected(RadiusAuditRequest request, RadiusAudit.CappedSelection selection) {
	}

	private static @Nullable Selected select(Consumer<Component> report, int radius,
			@Nullable String detectorName, int centreX, int centreY, int centreZ,
			List<AuditedSensor> candidates, int cap) {

		RadiusAuditRequest request;
		try {
			request = RadiusAuditRequest.of(radius, detectorName);
		} catch (RadiusAuditArgumentException problem) {
			report.accept(problem.component());
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

	private static Component describeSelection(int count) {
		return switch (count) {
			case 0 -> Component.translatable("sculksight.command.find.selection.none");
			case 1 -> Component.translatable("sculksight.command.find.selection.one");
			default -> Component.translatable("sculksight.command.find.selection.many", count);
		};
	}

	private static Component describeCapWarning(int matchedCount, int cap) {
		return Component.translatable("sculksight.command.find.cap_reached", matchedCount, cap);
	}
}
