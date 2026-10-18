package com.example.blockhighlighter.scan;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.LongPredicate;

final class PendingBlockUpdates {
	private final Set<Long> pending = new LinkedHashSet<>();
	private final Set<Long> ready = new LinkedHashSet<>();

	void record(long position) {
		pending.add(position);
		ready.remove(position);
	}

	boolean isWaiting(long position) {
		return pending.contains(position);
	}

	void beginCycle() {
		ready.addAll(pending);
		pending.clear();
	}

	Long pollReady() {
		Iterator<Long> iterator = ready.iterator();
		if (!iterator.hasNext()) {
			return null;
		}
		Long position = iterator.next();
		iterator.remove();
		return position;
	}

	void postponeReady() {
		pending.addAll(ready);
		ready.clear();
	}

	void retain(LongPredicate predicate) {
		pending.removeIf(position -> !predicate.test(position));
		ready.removeIf(position -> !predicate.test(position));
	}

	void clear() {
		pending.clear();
		ready.clear();
	}
}
