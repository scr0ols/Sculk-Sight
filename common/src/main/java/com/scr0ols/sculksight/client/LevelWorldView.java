package com.scr0ols.sculksight.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.scr0ols.sculksight.solver.WorldView;

/** The adapter that backs {@link WorldView} with a real Minecraft level. */
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

		return level.isBlockInLine(context).getType() == HitResult.Type.BLOCK;
	}
}
