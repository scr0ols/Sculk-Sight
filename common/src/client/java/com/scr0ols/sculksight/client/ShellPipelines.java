package com.scr0ols.sculksight.client;

import java.util.Optional;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;

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
 * <p><b>There were four.</b> ADR-028 added the same pair again over the {@code LINES_SNIPPET}
 * family, for the crease-edge outline; ADR-030 superseded it and those two are gone. What R15.7
 * established about that family, in particular that its vertex shader already pulls a line toward
 * the camera and so needs no depth bias of its own, stands on its own and does not depend on the
 * pipelines existing here.
 *
 * <p><b>Every pipeline here inherits its bind-group layout and its shader from one vanilla
 * snippet, and that is a correctness requirement rather than tidiness.</b> {@code GlProgram
 * .setupBindGroupLayouts} matches uniform blocks <b>by name</b> and silently drops what the
 * compiled shader lacks, and {@code DynamicTransforms} is deliberately outside the set of names it
 * tolerates in the reverse direction (R15.3). A pipeline whose layout and shader were chosen
 * independently would therefore draw in the wrong place with no error at all. Assembling each from
 * a single snippet makes that mismatch impossible by construction.
 *
 * <p><b>Nothing here is registered</b>, per ADR-024. {@code RenderPipelines.register(...)} is
 * public and optional; registering buys eager compilation on every resource reload at the price of
 * a hard throw if compilation fails, and lazy compilation at first draw is the better trade for
 * pipelines that exist only while a shell is on screen. Resolution happens in
 * {@code GlDevice.getOrCompilePipeline}, which is keyed by the pipeline object and does not consult
 * the registry (R15.3).
 *
 * <p><b>No shader asset is shipped.</b> Each snippet already names its shaders on both stages, the
 * reference is an {@code Identifier} resolved through the ordinary {@code ResourceManager}, and
 * this mod changes state only, so vanilla's own assets are what link (R15.3).
 */
public final class ShellPipelines {

	/**
	 * The depth bias the depth-tested face pass carries, as
	 * {@code (depthBiasScaleFactor, depthBiasConstant)} mapped straight onto
	 * {@code glPolygonOffset} by {@code GlCommandEncoder.applyPipelineState} (R15.7).
	 *
	 * <p><b>Why there is a bias at all.</b> The shell quads lie exactly on block boundaries, so a
	 * shell face flush against a terrain face compares equal under {@code GREATER_THAN_OR_EQUAL}
	 * and passes. R15.5 recorded that as a thing to look for rather than a claim; the first live
	 * run found it reads as speckle, most visibly under a wool block. See ADR-024's 2026-09-02
	 * addendum.
	 *
	 * <p><b>Why these two values.</b> They are vanilla's own for the same problem, taken from
	 * {@code RenderPipelines.LINES_DEPTH_BIAS} rather than invented (R15.7).
	 *
	 * <p><b>Why positive is forward, and the one thing here that is a derivation rather than a
	 * read.</b> A positive polygon offset raises the written depth value, and 26.2 is reverse-Z, so
	 * the larger value is the nearer one (R15.5) and a positive bias wins the coplanar comparison.
	 * That chain is the OpenGL specification joined to a source read, not a source read on its own,
	 * so it is confirmed on screen and not here. If it turns out backwards, negating these two
	 * constants is the whole correction.
	 */
	private static final float DEPTH_BIAS_SCALE = 1.0F;

	private static final float DEPTH_BIAS_CONSTANT = 1.0F;

	/**
	 * The depth-tested face pass: vanilla's {@code DEBUG_QUADS} state with a depth bias added.
	 *
	 * <p>Everything but the depth state is inherited from {@code DEBUG_FILLED_SNIPPET} and is
	 * exactly what ADR-021 and ADR-022 ask for: {@code BlendFunction.TRANSLUCENT}, no back-face
	 * culling so the far side of the shell is drawn, and {@code DynamicTransforms} via
	 * {@code BindGroupLayouts.MATRICES_PROJECTION}. The depth state restates {@code DEBUG_QUADS}'s
	 * own comparison and its absent depth write, and adds the bias above. Writing depth here would
	 * let the shell occlude the terrain behind it, which is the opposite of what a translucent
	 * overlay wants.
	 */
	public static final RenderPipeline FACES_DEPTH_TESTED = RenderPipeline
			.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(SculkSight.MOD_ID, "pipeline/shell_faces_depth_tested"))
			.withDepthStencilState(new DepthStencilState(
					CompareOp.GREATER_THAN_OR_EQUAL, false, DEPTH_BIAS_SCALE, DEPTH_BIAS_CONSTANT))
			.build();

	/**
	 * The see-through face pass: the same snippet with the depth state cleared.
	 *
	 * <p>How the depth state is removed was an unread lookup in ARCHITECTURE.md section 8 and was
	 * read on 2026-09-01 (R15.5). {@code RenderPipeline.Builder} carries the state as an
	 * {@code Optional<DepthStencilState>} and exposes two setters: one taking a
	 * {@code DepthStencilState}, which wraps it in {@code Optional.of}, and one taking the
	 * {@code Optional} directly. Passing {@code Optional.empty()} to the second is what clears a
	 * snippet-supplied value, because {@code build()} then passes {@code orElse(null)} into the
	 * pipeline. There is no clear method and no need to compose the pipeline without the snippet.
	 *
	 * <p>A null depth state is genuinely no depth test rather than a permissive one:
	 * {@code GlCommandEncoder.applyPipelineState} branches on null and calls
	 * {@code _disableDepthTest()} and {@code _depthMask(false)}. The pipeline's
	 * {@code wantsDepthTexture()} then returns false, so the encoder does not warn about the depth
	 * attachment the shared render pass still carries for the other passes. That same branch also
	 * calls {@code _disablePolygonOffset()}, which is why this pass needs no bias of its own: with
	 * no depth test there is nothing to lose a comparison against.
	 */
	public static final RenderPipeline FACES_SEE_THROUGH = RenderPipeline
			.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(SculkSight.MOD_ID, "pipeline/shell_faces_see_through"))
			.withDepthStencilState(Optional.empty())
			.build();

	private ShellPipelines() {
	}
}
