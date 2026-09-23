package com.scr0ols.sculksight.verify;

/** One position where an independent sweep and the live sensor index disagree. */
public record IndexDiscrepancy(int x, int y, int z, Kind kind, int sweptRadius, int indexedRadius) {

	/** Marks the side with no entry, since a real listener radius is never negative. */
	public static final int NO_ENTRY = -1;

	public enum Kind {
		MISSING_FROM_INDEX,
		STALE_IN_INDEX,
		RADIUS_MISMATCH
	}

	/** A one-line description for a report, readable without the surrounding context. */
	public String describe() {
		String detail = switch (kind) {
			case MISSING_FROM_INDEX -> "swept at radius " + sweptRadius + ", absent from the index";
			case STALE_IN_INDEX -> "indexed at radius " + indexedRadius + ", absent from the sweep";
			case RADIUS_MISMATCH -> "swept radius " + sweptRadius + " vs indexed radius " + indexedRadius;
		};

		return "(" + x + ", " + y + ", " + z + "): " + kind + ", " + detail;
	}
}
