package com.example.blockhighlighter.scan;

import org.junit.jupiter.api.Test;

import net.minecraft.util.math.BlockPos;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HighlightCacheTest {
	@Test
	void incrementalUpdatesRetainOtherMatchesAndCountOnlyActualChanges() {
		HighlightCache cache = new HighlightCache();
		cache.setMatch(1L, 10L, true);
		long revision = cache.revision();
		cache.setMatch(1L, 10L, true);
		assertEquals(revision, cache.revision());
		cache.setMatch(1L, 11L, true);
		cache.setMatch(1L, 10L, false);
		assertEquals(1, cache.size());
		assertEquals(Set.of(11L), cache.entries().iterator().next().getValue());
	}

	@Test
	void sectionCountsTrackNegativeHeightsAndSurviveReplacement() {
		HighlightCache cache = new HighlightCache();
		long lower = BlockPos.asLong(0, -1, 0);
		long upper = BlockPos.asLong(0, 16, 0);
		cache.replaceChunk(1L, Set.of(lower, upper));
		assertTrue(cache.hasMatchesInSection(1L, -1));
		assertTrue(cache.hasMatchesInSection(1L, 1));
		cache.setMatch(1L, lower, false);
		assertFalse(cache.hasMatchesInSection(1L, -1));
		cache.removeChunk(1L);
		assertFalse(cache.hasMatchesInSection(1L, 1));
		assertEquals(0, cache.size());
	}

	@Test
	void cacheViewsCannotBeUsedToMutateMatches() {
		HighlightCache cache = new HighlightCache();
		cache.setMatch(1L, 10L, true);
		Set<Long> view = cache.entries().iterator().next().getValue();
		assertThrows(UnsupportedOperationException.class, () -> view.add(11L));
	}
	@Test
	void retainsEmptyChunksAndAvoidsRevisionForIdenticalResults() {
		HighlightCache cache = new HighlightCache();

		cache.replaceChunk(1L, Set.of());
		long firstRevision = cache.revision();
		cache.replaceChunk(1L, Set.of());

		assertTrue(cache.containsChunk(1L));
		assertTrue(cache.isEmpty());
		assertEquals(firstRevision, cache.revision());
	}

	@Test
	void replacesPartialResultsAndMaintainsMatchCount() {
		HighlightCache cache = new HighlightCache();

		cache.replaceChunk(1L, Set.of(10L));
		cache.replaceChunk(1L, Set.of(10L, 11L));
		cache.replaceChunk(2L, Set.of(20L));

		assertEquals(3, cache.size());
		cache.replaceChunk(1L, Set.of());
		assertEquals(1, cache.size());
		assertFalse(cache.isEmpty());
	}
}
