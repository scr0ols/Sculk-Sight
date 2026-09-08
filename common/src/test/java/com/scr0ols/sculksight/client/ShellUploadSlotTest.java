package com.scr0ols.sculksight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * ARCHITECTURE.md section 6.3's worker-to-render hand-off and its ownership rule, DECISIONS.md
 * ADR-017, widened to a generic payload by ADR-048's own wiring session.
 *
 * <p><b>This is the one place in the mod where a mistake frees native memory twice rather than
 * drawing a wrong picture</b> (ADR-048's own consequence naming this test class as owed), so every
 * test below is written against the ownership rule directly - a payload's own close count - rather
 * than against what the slot happens to return, which a double-close bug could still get right by
 * accident.
 */
class ShellUploadSlotTest {

	/** A minimal payload: no native memory, just a count of how many times close() ran. */
	private static final class RecordingPayload implements ShellUploadSlot.Payload {

		private final AtomicInteger closes = new AtomicInteger();

		@Override
		public void close() {
			closes.incrementAndGet();
		}

		int closeCount() {
			return closes.get();
		}
	}

	@Test
	void offerIntoAnEmptySlotSucceedsAndTakeReturnsIt() {
		ShellUploadSlot<RecordingPayload> slot = new ShellUploadSlot<>();
		RecordingPayload payload = new RecordingPayload();

		assertTrue(slot.offer(1L, payload));
		assertSame(payload, slot.take());
		assertEquals(0, payload.closeCount(), "take() hands ownership to the caller, not close it itself");
	}

	@Test
	void takeClearsTheSlot() {
		ShellUploadSlot<RecordingPayload> slot = new ShellUploadSlot<>();
		slot.offer(1L, new RecordingPayload());

		slot.take();

		assertNull(slot.take(), "a second take() must find nothing left to return");
	}

	@Test
	void aNewerRevisionDisplacesAndClosesTheOlderPendingResult() {
		ShellUploadSlot<RecordingPayload> slot = new ShellUploadSlot<>();
		RecordingPayload older = new RecordingPayload();
		RecordingPayload newer = new RecordingPayload();

		assertTrue(slot.offer(1L, older));
		assertTrue(slot.offer(2L, newer));

		assertEquals(1, older.closeCount(), "the displaced result must be closed exactly once");
		assertEquals(0, newer.closeCount());
		assertSame(newer, slot.take());
	}

	@Test
	void aRevisionNotNewerThanThePendingOneIsRejectedAndClosesItself() {
		ShellUploadSlot<RecordingPayload> slot = new ShellUploadSlot<>();
		RecordingPayload pending = new RecordingPayload();
		RecordingPayload stale = new RecordingPayload();

		slot.offer(5L, pending);

		assertFalse(slot.offer(5L, stale), "an equal revision must not displace the pending result");
		assertFalse(slot.offer(4L, stale), "an older revision must not displace the pending result either");

		assertEquals(2, stale.closeCount(), "each rejected offer must close its own argument once");
		assertEquals(0, pending.closeCount(), "the pending result must survive both rejected offers");
		assertSame(pending, slot.take());
	}

	@Test
	void closeDrainsAndClosesWhateverIsStillPending() {
		ShellUploadSlot<RecordingPayload> slot = new ShellUploadSlot<>();
		RecordingPayload payload = new RecordingPayload();
		slot.offer(1L, payload);

		slot.close();

		assertEquals(1, payload.closeCount());
		assertNull(slot.take(), "close() must leave nothing behind for a later take()");
	}

	@Test
	void closeIsIdempotentAndDoesNotDoubleCloseAnAlreadyDrainedResult() {
		ShellUploadSlot<RecordingPayload> slot = new ShellUploadSlot<>();
		RecordingPayload payload = new RecordingPayload();
		slot.offer(1L, payload);

		slot.close();
		slot.close();

		assertEquals(1, payload.closeCount(), "a second close() must not touch a result the first already closed");
	}

	@Test
	void anOfferAfterCloseIsDeclinedAndClosesTheOfferedResultImmediately() {
		ShellUploadSlot<RecordingPayload> slot = new ShellUploadSlot<>();
		slot.close();

		RecordingPayload payload = new RecordingPayload();

		assertFalse(slot.offer(1L, payload));
		assertEquals(1, payload.closeCount());
		assertNull(slot.take());
	}

	@Test
	void aResultAlreadyTakenIsNotTouchedByAFollowingClose() {
		// take() hands ownership to its caller; close() must only ever act on what is still
		// sitting in the slot, never on something already handed out - the same "whoever removes
		// the reference from the slot closes it" rule ARCHITECTURE.md section 6.3 states.
		ShellUploadSlot<RecordingPayload> slot = new ShellUploadSlot<>();
		RecordingPayload payload = new RecordingPayload();
		slot.offer(1L, payload);

		RecordingPayload taken = slot.take();
		slot.close();

		assertEquals(0, taken.closeCount(), "the caller of take() owns the close, not close() itself");
	}
}
