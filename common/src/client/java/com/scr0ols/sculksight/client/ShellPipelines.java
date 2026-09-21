package com.scr0ols.sculksight.client;

import java.util.Optional;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import com.scr0ols.sculksight.SculkSight;

/** The two render pipelines the shell draws with. */
public final class ShellPipelines {

	private static final float DEPTH_BIAS_SCALE = 1.0F;

	private static final float DEPTH_BIAS_CONSTANT = 1.0F;

	/** The depth-tested face pass, drawn with a small depth bias. */
	public static final RenderPipeline FACES_DEPTH_TESTED = RenderPipeline
			.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(SculkSight.MOD_ID, "pipeline/shell_faces_depth_tested"))
			.withDepthStencilState(new DepthStencilState(
					CompareOp.GREATER_THAN_OR_EQUAL, false, DEPTH_BIAS_SCALE, DEPTH_BIAS_CONSTANT))
			.build();

	/** The see-through face pass, drawn with no depth test. */
	public static final RenderPipeline FACES_SEE_THROUGH = RenderPipeline
			.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(SculkSight.MOD_ID, "pipeline/shell_faces_see_through"))
			.withDepthStencilState(Optional.empty())
			.build();

	private ShellPipelines() {
	}
}
