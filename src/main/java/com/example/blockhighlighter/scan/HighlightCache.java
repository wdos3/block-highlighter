package com.example.blockhighlighter.scan;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class HighlightCache {
	private final Map<Long, ChunkMatches> matchesByChunk = new HashMap<>();
	private int matchCount;
	private long revision;

	public void replaceChunk(long chunkKey, Set<Long> positions) {
		ChunkMatches previous = matchesByChunk.get(chunkKey);
		if (previous != null && Objects.equals(previous.positions, positions)) {
			return;
		}
		ChunkMatches replacement = new ChunkMatches();
		positions.forEach(replacement::add);
		matchesByChunk.put(chunkKey, replacement);
		matchCount -= previous == null ? 0 : previous.positions.size();
		matchCount += replacement.positions.size();
		revision++;
	}

	public void setMatch(long chunkKey, long position, boolean matchesSelection) {
		ChunkMatches matches = matchesByChunk.get(chunkKey);
		if (matches == null) {
			if (!matchesSelection) {
				return;
			}
			matches = new ChunkMatches();
			matchesByChunk.put(chunkKey, matches);
		}
		boolean changed = matchesSelection ? matches.add(position) : matches.remove(position);
		if (changed) {
			matchCount += matchesSelection ? 1 : -1;
			revision++;
		}
	}

	boolean hasMatchesInSection(long chunkKey, int sectionY) {
		ChunkMatches matches = matchesByChunk.get(chunkKey);
		return matches != null && matches.sectionCounts.containsKey(sectionY);
	}

	public void removeChunk(long chunkKey) {
		ChunkMatches previous = matchesByChunk.remove(chunkKey);
		if (previous != null) {
			matchCount -= previous.positions.size();
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
		return () -> matchesByChunk.entrySet().stream()
				.map(entry -> Map.entry(entry.getKey(), entry.getValue().view)).iterator();
	}

	private static final class ChunkMatches {
		private final Set<Long> positions = new HashSet<>();
		private final Set<Long> view = Collections.unmodifiableSet(positions);
		private final Map<Integer, Integer> sectionCounts = new HashMap<>();

		private boolean add(long position) {
			if (!positions.add(position)) {
				return false;
			}
			sectionCounts.merge(BlockPos.unpackLongY(position) >> 4, 1, Integer::sum);
			return true;
		}

		private boolean remove(long position) {
			if (!positions.remove(position)) {
				return false;
			}
			int sectionY = BlockPos.unpackLongY(position) >> 4;
			int remaining = sectionCounts.get(sectionY) - 1;
			if (remaining == 0) {
				sectionCounts.remove(sectionY);
			} else {
				sectionCounts.put(sectionY, remaining);
			}
			return true;
		}
	}
}
