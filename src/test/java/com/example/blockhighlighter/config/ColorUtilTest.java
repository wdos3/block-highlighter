package com.example.blockhighlighter.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorUtilTest {
	@Test
	void parsesAndNormalizesRgbColors() {
		assertEquals(0x00FFFF, ColorUtil.parseRgb("#00ffff", 0));
		assertEquals("#12ABEF", ColorUtil.normalize("12abef", "#00FFFF"));
	}

	@Test
	void invalidColorsUseFallback() {
		assertEquals(0x123456, ColorUtil.parseRgb("#12", 0x123456));
		assertEquals("#123456", ColorUtil.normalize("wat", "#123456"));
	}
}
