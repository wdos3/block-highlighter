package com.example.blockhighlighter.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class HighlightHud {
	private static final Identifier HUD_ID = Identifier.of(BlockHighlighterClient.MOD_ID, "status");

	private HighlightHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, HUD_ID, HighlightHud::render);
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		if (!BlockHighlighterClient.config().hudEnabled) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		Text status = Text.translatable("hud.block_highlighter.status", Text.translatable(
				BlockHighlighterClient.config().enabled ? "hud.block_highlighter.on" : "hud.block_highlighter.off"));
		int width = client.textRenderer.getWidth(status) + 10;
		int height = client.textRenderer.fontHeight + 6;
		context.fill(6, 6, 6 + width, 6 + height, 0xAA10151C);
		context.drawTextWithShadow(client.textRenderer, status, 11, 9, BlockHighlighterClient.config().enabled ? 0xFF65F3FF : 0xFFB0B0B0);
	}
}
