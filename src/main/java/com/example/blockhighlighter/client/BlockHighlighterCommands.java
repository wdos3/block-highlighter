package com.example.blockhighlighter.client;

import com.example.blockhighlighter.gui.HighlightScreen;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;

public final class BlockHighlighterCommands {
	private static boolean openRequested;

	private BlockHighlighterCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(openCommand("blockhighlighter"));
			dispatcher.register(openCommand("bh"));
			BlockHighlighterClient.LOGGER.info("Registered client commands /blockhighlighter and /bh.");
		});
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> openCommand(String name) {
		return ClientCommandManager.literal(name).executes(context -> {
			openRequested = true;
			BlockHighlighterClient.LOGGER.info("Received /{}; opening the block highlighter on the next client tick.", name);
			return 1;
		});
	}

	public static void handlePendingOpen(MinecraftClient client) {
		if (!openRequested) {
			return;
		}
		if (client.world == null) {
			openRequested = false;
			return;
		}
		// ChatScreen closes as part of command submission. Waiting until no screen is
		// active prevents that teardown from immediately replacing our screen.
		if (client.currentScreen == null) {
			openRequested = false;
			client.setScreen(new HighlightScreen(null));
		}
	}
}
