package com.example.blockhighlighter.scan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingBlockUpdatesTest {
	@Test
	void changesWaitUntilTheRefreshDeadlineAndAreDeduplicated() {
		PendingBlockUpdates updates = new PendingBlockUpdates();
		updates.record(1L);
		updates.record(1L);
		assertTrue(updates.isWaiting(1L));
		assertNull(updates.pollReady());
		updates.beginCycle();
		assertFalse(updates.isWaiting(1L));
		assertEquals(1L, updates.pollReady());
		assertNull(updates.pollReady());
	}

	@Test
	void laterChangesWaitForTheNextCycle() {
		PendingBlockUpdates updates = new PendingBlockUpdates();
		updates.record(1L);
		updates.beginCycle();
		updates.record(2L);
		assertEquals(1L, updates.pollReady());
		assertNull(updates.pollReady());
		updates.beginCycle();
		assertEquals(2L, updates.pollReady());
	}

	@Test
	void repeatedChangeDoesNotPublishTheNewStateAtAnOldDeadline() {
		PendingBlockUpdates updates = new PendingBlockUpdates();
		updates.record(1L);
		updates.beginCycle();
		updates.record(1L);
		assertNull(updates.pollReady());
		updates.beginCycle();
		assertEquals(1L, updates.pollReady());
	}

	@Test
	void intervalChangePostponesOldWorkWithoutLosingIt() {
		PendingBlockUpdates updates = new PendingBlockUpdates();
		updates.record(1L);
		updates.beginCycle();
		updates.record(2L);
		updates.postponeReady();
		assertNull(updates.pollReady());
		assertTrue(updates.isWaiting(1L));
		assertTrue(updates.isWaiting(2L));
		updates.beginCycle();
		assertEquals(2L, updates.pollReady());
		assertEquals(1L, updates.pollReady());
	}

	@Test
	void droppingUnloadedOrOutOfRangePositionsKeepsOtherUpdates() {
		PendingBlockUpdates updates = new PendingBlockUpdates();
		updates.record(1L);
		updates.record(2L);
		updates.beginCycle();
		updates.record(3L);
		updates.retain(position -> position != 2L && position != 3L);
		assertEquals(1L, updates.pollReady());
		updates.beginCycle();
		assertNull(updates.pollReady());
	}

	@Test
	void resettingWorldOrSelectionDropsBothQueues() {
		PendingBlockUpdates updates = new PendingBlockUpdates();
		updates.record(1L);
		updates.beginCycle();
		updates.record(2L);
		updates.clear();
		assertNull(updates.pollReady());
		assertFalse(updates.isWaiting(2L));
	}
}
