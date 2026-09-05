package com.scr0ols.sculksight.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;

import org.jspecify.annotations.Nullable;

/**
 * A snapshot of one sensor's bounding cube, copied off the live level on the client thread so that
 * a worker may solve against it. ARCHITECTURE.md section 6.2's snapshot phase, the first of the
 * four; this mod's counterpart to vanilla's {@code RenderSectionRegion} (RESEARCH-LOG.md R16).
 *
 * <p><b>It implements {@link BlockGetter}, which is what makes it invisible to everything above
 * it.</b> {@code LevelWorldView} already takes a {@code BlockGetter} rather than a
 * {@code ClientLevel} (ADR-015), so handing it one of these instead of a level changes no interface
 * and no line of the solver: the solver still sees only {@code WorldView.occluderOnSegment} and
 * still never learns it is reading a copy. R16's implications paragraph names this as the reason
 * the {@code WorldView} contract absorbs the snapshot unchanged, and it is the same relationship
 * vanilla's own {@code RenderSectionRegion} has to {@code BlockAndTintGetter}.
 *
 * <p><b>Once constructed it holds no reference to the level, and that is the point.</b> The chunk
 * lookups, the section copies and the two height values are all taken in {@link #of}, on the client
 * thread. Vanilla's own {@code RenderSectionRegion} keeps its {@code ClientLevel} for lighting and
 * biome tint, which this mod needs neither of; keeping none means there is nothing here a worker
 * could reach back through to a live object.
 *
 * <p><b>The span is derived from the cube, not fixed at three.</b> Vanilla hardcodes 3 by 3 by 3
 * because it always remeshes one section plus its neighbours. This mod's volume is the bounding
 * cube of edge {@code 2r + 1} (GLOSSARY.md; ARCHITECTURE.md section 5 derives why the cube rather
 * than the sphere is the sufficient volume), whose section span depends on the radius and on where
 * the sensor happens to sit. At radius 16 that is 33 blocks, which spans exactly three sections per
 * axis at every alignment, so the count matches vanilla's 27 there; at radius 8 it is 17 blocks,
 * which often spans two, and copying 27 sections would mean up to 19
 * {@code PalettedContainer.copy()} calls for sections the solve provably cannot read.
 * {@link SectionGrid} owns that arithmetic and is unit-tested without a game.
 *
 * <p><b>What this deliberately does not answer.</b> Which positions in the cube were unloaded is
 * knowable here, once, for the whole volume, and R16's implications and OPEN-QUESTIONS.md
 * section 12 both note that this is a cheaper and more honest place to answer section 12 from than
 * the segment test was. This class does not answer it: section 12's open half is which of three
 * player-visible behaviours to choose at a chunk edge, that is the author's call under PLAN.md
 * section 1, and taking it in passing while building the plumbing is exactly what
 * SESSION-KICKOFF.md invariant 6 rules out. An absent position reads as air here, as
 * {@link SectionSnapshot} documents, which is what the live level already did (R16 point 9) modulo
 * the {@code AIR}/{@code VOID_AIR} discrepancy R16 point 12 recorded.
 */
final class VolumeSnapshot implements BlockGetter {

	private final SectionGrid grid;

	private final SectionSnapshot[] sections;

	private final int minY;

	private final int height;

	private VolumeSnapshot(SectionGrid grid, SectionSnapshot[] sections, int minY, int height) {
		this.grid = grid;
		this.sections = sections;
		this.minY = minY;
		this.height = height;
	}

	/**
	 * Client thread. Copies every section the sensor's bounding cube touches.
	 *
	 * <p><b>This must run on the client thread</b>, which is the whole of what R16 requires and what
	 * makes every read afterwards legal. Calling it from a worker would reintroduce precisely the
	 * race the copy exists to remove.
	 *
	 * <p>The chunk is looked up once per column rather than once per section, since every section in
	 * a column comes from the same chunk. The lookup is vanilla's own
	 * {@code Level.getChunk(int, int)}, the same one {@code RenderRegionCache.getSectionDataCopy}
	 * uses; on the client it returns an {@code EmptyLevelChunk} rather than null for a column that
	 * is not loaded (R16 point 9), which {@link SectionSnapshot} handles as air.
	 *
	 * @param level the live level, read here and not retained
	 * @param sensorX the sensor's block X, the centre of the cube
	 * @param sensorY the sensor's block Y
	 * @param sensorZ the sensor's block Z
	 * @param radius the sensor's own listener radius, derived from the game at the call site
	 */
	static VolumeSnapshot of(Level level, int sensorX, int sensorY, int sensorZ, int radius) {
		// SectionPos.blockToSectionCoord is vanilla's own block-to-section reduction, used rather
		// than a shift of this project's own so that the section size stays a game fact
		// (PLAN.md section 3.2). RenderSectionRegion reduces positions the same way.
		SectionGrid grid = SectionGrid.over(
				SectionPos.blockToSectionCoord(sensorX - radius),
				SectionPos.blockToSectionCoord(sensorY - radius),
				SectionPos.blockToSectionCoord(sensorZ - radius),
				SectionPos.blockToSectionCoord(sensorX + radius),
				SectionPos.blockToSectionCoord(sensorY + radius),
				SectionPos.blockToSectionCoord(sensorZ + radius));

		SectionSnapshot[] sections = new SectionSnapshot[grid.size()];

		int maxSectionX = grid.minSectionX() + grid.spanX();
		int maxSectionY = grid.minSectionY() + grid.spanY();
		int maxSectionZ = grid.minSectionZ() + grid.spanZ();

		for (int sectionZ = grid.minSectionZ(); sectionZ < maxSectionZ; sectionZ++) {
			for (int sectionX = grid.minSectionX(); sectionX < maxSectionX; sectionX++) {
				LevelChunk chunk = level.getChunk(sectionX, sectionZ);

				for (int sectionY = grid.minSectionY(); sectionY < maxSectionY; sectionY++) {
					sections[grid.index(sectionX, sectionY, sectionZ)] =
							new SectionSnapshot(chunk, chunk.getSectionIndexFromSectionY(sectionY));
				}
			}
		}

		return new VolumeSnapshot(grid, sections, level.getMinY(), level.getHeight());
	}

	/**
	 * The only method of this interface the solve path actually calls, confirmed rather than
	 * assumed: R16 point 12 disassembled {@code BlockGetter.isBlockInLine}'s own per-position
	 * visitor and found {@code getBlockState(pos)} and the caller's predicate, with no
	 * {@code getFluidState} and no {@code getBlockEntity}.
	 *
	 * <p>A position outside the copied grid reads as air. ARCHITECTURE.md section 5 derives that no
	 * block outside the bounding cube can occlude any ray inside it, so this is not expected to
	 * happen; {@link SectionGrid#index} explains why it is handled rather than trusted.
	 */
	@Override
	public BlockState getBlockState(BlockPos pos) {
		int index = grid.index(
				SectionPos.blockToSectionCoord(pos.getX()),
				SectionPos.blockToSectionCoord(pos.getY()),
				SectionPos.blockToSectionCoord(pos.getZ()));

		if (index < 0) {
			return Blocks.AIR.defaultBlockState();
		}

		return sections[index].getBlockState(pos);
	}

	/**
	 * Declared by {@link BlockGetter} and never called by this mod's solve path (R16 point 12).
	 * Answered from the copied state rather than left to throw, since it is derivable from what this
	 * snapshot already holds and vanilla's own {@code RenderSectionRegion} answers it the same way,
	 * by asking its own {@code getBlockState}.
	 */
	@Override
	public FluidState getFluidState(BlockPos pos) {
		return getBlockState(pos).getFluidState();
	}

	/**
	 * Always null: this snapshot deliberately copies no block entities.
	 *
	 * <p>{@link SectionSnapshot}'s class javadoc carries the reasoning in full. In short, R16
	 * point 12 established the solve path never reads one, and R10 point 11 measured what copying
	 * them would cost in a dense scene. <b>Null rather than a throw</b> because
	 * {@code BlockGetter.getBlockEntity} is declared to return null for a position with no block
	 * entity, so null is a legal answer this interface's own callers already handle, whereas a throw
	 * would be a new failure mode for a method whose contract does not have one. A future mode that
	 * needs block entities through a snapshot must add the copy here rather than assume this returns
	 * them.
	 */
	@Override
	public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
		return null;
	}

	/** The level's own value, read once at snapshot time. */
	@Override
	public int getMinY() {
		return minY;
	}

	/** The level's own value, read once at snapshot time. */
	@Override
	public int getHeight() {
		return height;
	}
}
