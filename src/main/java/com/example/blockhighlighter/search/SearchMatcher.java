package com.example.blockhighlighter.search;

import java.util.Locale;

public final class SearchMatcher {
	private SearchMatcher() {
	}

	public static boolean matches(String displayName, String registryId, String query) {
		return score(displayName, registryId, query) >= 0;
	}

	public static int score(String displayName, String registryId, String query) {
		String needle = normalize(query);
		if (needle.isEmpty()) {
			return 0;
		}
		String name = normalize(displayName);
		String id = normalize(registryId);
		if (name.contains(needle)) {
			return 1000 - name.indexOf(needle);
		}
		if (id.contains(needle)) {
			return 800 - id.indexOf(needle);
		}

		int nameFuzzy = subsequenceScore(name, needle);
		int idFuzzy = subsequenceScore(id, needle);
		if (nameFuzzy < 0 && idFuzzy < 0) {
			return -1;
		}
		return Math.max(nameFuzzy + 400, idFuzzy + 200);
	}

	private static int subsequenceScore(String value, String needle) {
		int previous = -1;
		int gaps = 0;
		for (int i = 0; i < needle.length(); i++) {
			int found = value.indexOf(needle.charAt(i), previous + 1);
			if (found < 0) {
				return -1;
			}
			if (previous >= 0) {
				gaps += found - previous - 1;
			}
			previous = found;
		}
		return 500 - gaps - previous;
	}

	private static String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
	}
}
