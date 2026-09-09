package com.scr0ols.sculksight.client;

import net.minecraft.core.BlockPos;
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
 * <p>The mod-owned predicate in {@link VibrationOcclusion} preserves vanilla's occlusion tag
 * and adds the 16 registered wool-carpet blocks, because wool carpets are intentionally treated
 * as vibration occluders by this mod. The separate source-below check uses vanilla's
 * {@code DAMPENS_VIBRATIONS} tag, which includes both wool and wool carpets.
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
	public boolean dampensVibrationsBelow(int sourceX, int sourceY, int sourceZ) {
		return VibrationOcclusion.isDampener(level.getBlockState(new BlockPos(sourceX, sourceY - 1, sourceZ)));
	}

	@Override
	public boolean occluderOnSegment(double fromX, double fromY, double fromZ,
			double toX, double toY, double toZ) {

		ClipBlockStateContext context = new ClipBlockStateContext(
				new Vec3(fromX, fromY, fromZ),
				new Vec3(toX, toY, toZ),
				VibrationOcclusion::isOccluder);

		// A miss returns BlockHitResult.miss(...), whose type is not BLOCK, so testing for
		// BLOCK is the same test vanilla's isOccluded makes on the same call (R4).
		return level.isBlockInLine(context).getType() == HitResult.Type.BLOCK;
	}
}
