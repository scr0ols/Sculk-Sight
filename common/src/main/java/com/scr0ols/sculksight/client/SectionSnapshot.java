package com.scr0ols.sculksight.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

import org.jspecify.annotations.Nullable;

/**
 * One chunk section's block states, deep-copied off the live level so that a worker thread may read
 * them. This mod's counterpart to vanilla's own {@code SectionCopy} (RESEARCH-LOG.md R16 point 1),
 * which is the class this one is modelled on rather than invented against.
 *
 * <p><b>Why a copy exists at all.</b> R16 established that a worker may not read a live
 * {@code ClientLevel}: nothing on the read path synchronises a reader against a writer, and
 * {@code PalettedContainer.read} rewrites an existing container's palette in place, so a racing
 * read can return a well-formed but wrong state (R16 point 5) or throw
 * {@code MissingPaletteEntryException} inside the solver (R16 point 7). The copy is what makes the
 * off-thread solve legal, and it has to be taken on the client thread (ARCHITECTURE.md
 * section 6.2).
 *
 * <p><b>The copy is deep where it needs to be.</b> {@code PalettedContainer.copy()} clones the
 * backing {@code long[]} rather than sharing it (R16 point 1), so the states here are genuinely
 * this object's own. One sharing edge R16 point 11 recorded is deliberately not assumed away:
 * {@code SingleValuePalette.copy()} returns {@code this} and its {@code read} assigns
 * {@code this.value}, so a copy could in principle observe a later change to the container it came
 * from. R16 marks that as an observation rather than a demonstrated failure and did not trace
 * whether the case is reachable; this class inherits vanilla's own {@code copy()} and therefore
 * inherits that edge exactly, which is recorded here so it is not mistaken for an isolation
 * guarantee this project has verified.
 *
 * <p><b>Two deliberate divergences from vanilla's {@code SectionCopy}, both narrowings.</b>
 *
 * <ol>
 * <li><b>No block-entity map.</b> {@code SectionCopy} does
 * {@code ImmutableMap.copyOf(levelChunk.getBlockEntities())} per section, so vanilla copies a whole
 * chunk's map up to 27 times per region. R16 point 12 disassembled
 * {@code BlockGetter.isBlockInLine}'s own per-position visitor and found it calls
 * {@code getBlockState} and nothing else, so this mod's solve path never reads a block entity
 * through the snapshot at all, and R10 point 11 measured up to 3589 block entities in one column of
 * a built-up scene. Copying that for something never read is cost this mod does not have to pay.
 * {@link VolumeSnapshot#getBlockEntity} returns null and says so.</li>
 * <li><b>No debug-world branch.</b> {@code SectionCopy.getBlockState} special-cases
 * {@code levelChunk.getLevel().isDebug()}, substituting barriers at y 60 and
 * {@code DebugLevelSource} states at y 70. That is the vanilla debug world, which this mod targets
 * nowhere: it draws a sensor's effective range in ordinary play. Reproducing the branch would mean
 * carrying a code path no scene in TESTING-STRATEGY.md section 5 can reach.</li>
 * </ol>
 *
 * <p><b>An absent section reads as {@code AIR}, and that is vanilla's substitution rather than this
 * project's choice.</b> R16 point 12 recorded the discrepancy in full: an unloaded position read
 * through the live level reaches {@code EmptyLevelChunk.getBlockState} and returns
 * {@code VOID_AIR} (R16 point 9), while vanilla's own snapshot mechanism substitutes ordinary
 * {@code AIR} in the same situation. Neither block is expected to be in the occlusion tag, so this
 * is not asserted to change any occlusion result. <b>It does not decide OPEN-QUESTIONS.md
 * section 12</b>, whose open half is what the mod should <i>draw</i> at a position the client has
 * not loaded, and which is the author's call under PLAN.md section 1.
 */
final class SectionSnapshot {

	/**
	 * Null for a section that holds nothing worth copying: one that is only air, one outside the
	 * chunk's own section array, or a chunk that is not loaded. All three read as air, which is
	 * exactly the set of cases vanilla's own {@code section} field is null for (R16 point 1).
	 */
	private final @Nullable PalettedContainer<BlockState> states;

	/**
	 * Client thread. Copies one section out of a live chunk.
	 *
	 * @param chunk the chunk owning the section, as returned by the level's own chunk lookup
	 * @param sectionIndex the section's index within that chunk, from
	 *        {@code getSectionIndexFromSectionY}
	 */
	SectionSnapshot(LevelChunk chunk, int sectionIndex) {
		this.states = copyStates(chunk, sectionIndex);
	}

	private static @Nullable PalettedContainer<BlockState> copyStates(LevelChunk chunk, int sectionIndex) {
		// An unloaded column arrives here as EmptyLevelChunk, whose section array is not meaningful.
		// Vanilla's SectionCopy takes the same branch first, before touching getSections().
		if (chunk instanceof EmptyLevelChunk) {
			return null;
		}

		LevelChunkSection[] sections = chunk.getSections();

		// A cube reaching above or below the level's build height produces a section index outside
		// this array. Vanilla bounds-checks the same way rather than clamping, and air is the honest
		// answer: there is no block there to occlude anything.
		if (sectionIndex < 0 || sectionIndex >= sections.length) {
			return null;
		}

		LevelChunkSection section = sections[sectionIndex];

		return section.hasOnlyAir() ? null : section.getStates().copy();
	}

	/**
	 * The state at one position, which must lie inside the section this snapshot copied.
	 *
	 * <p>The caller has already chosen this snapshot by section coordinate, so only the position's
	 * offset within the section matters. {@code SectionPos.sectionRelative} is vanilla's own idiom
	 * for that reduction, used here rather than a masking constant of this project's own so that the
	 * section size stays a game fact (PLAN.md section 3.2). {@code SectionCompiler} reduces
	 * positions the same way.
	 *
	 * <p>Unlike vanilla's, this method does not wrap the container read in a {@code CrashReport}:
	 * every failure mode R16 found on the live path is one the copy exists to remove, and a throw
	 * from here would mean the copy itself is wrong, which is a bug to see plainly rather than to
	 * dress as a vanilla crash report this mod builds no other part of.
	 */
	BlockState getBlockState(BlockPos pos) {
		if (states == null) {
			return Blocks.AIR.defaultBlockState();
		}

		return states.get(
				SectionPos.sectionRelative(pos.getX()),
				SectionPos.sectionRelative(pos.getY()),
				SectionPos.sectionRelative(pos.getZ()));
	}
}
