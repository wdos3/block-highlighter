package com.example.blockhighlighter.scan;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HighlightCacheTest {
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
