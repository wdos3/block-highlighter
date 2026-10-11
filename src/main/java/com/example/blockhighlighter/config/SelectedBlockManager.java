package com.example.blockhighlighter.config;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

public final class SelectedBlockManager {
	private final HighlightConfig config;
	private final LinkedHashSet<Identifier> selected = new LinkedHashSet<>();
	private final BiConsumer<String, Throwable> logger;

	public SelectedBlockManager(HighlightConfig config, BiConsumer<String, Throwable> logger) {
		this.config = config;
		this.logger = logger;
		loadAndValidate();
	}

	public boolean isSelected(Identifier id) {
		return selected.contains(id);
	}

	public boolean isSelected(Block block) {
		return isSelected(Registries.BLOCK.getId(block));
	}

	public boolean add(Identifier id) {
		if (!isValid(id) || !selected.add(id)) {
			return false;
		}
		HighlightConfig.SelectedBlock settings = new HighlightConfig.SelectedBlock();
		settings.color = null;
		config.selectedBlocks.put(id.toString(), settings);
		return true;
	}

	public boolean remove(Identifier id) {
		if (!selected.remove(id)) {
			return false;
		}
		config.selectedBlocks.remove(id.toString());
		return true;
	}

	public boolean toggle(Identifier id) {
		return isSelected(id) ? remove(id) : add(id);
	}

	public void clear() {
		selected.clear();
		config.selectedBlocks.clear();
	}

	public List<Identifier> ids() {
		return List.copyOf(selected);
	}

	public Set<Identifier> idSet() {
		return Collections.unmodifiableSet(selected);
	}

	public int colorFor(Identifier id, int defaultColor) {
		HighlightConfig.SelectedBlock settings = config.selectedBlocks.get(id.toString());
		return settings == null || settings.color == null
				? defaultColor
				: ColorUtil.parseRgb(settings.color, defaultColor);
	}

	private void loadAndValidate() {
		if (config.selectedBlocks == null) {
			config.selectedBlocks = new java.util.LinkedHashMap<>();
		}
		List<String> invalid = new ArrayList<>();
		for (String rawId : config.selectedBlocks.keySet()) {
			Identifier id = Identifier.tryParse(rawId);
			if (id == null || !isValid(id)) {
				invalid.add(rawId);
				log("Ignoring unknown or invalid selected block ID: " + rawId, null);
			} else if (config.selectedBlocks.get(rawId) == null || config.selectedBlocks.get(rawId).enabled) {
				selected.add(id);
			}
		}
		invalid.forEach(config.selectedBlocks::remove);
	}

	private boolean isValid(Identifier id) {
		return id != null && Registries.BLOCK.containsId(id);
	}

	private void log(String message, Throwable throwable) {
		if (logger != null) {
			logger.accept(message, throwable);
		}
	}
}
