package com.example.blockhighlighter.config;

import java.util.LinkedHashMap;
import java.util.Map;

public class HighlightConfig {
	public static final int CURRENT_VERSION = 1;
	public static final int MIN_RADIUS = 16;
	public static final int MAX_RADIUS = 256;
	public static final int MIN_REFRESH_SECONDS = 1;
	public static final int MAX_REFRESH_SECONDS = 120;
	public static final int DEFAULT_REFRESH_SECONDS = 60;
	public static final float MIN_OPACITY = 0.15f;
	public static final float MAX_OPACITY = 1.0f;
	public static final float MIN_LINE_WIDTH = 1.0f;
	public static final float MAX_LINE_WIDTH = 4.0f;

	public int configVersion = CURRENT_VERSION;
	public boolean enabled = true;
	public int radius = 64;
	public int refreshSeconds = DEFAULT_REFRESH_SECONDS;
	public boolean hudEnabled = true;
	public String defaultColor = "#00FFFF";
	public float opacity = 0.85f;
	public float lineWidth = 2.0f;
	public LinkedHashMap<String, SelectedBlock> selectedBlocks = new LinkedHashMap<>();

	public static HighlightConfig defaults() {
		return new HighlightConfig();
	}

	public HighlightConfig copy() {
		HighlightConfig copy = new HighlightConfig();
		copy.configVersion = configVersion;
		copy.enabled = enabled;
		copy.radius = radius;
		copy.refreshSeconds = refreshSeconds;
		copy.hudEnabled = hudEnabled;
		copy.defaultColor = defaultColor;
		copy.opacity = opacity;
		copy.lineWidth = lineWidth;
		copy.selectedBlocks = new LinkedHashMap<>();
		if (selectedBlocks != null) {
			selectedBlocks.forEach((id, settings) -> copy.selectedBlocks.put(id, settings == null ? new SelectedBlock() : settings.copy()));
		}
		copy.validate();
		return copy;
	}

	public void validate() {
		configVersion = CURRENT_VERSION;
		radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
		refreshSeconds = Math.max(MIN_REFRESH_SECONDS, Math.min(MAX_REFRESH_SECONDS, refreshSeconds));
		if (!Float.isFinite(opacity)) {
			opacity = 0.85f;
		}
		opacity = Math.max(MIN_OPACITY, Math.min(MAX_OPACITY, opacity));
		if (!Float.isFinite(lineWidth)) {
			lineWidth = 2.0f;
		}
		lineWidth = Math.max(MIN_LINE_WIDTH, Math.min(MAX_LINE_WIDTH, lineWidth));
		defaultColor = ColorUtil.normalize(defaultColor, "#00FFFF");
		if (selectedBlocks == null) {
			selectedBlocks = new LinkedHashMap<>();
		}
		selectedBlocks.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank());
		for (Map.Entry<String, SelectedBlock> entry : selectedBlocks.entrySet()) {
			if (entry.getValue() == null) {
				entry.setValue(new SelectedBlock());
			}
			entry.getValue().validate(defaultColor);
		}
	}

	public static class SelectedBlock {
		public boolean enabled = true;
		public String color;

		public SelectedBlock copy() {
			SelectedBlock copy = new SelectedBlock();
			copy.enabled = enabled;
			copy.color = color;
			return copy;
		}

		private void validate(String fallbackColor) {
			if (color != null && !color.isBlank()) {
				color = ColorUtil.normalize(color, fallbackColor);
			}
		}
	}
}
