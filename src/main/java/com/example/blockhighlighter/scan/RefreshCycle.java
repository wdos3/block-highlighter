package com.example.blockhighlighter.scan;

final class RefreshCycle {
	private static final int TICKS_PER_SECOND = 20;
	private int elapsedTicks;

	boolean tick(int intervalSeconds) {
		if (++elapsedTicks < intervalSeconds * TICKS_PER_SECOND) {
			return false;
		}
		elapsedTicks = 0;
		return true;
	}

	void reset() {
		elapsedTicks = 0;
	}
}
