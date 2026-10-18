package com.example.blockhighlighter.scan;

final class RefreshCycle {
	private static final long NANOS_PER_SECOND = 1_000_000_000L;
	private long cycleStart;
	private int intervalSeconds;
	private boolean started;

	boolean poll(long now, int requestedSeconds) {
		if (requestedSeconds < 1) {
			throw new IllegalArgumentException("Refresh interval must be positive.");
		}
		if (!started || intervalSeconds != requestedSeconds) {
			cycleStart = now;
			intervalSeconds = requestedSeconds;
			started = true;
			return false;
		}
		long interval = intervalSeconds * NANOS_PER_SECOND;
		long elapsed = now - cycleStart;
		if (elapsed < interval) {
			return false;
		}
		// Coalesce missed deadlines without moving the next deadline by tick latency.
		cycleStart += (elapsed / interval) * interval;
		return true;
	}

	void reset() {
		started = false;
	}
}
