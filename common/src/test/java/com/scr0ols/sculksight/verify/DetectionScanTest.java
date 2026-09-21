package com.scr0ols.sculksight.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.scr0ols.sculksight.solver.DetectionSet;
import com.scr0ols.sculksight.solver.ShellSolution;
import com.scr0ols.sculksight.solver.ShellSolver;
import com.scr0ols.sculksight.solver.WorldView;

class DetectionScanTest {

	private static final int SENSOR_X = -14;
	private static final int SENSOR_Y = 71;
	private static final int SENSOR_Z = 305;

	private static final WorldView OPEN_AIR =
			(fromX, fromY, fromZ, toX, toY, toZ) -> false;

	private static final WorldView FULLY_ENCLOSED =
			(fromX, fromY, fromZ, toX, toY, toZ) -> true;

	private static final WorldView HALF_BLOCKED =
			(fromX, fromY, fromZ, toX, toY, toZ) -> Math.floor(fromX) - SENSOR_X < 0;

	private static ShellSolution scan(WorldView world, int radius) {
		return DetectionScan.scan(world, SENSOR_X, SENSOR_Y, SENSOR_Z, radius);
	}

	private static ShellSolution solve(WorldView world, int radius) {
		return ShellSolver.solveDetailed(world, SENSOR_X, SENSOR_Y, SENSOR_Z, radius);
	}

	private static Set<String> disagreements(ShellSolution left, ShellSolution right, int radius) {
		Set<String> found = new java.util.TreeSet<>();

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					boolean sameAccepted = left.accepted().contains(dx, dy, dz)
							== right.accepted().contains(dx, dy, dz);
					boolean sameOccluded = left.occludedOut().contains(dx, dy, dz)
							== right.occludedOut().contains(dx, dy, dz);

					if (!sameAccepted || !sameOccluded) {
						found.add("(" + dx + ", " + dy + ", " + dz + ")");
					}
				}
			}
		}

		return found;
	}

	@Test
	@DisplayName("in open air the scan matches the shell solver exactly, position for position")
	void openAirScanMatchesTheShellSolver() {
		int radius = 8;

		assertEquals(Set.of(), disagreements(scan(OPEN_AIR, radius), solve(OPEN_AIR, radius), radius));
	}

	@Test
	@DisplayName("with occlusion the scan matches the shell solver exactly, in both classes")
	void occludedScanMatchesTheShellSolver() {
		int radius = 8;
		ShellSolution scanned = scan(HALF_BLOCKED, radius);

		assertTrue(scanned.occludedOut().size() > 0, "fixture sanity check: something must be occluded");
		assertTrue(scanned.accepted().size() > 0, "fixture sanity check: something must survive");
		assertEquals(Set.of(), disagreements(scanned, solve(HALF_BLOCKED, radius), radius));
	}

	@Test
	@DisplayName("a fully enclosed sensor detects nothing, and every in-range position is occluded-out")
	void fullyEnclosedPutsEveryInRangePositionInTheOccludedClass() {
		int radius = 5;
		ShellSolution scanned = scan(FULLY_ENCLOSED, radius);

		assertEquals(0, scanned.accepted().size());
		assertEquals(solve(OPEN_AIR, radius).accepted().size(), scanned.occludedOut().size(),
				"the occluded-out class must hold exactly the positions that passed the range test");
	}

	@Test
	@DisplayName("the range test is inclusive at exactly the radius, and nothing beyond it is classified")
	void theCubeIsPartitionedByTheInclusiveRangeTest() {
		int radius = 4;
		ShellSolution scanned = scan(OPEN_AIR, radius);

		assertTrue(scanned.accepted().contains(radius, 0, 0), "distSqr == radiusSqr must be in the set");

		assertFalse(scanned.accepted().contains(radius, 1, 0));
		assertFalse(scanned.occludedOut().contains(radius, 1, 0));
		assertTrue(scanned.isOutOfRange(radius, 1, 0));
	}

	@Test
	@DisplayName("the scan feeds the verifier unchanged, and a game that agrees produces a clean report")
	void theScanDrivesTheSharedVerifier() {
		int radius = 6;
		ShellSolution scanned = scan(HALF_BLOCKED, radius);

		SensorProbe agreeing = (sx, sy, sz, px, py, pz) ->
				scanned.accepted().contains(px - sx, py - sy, pz - sz)
						? Reaction.REACTED
						: Reaction.DID_NOT_REACT;

		VerificationReport report = DifferentialVerifier.verify("mode-c-agreement", scanned,
				SENSOR_X, SENSOR_Y, SENSOR_Z, agreeing, 200, 17L);

		assertEquals(200, report.conclusive());
		assertEquals(0, report.disagreements());
		assertTrue(report.occludedOutSampled() > 0,
				"the occlusion seam must actually be sampled, which is what the three-way split is for");
		assertTrue(report.clean());
	}

	@Test
	@DisplayName("a detector that disagrees with the game at one position is caught through the scan")
	void aDisagreementAtOnePositionIsCaught() {
		int radius = 6;
		ShellSolution scanned = scan(OPEN_AIR, radius);

		SensorProbe silentAtOnePosition = (sx, sy, sz, px, py, pz) -> {
			if (px - sx == 3 && py - sy == 0 && pz - sz == 0) {
				return Reaction.DID_NOT_REACT;
			}

			return scanned.accepted().contains(px - sx, py - sy, pz - sz)
					? Reaction.REACTED
					: Reaction.DID_NOT_REACT;
		};

		int everything = 3 * scanned.accepted().size();
		VerificationReport report = DifferentialVerifier.verify("mode-c-one-bad", scanned,
				SENSOR_X, SENSOR_Y, SENSOR_Z, silentAtOnePosition, everything, 23L);

		assertEquals(1, report.disagreements());
		assertFalse(report.clean());
		assertEquals("offset (3, 0, 0): predicted IN set, game DID_NOT_REACT",
				report.disagreementDetail().getFirst().describe());
	}

	@Test
	@DisplayName("radius zero scans exactly the sensor's own position")
	void radiusZeroIsASinglePosition() {
		ShellSolution scanned = scan(OPEN_AIR, 0);

		assertEquals(1, scanned.accepted().size());
		assertTrue(scanned.accepted().contains(0, 0, 0));
		assertEquals(0, scanned.occludedOut().size());
	}

	@Test
	@DisplayName("the two partitions never overlap, so every classified position has exactly one class")
	void theTwoClassesAreDisjoint() {
		int radius = 7;
		ShellSolution scanned = scan(HALF_BLOCKED, radius);
		DetectionSet accepted = scanned.accepted();
		DetectionSet occludedOut = scanned.occludedOut();

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					assertFalse(accepted.contains(dx, dy, dz) && occludedOut.contains(dx, dy, dz),
							"a position in both classes would make PredictedClass ambiguous at ("
									+ dx + ", " + dy + ", " + dz + ")");
				}
			}
		}
	}
}
