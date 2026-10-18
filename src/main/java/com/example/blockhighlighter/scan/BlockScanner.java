package com.example.blockhighlighter.scan;

import com.example.blockhighlighter.config.HighlightConfig;
import com.example.blockhighlighter.config.SelectedBlockManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlockScanner {
	private static final int BLOCKS_PER_TICK = 4_096;
	private static final int SCAN_BATCH_SIZE = 256;
	private static final long MAX_SCAN_NANOS_PER_TICK = 2_000_000L;
	private static final int DISCOVERY_INTERVAL_TICKS = 20;

	private final HighlightConfig config;
	private final SelectedBlockManager selectedBlocks;
	private final HighlightCache cache;
	private final RefreshCycle refreshCycle = new RefreshCycle();
	private final PendingBlockUpdates updates = new PendingBlockUpdates();
	private final ArrayDeque<ScanTask> queue = new ArrayDeque<>();
	private final Set<Long> queuedChunks = new HashSet<>();
	private final Map<Long, ScanRegion> scannedRegions = new HashMap<>();
	private List<ChunkPos> nearbyChunks = List.of();
	private ClientWorld currentWorld;
	private ScanRegion region;
	private int ticks;
	private int scheduledSeconds = -1;

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
		if (world != currentWorld) {
			reset();
			currentWorld = world;
		}
		if (!config.enabled || selectedBlocks.idSet().isEmpty() || client.isPaused()) {
			cancelPeriodicWork();
			refreshCycle.reset();
			return;
		}

		BlockPos player = client.player.getBlockPos();
		ScanRegion next = ScanRegion.around(player.getX(), player.getY(), player.getZ(),
				config.radius, world.getBottomY(), world.getHeight());
		boolean moved = !next.equals(region);
		if (moved) {
			region = next;
			rebuildNearbyChunks(player);
			pruneOutsideRegion(world);
		}
		if (config.refreshSeconds != scheduledSeconds) {
			cancelPeriodicWork();
			refreshCycle.reset();
			scheduledSeconds = config.refreshSeconds;
		}
		if (moved || ticks++ % DISCOVERY_INTERVAL_TICKS == 0) {
			discoverLoadedChunks(world, player.getY());
		}
		if (refreshCycle.poll(System.nanoTime(), scheduledSeconds)) {
			updates.beginCycle();
			schedulePeriodicRefresh(world, player.getY());
		}
		processQueue(world);
	}

	public void blockUpdated(ClientWorld world, BlockPos pos) {
		if (world == currentWorld && config.enabled && region != null
				&& region.contains(pos.getX(), pos.getY(), pos.getZ()) && !selectedBlocks.idSet().isEmpty()) {
			updates.record(pos.asLong());
		}
	}

	public void chunkLoaded(ClientWorld world, ChunkPos chunk) {
		if (world == currentWorld) {
			removeChunk(chunk.toLong());
			// A full chunk packet can replace every state without individual updates.
			// Discovery will rescan its bounded region on the next client tick.
			ticks = 0;
		}
	}

	public void chunkUnloaded(ClientWorld world, ChunkPos chunk) {
		if (world == currentWorld) {
			removeChunk(chunk.toLong());
		}
	}

	public void invalidateLoadedChunks() {
		queue.clear();
		queuedChunks.clear();
		scannedRegions.clear();
		updates.clear();
		cache.clear();
		refreshCycle.reset();
		ticks = 0;
	}

	public void reset() {
		invalidateLoadedChunks();
		currentWorld = null;
		region = null;
		nearbyChunks = List.of();
		scheduledSeconds = -1;
	}

	private void rebuildNearbyChunks(BlockPos player) {
		List<ChunkPos> chunks = new ArrayList<>();
		if (region.volume() > 0) {
			for (int x = region.minX() >> 4; x <= region.maxX() >> 4; x++) {
				for (int z = region.minZ() >> 4; z <= region.maxZ() >> 4; z++) {
					chunks.add(new ChunkPos(x, z));
				}
			}
		}
		ChunkPos center = new ChunkPos(player);
		chunks.sort(Comparator.comparingLong(chunk -> {
			long dx = (long) chunk.x - center.x;
			long dz = (long) chunk.z - center.z;
			return dx * dx + dz * dz;
		}));
		nearbyChunks = chunks;
	}

	private void discoverLoadedChunks(ClientWorld world, int playerY) {
		for (ChunkPos chunk : nearbyChunks) {
			ScanRegion target = region.inChunk(chunk.x, chunk.z);
			ScanRegion previous = scannedRegions.get(chunk.toLong());
			if (world.isChunkLoaded(chunk.x, chunk.z) && (previous == null || !previous.contains(target))) {
				enqueue(chunk, target, playerY, true);
			}
		}
	}

	private void schedulePeriodicRefresh(ClientWorld world, int playerY) {
		for (ChunkPos chunk : nearbyChunks) {
			if (world.isChunkLoaded(chunk.x, chunk.z)) {
				enqueue(chunk, region.inChunk(chunk.x, chunk.z), playerY, false);
			}
		}
	}

	private void enqueue(ChunkPos chunk, ScanRegion target, int playerY, boolean discovery) {
		if (queuedChunks.add(chunk.toLong())) {
			ScanTask task = new ScanTask(chunk, target, playerY, discovery);
			queue.addLast(task);
		}
	}

	private void processQueue(ClientWorld world) {
		int budget = BLOCKS_PER_TICK;
		long deadline = System.nanoTime() + MAX_SCAN_NANOS_PER_TICK;
		// Eligible updates have priority over both initial scans and periodic passes.
		while (budget > 0 && System.nanoTime() < deadline) {
			Long packed = updates.pollReady();
			if (packed == null) {
				break;
			}
			BlockPos pos = BlockPos.fromLong(packed);
			WorldChunk chunk = world.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4, false);
			if (chunk != null && region.contains(pos.getX(), pos.getY(), pos.getZ())) {
				BlockState state = chunk.getBlockState(pos);
				cache.setMatch(chunk.getPos().toLong(), packed, matches(state));
			}
			budget--;
		}
		while (budget > 0 && !queue.isEmpty() && System.nanoTime() < deadline) {
			ScanTask task = queue.peekFirst();
			WorldChunk chunk = world.getChunkManager().getWorldChunk(task.chunk.x, task.chunk.z, false);
			if (chunk == null) {
				removeChunk(task.chunk.toLong());
				continue;
			}
			int processed = task.scan(chunk, Math.min(budget, SCAN_BATCH_SIZE), deadline);
			budget -= processed;
			if (task.complete()) {
				scannedRegions.put(task.chunk.toLong(), task.target);
				queue.removeFirst();
				queuedChunks.remove(task.chunk.toLong());
			} else if (processed == 0) {
				break;
			}
		}
	}

	private boolean matches(BlockState state) {
		return !state.isAir() && selectedBlocks.isSelected(state.getBlock());
	}

	private void cancelPeriodicWork() {
		Iterator<ScanTask> iterator = queue.iterator();
		while (iterator.hasNext()) {
			ScanTask task = iterator.next();
			if (!task.discovery) {
				queuedChunks.remove(task.chunk.toLong());
				iterator.remove();
			}
		}
		updates.postponeReady();
	}

	private void pruneOutsideRegion(ClientWorld world) {
		Set<Long> known = new HashSet<>(scannedRegions.keySet());
		known.addAll(cache.chunkKeys());
		known.addAll(queuedChunks);
		for (long key : known) {
			int x = ChunkPos.getPackedX(key);
			int z = ChunkPos.getPackedZ(key);
			if (!region.includesChunk(x, z) || !world.isChunkLoaded(x, z)) {
				removeChunk(key);
			}
		}
		updates.retain(packed -> region.contains(BlockPos.unpackLongX(packed),
				BlockPos.unpackLongY(packed), BlockPos.unpackLongZ(packed)));
	}

	private void removeChunk(long key) {
		cache.removeChunk(key);
		scannedRegions.remove(key);
		if (queuedChunks.remove(key)) {
			queue.removeIf(task -> task.chunk.toLong() == key);
		}
		updates.retain(packed -> ChunkPos.toLong(BlockPos.unpackLongX(packed) >> 4,
				BlockPos.unpackLongZ(packed) >> 4) != key);
	}

	private final class ScanTask {
		private final ChunkPos chunk;
		private final ScanRegion target;
		private final int[] yOrder;
		private final boolean discovery;
		private final int width;
		private final int planeSize;
		private byte[] sectionSelection;
		private int index;

		private ScanTask(ChunkPos chunk, ScanRegion target, int playerY, boolean discovery) {
			this.chunk = chunk;
			this.target = target;
			this.discovery = discovery;
			this.width = target.maxX() - target.minX() + 1;
			this.planeSize = width * (target.maxZ() - target.minZ() + 1);
			this.yOrder = verticalOrder(playerY, target.minY(), target.maxY());
		}

		private int scan(WorldChunk worldChunk, int budget, long deadline) {
			ChunkSection[] sections = worldChunk.getSectionArray();
			if (sectionSelection == null) {
				sectionSelection = new byte[sections.length];
			}
			BlockPos.Mutable pos = new BlockPos.Mutable();
			int visited = 0;
			while (!complete() && visited < budget && System.nanoTime() < deadline) {
				int y = yOrder[index / planeSize];
				int sectionIndex = (y - currentWorld.getBottomY()) >> 4;
				if (sectionSelection[sectionIndex] == 0) {
					ChunkSection section = sections[sectionIndex];
					sectionSelection[sectionIndex] = (byte) (!section.isEmpty() && section.hasAny(BlockScanner.this::matches) ? 2 : 1);
				}
				if (sectionSelection[sectionIndex] == 1 && !cache.hasMatchesInSection(chunk.toLong(), y >> 4)) {
					index += planeSize - index % planeSize;
					continue;
				}
				int horizontal = index % planeSize;
				pos.set(target.minX() + horizontal % width, y, target.minZ() + horizontal / width);
				long packed = pos.asLong();
				// A background pass must not publish a newly changed position before
				// that update becomes eligible at the configured refresh deadline.
				if (!updates.isWaiting(packed)) {
					cache.setMatch(chunk.toLong(), packed, matches(worldChunk.getBlockState(pos)));
				}
				index++;
				visited++;
			}
			return visited;
		}

		private boolean complete() {
			return index >= yOrder.length * planeSize;
		}
	}

	private static int[] verticalOrder(int playerY, int bottomY, int topY) {
		int[] order = new int[Math.max(0, topY - bottomY + 1)];
		int center = Math.max(bottomY, Math.min(topY, playerY));
		int count = 0;
		for (int distance = 0; count < order.length; distance++) {
			if (center + distance <= topY) {
				order[count++] = center + distance;
			}
			if (distance > 0 && center - distance >= bottomY) {
				order[count++] = center - distance;
			}
		}
		return order;
	}
}
