package com.scr0ols.sculksight.solver;

/**
 * A per-position predicate applied alongside the range test, per ARCHITECTURE.md section 4.4.
 *
 * <p>This exists so that c-docs/OPEN-QUESTIONS.md section 1 - whether positions inside solid
 * blocks belong in the rendered set - can be decided later without reshaping anything above
 * it. All three candidate framings in that question are per-position predicates over world
 * state, so whichever wins changes what {@link #keep} returns and adds one method to
 * {@link WorldView}; it changes neither {@link DetectionSet}, nor boundary extraction, nor
 * the cache, nor the threading model.
 *
 * <p><b>v0.0 passes {@link #ACCEPT_ALL}</b>, which is the conservative choice rather than
 * merely the current one: it draws the full geometric truth and claims nothing about what
 * can happen at a position. If the mod ever filters, the drawn set is no longer the detection
 * set but a subset chosen for usefulness, and the interface must say so or the governing
 * principle is violated from the other side.
 */
@FunctionalInterface
public interface PositionFilter {

	/** Offsets are relative to the sensor block, as everywhere else in this package. */
	boolean keep(int dx, int dy, int dz);

	/**
	 * Keeps everything.
	 *
	 * <p>A field in an interface is implicitly {@code public static final}, so this is a
	 * constant rather than an instance member, and a functional interface may hold one
	 * without ceasing to be functional - only the abstract method count matters.
	 */
	PositionFilter ACCEPT_ALL = (dx, dy, dz) -> true;
}
