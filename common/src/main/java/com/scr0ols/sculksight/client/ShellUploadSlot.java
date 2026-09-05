package com.scr0ols.sculksight.client;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;

/**
 * One pending result per shell, handed from a worker thread to the render thread.
 * ARCHITECTURE.md section 6.3, DECISIONS.md ADR-017, its payload widened by ADR-048.
 *
 * <p><b>This exists because vanilla has no such queue.</b> R13 point 14 established that
 * {@code RenderSystem.queueFencedTask} is not a worker-to-render hand-off - enqueuing is itself a
 * GPU call, so a caller must already be on the render thread, and its backing deque carries no
 * synchronisation. The mod supplies the hand-off itself, which is why this is a named component of
 * the architecture rather than a platform service.
 *
 * <p><b>Generic over the payload, and deliberately with no Minecraft type in sight.</b> The
 * payload was a bare {@code MeshData} until ADR-048 widened it to also carry the encode's own
 * {@code ByteBufferBuilder} (RESEARCH-LOG.md R19) and the stats/timing fields ADR-026 had been
 * carrying beside the slot instead of inside it. Rather than name that pair-plus-two-numbers type
 * here, this class only ever asks its payload to close itself, which is what lets it need nothing
 * from {@code com.mojang} or {@code net.minecraft} at all - the same placement rule DECISIONS.md
 * ADR-046 already applies to {@link ShellWorkerExecutor}: a class needing no true client-jar type
 * belongs in common's own bare classpath, where JUnit can reach it directly, rather than in
 * {@code src/client/java}. {@link ShellEntry} is where the real payload, {@code ShellSolveResult},
 * is named.
 *
 * <p><b>{@link Payload} narrows {@link AutoCloseable} rather than being reused as-is</b>, purely to
 * drop its checked {@code Exception}: nothing this project ever hands to this slot throws one on
 * close, and generic code written against the wider interface would have to plan for one it can
 * never usefully handle.
 *
 * <p><b>The ownership rule, complete: whoever removes a reference from the slot closes it.</b>
 * A result here holds native memory the garbage collector will not reclaim (R13 points 7 and 10 -
 * true of {@code MeshData} and the {@code ByteBufferBuilder} behind it), so exactly one thread must
 * close each one. The worker that displaces an older pending result closes what it displaced; the
 * worker whose result is rejected as stale closes its own; the render thread closes what
 * {@link #take()} gave it, after the bytes have been copied; and {@link #close()} closes whatever
 * is left.
 *
 * <p><b>Why that is free of double-closes without a lock, mechanically.</b>
 * {@link AtomicReference#getAndSet} and a successful {@code compareAndSet} each hand a given
 * reference to exactly one caller, so two threads cannot both receive the same pending result and
 * therefore cannot both decide to close the same payload. A plain field with a null check would not
 * have this property: two workers finishing at once could read the same old reference and both
 * close it, which is a native-memory double-free.
 *
 * <p><b>Why the revision is needed.</b> Without it, a solve started before an invalidation and
 * finishing after a newer one would displace the fresher result and the mod would draw a stale
 * shell. Drawing the wrong shape is what PLAN.md section 1 ranks as worse than drawing nothing.
 */
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

	/**
	 * Worker thread. Offers a finished result stamped with the revision it was solved for.
	 *
	 * @return false if a newer result was already pending or the slot is closed, in which case
	 *         the offered result has been closed here and the caller must not touch it again
	 */
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

				// The close-after-close race: close() sets the flag and then drains, so an offer
				// that stored after that drain re-checks the flag here and drains again. Both
				// drains use getAndSet, so whichever runs second gets null and does nothing, and
				// whichever gets the reference closes it. A worker finishing after the world
				// unloaded therefore frees its own memory rather than leaking it.
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
