package com.scr0ols.sculksight.client;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import com.scr0ols.sculksight.SculkSight;

/**
 * The two render pipelines the shell draws with, partitioned so that every fragment the shell
 * touches is drawn by exactly one of them.
 *
 * <p>26.2 is reverse-Z: nearer geometry carries the larger depth value, so
 * {@code CompareOp.GREATER_THAN_OR_EQUAL} is the ordinary "in front of or equal to what's already
 * there" test. Its exact complement under that scheme is {@code CompareOp.LESS_THAN} ("strictly
 * behind"), not {@code LESS_THAN_OR_EQUAL} - the equal case belongs to the visible pass, so the two
 * comparisons never overlap and never leave a gap. Both passes carry the same depth bias so the
 * complement holds exactly for shell faces that lie exactly on a terrain face, not just
 * approximately.
 */
public final class ShellPipelines {

	private static final float DEPTH_BIAS_SCALE = 1.0F;

	private static final float DEPTH_BIAS_CONSTANT = 1.0F;

	/** The visible face pass: draws where the shell is in front of or level with the world. */
	public static final RenderPipeline FACES_DEPTH_TESTED = RenderPipeline
			.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(SculkSight.MOD_ID, "pipeline/shell_faces_depth_tested"))
			.withDepthStencilState(new DepthStencilState(
					CompareOp.GREATER_THAN_OR_EQUAL, false, DEPTH_BIAS_SCALE, DEPTH_BIAS_CONSTANT))
			.build();

	/**
	 * The occluded face pass: draws only where the shell is strictly behind the world already
	 * drawn there, the exact complement of {@link #FACES_DEPTH_TESTED}. Never draws the same
	 * fragment as that pass.
	 */
	public static final RenderPipeline FACES_SEE_THROUGH = RenderPipeline
			.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(SculkSight.MOD_ID, "pipeline/shell_faces_see_through"))
			.withDepthStencilState(new DepthStencilState(
					CompareOp.LESS_THAN, false, DEPTH_BIAS_SCALE, DEPTH_BIAS_CONSTANT))
			.build();

	private ShellPipelines() {
	}
}
