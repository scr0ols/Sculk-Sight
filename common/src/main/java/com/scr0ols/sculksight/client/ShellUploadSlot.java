package com.scr0ols.sculksight.client;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;

/** One pending result per shell, handed from a worker thread to the render thread. */
public final class ShellUploadSlot<T extends ShellUploadSlot.Payload> implements AutoCloseable {

	/** A result this slot can own: closeable, with no checked exception to plumb through. */
	public interface Payload extends AutoCloseable {
		@Override
		void close();
	}

	private record Pending<T extends Payload>(long revision, T payload) {
	}

	private final AtomicReference<@Nullable Pending<T>> pending = new AtomicReference<>();

	private final AtomicBoolean closed = new AtomicBoolean();

	/** Worker thread. Offers a finished result stamped with the revision it was solved for. */
	public boolean offer(long revision, T result) {
		while (true) {
			if (closed.get()) {
				result.close();
				return false;
			}

			Pending<T> current = pending.get();

			if (current != null && current.revision() >= revision) {
				result.close();
				return false;
			}

			if (pending.compareAndSet(current, new Pending<>(revision, result))) {
				if (current != null) {
					current.payload().close();
				}

				if (closed.get()) {
					drain();
					return false;
				}

				return true;
			}
		}
	}

	/** Render thread. Returns the pending result and clears the slot; the caller then owns it. */
	public @Nullable T take() {
		Pending<T> taken = pending.getAndSet(null);
		return taken == null ? null : taken.payload();
	}

	/** Render thread. Closes anything still pending; subsequent offers close their argument. */
	@Override
	public void close() {
		closed.set(true);
		drain();
	}

	private void drain() {
		Pending<T> taken = pending.getAndSet(null);

		if (taken != null) {
			taken.payload().close();
		}
	}
}
