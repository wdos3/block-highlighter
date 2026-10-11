package com.example.blockhighlighter.config;

import java.util.Locale;

public final class ColorUtil {
	private ColorUtil() {
	}

	public static int parseRgb(String value, int fallback) {
		if (value == null) {
			return fallback;
		}
		String normalized = value.trim();
		if (normalized.startsWith("#")) {
			normalized = normalized.substring(1);
		}
		if (!normalized.matches("[0-9a-fA-F]{6}")) {
			return fallback;
		}
		try {
			return Integer.parseInt(normalized, 16) & 0xFFFFFF;
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	public static String toHex(int rgb) {
		return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
	}

	public static String normalize(String value, String fallback) {
		return toHex(parseRgb(value, parseRgb(fallback, 0x00FFFF)));
	}
}
