package com.example.blockhighlighter.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConfigManagerTest {
	@Test
	void roundTripPreservesSettingsAndSelection() {
		HighlightConfig original = HighlightConfig.defaults();
		original.radius = 128;
		original.refreshSeconds = 37;
		original.defaultColor = "#FF8000";
		HighlightConfig.SelectedBlock selected = new HighlightConfig.SelectedBlock();
		selected.color = "#00FF00";
		original.selectedBlocks.put("minecraft:diamond_ore", selected);

		HighlightConfig restored = ConfigManager.fromJsonOrDefaults(ConfigManager.toJson(original));

		assertEquals(128, restored.radius);
		assertEquals(37, restored.refreshSeconds);
		assertEquals("#FF8000", restored.defaultColor);
		assertNotNull(restored.selectedBlocks.get("minecraft:diamond_ore"));
		assertEquals("#00FF00", restored.selectedBlocks.get("minecraft:diamond_ore").color);
	}

	@Test
	void validationClampsOutOfRangeValues() {
		HighlightConfig config = ConfigManager.fromJsonOrDefaults("{\"radius\":999,\"refreshSeconds\":999,\"opacity\":-4,\"lineWidth\":99,\"defaultColor\":\"bad\"}");

		assertEquals(HighlightConfig.MAX_RADIUS, config.radius);
		assertEquals(HighlightConfig.MAX_REFRESH_SECONDS, config.refreshSeconds);
		assertEquals(HighlightConfig.MIN_OPACITY, config.opacity);
		assertEquals(HighlightConfig.MAX_LINE_WIDTH, config.lineWidth);
		assertEquals("#00FFFF", config.defaultColor);
		assertEquals(HighlightConfig.MIN_REFRESH_SECONDS,
				ConfigManager.fromJsonOrDefaults("{\"refreshSeconds\":0}").refreshSeconds);
	}

	@Test
	void malformedJsonUsesSafeDefaults() {
		HighlightConfig config = ConfigManager.fromJsonOrDefaults("not json");

		assertNotNull(config);
		assertFalse(config.selectedBlocks == null);
		assertEquals(64, config.radius);
		assertEquals(HighlightConfig.DEFAULT_REFRESH_SECONDS, config.refreshSeconds);
	}

	@Test
	void olderConfigWithoutRefreshSettingKeepsDefault() {
		HighlightConfig config = ConfigManager.fromJsonOrDefaults("{\"radius\":32}");

		assertEquals(HighlightConfig.DEFAULT_REFRESH_SECONDS, config.refreshSeconds);
	}

	@Test
	void malformedFileIsBackedUpBeforeDefaultsAreWritten() throws IOException {
		Path directory = Files.createTempDirectory("block-highlighter-config-test");
		Path path = directory.resolve("config.json");
		Files.writeString(path, "not json");

		HighlightConfig config = ConfigManager.load(path, (message, error) -> {
		});

		assertEquals(64, config.radius);
		try (var files = Files.list(directory)) {
			assertEquals(1, files
					.filter(candidate -> candidate.getFileName().toString().endsWith(".broken"))
					.count());
		}
		assertEquals(64, ConfigManager.fromJsonOrDefaults(Files.readString(path)).radius);
	}
}
