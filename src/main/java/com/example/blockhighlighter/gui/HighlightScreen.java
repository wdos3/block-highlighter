package com.example.blockhighlighter.gui;

import com.example.blockhighlighter.client.BlockHighlighterClient;
import com.example.blockhighlighter.config.ColorUtil;
import com.example.blockhighlighter.config.HighlightConfig;
import com.example.blockhighlighter.search.BlockSearchIndex;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.Comparator;
import java.util.List;

public final class HighlightScreen extends Screen {
	private static final int ROW_HEIGHT = 32;
	private static final int PANEL_GAP = 8;
	private static final int PANEL_MARGIN = 12;

	private final Screen parent;
	private BlockSearchIndex index;
	private TextFieldWidget searchField;
	private TextFieldWidget colorField;
	private RadiusSlider radiusSlider;
	private OpacitySlider opacitySlider;
	private WidthSlider widthSlider;
	private ButtonWidget toggleButton;
	private ButtonWidget hudButton;
	private ButtonWidget sortButton;
	private int availableScroll;
	private int selectedScroll;
	private boolean sortById;
	private String status = "";

	public HighlightScreen(Screen parent) {
		super(Text.translatable("screen.block_highlighter.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		index = index == null ? BlockSearchIndex.create() : index;
		Layout layout = layout();

		searchField = addDrawableChild(new TextFieldWidget(textRenderer, layout.left, 24, layout.contentWidth, 20, Text.translatable("screen.block_highlighter.search")));
		searchField.setMaxLength(128);
		searchField.setPlaceholder(Text.translatable("screen.block_highlighter.search_placeholder"));
		searchField.setChangedListener(value -> {
			index.filtered(value);
			availableScroll = 0;
			selectedScroll = 0;
		});

		toggleButton = addDrawableChild(ButtonWidget.builder(Text.empty(), ignored -> {
			BlockHighlighterClient.config().enabled = !BlockHighlighterClient.config().enabled;
			refreshControlMessages();
		}).dimensions(layout.left, layout.controlsY, layout.toggleWidth, 20).build());
		hudButton = addDrawableChild(ButtonWidget.builder(Text.empty(), ignored -> {
			BlockHighlighterClient.config().hudEnabled = !BlockHighlighterClient.config().hudEnabled;
			refreshControlMessages();
		}).dimensions(layout.hudX, layout.controlsY, layout.hudWidth, 20).build());

		radiusSlider = addDrawableChild(new RadiusSlider(layout.radiusX, layout.radiusY, layout.radiusWidth, 20, BlockHighlighterClient.config().radius));

		colorField = addDrawableChild(new TextFieldWidget(textRenderer, layout.left, layout.settingsY, layout.colorWidth, 20, Text.translatable("screen.block_highlighter.color")));
		colorField.setMaxLength(7);
		colorField.setTextPredicate(value -> value.isEmpty() || value.matches("#?[0-9a-fA-F]{0,6}"));
		colorField.setPlaceholder(Text.literal("#00FFFF"));
		colorField.setText(BlockHighlighterClient.config().defaultColor);
		opacitySlider = addDrawableChild(new OpacitySlider(layout.opacityX, layout.settingsY, layout.sliderWidth, 20, BlockHighlighterClient.config().opacity));
		widthSlider = addDrawableChild(new WidthSlider(layout.widthX, layout.settingsY, layout.sliderWidth, 20, BlockHighlighterClient.config().lineWidth));
		addDrawableChild(new RefreshSlider(layout.refreshX, layout.refreshY, layout.refreshWidth, 20, BlockHighlighterClient.config().refreshSeconds));

		addDrawableChild(ButtonWidget.builder(Text.translatable("screen.block_highlighter.clear_all"), ignored -> confirmClearAll())
				.dimensions(layout.left, layout.bottomButtonY, 110, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("screen.block_highlighter.done"), ignored -> attemptClose())
				.dimensions(width - layout.left - 110, layout.bottomButtonY, 110, 20).build());
		sortButton = addDrawableChild(ButtonWidget.builder(Text.empty(), ignored -> {
			sortById = !sortById;
			refreshSortMessage();
		}).dimensions(layout.right + layout.halfWidth - 90, layout.panelTop + 2, 88, 18).build());
		refreshControlMessages();
		refreshSortMessage();
		setInitialFocus(searchField);

		// Keep the two list columns symmetrical at all supported GUI sizes.
		if (layout.halfWidth < 150 || layout.panelBottom <= layout.panelTop) {
			status = Text.translatable("screen.block_highlighter.resize_hint").getString();
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		Layout layout = layout();

		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFF);
		renderPanel(context, mouseX, mouseY, layout.left, layout.panelTop, layout.halfWidth, layout.panelBottom,
				Text.translatable("screen.block_highlighter.available"), index.filtered(searchField.getText()), availableScroll, false);
		List<BlockSearchIndex.BlockEntry> selected = selectedEntries(searchField.getText());
		renderPanel(context, mouseX, mouseY, layout.right, layout.panelTop, layout.halfWidth, layout.panelBottom,
				Text.translatable("screen.block_highlighter.selected"), selected, selectedScroll, true);

		if (!status.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2, layout.bottomButtonY - 20, 0xFFFFC080);
		}
	}

	private void renderPanel(DrawContext context, int mouseX, int mouseY, int x, int top, int panelWidth, int bottom,
			Text heading, List<BlockSearchIndex.BlockEntry> entries, int scroll, boolean selectedPanel) {
		context.fill(x, top, x + panelWidth, bottom, 0xCC111820);
		context.fill(x, top, x + panelWidth, top + 1, 0xFF6ED7E5);
		context.drawTextWithShadow(textRenderer, heading, x + 7, top + 7, 0xFFFFFF);
		if (!selectedPanel) {
			context.drawTextWithShadow(textRenderer, Text.translatable("screen.block_highlighter.count", entries.size()), x + panelWidth - 76, top + 7, 0xFFB7C8D6);
		}
		int listTop = top + 22;
		int listBottom = bottom - 4;
		int contentHeight = Math.max(0, entries.size() * ROW_HEIGHT - (listBottom - listTop));
		int clampedScroll = Math.max(0, Math.min(scroll, contentHeight));
		if (selectedPanel) {
			selectedScroll = clampedScroll;
		} else {
			availableScroll = clampedScroll;
		}

		context.enableScissor(x, listTop, x + panelWidth, listBottom);
		for (int i = 0; i < entries.size(); i++) {
			int rowY = listTop + i * ROW_HEIGHT - clampedScroll;
			if (rowY + ROW_HEIGHT < listTop || rowY > listBottom) {
				continue;
			}
			BlockSearchIndex.BlockEntry entry = entries.get(i);
			boolean hovered = mouseX >= x && mouseX < x + panelWidth && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
			context.fill(x + 2, rowY, x + panelWidth - 2, rowY + ROW_HEIGHT - 1, hovered ? 0xFF294453 : (i % 2 == 0 ? 0xAA18232C : 0xAA141D25));
			ItemStack stack = new ItemStack(entry.block().asItem());
			if (!stack.isEmpty()) {
				context.drawItem(stack, x + 5, rowY + 5);
			}
			int textX = x + 29;
			String name = truncate(entry.displayName().getString(), panelWidth - 38);
			String id = truncate(entry.id().toString(), panelWidth - 38);
			context.drawTextWithShadow(textRenderer, name, textX, rowY + 4, 0xFFF3F7FA);
			context.drawTextWithShadow(textRenderer, id, textX, rowY + 17, 0xFF9DB3C1);
			if (selectedPanel) {
				context.drawTextWithShadow(textRenderer, "✓", x + panelWidth - 14, rowY + 10, 0xFF68F2C3);
			}
		}
		context.disableScissor();
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (super.mouseClicked(click, doubled)) {
			return true;
		}
		if (click.button() != 0) {
			return false;
		}
		Layout layout = layout();
		if (click.y() >= layout.panelTop + 22 && click.y() < layout.panelBottom) {
			if (click.x() >= layout.left && click.x() < layout.left + layout.halfWidth) {
				BlockSearchIndex.BlockEntry entry = entryAt(index.filtered(searchField.getText()), click.y(), layout.panelTop, layout.panelBottom, availableScroll);
				if (entry != null) {
					if (BlockHighlighterClient.selectedBlocks().add(entry.id())) {
						BlockHighlighterClient.selectionChanged();
						status = Text.translatable("screen.block_highlighter.added", entry.displayName()).getString();
					}
					return true;
				}
			} else if (click.x() >= layout.right && click.x() < layout.left + layout.contentWidth) {
				List<BlockSearchIndex.BlockEntry> selected = selectedEntries(searchField.getText());
				BlockSearchIndex.BlockEntry entry = entryAt(selected, click.y(), layout.panelTop, layout.panelBottom, selectedScroll);
				if (entry != null) {
					if (BlockHighlighterClient.selectedBlocks().remove(entry.id())) {
						BlockHighlighterClient.selectionChanged();
						status = Text.translatable("screen.block_highlighter.removed", entry.displayName()).getString();
					}
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		Layout layout = layout();
		if (mouseY >= layout.panelTop && mouseY < layout.panelBottom) {
			if (mouseX >= layout.left && mouseX < layout.left + layout.halfWidth) {
				availableScroll = Math.max(0, availableScroll - (int) (verticalAmount * ROW_HEIGHT));
				return true;
			}
			if (mouseX >= layout.right && mouseX < layout.left + layout.contentWidth) {
				selectedScroll = Math.max(0, selectedScroll - (int) (verticalAmount * ROW_HEIGHT));
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		attemptClose();
	}

	private void attemptClose() {
		if (!saveSettings()) {
			status = Text.translatable("screen.block_highlighter.invalid_color").getString();
			return;
		}
		if (client != null) {
			client.setScreen(parent);
		}
	}

	private boolean saveSettings() {
		int parsed = ColorUtil.parseRgb(colorField.getText(), -1);
		if (parsed < 0) {
			return false;
		}
		BlockHighlighterClient.config().defaultColor = ColorUtil.toHex(parsed);
		BlockHighlighterClient.saveConfig();
		return true;
	}

	private void confirmClearAll() {
		if (client == null) {
			return;
		}
		client.setScreen(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				BlockHighlighterClient.selectedBlocks().clear();
				BlockHighlighterClient.selectionChanged();
				BlockHighlighterClient.saveConfig();
				status = Text.translatable("screen.block_highlighter.cleared").getString();
			}
			client.setScreen(this);
		}, Text.translatable("screen.block_highlighter.confirm_clear"), Text.translatable("gui.yes")));
	}

	private void refreshControlMessages() {
		if (toggleButton == null) {
			return;
		}
		toggleButton.setMessage(Text.translatable("screen.block_highlighter.toggle", Text.translatable(
				BlockHighlighterClient.config().enabled ? "screen.block_highlighter.on" : "screen.block_highlighter.off")));
		hudButton.setMessage(Text.translatable("screen.block_highlighter.hud", Text.translatable(
				BlockHighlighterClient.config().hudEnabled ? "screen.block_highlighter.on" : "screen.block_highlighter.off")));
	}

	private void refreshSortMessage() {
		if (sortButton != null) {
			sortButton.setMessage(Text.translatable("screen.block_highlighter.sort", Text.translatable(
					sortById ? "screen.block_highlighter.sort_id" : "screen.block_highlighter.sort_name")));
		}
	}

	private List<BlockSearchIndex.BlockEntry> selectedEntries(String query) {
		Comparator<BlockSearchIndex.BlockEntry> comparator = sortById
				? Comparator.comparing(entry -> entry.id().toString())
				: Comparator.comparing((BlockSearchIndex.BlockEntry entry) -> entry.displayName().getString(), String.CASE_INSENSITIVE_ORDER)
						.thenComparing(entry -> entry.id().toString());
		return index.filtered(query).stream()
				.filter(entry -> BlockHighlighterClient.selectedBlocks().isSelected(entry.id()))
				.sorted(comparator)
				.toList();
	}

	private Layout layout() {
		int contentWidth = Math.min(920, Math.max(360, width - PANEL_MARGIN * 2));
		int left = (width - contentWidth) / 2;
		int halfWidth = (contentWidth - PANEL_GAP) / 2;
		boolean compact = contentWidth < 520;
		int controlsY = 49;
		int toggleWidth = compact ? halfWidth : 128;
		int hudX = compact ? left + halfWidth + PANEL_GAP : left + 132;
		int hudWidth = compact ? halfWidth : 112;
		int radiusY = compact ? controlsY + 25 : controlsY;
		int radiusX = compact ? left : hudX + hudWidth + PANEL_GAP;
		int radiusWidth = compact ? halfWidth : (left + contentWidth - radiusX - PANEL_GAP) / 2;
		int refreshX = radiusX + radiusWidth + PANEL_GAP;
		int refreshWidth = left + contentWidth - refreshX;
		int refreshY = radiusY;
		int settingsY = compact ? radiusY + 25 : 74;
		int colorWidth = Math.min(118, Math.max(90, (contentWidth - PANEL_GAP * 2) / 3));
		int sliderWidth = Math.min(166, (contentWidth - colorWidth - PANEL_GAP * 2) / 2);
		int opacityX = left + colorWidth + PANEL_GAP;
		int widthX = opacityX + sliderWidth + PANEL_GAP;
		int panelTop = settingsY + 27;
		int bottomButtonY = Math.max(panelTop + 50, height - 28);
		return new Layout(contentWidth, left, halfWidth, left + halfWidth + PANEL_GAP, controlsY, toggleWidth,
				hudX, hudWidth, radiusX, radiusY, radiusWidth, refreshX, refreshY, refreshWidth,
				settingsY, colorWidth, sliderWidth, opacityX, widthX,
				panelTop, bottomButtonY - 6, bottomButtonY);
	}

	private static BlockSearchIndex.BlockEntry entryAt(List<BlockSearchIndex.BlockEntry> entries, double mouseY, int panelTop, int panelBottom, int scroll) {
		int listTop = panelTop + 22;
		if (mouseY < listTop || mouseY >= panelBottom - 4) {
			return null;
		}
		int index = ((int) mouseY - listTop + scroll) / ROW_HEIGHT;
		return index >= 0 && index < entries.size() ? entries.get(index) : null;
	}

	private String truncate(String value, int maxWidth) {
		if (textRenderer.getWidth(value) <= maxWidth) {
			return value;
		}
		String suffix = "...";
		String result = value;
		while (!result.isEmpty() && textRenderer.getWidth(result + suffix) > maxWidth) {
			result = result.substring(0, result.length() - 1);
		}
		return result + suffix;
	}

	private abstract class ConfigSlider extends SliderWidget {
		private ConfigSlider(int x, int y, int width, int height, double value) {
			super(x, y, width, height, Text.empty(), value);
		}

		protected int radius() {
			return HighlightConfig.MIN_RADIUS + (int) Math.round(value * (HighlightConfig.MAX_RADIUS - HighlightConfig.MIN_RADIUS));
		}
	}

	private record Layout(int contentWidth, int left, int halfWidth, int right, int controlsY, int toggleWidth,
			int hudX, int hudWidth, int radiusX, int radiusY, int radiusWidth, int refreshX, int refreshY, int refreshWidth,
			int settingsY, int colorWidth,
			int sliderWidth, int opacityX, int widthX, int panelTop, int panelBottom, int bottomButtonY) {
	}

	private final class RadiusSlider extends ConfigSlider {
		private RadiusSlider(int x, int y, int width, int height, int radius) {
			super(x, y, width, height, (radius - HighlightConfig.MIN_RADIUS) / (double) (HighlightConfig.MAX_RADIUS - HighlightConfig.MIN_RADIUS));
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.translatable("screen.block_highlighter.radius", radius()));
		}

		@Override
		protected void applyValue() {
			BlockHighlighterClient.config().radius = radius();
		}
	}

	private final class OpacitySlider extends ConfigSlider {
		private OpacitySlider(int x, int y, int width, int height, float opacity) {
			super(x, y, width, height, (opacity - HighlightConfig.MIN_OPACITY) / (HighlightConfig.MAX_OPACITY - HighlightConfig.MIN_OPACITY));
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.translatable("screen.block_highlighter.opacity", Math.round(HighlightConfig.MIN_OPACITY * 100 + value * (HighlightConfig.MAX_OPACITY - HighlightConfig.MIN_OPACITY) * 100)));
		}

		@Override
		protected void applyValue() {
			BlockHighlighterClient.config().opacity = HighlightConfig.MIN_OPACITY + (float) value * (HighlightConfig.MAX_OPACITY - HighlightConfig.MIN_OPACITY);
		}
	}

	private final class RefreshSlider extends ConfigSlider {
		private RefreshSlider(int x, int y, int width, int height, int refreshSeconds) {
			super(x, y, width, height, (refreshSeconds - HighlightConfig.MIN_REFRESH_SECONDS)
					/ (double) (HighlightConfig.MAX_REFRESH_SECONDS - HighlightConfig.MIN_REFRESH_SECONDS));
			updateMessage();
		}

		private int refreshSeconds() {
			return HighlightConfig.MIN_REFRESH_SECONDS + (int) Math.round(value
					* (HighlightConfig.MAX_REFRESH_SECONDS - HighlightConfig.MIN_REFRESH_SECONDS));
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.translatable("screen.block_highlighter.refresh_interval", refreshSeconds()));
		}

		@Override
		protected void applyValue() {
			BlockHighlighterClient.config().refreshSeconds = refreshSeconds();
		}
	}

	private final class WidthSlider extends ConfigSlider {
		private WidthSlider(int x, int y, int width, int height, float lineWidth) {
			super(x, y, width, height, (lineWidth - HighlightConfig.MIN_LINE_WIDTH) / (HighlightConfig.MAX_LINE_WIDTH - HighlightConfig.MIN_LINE_WIDTH));
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.translatable("screen.block_highlighter.thickness", String.format(java.util.Locale.ROOT, "%.1f", HighlightConfig.MIN_LINE_WIDTH + value * (HighlightConfig.MAX_LINE_WIDTH - HighlightConfig.MIN_LINE_WIDTH))));
		}

		@Override
		protected void applyValue() {
			BlockHighlighterClient.config().lineWidth = HighlightConfig.MIN_LINE_WIDTH + (float) value * (HighlightConfig.MAX_LINE_WIDTH - HighlightConfig.MIN_LINE_WIDTH);
		}
	}
}
