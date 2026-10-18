package com.example.blockhighlighter.scan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshCycleTest {
	private static final long SECOND = 1_000_000_000L;

	@ParameterizedTest
	@ValueSource(ints = {1, 5, 10, 60, 120})
	void firesAtTheConfiguredRealTimeDeadline(int seconds) {
		RefreshCycle cycle = new RefreshCycle();
		assertFalse(cycle.poll(0, seconds));
		assertFalse(cycle.poll(seconds * SECOND - 1, seconds));
		assertTrue(cycle.poll(seconds * SECOND, seconds));
		assertFalse(cycle.poll(seconds * SECOND + 1, seconds));
		assertTrue(cycle.poll(seconds * SECOND * 2, seconds));
	}

	@Test
	void pollingFrequencyDoesNotChangeTheInterval() {
		RefreshCycle cycle = new RefreshCycle();
		for (int i = 0; i < 1_000; i++) {
			assertFalse(cycle.poll(0, 1));
		}
		assertTrue(cycle.poll(SECOND, 1));
	}

	@Test
	void shorteningTheIntervalStartsAFreshDeadline() {
		RefreshCycle cycle = new RefreshCycle();
		assertFalse(cycle.poll(0, 120));
		assertFalse(cycle.poll(30 * SECOND, 1));
		assertFalse(cycle.poll(31 * SECOND - 1, 1));
		assertTrue(cycle.poll(31 * SECOND, 1));
	}

	@Test
	void lengtheningTheIntervalDoesNotReuseElapsedTime() {
		RefreshCycle cycle = new RefreshCycle();
		assertFalse(cycle.poll(0, 60));
		assertFalse(cycle.poll(59 * SECOND, 120));
		assertFalse(cycle.poll(178 * SECOND, 120));
		assertTrue(cycle.poll(179 * SECOND, 120));
	}

	@Test
	void latePollingCoalescesDeadlinesWithoutDriftingOrBursting() {
		RefreshCycle cycle = new RefreshCycle();
		assertFalse(cycle.poll(0, 5));
		assertTrue(cycle.poll(17 * SECOND, 5));
		assertFalse(cycle.poll(17 * SECOND, 5));
		assertFalse(cycle.poll(20 * SECOND - 1, 5));
		assertTrue(cycle.poll(20 * SECOND, 5));
	}

	@Test
	void resetRestartsTimingAfterDisablingOrPausing() {
		RefreshCycle cycle = new RefreshCycle();
		assertFalse(cycle.poll(0, 1));
		cycle.reset();
		assertFalse(cycle.poll(120 * SECOND, 1));
		assertTrue(cycle.poll(121 * SECOND, 1));
	}

	@Test
	void handlesNanoTimeWraparound() {
		RefreshCycle cycle = new RefreshCycle();
		long start = Long.MAX_VALUE - SECOND / 2;
		assertFalse(cycle.poll(start, 1));
		assertFalse(cycle.poll(start + SECOND - 1, 1));
		assertTrue(cycle.poll(start + SECOND, 1));
	}

	@Test
	void rejectsZeroIntervals() {
		assertThrows(IllegalArgumentException.class, () -> new RefreshCycle().poll(0, 0));
	}
}
