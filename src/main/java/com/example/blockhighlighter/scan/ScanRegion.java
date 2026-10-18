package com.example.blockhighlighter.scan;

record ScanRegion(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
	static ScanRegion around(int x, int y, int z, int radius, int bottomY, int height) {
		return new ScanRegion(x - radius, Math.max(bottomY, y - radius), z - radius,
				x + radius, Math.min(bottomY + height - 1, y + radius), z + radius);
	}

	ScanRegion inChunk(int chunkX, int chunkZ) {
		return new ScanRegion(Math.max(minX, chunkX << 4), minY, Math.max(minZ, chunkZ << 4),
				Math.min(maxX, (chunkX << 4) + 15), maxY, Math.min(maxZ, (chunkZ << 4) + 15));
	}

	boolean contains(int x, int y, int z) {
		return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
	}

	boolean contains(ScanRegion other) {
		return minX <= other.minX && maxX >= other.maxX && minY <= other.minY
				&& maxY >= other.maxY && minZ <= other.minZ && maxZ >= other.maxZ;
	}

	boolean includesChunk(int chunkX, int chunkZ) {
		return chunkX >= (minX >> 4) && chunkX <= (maxX >> 4)
				&& chunkZ >= (minZ >> 4) && chunkZ <= (maxZ >> 4);
	}

	long volume() {
		return (long) Math.max(0, maxX - minX + 1) * Math.max(0, maxY - minY + 1)
				* Math.max(0, maxZ - minZ + 1);
	}
}
