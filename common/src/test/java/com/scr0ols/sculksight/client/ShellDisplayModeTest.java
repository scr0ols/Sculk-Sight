package com.scr0ols.sculksight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.scr0ols.sculksight.timing.DelayBand;

class ShellDisplayModeTest {
	@ParameterizedTest
	@MethodSource("bands")
	void mapsEveryDelayBandToItsApprovedColour(DelayBand band) {
		assertEquals(band.colour(), ShellDisplayMode.DELAY_HEATMAP.colour(DetectorType.SHRIEKER, band));
	}

	@ParameterizedTest
	@MethodSource("detectors")
	void turningHeatmapOffRestoresDetectorColourExactly(DetectorType detector) {
		assertEquals(detector.colour(), ShellDisplayMode.TYPE.colour(detector, DelayBand.SEVENTEEN_PLUS));
	}

	private static Stream<Arguments> bands() {
		return Stream.of(DelayBand.values()).map(Arguments::of);
	}

	private static Stream<Arguments> detectors() {
		return Stream.of(DetectorType.values()).map(Arguments::of);
	}
}
