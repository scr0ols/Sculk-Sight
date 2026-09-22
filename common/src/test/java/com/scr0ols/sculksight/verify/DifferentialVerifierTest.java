package com.scr0ols.sculksight.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.scr0ols.sculksight.solver.DetectionSet;
import com.scr0ols.sculksight.solver.ShellSolution;
import com.scr0ols.sculksight.solver.ShellSolver;
import com.scr0ols.sculksight.solver.WorldView;

class DifferentialVerifierTest {

	private static final int SENSOR_X = 20;
	private static final int SENSOR_Y = 70;
	private static final int SENSOR_Z = -8;

	private static final WorldView OPEN_AIR =
			(fromX, fromY, fromZ, toX, toY, toZ) -> false;

	private static final class ScriptedProbe implements SensorProbe {
		private final List<int[]> asked = new ArrayList<>();
		private final SensorProbe rule;

		ScriptedProbe(SensorProbe rule) {
			this.rule = rule;
		}

		@Override
		public Reaction test(int sensorX, int sensorY, int sensorZ, int sourceX, int sourceY, int sourceZ) {
			asked.add(new int[] { sourceX - sensorX, sourceY - sensorY, sourceZ - sensorZ });
			return rule.test(sensorX, sensorY, sensorZ, sourceX, sourceY, sourceZ);
		}

		Set<String> askedOffsets() {
			Set<String> offsets = new HashSet<>();

			for (int[] offset : asked) {
				offsets.add(offset[0] + "," + offset[1] + "," + offset[2]);
			}

			return offsets;
		}

		int callCount() {
			return asked.size();
		}
	}

	private static ShellSolution openAirSolution(int radius) {
		return ShellSolver.solveDetailed(OPEN_AIR, SENSOR_X, SENSOR_Y, SENSOR_Z, radius);
	}

	private static DetectionSet openAirShell(int radius) {
		return openAirSolution(radius).accepted();
	}

	private static SensorProbe agreeingWith(DetectionSet prediction) {
		return (sx, sy, sz, px, py, pz) ->
				prediction.contains(px - sx, py - sy, pz - sz) ? Reaction.REACTED : Reaction.DID_NOT_REACT;
	}

	private static String key(int dx, int dy, int dz) {
		return dx + "," + dy + "," + dz;
	}

	@Test
	@DisplayName("a probe that agrees everywhere produces a clean report")
	void agreementProducesACleanReport() {
		ShellSolution solution = openAirSolution(6);

		VerificationReport report = DifferentialVerifier.verify("agreement", solution,
				SENSOR_X, SENSOR_Y, SENSOR_Z, agreeingWith(solution.accepted()), 200, 1L);

		assertEquals(200, report.requested());
		assertEquals(200, report.conclusive());
		assertEquals(0, report.disagreements());
		assertEquals(0, report.inconclusive());
		assertTrue(report.clean());
	}

	@Test
	@DisplayName("a single disagreeing position is caught and reported with its offset")
	void oneDisagreementIsCaught() {
		ShellSolution solution = openAirSolution(6);

		SensorProbe lyingAtOnePosition = (sx, sy, sz, px, py, pz) -> {
			if (px - sx == 2 && py - sy == 0 && pz - sz == 0) {
				return Reaction.DID_NOT_REACT;
			}

			return solution.accepted().contains(px - sx, py - sy, pz - sz) ? Reaction.REACTED : Reaction.DID_NOT_REACT;
		};

		int everything = 3 * solution.accepted().size();
		VerificationReport report = DifferentialVerifier.verify("one-bad", solution,
				SENSOR_X, SENSOR_Y, SENSOR_Z, lyingAtOnePosition, everything, 7L);

		assertEquals(1, report.disagreements());
		assertFalse(report.clean());
		assertEquals("offset (2, 0, 0): predicted IN set, game DID_NOT_REACT",
				report.disagreementDetail().getFirst().describe());
		assertTrue(report.summary().contains("DISAGREE"));
	}

	@Test
	@DisplayName("an all-inconclusive run is not clean, despite having zero disagreements")
	void allInconclusiveIsNotClean() {
		ShellSolution solution = openAirSolution(5);

		VerificationReport report = DifferentialVerifier.verify("silent", solution,
				SENSOR_X, SENSOR_Y, SENSOR_Z,
				(sx, sy, sz, px, py, pz) -> Reaction.INCONCLUSIVE, 100, 3L);

		assertEquals(0, report.disagreements());
		assertEquals(0, report.conclusive());
		assertEquals(100, report.inconclusive());
		assertFalse(report.clean(), "zero disagreements out of zero observations is not a pass");
	}

	@Test
	@DisplayName("a solver that claims nothing is caught, which sampling only members would miss")
	void anEmptyPredictionIsCaught() {
		ShellSolution empty = new ShellSolution(new DetectionSet(4), new DetectionSet(4));
		DetectionSet truth = openAirShell(4);

		VerificationReport report = DifferentialVerifier.verify("empty-solver", empty,
				SENSOR_X, SENSOR_Y, SENSOR_Z, agreeingWith(truth), 100, 11L);

		assertTrue(report.disagreements() > 0, "an empty prediction must not pass");
		assertFalse(report.clean());
	}

	@Test
	@DisplayName("the occluded-out class is sampled in full when it is small, by construction rather than by luck")
	void occludedOutClassIsFullySampledWhenItIsSmall() {
		int radius = 6;
		Set<String> occluded = Set.of(key(2, 0, 0), key(0, 3, 0), key(-1, -1, -1));

		WorldView threeOccluded = (fromX, fromY, fromZ, toX, toY, toZ) -> {
			int dx = (int) Math.floor(fromX) - SENSOR_X;
			int dy = (int) Math.floor(fromY) - SENSOR_Y;
			int dz = (int) Math.floor(fromZ) - SENSOR_Z;
			return occluded.contains(key(dx, dy, dz));
		};

		ShellSolution solution = ShellSolver.solveDetailed(threeOccluded, SENSOR_X, SENSOR_Y, SENSOR_Z, radius);

		assertEquals(3, solution.occludedOut().size(), "fixture sanity check");

		ScriptedProbe probe = new ScriptedProbe(agreeingWith(solution.accepted()));
		DifferentialVerifier.verify("small-occluded-class", solution,
				SENSOR_X, SENSOR_Y, SENSOR_Z, probe, 90, 5L);

		for (String offset : occluded) {
			assertTrue(probe.askedOffsets().contains(offset),
					"every occluded-out position must be probed when the class is this small: " + offset);
		}
	}

	@Test
	@DisplayName("sampling draws from all three predicted classes, split into thirds")
	void samplingIsStratifiedThreeWays() {
		int radius = 6;

		WorldView halfBlocked = (fromX, fromY, fromZ, toX, toY, toZ) -> Math.floor(fromX) - SENSOR_X < 0;
		ShellSolution solution = ShellSolver.solveDetailed(halfBlocked, SENSOR_X, SENSOR_Y, SENSOR_Z, radius);
		ScriptedProbe probe = new ScriptedProbe(agreeingWith(solution.accepted()));

		DifferentialVerifier.verify("stratified", solution,
				SENSOR_X, SENSOR_Y, SENSOR_Z, probe, 99, 5L);

		int inSet = 0;
		int occludedOut = 0;
		int outOfRange = 0;

		for (String offset : probe.askedOffsets()) {
			String[] parts = offset.split(",");
			int dx = Integer.parseInt(parts[0]);
			int dy = Integer.parseInt(parts[1]);
			int dz = Integer.parseInt(parts[2]);

			if (solution.accepted().contains(dx, dy, dz)) {
				inSet++;
			} else if (solution.occludedOut().contains(dx, dy, dz)) {
				occludedOut++;
			} else {
				outOfRange++;
			}
		}

		assertEquals(33, inSet);
		assertEquals(33, occludedOut);
		assertEquals(33, outOfRange);
	}

	@Test
	@DisplayName("the same seed probes the same positions, a different seed does not")
	void samplingIsReproducible() {
		ShellSolution solution = openAirSolution(6);

		ScriptedProbe first = new ScriptedProbe(agreeingWith(solution.accepted()));
		DifferentialVerifier.verify("a", solution, SENSOR_X, SENSOR_Y, SENSOR_Z, first, 60, 42L);

		ScriptedProbe second = new ScriptedProbe(agreeingWith(solution.accepted()));
		DifferentialVerifier.verify("b", solution, SENSOR_X, SENSOR_Y, SENSOR_Z, second, 60, 42L);

		ScriptedProbe other = new ScriptedProbe(agreeingWith(solution.accepted()));
		DifferentialVerifier.verify("c", solution, SENSOR_X, SENSOR_Y, SENSOR_Z, other, 60, 43L);

		assertEquals(first.askedOffsets(), second.askedOffsets());
		assertFalse(first.askedOffsets().equals(other.askedOffsets()));
	}

	@Test
	@DisplayName("no position is probed twice in a run")
	void samplingIsWithoutReplacement() {
		ShellSolution solution = openAirSolution(5);
		ScriptedProbe probe = new ScriptedProbe(agreeingWith(solution.accepted()));

		DifferentialVerifier.verify("distinct", solution,
				SENSOR_X, SENSOR_Y, SENSOR_Z, probe, 300, 9L);

		assertEquals(probe.callCount(), probe.askedOffsets().size());
	}

	@Test
	@DisplayName("asking for more samples than exist takes what there is and says so")
	void oversizedSampleDoesNotExceedThePopulation() {
		ShellSolution solution = openAirSolution(1);

		VerificationReport report = DifferentialVerifier.verify("small", solution,
				SENSOR_X, SENSOR_Y, SENSOR_Z, agreeingWith(solution.accepted()), 200, 2L);

		assertEquals(7, solution.accepted().size());
		assertEquals(27, report.requested());
		assertEquals(27, report.conclusive());
		assertTrue(report.clean());
	}

	@Test
	@DisplayName("a false negative counts as a disagreement, like a false positive, for either out-of-set class")
	void disagreementIsSymmetric() {
		VerificationSample falsePositiveOutOfRange =
				new VerificationSample(1, 0, 0, PredictedClass.OUT_OF_RANGE, Reaction.REACTED);
		VerificationSample falsePositiveOccluded =
				new VerificationSample(1, 0, 0, PredictedClass.OCCLUDED_OUT, Reaction.REACTED);
		VerificationSample falseNegative =
				new VerificationSample(1, 0, 0, PredictedClass.IN_SET, Reaction.DID_NOT_REACT);

		assertEquals(Outcome.DISAGREEMENT, falsePositiveOutOfRange.outcome());
		assertEquals(Outcome.DISAGREEMENT, falsePositiveOccluded.outcome());
		assertEquals(Outcome.DISAGREEMENT, falseNegative.outcome());
		assertEquals(Outcome.AGREEMENT,
				new VerificationSample(1, 0, 0, PredictedClass.IN_SET, Reaction.REACTED).outcome());
		assertEquals(Outcome.AGREEMENT,
				new VerificationSample(1, 0, 0, PredictedClass.OUT_OF_RANGE, Reaction.DID_NOT_REACT).outcome());
		assertEquals(Outcome.AGREEMENT,
				new VerificationSample(1, 0, 0, PredictedClass.OCCLUDED_OUT, Reaction.DID_NOT_REACT).outcome());
	}
}
