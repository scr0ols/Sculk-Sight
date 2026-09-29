package com.scr0ols.sculksight.client;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * How strongly the see-through pass is faded out for near-camera occluders while the camera is
 * inside a shell.
 *
 * <p>The see-through pass paints an occluded shell fragment over whatever opaque geometry already
 * blocks it - typically the floor right under the camera when the player is standing inside a
 * shell. That reads fine for occluders a few blocks away, but makes the floor at the player's own
 * feet harder to read while stepping through. This fades the pass's alpha toward zero as the
 * nearest solid block straight below the camera gets close, and leaves it untouched once that
 * block is far enough away that footing is no longer a concern.
 *
 * <p>Scoped to straight down, on purpose: that is exactly what was reported (the floor underfoot),
 * and it is the one direction a player always needs to read while moving. It does not touch the
 * see-through pass anywhere else - walls beside the player, or the shell seen dimly through a
 * distant wall from outside it, are unaffected.
 */
final class NearCameraFade {

	/** At or below this distance to the nearest solid block underfoot, the pass is fully suppressed. */
	static final float FULLY_SUPPRESSED_BLOCKS = 1.0F;

	/** At or beyond this distance, the pass is left at its configured strength. */
	static final float UNAFFECTED_BLOCKS = 4.0F;

	private NearCameraFade() {
	}

	/**
	 * The see-through pass's alpha multiplier for the given camera position: casts straight down
	 * from the camera and delegates the found distance to {@link #factorFor(double)}.
	 */
	static float factorForCamera(BlockGetter level, Vec3 camera) {
		return factorFor(distanceToNearestSolidBelow(level, camera));
	}

	/**
	 * The multiplier for a given distance to the nearest solid block below the camera: 0 right at
	 * the floor, ramping linearly up to 1 by {@link #UNAFFECTED_BLOCKS}, and 1 if nothing solid is
	 * that close.
	 */
	static float factorFor(double distanceToNearestSolidBelow) {
		if (distanceToNearestSolidBelow <= FULLY_SUPPRESSED_BLOCKS) {
			return 0.0F;
		}
		if (distanceToNearestSolidBelow >= UNAFFECTED_BLOCKS) {
			return 1.0F;
		}

		return (float) ((distanceToNearestSolidBelow - FULLY_SUPPRESSED_BLOCKS)
				/ (UNAFFECTED_BLOCKS - FULLY_SUPPRESSED_BLOCKS));
	}

	/**
	 * The distance from the camera straight down to the nearest block with collision (the same
	 * test the game itself uses for "would something stand on this"), or {@link #UNAFFECTED_BLOCKS}
	 * if nothing collidable is found within that range. Deliberately not
	 * {@code VibrationOcclusion.isOccluder}: that predicate is the solver's narrow, vanilla-matched
	 * definition of what blocks a vibration signal, which excludes ordinary solid blocks like
	 * stone. This is about what the player can physically stand on, which is a different and much
	 * broader question.
	 */
	private static double distanceToNearestSolidBelow(BlockGetter level, Vec3 camera) {
		Vec3 to = camera.subtract(0.0, UNAFFECTED_BLOCKS, 0.0);
		ClipContext context = new ClipContext(
				camera, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
		BlockHitResult hit = level.clip(context);

		if (hit.getType() != HitResult.Type.BLOCK) {
			return UNAFFECTED_BLOCKS;
		}

		return camera.distanceTo(hit.getLocation());
	}
}
