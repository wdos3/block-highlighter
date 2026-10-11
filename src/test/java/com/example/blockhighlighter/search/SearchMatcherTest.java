package com.example.blockhighlighter.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchMatcherTest {
	@Test
	void matchesNamesIdsAndFuzzyQueriesCaseInsensitively() {
		assertTrue(SearchMatcher.matches("Diamond Ore", "minecraft:diamond_ore", "diamond"));
		assertTrue(SearchMatcher.matches("Oak Button", "minecraft:oak_button", "MINECRAFT:oak_button"));
		assertTrue(SearchMatcher.matches("Pressure Plate", "minecraft:stone_pressure_plate", "prssr"));
		assertFalse(SearchMatcher.matches("Oak Button", "minecraft:oak_button", "ancient debris"));
	}
}
