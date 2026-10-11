package com.example.blockhighlighter.search;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BlockSearchIndex {
	private final List<BlockEntry> allEntries;
	private String lastQuery = "";
	private List<BlockEntry> filteredEntries;

	private BlockSearchIndex(List<BlockEntry> entries) {
		this.allEntries = List.copyOf(entries);
		this.filteredEntries = this.allEntries;
	}

	public static BlockSearchIndex create() {
		List<BlockEntry> entries = new ArrayList<>();
		for (Identifier id : Registries.BLOCK.getIds()) {
			Block block = Registries.BLOCK.get(id);
			if (block != null) {
				entries.add(new BlockEntry(id, block, block.getName()));
			}
		}
		entries.sort(Comparator.comparing((BlockEntry entry) -> entry.displayName().getString(), String.CASE_INSENSITIVE_ORDER)
				.thenComparing(entry -> entry.id().toString()));
		return new BlockSearchIndex(entries);
	}

	public List<BlockEntry> allEntries() {
		return allEntries;
	}

	public List<BlockEntry> filtered(String query) {
		String normalized = query == null ? "" : query.trim();
		if (normalized.equals(lastQuery)) {
			return filteredEntries;
		}
		lastQuery = normalized;
		if (normalized.isEmpty()) {
			filteredEntries = allEntries;
		} else {
			filteredEntries = allEntries.stream()
					.filter(entry -> SearchMatcher.matches(entry.displayName().getString(), entry.id().toString(), normalized))
					.sorted(Comparator.comparingInt((BlockEntry entry) -> -SearchMatcher.score(entry.displayName().getString(), entry.id().toString(), normalized))
							.thenComparing(entry -> entry.displayName().getString(), String.CASE_INSENSITIVE_ORDER)
							.thenComparing(entry -> entry.id().toString()))
					.toList();
		}
		return filteredEntries;
	}

	public record BlockEntry(Identifier id, Block block, Text displayName) {
	}
}
