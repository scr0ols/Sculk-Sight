package com.scr0ols.sculksight.solver;

/**
 * The solver's entire contact with the world.
 *
 * <p>One method, primitive coordinates, no Minecraft types. Recorded as
 * c-docs/DECISIONS.md ADR-015 and specified in c-docs/ARCHITECTURE.md section 4.1.
 *
 * <p><b>This interface is the seam where the mod trusts vanilla without having read it.</b>
 * ARCHITECTURE.md section 2.2 draws the boundary here deliberately: the outer six-ray rule
 * of R4 is reproduced above this line, in ordinary Java a unit test can reach, while the
 * inner segment traversal stays below it. A JUnit fake implements a different traversal
 * from the game's, so unit tests validate the six-ray composition and never the traversal.
 * Only differential verification checks that seam - see c-docs/TESTING-STRATEGY.md section 3.
 */
public interface WorldView {

	/**
	 * True if any block whose state matches the vibration-occlusion predicate lies on the
	 * segment from (fromX, fromY, fromZ) to (toX, toY, toZ).
	 *
	 * <p>Implementations back this with {@code BlockGetter#isBlockInLine(ClipBlockStateContext)},
	 * passing the predicate {@code state -> state.is(BlockTags.OCCLUDES_VIBRATION_SIGNALS)} and
	 * treating a {@code HitResult} of type {@code BLOCK} as true (R3, R4). The tag is never
	 * named in this module (ARCHITECTURE.md section 2.3).
	 *
	 * <p>Coordinates are {@code double} rather than integers because the endpoints are block
	 * centres, possibly nudged (R4), so they are genuinely continuous even though the solver's
	 * domain is integral (R12). Six primitives rather than two objects means the solver
	 * allocates nothing per ray, and at radius 16 there are up to six rays for each of 17 077
	 * in-range positions.
	 *
	 * <p><b>Behaviour across a position the client has not loaded is undefined by this
	 * contract</b>, deliberately and not by omission: what the underlying traversal does there
	 * has not been read, and nothing is asserted about it in either direction. Tracked as
	 * c-docs/OPEN-QUESTIONS.md section 12.
	 */
	boolean occluderOnSegment(double fromX, double fromY, double fromZ,
			double toX, double toY, double toZ);
}
