package com.scr0ols.sculksight.verify;

/**
 * One position where an independent sweep and {@code SensorIndex}'s live contents disagree.
 * DECISIONS.md ADR-041.
 *
 * <p>Three ways to disagree, and they are not symmetric in what they mean. {@link Kind#MISSING_FROM_INDEX}
 * is the sweep finding a sensor the index has no entry for - a {@code BLOCK_ENTITY_LOAD} or
 * {@code CHUNK_LOAD} callback that should have fired and did not, ADR-041's whole reason for
 * being: this is the silent failure that reads as "you are not detected" and is indistinguishable
 * from a correct negative without exactly this mechanism. {@link Kind#STALE_IN_INDEX} is the
 * reverse - the index holds a position the sweep found nothing backing, a missed
 * {@code BLOCK_ENTITY_UNLOAD}. {@link Kind#RADIUS_MISMATCH} is neither a missed callback nor a
 * geometry bug on its own; it means both sides agree a listener is there but disagree about its
 * radius, which given `RESEARCH-LOG.md` R1 point 4 is the one public formula for that number
 * would point at the value having changed after the index's insert-time read rather than at a
 * second read disagreeing with the first.
 */
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
