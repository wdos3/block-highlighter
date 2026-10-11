package com.example.blockhighlighter.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.BiConsumer;

public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private ConfigManager() {
	}

	public static HighlightConfig load(Path path, BiConsumer<String, Throwable> logger) {
		if (!Files.exists(path)) {
			HighlightConfig config = HighlightConfig.defaults();
			saveQuietly(config, path, logger);
			return config;
		}

		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			HighlightConfig config = GSON.fromJson(reader, HighlightConfig.class);
			if (config == null) {
				throw new JsonParseException("Configuration file is empty.");
			}
			config.validate();
			return config;
		} catch (IOException | RuntimeException ex) {
			log(logger, "Failed to load block-highlighter config; preserving the malformed file and using defaults.", ex);
			backupMalformed(path, logger);
			HighlightConfig config = HighlightConfig.defaults();
			saveQuietly(config, path, logger);
			return config;
		}
	}

	public static HighlightConfig fromJsonOrDefaults(String json) {
		try {
			HighlightConfig config = GSON.fromJson(json, HighlightConfig.class);
			if (config == null) {
				throw new JsonParseException("Configuration is empty.");
			}
			config.validate();
			return config;
		} catch (RuntimeException ex) {
			return HighlightConfig.defaults();
		}
	}

	public static String toJson(HighlightConfig config) {
		config.validate();
		return GSON.toJson(config);
	}

	public static void save(HighlightConfig config, Path path) throws IOException {
		config.validate();
		Path parent = path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path temp = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
		boolean moved = false;
		try {
			try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
			try {
				Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException atomicMoveFailure) {
				Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
			}
			moved = true;
		} finally {
			if (!moved) {
				Files.deleteIfExists(temp);
			}
		}
	}

	private static void saveQuietly(HighlightConfig config, Path path, BiConsumer<String, Throwable> logger) {
		try {
			save(config, path);
		} catch (IOException ex) {
			log(logger, "Failed to save block-highlighter config.", ex);
		}
	}

	private static void backupMalformed(Path path, BiConsumer<String, Throwable> logger) {
		if (!Files.exists(path)) {
			return;
		}
		Path parent = path.getParent();
		if (parent == null) {
			parent = Path.of(".");
		}
		Path backup = null;
		try {
			backup = Files.createTempFile(parent, path.getFileName() + "." + LocalDateTime.now().format(BACKUP_TIME) + ".", ".broken");
			Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ex) {
			if (backup != null) {
				try {
					Files.deleteIfExists(backup);
				} catch (IOException cleanupFailure) {
					ex.addSuppressed(cleanupFailure);
				}
			}
			log(logger, "Failed to back up malformed block-highlighter config.", ex);
		}
	}

	private static void log(BiConsumer<String, Throwable> logger, String message, Throwable throwable) {
		if (logger != null) {
			logger.accept(message, throwable);
		}
	}
}
