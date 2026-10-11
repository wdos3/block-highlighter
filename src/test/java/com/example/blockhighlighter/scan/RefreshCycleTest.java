package com.example.blockhighlighter.scan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshCycleTest {
	@Test
	void startsOncePerConfiguredInterval() {
		RefreshCycle cycle = new RefreshCycle();

		for (int tick = 1; tick < 20; tick++) {
			assertFalse(cycle.tick(1));
		}
		assertTrue(cycle.tick(1));
		for (int tick = 1; tick < 20; tick++) {
			assertFalse(cycle.tick(1));
		}
		assertTrue(cycle.tick(1));
	}

	@Test
	void changingSliderIntervalTakesEffectWithoutRestart() {
		RefreshCycle cycle = new RefreshCycle();

		for (int tick = 0; tick < 19; tick++) {
			assertFalse(cycle.tick(120));
		}
		assertTrue(cycle.tick(1));
	}

	@Test
	void supportsTwoMinuteInterval() {
		RefreshCycle cycle = new RefreshCycle();

		for (int tick = 1; tick < 2_400; tick++) {
			assertFalse(cycle.tick(120));
		}
		assertTrue(cycle.tick(120));
	}

	@Test
	void resetStartsANewCycle() {
		RefreshCycle cycle = new RefreshCycle();

		for (int tick = 0; tick < 19; tick++) {
			assertFalse(cycle.tick(1));
		}
		cycle.reset();
		assertFalse(cycle.tick(1));
	}
}
