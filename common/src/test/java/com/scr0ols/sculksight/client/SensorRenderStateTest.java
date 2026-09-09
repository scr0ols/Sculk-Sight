package com.scr0ols.sculksight.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SensorRenderStateTest {

	@Test
	void globalSwitchAndSensorSwitchAreIndependentGates() {
		assertTrue(SensorRenderState.shouldRender(true, true));
		assertFalse(SensorRenderState.shouldRender(true, false));
		assertFalse(SensorRenderState.shouldRender(false, true));
		assertFalse(SensorRenderState.shouldRender(false, false));
	}
}
