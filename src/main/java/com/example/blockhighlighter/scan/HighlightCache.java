package com.example.blockhighlighter.scan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class HighlightCache {
	private final Map<Long, Set<Long>> matchesByChunk = new HashMap<>();
	private int matchCount;
	private long revision;

	public void replaceChunk(long chunkKey, Set<Long> positions) {
		Set<Long> replacement = Set.copyOf(positions);
		Set<Long> previous = matchesByChunk.put(chunkKey, replacement);
		if (Objects.equals(previous, replacement)) {
			return;
		}
		matchCount -= previous == null ? 0 : previous.size();
		matchCount += replacement.size();
		revision++;
	}

	public void removeChunk(long chunkKey) {
		Set<Long> previous = matchesByChunk.remove(chunkKey);
		if (previous != null) {
			matchCount -= previous.size();
			revision++;
		}
	}

	public void clear() {
		if (!matchesByChunk.isEmpty()) {
			matchesByChunk.clear();
			matchCount = 0;
			revision++;
		}
	}

	public boolean containsChunk(long chunkKey) {
		return matchesByChunk.containsKey(chunkKey);
	}

	public int size() {
		return matchCount;
	}

	public boolean isEmpty() {
		return matchCount == 0;
	}

	public long revision() {
		return revision;
	}

	public List<Long> chunkKeys() {
		return new ArrayList<>(matchesByChunk.keySet());
	}

	public Iterable<Map.Entry<Long, Set<Long>>> entries() {
		return matchesByChunk.entrySet();
	}
}
