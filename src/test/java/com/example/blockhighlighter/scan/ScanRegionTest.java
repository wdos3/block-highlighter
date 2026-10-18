package com.example.blockhighlighter.scan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanRegionTest {
	@Test
	void minimumRadiusDoesNotScanTheFullWorldHeightOrExtraChunkRings() {
		ScanRegion region = ScanRegion.around(5, 169, 0, 16, -64, 384);
		assertEquals(33L * 33 * 33, region.volume());
		assertEquals(153, region.minY());
		assertEquals(185, region.maxY());
		assertTrue(region.includesChunk(-1, -1));
		assertTrue(region.includesChunk(1, 1));
		assertFalse(region.includesChunk(2, 0));
	}

	@Test
	void partitionsTheRegionExactlyAcrossPositiveAndNegativeChunks() {
		ScanRegion region = ScanRegion.around(-5, 10, -17, 16, -64, 384);
		long volume = 0;
		for (int x = region.minX() >> 4; x <= region.maxX() >> 4; x++) {
			for (int z = region.minZ() >> 4; z <= region.maxZ() >> 4; z++) {
				ScanRegion slice = region.inChunk(x, z);
				assertTrue(region.contains(slice));
				assertTrue(slice.maxX() - slice.minX() < 16);
				assertTrue(slice.maxZ() - slice.minZ() < 16);
				volume += slice.volume();
			}
		}
		assertEquals(region.volume(), volume);
	}

	@Test
	void clipsBothWorldHeightLimits() {
		assertEquals(-64, ScanRegion.around(0, -64, 0, 16, -64, 384).minY());
		assertEquals(319, ScanRegion.around(0, 319, 0, 16, -64, 384).maxY());
	}

	@Test
	void playerOutsideTheBuildLimitsCanHaveAnEmptyRegion() {
		assertEquals(0, ScanRegion.around(0, 500, 0, 16, -64, 384).volume());
		assertEquals(0, ScanRegion.around(0, -100, 0, 16, -64, 384).volume());
	}

	@Test
	void containsChecksAllThreeAxesAndInclusiveEdges() {
		ScanRegion region = ScanRegion.around(0, 100, 0, 16, -64, 384);
		assertTrue(region.contains(-16, 84, 16));
		assertFalse(region.contains(-17, 100, 0));
		assertFalse(region.contains(0, 117, 0));
		assertFalse(region.contains(0, 100, 17));
	}
}
