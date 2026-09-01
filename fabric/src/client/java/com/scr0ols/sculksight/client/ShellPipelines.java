package com.scr0ols.sculksight.client;

import java.util.Optional;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import com.scr0ols.sculksight.SculkSight;

/**
 * The two pipelines the shell draws with. c-docs/DECISIONS.md ADR-021 and ADR-024.
 *
 * <p>ADR-021 draws the shell twice from one cached buffer: a see-through pass with no depth test,
 * so the whole shell is always present, then a depth-tested pass so the part with line of sight to
 * the camera is reinforced. ADR-024 fixes both to the {@code DEBUG_FILLED_SNIPPET} family at
 * {@code DefaultVertexFormat.POSITION_COLOR}.
 *
 * <p><b>Both pipelines inherit their bind-group layout and their shader from the same vanilla
 * snippet, and that is a correctness requirement rather than tidiness.</b> {@code GlProgram
 * .setupBindGroupLayouts} matches uniform blocks <b>by name</b> and silently drops what the
 * compiled shader lacks, and {@code DynamicTransforms} is deliberately outside the set of names it
 * tolerates in the reverse direction (R15.3). A pipeline whose layout and shader were chosen
 * independently would therefore draw in the wrong place with no error at all. Assembling both from
 * {@code DEBUG_FILLED_SNIPPET} makes that mismatch impossible by construction.
 */
public final class ShellPipelines {

	/**
	 * The depth-tested pass: vanilla's own pipeline, unmodified (ADR-024).
	 *
	 * <p>Its state is already exactly what ADR-021 and ADR-022 ask for -
	 * {@code BlendFunction.TRANSLUCENT}, {@code cull = false} so the far side of the shell is
	 * drawn, depth-tested with no depth write, and {@code DynamicTransforms} via
	 * {@code BindGroupLayouts.MATRICES_PROJECTION}.
	 */
	public static final RenderPipeline DEPTH_TESTED = RenderPipelines.DEBUG_QUADS;

	/**
	 * The see-through pass: built by this mod from the same snippet with the depth state cleared.
	 *
	 * <p><b>How the depth state is removed</b> - ARCHITECTURE.md section 8 listed this as an
	 * unread lookup and it was read on 2026-09-01. {@code RenderPipeline.Builder} carries the
	 * state as an {@code Optional<DepthStencilState>} and exposes two setters: one taking a
	 * {@code DepthStencilState}, which wraps it in {@code Optional.of}, and one taking the
	 * {@code Optional} directly. Passing {@code Optional.empty()} to the second is what clears a
	 * snippet-supplied value, because {@code build()} then passes {@code orElse(null)} into the
	 * pipeline. There is no "clear" method and no need to compose the pipeline without the
	 * snippet.
	 *
	 * <p>A null depth state is genuinely no depth test rather than a permissive one:
	 * {@code GlCommandEncoder.applyPipelineState} branches on null and calls
	 * {@code _disableDepthTest()} and {@code _depthMask(false)}. The pipeline's
	 * {@code wantsDepthTexture()} then returns false, so the encoder does not warn about the depth
	 * attachment the shared render pass still carries for the other pass.
	 *
	 * <p><b>Not registered</b>, per ADR-024. {@code RenderPipelines.register(...)} is public and
	 * optional; registering buys eager compilation on every resource reload at the price of a hard
	 * throw if compilation fails, and lazy compilation at first draw is the better trade for a
	 * pipeline that exists only while a shell is on screen. Resolution happens in
	 * {@code GlDevice.getOrCompilePipeline}, which is keyed by the pipeline object and does not
	 * consult the registry (R15.3).
	 *
	 * <p>No shader asset is shipped. The snippet already names {@code core/position_color} on both
	 * stages, the reference is an {@code Identifier} resolved through the ordinary
	 * {@code ResourceManager}, and this mod changes state only - so vanilla's own asset is what
	 * links (R15.3).
	 */
	public static final RenderPipeline SEE_THROUGH = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(SculkSight.MOD_ID, "pipeline/shell_see_through"))
			.withDepthStencilState(Optional.empty())
			.build();

	private ShellPipelines() {
	}
}
