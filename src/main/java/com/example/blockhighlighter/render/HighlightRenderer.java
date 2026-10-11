package com.example.blockhighlighter.render;

import com.example.blockhighlighter.config.ColorUtil;
import com.example.blockhighlighter.config.HighlightConfig;
import com.example.blockhighlighter.config.SelectedBlockManager;
import com.example.blockhighlighter.scan.HighlightCache;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.MappableRingBuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

public final class HighlightRenderer {
	private static final Identifier PIPELINE_ID = Identifier.of("block-highlighter", "pipeline/through-wall-lines");
	private static final Identifier VERTEX_SHADER_ID = Identifier.of("block-highlighter", "core/through_wall_lines");
	private static final Identifier FRAGMENT_SHADER_ID = Identifier.of("block-highlighter", "core/through_wall_lines");
	private static final RenderPipeline THROUGH_WALL_LINES = RenderPipelines.register(
			RenderPipeline.builder()
					.withLocation(PIPELINE_ID)
					.withVertexShader(VERTEX_SHADER_ID)
					.withFragmentShader(FRAGMENT_SHADER_ID)
					.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
					.withUniform("Projection", UniformType.UNIFORM_BUFFER)
					.withUniform("Globals", UniformType.UNIFORM_BUFFER)
					.withBlend(BlendFunction.TRANSLUCENT)
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.withDepthWrite(false)
					.withCull(false)
					.withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.DrawMode.LINES)
					.build()
	);
	private static final VertexFormat VERTEX_FORMAT = VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH;
	private static final VertexFormat.DrawMode DRAW_MODE = VertexFormat.DrawMode.LINES;
	private static final int INITIAL_BUFFER_SIZE = 1 << 20;
	private static final int MAX_RENDERED_POSITIONS = 8_192;
	private static final long GEOMETRY_REFRESH_INTERVAL_TICKS = 20L;
	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
	private static final BufferAllocator ALLOCATOR = new BufferAllocator(INITIAL_BUFFER_SIZE);

	private static HighlightCache cache;
	private static HighlightConfig config;
	private static SelectedBlockManager selectedBlocks;
	private static MappableRingBuffer vertexBuffer;
	private static int renderedIndexCount;
	private static long renderedCacheRevision = -1L;
	private static long renderedWorldTime = Long.MIN_VALUE;
	private static BlockPos renderedPlayerBlock;
	private static int renderedSettingsHash;
	private static boolean vertexBufferHasData;

	private HighlightRenderer() {
	}

	public static void initialize(HighlightConfig highlightConfig, SelectedBlockManager manager, HighlightCache highlightCache) {
		config = highlightConfig;
		selectedBlocks = manager;
		cache = highlightCache;
		renderedIndexCount = 0;
		renderedCacheRevision = -1L;
		renderedWorldTime = Long.MIN_VALUE;
		renderedPlayerBlock = null;
		renderedSettingsHash = 0;
		vertexBufferHasData = false;
		// Draw after the main world pass has finished.  The renderer writes directly to
		// Minecraft's main framebuffer, so drawing earlier can be overwritten by the
		// remaining terrain/translucent passes on modern 1.21.11 frame graphs.
		WorldRenderEvents.END_MAIN.register(HighlightRenderer::render);
	}

	public static void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null || cache == null || config == null || selectedBlocks == null
				|| !config.enabled || selectedBlocks.idSet().isEmpty() || cache.isEmpty()) {
			return;
		}

		long worldTime = client.world.getTime();
		BlockPos playerBlock = client.player.getBlockPos();
		int settingsHash = settingsHash();
		if (needsGeometryRebuild(worldTime, playerBlock, settingsHash)) {
			rebuildGeometry(client, worldTime, playerBlock, settingsHash);
		}
		if (renderedIndexCount > 0) {
			draw(client);
		}
	}

	private static boolean needsGeometryRebuild(long worldTime, BlockPos playerBlock, int settingsHash) {
		return renderedCacheRevision != cache.revision()
				|| renderedPlayerBlock == null
				|| !renderedPlayerBlock.equals(playerBlock)
				|| renderedSettingsHash != settingsHash
				|| renderedWorldTime == Long.MIN_VALUE
				|| worldTime - renderedWorldTime >= GEOMETRY_REFRESH_INTERVAL_TICKS;
	}

	private static int settingsHash() {
		int hash = 31 * config.radius + Objects.hashCode(config.defaultColor);
		hash = 31 * hash + Float.floatToIntBits(config.opacity);
		hash = 31 * hash + Float.floatToIntBits(config.lineWidth);
		return hash;
	}

	private static void rebuildGeometry(MinecraftClient client, long worldTime, BlockPos playerBlock, int settingsHash) {
		BufferBuilder builder = new BufferBuilder(ALLOCATOR, DRAW_MODE, VERTEX_FORMAT);
		// The camera translation is supplied in DynamicTransforms at draw time. Keeping
		// world coordinates in the cached buffer lets the camera move without rebuilding it.
		Matrix4f positionMatrix = new Matrix4f();
		int renderedPositions = 0;
		int radiusSquared = config.radius * config.radius;
		int defaultColor = ColorUtil.parseRgb(config.defaultColor, 0x00FFFF);
		int alpha = Math.round(config.opacity * 255.0f);
		float lineWidth = config.lineWidth;

		outer:
		for (Map.Entry<Long, Set<Long>> chunkEntry : cache.entries()) {
			for (long positionLong : chunkEntry.getValue()) {
				int x = BlockPos.unpackLongX(positionLong);
				int y = BlockPos.unpackLongY(positionLong);
				int z = BlockPos.unpackLongZ(positionLong);
				double dx = x + 0.5 - client.player.getX();
				double dy = y + 0.5 - client.player.getY();
				double dz = z + 0.5 - client.player.getZ();
				if (dx * dx + dy * dy + dz * dz > radiusSquared) {
					continue;
				}

				BlockPos blockPos = BlockPos.fromLong(positionLong);
				BlockState state = client.world.getBlockState(blockPos);
				if (state.isAir() || !selectedBlocks.isSelected(state.getBlock())) {
					continue;
				}
				VoxelShape shape = outlineShape(client.world, state, blockPos);
				if (shape.isEmpty()) {
					continue;
				}
				int rgb = selectedBlocks.colorFor(net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()), defaultColor);
				emitShape(builder, positionMatrix, shape, x, y, z, rgb, alpha, lineWidth);
				if (++renderedPositions >= MAX_RENDERED_POSITIONS) {
					break outer;
				}
			}
		}

		BuiltBuffer builtBuffer = builder.endNullable();
		try {
			if (builtBuffer == null) {
				renderedIndexCount = 0;
				vertexBufferHasData = false;
			} else {
				uploadGeometry(builtBuffer);
			}
		} finally {
			if (builtBuffer != null) {
				builtBuffer.close();
			}
			ALLOCATOR.clear();
		}

		renderedCacheRevision = cache.revision();
		renderedWorldTime = worldTime;
		renderedPlayerBlock = playerBlock.toImmutable();
		renderedSettingsHash = settingsHash;
	}

	private static VoxelShape outlineShape(net.minecraft.client.world.ClientWorld world, BlockState state, BlockPos pos) {
		VoxelShape shape = state.getOutlineShape(world, pos, ShapeContext.absent());
		if (!shape.isEmpty()) {
			return shape;
		}
		shape = state.getCollisionShape(world, pos, ShapeContext.absent());
		if (!shape.isEmpty()) {
			return shape;
		}
		Box bounds = new Box(0.05, 0.05, 0.05, 0.95, 0.95, 0.95);
		return VoxelShapes.cuboid(bounds);
	}

	private static void emitShape(BufferBuilder builder, Matrix4f matrix, VoxelShape shape, int blockX, int blockY, int blockZ,
			int rgb, int alpha, float lineWidth) {
		int red = (rgb >> 16) & 0xFF;
		int green = (rgb >> 8) & 0xFF;
		int blue = rgb & 0xFF;
		shape.forEachEdge((minX, minY, minZ, maxX, maxY, maxZ) -> {
			// rendertype_lines.vsh uses Position + Normal to calculate the screen-space
			// extrusion. A zero normal makes that calculation NaN and draws nothing.
			float normalX = (float) (maxX - minX);
			float normalY = (float) (maxY - minY);
			float normalZ = (float) (maxZ - minZ);
			float length = (float) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
			if (length == 0.0f) {
				return;
			}
			normalX /= length;
			normalY /= length;
			normalZ /= length;
			emitVertex(builder, matrix, blockX + minX, blockY + minY, blockZ + minZ,
					normalX, normalY, normalZ, red, green, blue, alpha, lineWidth);
			emitVertex(builder, matrix, blockX + maxX, blockY + maxY, blockZ + maxZ,
					normalX, normalY, normalZ, red, green, blue, alpha, lineWidth);
		});
	}

	private static void emitVertex(VertexConsumer builder, Matrix4f matrix, double x, double y, double z,
			float normalX, float normalY, float normalZ, int red, int green, int blue, int alpha, float lineWidth) {
		builder.vertex(matrix, (float) x, (float) y, (float) z)
				.color(red, green, blue, alpha)
				.normal(normalX, normalY, normalZ)
				.lineWidth(lineWidth);
	}

	private static void uploadGeometry(BuiltBuffer builtBuffer) {
		ByteBuffer source = builtBuffer.getBuffer();
		int vertexBytes = source.remaining();
		if (vertexBytes == 0) {
			renderedIndexCount = 0;
			vertexBufferHasData = false;
			return;
		}
		ensureVertexBuffer(vertexBytes);
		if (vertexBufferHasData) {
			vertexBuffer.rotate();
		}
		GpuBuffer gpuVertexBuffer = vertexBuffer.getBlocking();
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.writeToBuffer(gpuVertexBuffer.slice(0, vertexBytes), source);

		BuiltBuffer.DrawParameters drawParameters = builtBuffer.getDrawParameters();
		renderedIndexCount = drawParameters.indexCount();
		vertexBufferHasData = true;
	}

	private static void draw(MinecraftClient client) {
		RenderSystem.ShapeIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(DRAW_MODE);
		GpuBuffer gpuIndexBuffer = indexBuffer.getIndexBuffer(renderedIndexCount);
		Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
		Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix())
				.translate((float) -camera.x, (float) -camera.y, (float) -camera.z);
		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().write(
				modelView, COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
		Framebuffer target = client.getFramebuffer();
		try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
					() -> "Block Highlighter outlines",
					target.getColorAttachmentView(), OptionalInt.empty(), target.getDepthAttachmentView(), OptionalDouble.empty())) {
			renderPass.setPipeline(THROUGH_WALL_LINES);
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", transforms);
			renderPass.setVertexBuffer(0, vertexBuffer.getBlocking());
			renderPass.setIndexBuffer(gpuIndexBuffer, indexBuffer.getIndexType());
			// In 1.21.11 these are vertex offset, first index, index count, instance count.
			renderPass.drawIndexed(0, 0, renderedIndexCount, 1);
		}
	}

	private static void ensureVertexBuffer(int requiredBytes) {
		if (vertexBuffer != null && vertexBuffer.size() >= requiredBytes) {
			return;
		}
		if (vertexBuffer != null) {
			vertexBuffer.close();
		}
		int size = INITIAL_BUFFER_SIZE;
		while (size < requiredBytes) {
			size = Math.multiplyExact(size, 2);
		}
		vertexBuffer = new MappableRingBuffer(() -> "Block Highlighter vertex buffer", GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_VERTEX, size);
		vertexBufferHasData = false;
	}

	public static void close() {
		if (vertexBuffer != null) {
			vertexBuffer.close();
			vertexBuffer = null;
		}
		renderedIndexCount = 0;
		vertexBufferHasData = false;
		ALLOCATOR.close();
	}
}
