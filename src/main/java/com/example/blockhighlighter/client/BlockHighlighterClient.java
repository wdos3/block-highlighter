package com.example.blockhighlighter.client;

import com.example.blockhighlighter.config.ConfigManager;
import com.example.blockhighlighter.config.HighlightConfig;
import com.example.blockhighlighter.config.SelectedBlockManager;
import com.example.blockhighlighter.render.HighlightRenderer;
import com.example.blockhighlighter.scan.BlockScanner;
import com.example.blockhighlighter.scan.HighlightCache;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class BlockHighlighterClient implements ClientModInitializer {
	public static final String MOD_ID = "block-highlighter";
	public static final String MOD_NAME = "Block Highlighter";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("config.json");

	private static HighlightConfig config;
	private static SelectedBlockManager selectedBlocks;
	private static BlockScanner scanner;

	@Override
	public void onInitializeClient() {
		config = ConfigManager.load(CONFIG_PATH, LOGGER::warn);
		selectedBlocks = new SelectedBlockManager(config, LOGGER::warn);
		HighlightCache cache = new HighlightCache();
		scanner = new BlockScanner(config, selectedBlocks, cache);

		BlockHighlighterKeybindings.register();
		BlockHighlighterCommands.register();
		HighlightHud.register();
		HighlightRenderer.initialize(config, selectedBlocks, cache);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			scanner.tick(client);
			BlockHighlighterKeybindings.handleKeyPresses(client);
			BlockHighlighterCommands.handlePendingOpen(client);
		});
		ClientTickEvents.START_CLIENT_TICK.register(BlockHighlighterCommands::handlePendingOpen);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> scanner.reset());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> scanner.reset());
		ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> scanner.chunkLoaded(world, chunk.getPos()));
		ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> scanner.chunkUnloaded(world, chunk.getPos()));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			HighlightRenderer.close();
			saveConfig();
		});
	}

	public static HighlightConfig config() {
		return config;
	}

	public static SelectedBlockManager selectedBlocks() {
		return selectedBlocks;
	}

	public static void saveConfig() {
		try {
			ConfigManager.save(config, CONFIG_PATH);
		} catch (Exception ex) {
			LOGGER.warn("Failed to save block-highlighter config.", ex);
		}
	}

	public static void setEnabled(MinecraftClient client, boolean enabled) {
		config.enabled = enabled;
		saveConfig();
		if (client.player != null) {
			client.player.sendMessage(Text.translatable(enabled ? "message.block_highlighter.enabled" : "message.block_highlighter.disabled"), true);
		}
	}

	public static void toggleEnabled(MinecraftClient client) {
		setEnabled(client, !config.enabled);
	}

	public static void selectionChanged() {
		if (scanner != null) {
			scanner.invalidateLoadedChunks();
		}
	}

	public static void blockUpdated(ClientWorld world, BlockPos pos) {
		if (scanner != null) {
			scanner.blockUpdated(world, pos);
		}
	}

	public static void notify(MinecraftClient client, Text message) {
		if (client.player != null) {
			client.player.sendMessage(message, true);
		}
	}

	public static String displayName(Identifier id) {
		return net.minecraft.registry.Registries.BLOCK.getOptionalValue(id)
				.map(block -> block.getName().getString())
				.orElse(id.toString());
	}
}
