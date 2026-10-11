package com.example.blockhighlighter.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import org.lwjgl.glfw.GLFW;

public final class BlockHighlighterKeybindings {
	private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of(BlockHighlighterClient.MOD_ID, "controls"));
	private static KeyBinding toggleKey;
	private static KeyBinding addLookedAtKey;

	private BlockHighlighterKeybindings() {
	}

	public static void register() {
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.block_highlighter.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY));
		addLookedAtKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.block_highlighter.add_looked_at", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY));
	}

	public static void handleKeyPresses(MinecraftClient client) {
		while (toggleKey.wasPressed()) {
			if (client.currentScreen == null) {
				BlockHighlighterClient.toggleEnabled(client);
			}
		}
		while (addLookedAtKey.wasPressed()) {
			if (client.currentScreen == null) {
				addLookedAtBlock(client);
			}
		}
	}

	private static void addLookedAtBlock(MinecraftClient client) {
		if (client.world == null || !(client.crosshairTarget instanceof BlockHitResult hit)) {
			return;
		}
		Block block = client.world.getBlockState(hit.getBlockPos()).getBlock();
		Identifier id = net.minecraft.registry.Registries.BLOCK.getId(block);
		boolean changed;
		if (client.isShiftPressed()) {
			changed = BlockHighlighterClient.selectedBlocks().remove(id);
			BlockHighlighterClient.notify(client, changed
					? Text.translatable("message.block_highlighter.removed", block.getName())
					: Text.translatable("message.block_highlighter.not_highlighted", block.getName()));
		} else {
			changed = BlockHighlighterClient.selectedBlocks().add(id);
			BlockHighlighterClient.notify(client, changed
					? Text.translatable("message.block_highlighter.added", block.getName())
					: Text.translatable("message.block_highlighter.already_highlighted", block.getName()));
		}
		if (changed) {
			BlockHighlighterClient.selectionChanged();
			BlockHighlighterClient.saveConfig();
		}
	}
}
