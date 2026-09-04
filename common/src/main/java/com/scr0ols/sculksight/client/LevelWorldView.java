package com.scr0ols.sculksight.client;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.scr0ols.sculksight.solver.WorldView;

/**
 * The adapter that backs {@link WorldView} with a real Minecraft level.
 *
 * <p>This is the entire surface where the solver meets the game, and it is deliberately this
 * small (ARCHITECTURE.md §2.2, ADR-015). Everything correctness-critical about the six-ray
 * rule lives above it in {@code common}-style code that JUnit can reach; everything below it
 * is vanilla's own traversal, which this project has not read and does not reimplement.
 *
 * <p><b>The occlusion tag is named here and nowhere else</b> (ARCHITECTURE.md §2.3). The
 * solver never sees a {@code BlockState} and never learns which tag matters, so R3's
 * still-unenumerated tag membership is not a gap in the solver — the contract needs the
 * predicate, not the list, and plan §3.2's derive-from-the-game rule forbids carrying the list
 * anyway.
 *
 * <p>Takes a {@link BlockGetter} rather than a {@code ClientLevel} because
 * {@code isBlockInLine} is a {@code default} method declared on {@code BlockGetter} (R4
 * addendum, point 1). Widening the parameter costs nothing and lets the same class serve a
 * client level for the mod and a server level for a verification cross-check.
 */
public final class LevelWorldView implements WorldView {

	private final BlockGetter level;

	public LevelWorldView(BlockGetter level) {
		this.level = level;
	}

	@Override
	public boolean occluderOnSegment(double fromX, double fromY, double fromZ,
			double toX, double toY, double toZ) {

		ClipBlockStateContext context = new ClipBlockStateContext(
				new Vec3(fromX, fromY, fromZ),
				new Vec3(toX, toY, toZ),
				state -> state.is(BlockTags.OCCLUDES_VIBRATION_SIGNALS));

		// A miss returns BlockHitResult.miss(...), whose type is not BLOCK, so testing for
		// BLOCK is the same test vanilla's isOccluded makes on the same call (R4).
		return level.isBlockInLine(context).getType() == HitResult.Type.BLOCK;
	}
}
