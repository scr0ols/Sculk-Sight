package com.scr0ols.sculksight.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BoundaryFaceExtractor}.
 *
 * <p>Like {@link DetectionSetTest}, these are free of the circularity problem: the oracle is
 * counting, not a belief about Minecraft.
 */
class BoundaryFaceExtractorTest {

	private record Emitted(int dx, int dy, int dz, Face face) {
	}

	private static List<Emitted> extract(DetectionSet set) {
		List<Emitted> emitted = new ArrayList<>();
		BoundaryFaceExtractor.extract(set, (dx, dy, dz, face) -> emitted.add(new Emitted(dx, dy, dz, face)));
		return emitted;
	}

	@Test
	@DisplayName("an empty set emits nothing")
	void emptySetEmitsNothing() {
		assertEquals(0, extract(new DetectionSet(4)).size());
	}

	@Test
	@DisplayName("a single isolated position emits all six of its faces")
	void isolatedPositionEmitsSixFaces() {
		DetectionSet set = new DetectionSet(4);
		set.add(0, 0, 0);

		List<Emitted> emitted = extract(set);

		assertEquals(6, emitted.size());

		Set<Face> faces = EnumSet.noneOf(Face.class);
		for (Emitted e : emitted) {
			assertEquals(0, e.dx() + e.dy() + e.dz());
			faces.add(e.face());
		}

		assertEquals(EnumSet.allOf(Face.class), faces, "all six directions, each exactly once");
	}

	@Test
	@DisplayName("two adjacent positions hide the faces between them")
	void adjacentPositionsHideTheSharedFaces() {
		// The whole value of boundary extraction is here in miniature: 12 faces become 10,
		// because the pair of faces facing each other are both interior.
		DetectionSet set = new DetectionSet(4);
		set.add(0, 0, 0);
		set.add(1, 0, 0);

		List<Emitted> emitted = extract(set);

		assertEquals(10, emitted.size());

		for (Emitted e : emitted) {
			boolean isHiddenFace = (e.dx() == 0 && e.face() == Face.EAST)
					|| (e.dx() == 1 && e.face() == Face.WEST);
			assertTrue(!isHiddenFace, "an interior face was emitted: " + e);
		}
	}

	@Test
	@DisplayName("a solid cube emits exactly its surface, and nothing from its interior")
	void solidCubeEmitsOnlyItsSurface() {
		// A filled cube of side n has 6 * n^2 outward faces. Checking this against a formula
		// rather than against a recorded number is what makes the test an oracle rather than a
		// snapshot: it would still be right if the extractor were rewritten completely.
		int radius = 3;
		int side = 2 * radius + 1;
		DetectionSet set = new DetectionSet(radius);

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					set.add(dx, dy, dz);
				}
			}
		}

		assertEquals(6 * side * side, extract(set).size());
	}

	@Test
	@DisplayName("members on the cube wall emit outward faces")
	void cubeWallMembersEmitOutward() {
		// This is the case that depends on DetectionSet.contains clamping to false outside the
		// cube rather than throwing. If the clamp were wrong, the shell would be open at the
		// corners of the bounding cube - which at radius 8 and 16 is exactly where the sphere
		// touches it.
		DetectionSet set = new DetectionSet(2);
		set.add(2, 0, 0);

		List<Emitted> emitted = extract(set);

		assertEquals(6, emitted.size());
		assertTrue(emitted.stream().anyMatch(e -> e.face() == Face.EAST),
				"the outward face at the cube wall must be emitted");
	}

	@Test
	@DisplayName("a solved shell emits far fewer faces than it has members")
	void aRealShellIsMostlySurface() {
		// Not an exact figure - the point is the order of magnitude PLAN.md section 3.3 relies
		// on when it says tens of thousands of quads become hundreds. A hollow spherical shell
		// of 2 109 members should emit roughly its surface area, not six faces per member.
		DetectionSet set = ShellSolver.solve(RecordingWorld.allClear(), 0, 0, 0, 8);
		int faces = extract(set).size();

		assertEquals(2109, set.size());
		assertTrue(faces < set.size(), "a solid ball emits fewer faces than it has members, got " + faces);
		assertTrue(faces > 0);
	}
}
