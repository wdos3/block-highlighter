package com.example.blockhighlighter.scan;

import com.example.blockhighlighter.config.HighlightConfig;
import com.example.blockhighlighter.config.SelectedBlockManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BlockScanner {
	private static final int BLOCKS_PER_TICK = 4_096;
	private static final int SCAN_BATCH_SIZE = 256;
	private static final long MAX_SCAN_NANOS_PER_TICK = 2_000_000L;
	private static final int PRUNE_INTERVAL_TICKS = 20;

	private final HighlightConfig config;
	private final SelectedBlockManager selectedBlocks;
	private final HighlightCache cache;
	private final RefreshCycle refreshCycle = new RefreshCycle();
	private final ArrayDeque<ScanTask> queue = new ArrayDeque<>();
	private final Set<Long> queuedChunks = new HashSet<>();
	private ClientWorld currentWorld;
	private Object currentDimension;
	private long ticks;
	private int scanCenterY;
	private int scannedRadius = -1;

	public BlockScanner(HighlightConfig config, SelectedBlockManager selectedBlocks, HighlightCache cache) {
		this.config = config;
		this.selectedBlocks = selectedBlocks;
		this.cache = cache;
	}

	public HighlightCache cache() {
		return cache;
	}

	public void tick(MinecraftClient client) {
		ClientWorld world = client.world;
		if (world == null || client.player == null) {
			reset();
			return;
		}
		Object dimension = world.getRegistryKey();
		if (world != currentWorld || !dimension.equals(currentDimension)) {
			resetForWorld(world, dimension);
		}
		if (scannedRadius != config.radius) {
			invalidateLoadedChunks();
			scannedRadius = config.radius;
		}
		if (!config.enabled || selectedBlocks.idSet().isEmpty()) {
			return;
		}

		ticks++;
		scanCenterY = client.player.getBlockPos().getY();
		discoverLoadedChunks(world, client.player.getBlockPos());
		if (ticks % PRUNE_INTERVAL_TICKS == 0) {
			pruneUnloadedChunks(world);
		}
		if (refreshCycle.tick(config.refreshSeconds)) {
			schedulePeriodicRefresh(world, client.player.getBlockPos());
		}
		processQueue(world);
	}

	public void invalidateLoadedChunks() {
		queue.clear();
		queuedChunks.clear();
		cache.clear();
		refreshCycle.reset();
	}

	public void reset() {
		currentWorld = null;
		currentDimension = null;
		queue.clear();
		queuedChunks.clear();
		cache.clear();
		refreshCycle.reset();
		ticks = 0;
		scanCenterY = 0;
		scannedRadius = -1;
	}

	private void resetForWorld(ClientWorld world, Object dimension) {
		reset();
		currentWorld = world;
		currentDimension = dimension;
	}

	private void discoverLoadedChunks(ClientWorld world, BlockPos playerPos) {
		int chunkRadius = Math.max(1, (config.radius + 15) / 16 + 1);
		ChunkPos center = new ChunkPos(playerPos);
		// Queue the closest chunks first. This keeps a newly selected block near the
		// player from waiting behind the far edge of the scan square.
		if (world.isChunkLoaded(center.x, center.z)) {
			enqueueIfNeeded(center.x, center.z);
		}
		for (int ring = 1; ring <= chunkRadius; ring++) {
			for (int offset = -ring; offset <= ring; offset++) {
				enqueueLoaded(world, center.x + offset, center.z - ring);
				enqueueLoaded(world, center.x + offset, center.z + ring);
			}
			for (int offset = -ring + 1; offset < ring; offset++) {
				enqueueLoaded(world, center.x - ring, center.z + offset);
				enqueueLoaded(world, center.x + ring, center.z + offset);
			}
		}
	}

	private void enqueueLoaded(ClientWorld world, int chunkX, int chunkZ) {
		if (world.isChunkLoaded(chunkX, chunkZ)) {
			enqueueIfNeeded(chunkX, chunkZ);
		}
	}

	private void enqueueIfNeeded(int chunkX, int chunkZ) {
		long chunkKey = ChunkPos.toLong(chunkX, chunkZ);
		if (!cache.containsChunk(chunkKey) && queuedChunks.add(chunkKey)) {
			queue.addLast(new ScanTask(chunkX, chunkZ, scanCenterY, currentWorld.getBottomY(), currentWorld.getHeight(), true));
		}
	}

	private void processQueue(ClientWorld world) {
		int budget = BLOCKS_PER_TICK;
		long deadline = System.nanoTime() + MAX_SCAN_NANOS_PER_TICK;
		while (budget > 0 && !queue.isEmpty()) {
			ScanTask task = queue.peekFirst();
			WorldChunk chunk = world.getChunkManager().getWorldChunk(task.chunkX, task.chunkZ, false);
			if (chunk == null) {
				queue.removeFirst();
				queuedChunks.remove(task.chunkKey());
				continue;
			}

			int processed = task.scan(chunk, selectedBlocks, Math.min(budget, SCAN_BATCH_SIZE));
			budget -= processed;
			boolean complete = task.isComplete();
			// Keep the previous complete result visible while a refresh is in progress.
			if (task.publishPartial && task.hasNewMatchesSincePublish() && task.shouldPublish()) {
				cache.replaceChunk(task.chunkKey(), task.matches);
				task.markPublished();
			}
			if (complete) {
				cache.replaceChunk(task.chunkKey(), task.matches);
			}
			if (complete) {
				queue.removeFirst();
				queuedChunks.remove(task.chunkKey());
			}
			if (processed == 0 && !complete) {
				break;
			}
			if (System.nanoTime() >= deadline) {
				break;
			}
		}
	}

	private void pruneUnloadedChunks(ClientWorld world) {
		for (long chunkKey : cache.chunkKeys()) {
			int chunkX = ChunkPos.getPackedX(chunkKey);
			int chunkZ = ChunkPos.getPackedZ(chunkKey);
			if (!world.isChunkLoaded(chunkX, chunkZ)) {
				cache.removeChunk(chunkKey);
			}
		}
	}

	private void schedulePeriodicRefresh(ClientWorld world, BlockPos playerPos) {
		List<Long> loadedKeys = cache.chunkKeys();
		ChunkPos center = new ChunkPos(playerPos);
		int chunkRadius = Math.max(1, (config.radius + 15) / 16 + 1);
		loadedKeys.sort(Comparator.comparingLong(chunkKey -> {
			long dx = (long) ChunkPos.getPackedX(chunkKey) - center.x;
			long dz = (long) ChunkPos.getPackedZ(chunkKey) - center.z;
			return dx * dx + dz * dz;
		}));
		for (long chunkKey : loadedKeys) {
			int chunkX = ChunkPos.getPackedX(chunkKey);
			int chunkZ = ChunkPos.getPackedZ(chunkKey);
			if (Math.abs(chunkX - center.x) > chunkRadius || Math.abs(chunkZ - center.z) > chunkRadius) {
				continue;
			}
			if (world.isChunkLoaded(chunkX, chunkZ) && queuedChunks.add(chunkKey)) {
				queue.addLast(new ScanTask(chunkX, chunkZ, scanCenterY, world.getBottomY(), world.getHeight(), false));
			}
		}
	}

	private static final class ScanTask {
		private final int chunkX;
		private final int chunkZ;
		private final int[] yOrder;
		private final boolean publishPartial;
		private final Set<Long> matches = new HashSet<>();
		private int index;
		private int lastPublishedIndex = -1;
		private int lastPublishedMatchCount;

		private ScanTask(int chunkX, int chunkZ, int playerY, int bottomY, int height, boolean publishPartial) {
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.yOrder = createVerticalOrder(playerY, bottomY, height);
			this.publishPartial = publishPartial;
		}

		private int scan(WorldChunk chunk, SelectedBlockManager selectedBlocks, int budget) {
			int totalBlocks = yOrder.length * 256;
			int start = index;
			BlockPos.Mutable mutable = new BlockPos.Mutable();
			while (index < totalBlocks && index - start < budget) {
				int localX = index & 15;
				int localZ = (index >> 4) & 15;
				int y = yOrder[index >> 8];
				mutable.set((chunkX << 4) + localX, y, (chunkZ << 4) + localZ);
				BlockState state = chunk.getBlockState(mutable);
				if (!state.isAir() && selectedBlocks.isSelected(state.getBlock())) {
					matches.add(mutable.asLong());
				}
				index++;
			}
			return index - start;
		}

		private boolean isComplete() {
			return index >= yOrder.length * 256;
		}

		private boolean hasNewMatchesSincePublish() {
			return matches.size() != lastPublishedMatchCount;
		}

		private boolean shouldPublish() {
			return lastPublishedIndex < 0 || index - lastPublishedIndex >= BLOCKS_PER_TICK;
		}

		private void markPublished() {
			lastPublishedIndex = index;
			lastPublishedMatchCount = matches.size();
		}

		private long chunkKey() {
			return ChunkPos.toLong(chunkX, chunkZ);
		}

		private static int[] createVerticalOrder(int playerY, int bottomY, int height) {
			int[] order = new int[height];
			int topY = bottomY + height;
			int centerY = Math.max(bottomY, Math.min(topY - 1, playerY));
			int count = 0;
			for (int distance = 0; count < height; distance++) {
				int above = centerY + distance;
				if (above < topY) {
					order[count++] = above;
				}
				if (distance > 0) {
					int below = centerY - distance;
					if (below >= bottomY) {
						order[count++] = below;
					}
				}
			}
			return order;
		}
	}
}
